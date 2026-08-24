# Keep the JS-bridge surface reachable from the web app via addJavascriptInterface.
-keepclassmembers class pk.myhisaab.sdk.internal.webview.WebAppBridge {
    @android.webkit.JavascriptInterface <methods>;
}
