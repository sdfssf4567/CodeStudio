package com.codestudio.editor

import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * پل ارتباطی WebView (CodeMirror) با Kotlin.
 * متدهای @JavascriptInterface از جاوااسکریپت صدا زده می‌شوند (thread جدا) —
 * همه عملیات UI باید با runOnUiThread انجام شود.
 */
class EditorBridge(private val act: MainActivity) {

    @JavascriptInterface
    fun saveFile(path: String, content: String) {
        if (path.isEmpty() || path.startsWith("@")) return
        val ok = FileManager.writeText(java.io.File(path), content)
        act.runOnUiThread {
            if (ok) {
                act.onFileSaved(path)
            } else {
                Toast.makeText(act, "خطا در ذخیره فایل", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun onDirty(path: String) {
        act.runOnUiThread { act.markDirty(path, true) }
    }

    @JavascriptInterface
    fun updateStatus(line: Int, col: Int) {
        act.runOnUiThread { act.setStatus(line, col) }
    }

    @JavascriptInterface
    fun toast(msg: String) {
        act.runOnUiThread { Toast.makeText(act, msg, Toast.LENGTH_SHORT).show() }
    }

    @JavascriptInterface
    fun log(msg: String) {
        Log.d("CodeStudio", msg)
    }
}
