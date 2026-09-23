package com.codestudio.editor

import android.util.Base64
import java.io.File

/**
 * ساخت خروجی پیش‌نمایش زنده:
 * - فایل‌های CSS و JS محلی پروژه به صورت خودکار inline می‌شوند (لینک خودکار)
 * - تصاویر محلی به base64 تبدیل می‌شوند
 * - در صورت فعال بودن، لینک‌های CDN بوت‌استرپ به head تزریق می‌شود
 */
object PreviewBuilder {

    private val linkRegex = Regex(
        """<link\b[^>]*?href\s*=\s*["']([^"']+)["'][^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val scriptRegex = Regex(
        """<script\b[^>]*?src\s*=\s*["']([^"']+)["'][^>]*>\s*</script>""",
        RegexOption.IGNORE_CASE
    )
    private val imgRegex = Regex(
        """(<img\b[^>]*?src\s*=\s*["'])([^"']+)(["'][^>]*>)""",
        RegexOption.IGNORE_CASE
    )
    private val headCloseRegex = Regex("""</head>""", RegexOption.IGNORE_CASE)

    fun build(projectDir: File, htmlFile: File, injectBootstrap: Boolean): String {
        var html = FileManager.readText(htmlFile)
        if (html.isEmpty()) {
            html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body></body></html>"
        }
        val dirPath = try {
            projectDir.canonicalPath
        } catch (e: Exception) {
            projectDir.absolutePath
        }

        fun resolveLocal(ref: String): File? {
            val clean = ref.substringBefore('#').substringBefore('?').trim()
            if (clean.isEmpty()) return null
            if (clean.startsWith("http://") || clean.startsWith("https://")) return null
            if (clean.startsWith("data:") || clean.startsWith("//")) return null
            val f = if (clean.startsWith("/")) File(dirPath, clean) else File(dirPath, clean)
            return try {
                if (f.exists() && f.isFile && f.canonicalPath.startsWith(dirPath)) f else null
            } catch (e: Exception) {
                null
            }
        }

        // ۱) inline کردن استایل‌شیت‌های محلی
        html = linkRegex.replace(html) { m ->
            val tag = m.value
            val href = m.groupValues[1]
            if (!tag.contains("stylesheet", ignoreCase = true)) {
                tag
            } else {
                val f = resolveLocal(href)
                if (f != null) "<style>\n" + FileManager.readText(f) + "\n</style>" else tag
            }
        }

        // ۲) inline کردن اسکریپت‌های محلی
        html = scriptRegex.replace(html) { m ->
            val f = resolveLocal(m.groupValues[1])
            if (f != null) "<script>\n" + FileManager.readText(f) + "\n</script>" else m.value
        }

        // ۳) تبدیل تصاویر محلی به base64
        html = imgRegex.replace(html) { m ->
            val f = resolveLocal(m.groupValues[2])
            if (f != null && f.length() < 2L * 1024 * 1024) {
                val ext = f.extension.lowercase()
                val mime = when (ext) {
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "gif" -> "image/gif"
                    "svg" -> "image/svg+xml"
                    "webp" -> "image/webp"
                    "bmp" -> "image/bmp"
                    else -> "application/octet-stream"
                }
                val b64 = try {
                    Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                } catch (e: Exception) {
                    null
                }
                if (b64 != null) m.groupValues[1] + "data:$mime;base64,$b64" + m.groupValues[3]
                else m.value
            } else {
                m.value
            }
        }

        // ۴) تزریق CDN بوت‌استرپ
        if (injectBootstrap) {
            val bs = "<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n" +
                "<script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js\"></script>\n"
            html = if (headCloseRegex.containsMatchIn(html)) {
                headCloseRegex.replaceFirst(html, bs + "</head>")
            } else {
                bs + html
            }
        }

        return html
    }
}
