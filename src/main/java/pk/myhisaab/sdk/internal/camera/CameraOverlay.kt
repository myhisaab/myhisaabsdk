package pk.myhisaab.sdk.internal.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import pk.myhisaab.sdk.R
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/**
 * In-app camera presented as a custom resizable bottom sheet over the current
 * page — an Android counterpart of the iOS `CameraViewController`. Opens at the
 * half detent; drag the grabber to resize; flick down / tap scrim / back to
 * dismiss.
 *
 * Self-contained and defensive by design:
 *  - **No ViewModel.** State lives in `remember`, so the overlay does NOT require
 *    a `ViewModelStoreOwner` in the host's composition (a missing one would
 *    otherwise crash the instant the camera opens).
 *  - **CameraX is bound only after permission is granted, inside try/catch.** A
 *    device or dependency failure surfaces as an error message and closes the
 *    overlay instead of crashing the host Activity.
 *
 * UI-only and web-agnostic: the caller delivers [onImageCaptured]'s file to the
 * web layer; [onClose] fires when dismissed without a capture.
 */
@Composable
internal fun CameraOverlay(
    onImageCaptured: (File) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var useBackCamera by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(context.hasPermission(Manifest.permission.CAMERA))
    }
    var permissionDenied by remember { mutableStateOf(value = false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionDenied = !granted
    }

    // Constructing the controller touches CameraX, which throws
    // IllegalStateException if no camera backend (camera-camera2) is on the
    // runtime classpath. Guard it so a mis-integrated host shows an error
    // instead of crashing at the constructor.
    val controller = remember {
        runCatching {
            LifecycleCameraController(context).apply {
                setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            }
        }.getOrNull()
    }

    LaunchedEffect(Unit) {
        if (controller == null) {
            errorMessage = context.getString(R.string.myhisaab_camera_unavailable)
        }
    }

    // Bind ONLY once permission is granted, and never let a CameraX failure crash
    // the host — surface it and close. Re-runs on lens change to rebind.
    LaunchedEffect(hasCameraPermission, useBackCamera) {
        val c = controller ?: return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect
        try {
            c.cameraSelector =
                if (useBackCamera) CameraSelector.DEFAULT_BACK_CAMERA
                else CameraSelector.DEFAULT_FRONT_CAMERA
            c.bindToLifecycle(lifecycleOwner)
        } catch (t: Throwable) {
            errorMessage = context.getString(R.string.myhisaab_camera_capture_failed)
        }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { controller?.unbind() } }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(errorMessage) {
        val message = errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            errorMessage = null
        }
    }

    val toggleLens: () -> Unit = { if (!isProcessing) useBackCamera = !useBackCamera }

    val onShutter: () -> Unit = {
        val c = controller
        if (!isProcessing && c != null) {
            capturePhoto(
                context = context,
                controller = c,
                onStart = { isProcessing = true },
                onSuccess = { file ->
                    isProcessing = false
                    onImageCaptured(file)
                },
                onFailure = { msg ->
                    isProcessing = false
                    errorMessage = msg
                },
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenHeightPx = constraints.maxHeight.toFloat()
        val screenHeightDp = with(density) { screenHeightPx.toDp() }

        val fullHeightPx = screenHeightPx * FULL_HEIGHT_FRACTION
        val halfHeightPx = screenHeightPx * HALF_HEIGHT_FRACTION
        val hiddenHeightPx = 0f

        val sheetHeight = remember(screenHeightPx) { Animatable(hiddenHeightPx) }

        LaunchedEffect(screenHeightPx) {
            sheetHeight.animateTo(halfHeightPx, tween(durationMillis = 280))
        }

        val dismiss: () -> Unit = {
            scope.launch {
                sheetHeight.animateTo(hiddenHeightPx, tween(durationMillis = 220))
                onClose()
            }
        }

        BackHandler(enabled = !isProcessing) { dismiss() }

        val dragState = rememberDraggableState { delta ->
            scope.launch {
                sheetHeight.snapTo((sheetHeight.value - delta).coerceIn(hiddenHeightPx, fullHeightPx))
            }
        }
        val dragHandleModifier = Modifier.draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            enabled = !isProcessing,
            onDragStopped = { velocity ->
                val target = pickDetent(sheetHeight.value, velocity, hiddenHeightPx, halfHeightPx, fullHeightPx)
                sheetHeight.animateTo(target, tween(durationMillis = 250))
                if (target == hiddenHeightPx) onClose()
            },
        )

        val sheetHeightDp = with(density) { sheetHeight.value.coerceAtLeast(0f).toDp() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !isProcessing,
                    onClick = dismiss,
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeightDp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            if (controller == null) {
                CameraUnavailable(
                    onCancel = dismiss,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            } else if (hasCameraPermission) {
                CameraPreview(
                    controller = controller,
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(screenHeightDp)
                        .align(Alignment.BottomCenter),
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ShutterButton(
                        isProcessing = isProcessing,
                        onClick = onShutter,
                    )
                }
            } else {
                CameraPermissionRequest(
                    permanentlyDenied = permissionDenied,
                    onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = { context.openAppSettings() },
                    onCancel = dismiss,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            SheetTopBar(
                dragHandleModifier = dragHandleModifier,
                showControls = hasCameraPermission && controller != null,
                controlsEnabled = !isProcessing,
                onClose = dismiss,
                onToggleLens = toggleLens,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}

@Composable
private fun CameraPreview(
    controller: LifecycleCameraController,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
    )
}

@Composable
private fun SheetTopBar(
    dragHandleModifier: Modifier,
    showControls: Boolean,
    controlsEnabled: Boolean,
    onClose: () -> Unit,
    onToggleLens: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(dragHandleModifier)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.6f)),
            )
        }

        if (showControls) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OverlayIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.myhisaab_camera_close),
                    enabled = controlsEnabled,
                    onClick = onClose,
                )
                OverlayIconButton(
                    icon = Icons.Default.Cameraswitch,
                    contentDescription = stringResource(R.string.myhisaab_camera_switch),
                    enabled = controlsEnabled,
                    onClick = onToggleLens,
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    isProcessing: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .border(width = 4.dp, color = Color.White.copy(alpha = 0.7f), shape = CircleShape)
            .padding(6.dp)
            .background(color = Color.White, shape = CircleShape)
            .then(if (isProcessing) Modifier else Modifier.clickable(onClick = onClick)),
        contentAlignment = Alignment.Center,
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
        }
    }
}

@Composable
private fun OverlayIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(color = Color.Black.copy(alpha = 0.35f), shape = CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
        )
    }
}

@Composable
private fun CameraPermissionRequest(
    permanentlyDenied: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.myhisaab_camera_permission_rationale),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) {
            Text(text = stringResource(R.string.myhisaab_camera_grant_permission))
        }
        if (permanentlyDenied) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text(text = stringResource(R.string.myhisaab_open_settings), color = Color.White)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) {
            Text(text = stringResource(R.string.myhisaab_cancel), color = Color.White)
        }
    }
}

@Composable
private fun CameraUnavailable(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.myhisaab_camera_unavailable),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) {
            Text(text = stringResource(R.string.myhisaab_cancel), color = Color.White)
        }
    }
}

/* --------------------------- logic / helpers --------------------------- */

/**
 * Picks the sheet HEIGHT to settle on when a drag ends. A fast flick snaps to the
 * next detent in the flick direction; otherwise snap to the nearest. Velocity is
 * px/s: positive = down (shrink), negative = up (grow). Pure and unit-testable.
 */
internal fun pickDetent(
    current: Float,
    velocity: Float,
    hidden: Float,
    half: Float,
    full: Float,
): Float {
    val detents = listOf(hidden, half, full)
    return when {
        velocity < -FLICK_VELOCITY_PX_S -> detents.firstOrNull { it > current + 1f } ?: full
        velocity > FLICK_VELOCITY_PX_S -> detents.lastOrNull { it < current - 1f } ?: hidden
        else -> detents.minByOrNull { abs(it - current) } ?: half
    }
}

/**
 * Captures a still. Every failure path — temp-file creation, the CameraX call
 * itself, and the async save callback — routes to [onFailure] so nothing escapes
 * as an uncaught exception.
 */
private fun capturePhoto(
    context: Context,
    controller: LifecycleCameraController,
    onStart: () -> Unit,
    onSuccess: (File) -> Unit,
    onFailure: (String) -> Unit,
) {
    val fallback = context.getString(R.string.myhisaab_camera_capture_failed)

    val photoFile = try {
        createTempImageFile(context)
    } catch (t: Throwable) {
        onFailure(fallback)
        return
    }

    onStart()
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    try {
        controller.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onSuccess(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    runCatching { photoFile.delete() }
                    onFailure(exception.message ?: fallback)
                }
            },
        )
    } catch (t: Throwable) {
        runCatching { photoFile.delete() }
        onFailure(t.message ?: fallback)
    }
}

/** Temp file under the app's external cache (matches `myhisaab_file_paths.xml`). */
private fun createTempImageFile(context: Context): File {
    val directory = File(context.externalCacheDir, "Images").apply { if (!exists()) mkdirs() }
    return File.createTempFile("CAPTURE_", ".jpg", directory)
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

private const val FULL_HEIGHT_FRACTION = 0.94f
private const val HALF_HEIGHT_FRACTION = 0.5f
private const val SCRIM_ALPHA = 0.45f
private const val FLICK_VELOCITY_PX_S = 800f
