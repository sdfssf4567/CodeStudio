package com.codestudio.editor

import android.content.Context
import java.io.File

/**
 * مدیریت فایل‌ها و پروژه‌ها — همه‌چیز داخل حافظه اختصاصی اپ (filesDir/Projects)
 */
object FileManager {

    fun projectsRoot(ctx: Context): File {
        val root = File(ctx.filesDir, "Projects")
        if (!root.exists()) root.mkdirs()
        return root
    }

    fun listProjects(ctx: Context): List<File> {
        val dirs = projectsRoot(ctx).listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.sortedBy { it.name.lowercase() }
    }

    fun ensureDefaultProject(ctx: Context): File {
        val existing = listProjects(ctx)
        if (existing.isNotEmpty()) return existing[0]
        return newProject(ctx, "MyWebsite") ?: projectsRoot(ctx)
    }

    fun sanitizeName(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "")
            .replace(Regex("\\s+"), " ")
        return cleaned.ifEmpty { "untitled" }
    }

    fun newProject(ctx: Context, rawName: String): File? {
        val name = sanitizeName(rawName)
        var dir = File(projectsRoot(ctx), name)
        var i = 1
        while (dir.exists()) {
            dir = File(projectsRoot(ctx), "$name-$i")
            i++
        }
        if (!dir.mkdirs()) return null
        File(dir, "index.html").writeText(Templates.indexHtml(dir.name), Charsets.UTF_8)
        File(dir, "style.css").writeText(Templates.styleCss(), Charsets.UTF_8)
        File(dir, "script.js").writeText(Templates.scriptJs(), Charsets.UTF_8)
        return dir
    }

    fun readText(f: File): String {
        return try {
            if (f.exists() && f.isFile) f.readText(Charsets.UTF_8) else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun writeText(f: File, content: String): Boolean {
        return try {
            f.parentFile?.mkdirs()
            f.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** زبان ادیتور برای CodeMirror */
    fun languageFor(path: String): String {
        val n = path.lowercase()
        return when {
            n.endsWith(".html") || n.endsWith(".htm") -> "html"
            n.endsWith(".css") -> "css"
            n.endsWith(".js") || n.endsWith(".mjs") -> "javascript"
            else -> "text"
        }
    }

    /** برچسب فارسی/انگلیسی نوع فایل برای نوار وضعیت */
    fun languageLabel(path: String): String {
        val n = path.lowercase()
        return when {
            n.endsWith(".html") || n.endsWith(".htm") -> "HTML"
            n.endsWith(".css") -> "CSS"
            n.endsWith(".js") || n.endsWith(".mjs") -> "JavaScript"
            n.endsWith(".json") -> "JSON"
            n.endsWith(".md") -> "Markdown"
            else -> "Text"
        }
    }

    /** رنگ آیکون فایل بر اساس پسوند */
    fun fileColor(path: String): Int {
        val n = path.lowercase()
        return when {
            n.endsWith(".html") || n.endsWith(".htm") -> 0xFFE44D26.toInt()
            n.endsWith(".css") -> 0xFF448AFF.toInt()
            n.endsWith(".js") || n.endsWith(".mjs") || n.endsWith(".json") -> 0xFFF0DB4F.toInt()
            else -> 0xFFCCCCCC.toInt()
        }
    }
}
