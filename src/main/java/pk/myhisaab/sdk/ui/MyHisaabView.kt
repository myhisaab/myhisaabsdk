package pk.myhisaab.sdk.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import pk.myhisaab.sdk.R
import pk.myhisaab.sdk.internal.camera.CameraOverlay
import pk.myhisaab.sdk.internal.webview.FcmTokenScript
import pk.myhisaab.sdk.internal.webview.MyHisaabUrls
import pk.myhisaab.sdk.internal.webview.WebAppBridge
import pk.myhisaab.sdk.model.MyHisaabEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Embeds the MyHisaab web app in a [WebView], loading
 * `https://app.myhisaab.pk/login?clientId=<clientId>`.
 *
 * Self-contained: no Hilt, no Firebase. The host app owns push and simply passes
 * the latest [fcmToken] in — the SDK injects it into the page (and re-injects on
 * reloads / token rotation). Handles edge-to-edge rendering, WebView camera/mic
 * permission prompts, HTML file inputs (native camera overlay or gallery), a JS
 * bridge to open the camera, and system back navigation.
 *
 * @param clientId tenant/client identifier appended as the `clientId` query param.
 * @param environment which MyHisaab host to load. Defaults to
 *   [MyHisaabEnvironment.PRODUCTION].
 * @param fcmToken optional FCM registration token to hand to the web app; pass
 *   `null` if the host app has no push token yet. Update it to re-inject.
 * @param onPageStarted optional; invoked on the main thread each time a page
 *   begins loading, with the target URL.
 * @param onPageFinished optional; invoked on the main thread each time a page
 *   finishes loading, with the loaded URL. Useful for detecting login completion
 *   (a redirect away from `/login`) so the host can dismiss the view.
 * @param onError optional; invoked when the main frame fails to load, with the
 *   error description. The built-in retry overlay is still shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyHisaabView(
    clientId: String,
    modifier: Modifier = Modifier,
    environment: MyHisaabEnvironment = MyHisaabEnvironment.PRODUCTION,
    fcmToken: String? = null,
    onPageStarted: ((url: String) -> Unit)? = null,
    onPageFinished: ((url: String) -> Unit)? = null,
    onError: ((description: String) -> Unit)? = null,
) {
    val context: Context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val scope: CoroutineScope = rememberCoroutineScope()

    // Keep the WebView's long-lived clients pointed at the latest callback lambdas
    // without recreating the WebView when a caller passes new lambda instances.
    val currentOnPageStarted by rememberUpdatedState(onPageStarted)
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val currentOnError by rememberUpdatedState(onError)

    val url: String = remember(environment, clientId) {
        MyHisaabUrls.loginUrl(environment, clientId)
    }

    // --- Internal WebView UI state (replaces the app's Hilt ViewModel) ---
    var isLoading: Boolean by remember { mutableStateOf(true) }
    var progress: Int by remember { mutableIntStateOf(0) }
    var errorMessage: String? by remember { mutableStateOf<String?>(null) }
    // Monotonic counter: navigations wipe window-scoped globals, so the FCM
    // injector re-runs after each completed load (0 = nothing loaded yet).
    var pageLoadCount: Int by remember { mutableIntStateOf(0) }

    var webViewRef: WebView? by remember { mutableStateOf(null) }
    var canGoBack: Boolean by remember { mutableStateOf(false) }
    var showCameraOverlay: Boolean by remember { mutableStateOf(false) }

    var filePathCallback: ValueCallback<Array<Uri>>? by remember { mutableStateOf(null) }
    var showImageSourceSheet: Boolean by remember { mutableStateOf(false) }
    val sheetState: SheetState = rememberModalBottomSheetState()

    val galleryLauncher: ManagedActivityResultLauncher<String, List<@JvmSuppressWildcards Uri>> =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents(),
            onResult = { uris ->
                filePathCallback?.onReceiveValue(if (uris.isNotEmpty()) uris.toTypedArray() else null)
                filePathCallback = null
            }
        )

    var pendingPermissionRequest: PermissionRequest? by remember { mutableStateOf<PermissionRequest?>(null) }

    val permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, @JvmSuppressWildcards Boolean>> =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { permissions ->
                val allGranted: Boolean = permissions.values.all { it }
                if (allGranted) {
                    pendingPermissionRequest?.grant(pendingPermissionRequest?.resources)
                } else {
                    pendingPermissionRequest?.deny()
                }
                pendingPermissionRequest = null
            }
        )

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    // --- FCM token injection (mirrors iOS `injectFCMToken`) ---
    LaunchedEffect(fcmToken, pageLoadCount, webViewRef) {
        val token: String? = fcmToken
        val webView: WebView? = webViewRef
        if (token.isNullOrBlank() || pageLoadCount == 0 || webView == null) {
            return@LaunchedEffect
        }
        webView.evaluateJavascript(FcmTokenScript.build(token)) { result ->
            Log.d("MyHisaabView", "FCM token injected into web (result=$result)")
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webViewRef?.onPause()
                Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                Lifecycle.Event.ON_DESTROY -> {
                    webViewRef?.destroy()
                    webViewRef = null
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isLoading && progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            configureSettings()

                            webViewClient = createWebViewClient(
                                onPageStarted = { pageUrl ->
                                    isLoading = true
                                    errorMessage = null
                                    currentOnPageStarted?.invoke(pageUrl.orEmpty())
                                },
                                onPageFinished = { view, pageUrl ->
                                    canGoBack = view?.canGoBack() == true
                                    isLoading = false
                                    progress = 100
                                    pageLoadCount += 1
                                    currentOnPageFinished?.invoke(pageUrl.orEmpty())
                                },
                                onError = { msg ->
                                    isLoading = false
                                    errorMessage = msg
                                    currentOnError?.invoke(msg)
                                }
                            )

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }

                                override fun onPermissionRequest(request: PermissionRequest) {
                                    val resources = request.resources
                                    val permsNeeded = mutableListOf<String>()

                                    if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                                        permsNeeded.add(Manifest.permission.CAMERA)
                                    }
                                    if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                                        permsNeeded.add(Manifest.permission.RECORD_AUDIO)
                                    }

                                    if (permsNeeded.isEmpty()) {
                                        request.grant(resources)
                                        return
                                    }

                                    val allGranted = permsNeeded.all {
                                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                                    }

                                    if (allGranted) {
                                        request.grant(resources)
                                    } else {
                                        pendingPermissionRequest = request
                                        permissionLauncher.launch(permsNeeded.toTypedArray())
                                    }
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    callback: ValueCallback<Array<Uri>>,
                                    params: FileChooserParams,
                                ): Boolean {
                                    filePathCallback?.onReceiveValue(null)
                                    filePathCallback = callback
                                    showImageSourceSheet = true
                                    return true
                                }
                            }

                            addJavascriptInterface(
                                WebAppBridge(onOpenCamera = { showCameraOverlay = true }),
                                WebAppBridge.NAME,
                            )

                            webViewRef = this
                            loadUrl(url)
                        }
                    },
                    update = { _ -> }
                )
            }

            if (showImageSourceSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showImageSourceSheet = false
                        filePathCallback?.onReceiveValue(null)
                        filePathCallback = null
                    },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp)
                    ) {
                        Text(
                            text = "Choose Image Source",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )

                        SourceItem(
                            text = "Camera",
                            icon = Icons.Default.PhotoCamera,
                            onClick = {
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    showImageSourceSheet = false
                                    showCameraOverlay = true
                                }
                            }
                        )

                        SourceItem(
                            text = "Gallery",
                            icon = Icons.Default.PhotoLibrary,
                            onClick = {
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    showImageSourceSheet = false
                                    galleryLauncher.launch("image/*")
                                }
                            }
                        )
                    }
                }
            }

            errorMessage?.let { message ->
                ErrorOverlay(
                    message = message,
                    onRetry = {
                        isLoading = true
                        errorMessage = null
                        progress = 0
                        webViewRef?.reload()
                    }
                )
            }
        }

        if (showCameraOverlay) {
            CameraOverlay(
                onImageCaptured = { file ->
                    val callback = filePathCallback
                    if (callback != null) {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.myhisaab.fileprovider",
                            file,
                        )
                        callback.onReceiveValue(arrayOf(uri))
                        filePathCallback = null
                    } else {
                        webViewRef?.let { injectFileAsDataUrl(it, file, scope) }
                    }
                    showCameraOverlay = false
                },
                onClose = {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    showCameraOverlay = false
                },
            )
        }
    }
}

/**
 * JS-bridge delivery (mirrors iOS `sendImageToWeb`): encodes the captured file to
 * a Base64 data URL off the main thread and hands it to the page via the shared
 * `window.receiveCameraImage(...)` callback. No-op if the web app hasn't defined
 * the handler.
 */
private fun injectFileAsDataUrl(webView: WebView, file: File, scope: CoroutineScope) {
    scope.launch {
        val dataUrl = withContext(Dispatchers.IO) {
            "data:image/jpeg;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }
        val js = "if (typeof window.receiveCameraImage === 'function') " +
                "{ window.receiveCameraImage('$dataUrl'); }"
        webView.evaluateJavascript(js, null)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureSettings() {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    isLongClickable = true
    setOnLongClickListener { true }

    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        userAgentString = userAgentString.replace("wv", "")
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        mediaPlaybackRequiresUserGesture = false
        allowFileAccess = true
        allowContentAccess = true
    }
}

private fun createWebViewClient(
    onPageStarted: (String?) -> Unit,
    onPageFinished: (WebView?, String?) -> Unit,
    onError: (String) -> Unit,
): WebViewClient = object : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished(view, url)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) {
            onError(error?.description?.toString() ?: "Connection Error")
        }
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (detail?.didCrash() == false) {
                view?.reload()
                return true
            }
        }
        view?.reload()
        return true
    }
}

@Composable
private fun SourceItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(text = stringResource(id = R.string.myhisaab_retry))
            }
        }
    }
}
