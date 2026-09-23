# Keep JavascriptInterface methods (bridge between Kotlin and the CodeMirror WebView)
-keepclassmembers class com.codestudio.editor.EditorBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.codestudio.editor.EditorBridge { *; }
