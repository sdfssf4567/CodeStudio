package com.codestudio.editor

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {

    companion object {
        const val WELCOME_PATH = "@welcome"
        val WELCOME_TEXT = "// به CodeStudio خوش آمدید!\n" +
            "// فایلی را از منوی کناری باز کنید یا از منوی بالا پروژه جدید بسازید.\n" +
            "// در فایل HTML علامت ! را تایپ کنید تا قالب HTML5 ساخته شود.\n" +
            "// تایپ هر حرف (مثل h) لیست تگ‌ها را پیشنهاد می‌دهد.\n" +
            "// این تب با دکمه ✕ بسته می‌شود."
        val SHORTCUTS = listOf("<", ">", "/", ":", ";", "{", "}", "(", ")", "\"", "'", "=", "\$", "Tab")
        const val ACCENT = "#007acc"
        const val TEXT_MAIN = "#cccccc"
        const val TEXT_DIM = "#858585"
    }

    // ---------- views ----------
    private lateinit var editorWeb: WebView
    private lateinit var previewWeb: WebView
    private lateinit var tabContainer: LinearLayout
    private lateinit var treeContainer: LinearLayout
    private lateinit var shortcutRow: LinearLayout
    private lateinit var drawerPanel: FrameLayout
    private lateinit var previewPanel: LinearLayout
    private lateinit var dimView: View
    private lateinit var tvFileName: TextView
    private lateinit var tvFileType: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvProjectName: TextView
    private lateinit var btnSave: ImageButton
    private lateinit var btnBootstrap: Button

    // ---------- state ----------
    private lateinit var bridge: EditorBridge
    private var editorReady = false
    private var pendingBootstrap = "[]"

    private data class TabInfo(val path: String, val name: String)
    private val tabs = mutableListOf<TabInfo>()
    private val tabViews = mutableMapOf<String, LinearLayout>()
    private val tabLabels = mutableMapOf<String, TextView>()
    private var activePath: String? = null
    private val dirtySet = mutableSetOf<String>()

    private var projectDir: File? = null
    private val expandedDirs = mutableSetOf<String>()

    private var bootstrapInject = false
    private var fontSize = 16
    private var previewOpen = false
    private var drawerOpen = false
    private var previewLoadedPath: String? = null
    private var previewFullscreen = false
    private var previewBaseHeight = 0

    private val prefs by lazy { getSharedPreferences("codestudio", MODE_PRIVATE) }

    // =========================================================
    // Lifecycle
    // =========================================================

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        setContentView(R.layout.activity_main)

        bootstrapInject = prefs.getBoolean("bootstrap_inject", false)
        fontSize = prefs.getInt("font_size", 16)

        bindViews()
        loadBootstrapData()
        setupEditorWeb()
        setupPreviewWeb()
        setupShortcuts()
        setupButtons()

        // پروژه پیش‌فرض
        val all0 = FileManager.listProjects(this)
        if (all0.isEmpty()) FileManager.newProject(this, "MyWebsite")
        val all = FileManager.listProjects(this)
        val lastName = prefs.getString("last_project", null)
        projectDir = all.firstOrNull { it.name == lastName } ?: all.firstOrNull()
            ?: FileManager.ensureDefaultProject(this)

        drawerPanel.post {
            if (!drawerOpen) drawerPanel.translationX = -drawerPanel.width.toFloat()
        }
        previewPanel.post {
            val h = (window.decorView.height * 0.78f).toInt()
            previewBaseHeight = h
            applyPreviewHeight(false)
        }
    }

    override fun onPause() {
        if (editorReady) {
            editorWeb.evaluateJavascript("window.Host.flushAll()", null)
        }
        super.onPause()
    }

    override fun onBackPressed() {
        when {
            drawerOpen -> setDrawer(false)
            previewOpen -> showPreview(false)
            else -> super.onBackPressed()
        }
    }

    // =========================================================
    // Setup
    // =========================================================

    private fun bindViews() {
        editorWeb = findViewById(R.id.editorWeb)
        previewWeb = findViewById(R.id.previewWeb)
        tabContainer = findViewById(R.id.tabContainer)
        treeContainer = findViewById(R.id.treeContainer)
        shortcutRow = findViewById(R.id.shortcutRow)
        drawerPanel = findViewById(R.id.drawerPanel)
        previewPanel = findViewById(R.id.previewPanel)
        dimView = findViewById(R.id.dimView)
        tvFileName = findViewById(R.id.tvFileName)
        tvFileType = findViewById(R.id.tvFileType)
        tvStatus = findViewById(R.id.tvStatus)
        tvProjectName = findViewById(R.id.tvProjectName)
        btnSave = findViewById(R.id.btnSave)
        btnBootstrap = findViewById(R.id.btnBootstrap)

        tabContainer.layoutDirection = View.LAYOUT_DIRECTION_LTR
        treeContainer.layoutDirection = View.LAYOUT_DIRECTION_LTR
        shortcutRow.layoutDirection = View.LAYOUT_DIRECTION_LTR
        drawerPanel.layoutDirection = View.LAYOUT_DIRECTION_LTR
    }

    private fun loadBootstrapData() {
        pendingBootstrap = try {
            assets.open("bootstrap.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "[]"
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setupEditorWeb() {
        editorWeb.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            defaultTextEncodingName = "utf-8"
            cacheMode = WebSettings.LOAD_NO_CACHE
            useWideViewPort = false
            loadWithOverviewMode = false
        }
        editorWeb.setBackgroundColor(Color.parseColor("#1e1e1e"))
        editorWeb.isFocusable = true
        editorWeb.isFocusableInTouchMode = true

        bridge = EditorBridge(this)
        editorWeb.addJavascriptInterface(bridge, "AndroidHost")
        editorWeb.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!editorReady) onEditorReady()
            }
        }
        editorWeb.loadUrl("file:///android_asset/editor/index.html")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupPreviewWeb() {
        previewWeb.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            defaultTextEncodingName = "utf-8"
        }
        previewWeb.setBackgroundColor(Color.WHITE)
    }

    private fun setupShortcuts() {
        for (k in SHORTCUTS) {
            val b = TextView(this).apply {
                text = if (k == "Tab") "Tab" else k
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#d4d4d4"))
                textSize = if (k == "Tab") 12f else 16f
                typeface = Typeface.MONOSPACE
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2d2d2d"))
                    cornerRadius = dp(5).toFloat()
                }
            }
            b.setOnClickListener {
                if (k == "Tab") {
                    js("window.Host.tabKey()")
                } else {
                    js("window.Host.insertKey(" + JSONObject.quote(k) + ")")
                }
            }
            val lp = LinearLayout.LayoutParams(dp(42), dp(36))
            lp.marginEnd = dp(4)
            shortcutRow.addView(b, lp)
        }
    }

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { setDrawer(!drawerOpen) }
        findViewById<ImageButton>(R.id.btnUndo).setOnClickListener { js("window.Host.undo()") }
        findViewById<ImageButton>(R.id.btnRedo).setOnClickListener { js("window.Host.redo()") }
        btnSave.setOnClickListener { js("window.Host.saveNow()") }
        findViewById<ImageButton>(R.id.btnRun).setOnClickListener { runPreview() }
        findViewById<ImageButton>(R.id.btnMore).setOnClickListener { showOverflowMenu(it) }

        findViewById<ImageButton>(R.id.btnNewFile).setOnClickListener {
            val d = projectDir
            if (d != null) promptNewFile(d) else toast("اول یک پروژه بسازید")
        }
        findViewById<ImageButton>(R.id.btnNewFolder).setOnClickListener {
            val d = projectDir
            if (d != null) promptNewFolder(d) else toast("اول یک پروژه بسازید")
        }
        findViewById<ImageButton>(R.id.btnSwitchProject).setOnClickListener { showProjectsDialog() }
        findViewById<ImageButton>(R.id.btnRefreshTree).setOnClickListener { renderTree() }

        btnBootstrap.setOnClickListener {
            bootstrapInject = !bootstrapInject
            prefs.edit().putBoolean("bootstrap_inject", bootstrapInject).apply()
            updateBootstrapBtn()
            if (previewOpen) reloadPreview()
        }
        findViewById<ImageButton>(R.id.btnReloadPreview).setOnClickListener { reloadPreview() }
        findViewById<ImageButton>(R.id.btnClosePreview).setOnClickListener { showPreview(false) }
        findViewById<ImageButton>(R.id.btnExpandPreview).setOnClickListener {
            applyPreviewHeight(!previewFullscreen)
        }
        setupSwipeToClose(findViewById(R.id.previewHandle))
        dimView.setOnClickListener {
            if (drawerOpen) setDrawer(false)
            if (previewOpen) showPreview(false)
        }
        updateBootstrapBtn()
    }

    /** ارتفاع پنل پیش‌نمایش: ۷۸٪ صفحه یا تمام‌صفحه */
    private fun applyPreviewHeight(fullscreen: Boolean) {
        previewFullscreen = fullscreen
        val lp = previewPanel.layoutParams as FrameLayout.LayoutParams
        lp.height = if (fullscreen) ViewGroup.LayoutParams.MATCH_PARENT else previewBaseHeight
        previewPanel.layoutParams = lp
        findViewById<ImageButton>(R.id.btnExpandPreview)
            .setImageResource(if (fullscreen) R.drawable.ic_collapse else R.drawable.ic_expand)
    }

    /** کشیدن دستگیره به پایین → بستن پیش‌نمایش */
    private fun setupSwipeToClose(handle: View) {
        var downY = 0f
        var tracking = false
        handle.setOnTouchListener { _, ev ->
            if (!previewOpen) return@setOnTouchListener false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = ev.rawY
                    tracking = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (tracking) {
                        val dy = ev.rawY - downY
                        if (dy > 0) previewPanel.translationY = dy
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (tracking) {
                        tracking = false
                        val dy = ev.rawY - downY
                        if (dy > previewPanel.height * 0.16f) {
                            showPreview(false)
                        } else {
                            previewPanel.animate().translationY(0f).setDuration(180).start()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    // =========================================================
    // JS bridge helpers
    // =========================================================

    private fun js(script: String) {
        if (!editorReady) return
        runOnUiThread {
            try {
                editorWeb.evaluateJavascript(script, null)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun onEditorReady() {
        editorReady = true
        js("window.Host.setBootstrapClasses(" + JSONObject.quote(pendingBootstrap) + ")")
        js("window.Host.setFontSize($fontSize)")
        renderTree()
        openInitialTab()
    }

    // called by EditorBridge
    fun onFileSaved(path: String) {
        markDirty(path, false)
    }

    // called by EditorBridge
    fun markDirty(path: String, dirty: Boolean) {
        if (dirty) dirtySet.add(path) else dirtySet.remove(path)
        val tv = tabLabels[path]
        if (tv != null) {
            val name = File(path).name
            tv.text = if (dirty) "● $name" else name
        }
        updateSaveIcon()
    }

    // called by EditorBridge
    fun setStatus(line: Int, col: Int) {
        tvStatus.text = "Ln $line, Col $col"
    }

    private fun updateSaveIcon() {
        btnSave.setColorFilter(
            if (dirtySet.isNotEmpty()) Color.parseColor(ACCENT)
            else Color.parseColor(TEXT_DIM)
        )
    }

    // =========================================================
    // Tabs
    // =========================================================

    private fun openInitialTab() {
        val dir = projectDir
        val idx = dir?.let { File(it, "index.html") }
        if (idx != null && idx.exists()) {
            addTab(idx.absolutePath)
        } else {
            showWelcome()
        }
    }

    private fun addTab(path: String) {
        val p = File(path).absolutePath
        if (tabs.any { it.path == p }) {
            activateTab(p)
            return
        }
        val info = TabInfo(p, File(p).name)
        tabs.add(info)
        val v = buildTabView(info)
        tabContainer.addView(v)
        tabViews[p] = v
        activateTab(p)
    }

    private fun activateTab(path: String) {
        activePath = path
        for ((p, v) in tabViews) styleTab(v, p == path)
        val v = tabViews[path]
        if (v != null) {
            val parent = v.parent
            if (parent is HorizontalScrollView) {
                parent.smoothScrollTo(maxOf(0, v.left - 220), 0)
            }
        }
        val isWelcome = path == WELCOME_PATH
        val content = if (isWelcome) WELCOME_TEXT else FileManager.readText(File(path))
        val lang = if (isWelcome) "text" else FileManager.languageFor(path)
        tvFileName.text = if (isWelcome) "CodeStudio" else File(path).name
        tvFileType.text = if (isWelcome) "راهنما" else FileManager.languageLabel(path)
        js(
            "window.Host.openFile(" + JSONObject.quote(path) + "," +
                JSONObject.quote(if (isWelcome) "welcome" else File(path).name) + "," +
                JSONObject.quote(content) + "," +
                JSONObject.quote(lang) + ")"
        )
        updateSaveIcon()
    }

    private fun closeTab(path: String) {
        val idx = tabs.indexOfFirst { it.path == path }
        if (idx < 0) return
        tabs.removeAt(idx)
        val v = tabViews.remove(path)
        if (v != null) tabContainer.removeView(v)
        tabLabels.remove(path)
        dirtySet.remove(path)
        js("window.Host.closeDoc(" + JSONObject.quote(path) + ")")
        if (activePath == path) {
            activePath = null
            val next = tabs.getOrNull(minOf(idx, tabs.size - 1))
            if (next != null) activateTab(next.path) else showEmptyState()
        }
        updateSaveIcon()
    }

    private fun closeAllTabs() {
        val copy = tabs.toList()
        tabs.clear()
        for (t in copy) {
            val v = tabViews.remove(t.path)
            if (v != null) tabContainer.removeView(v)
            tabLabels.remove(t.path)
        }
        tabViews.clear()
        tabLabels.clear()
        dirtySet.clear()
        js("window.Host.closeAll()")
        activePath = null
        updateSaveIcon()
    }

    private fun showWelcome() {
        // تب خوش‌آمدگویی واقعی و قابل بستن
        if (tabs.none { it.path == WELCOME_PATH }) {
            val info = TabInfo(WELCOME_PATH, "welcome")
            tabs.add(info)
            val v = buildTabView(info)
            tabContainer.addView(v)
            tabViews[WELCOME_PATH] = v
            tabLabels[WELCOME_PATH] = v.findViewWithTag("label") as TextView
        }
        activateTab(WELCOME_PATH)
    }

    private fun showEmptyState() {
        activePath = "@empty"
        tvFileName.text = "CodeStudio"
        tvFileType.text = "—"
        val t = "// فایلی باز نیست.\n" +
            "// برای شروع، از منوی کناری (☰) یک فایل باز کنید\n" +
            "// یا دکمه + را بزنید تا فایل جدید بسازید."
        js(
            "window.Host.openFile(" + JSONObject.quote("@empty") + "," +
                JSONObject.quote("empty") + "," +
                JSONObject.quote(t) + "," +
                JSONObject.quote("text") + ")"
        )
    }

    private fun buildTabView(info: TabInfo): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val strip = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            tag = "strip"
        }
        val stripLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(2), 0)
            minimumHeight = dp(35)
        }
        val tv = TextView(this).apply {
            text = info.name
            tag = "label"
            setSingleLine(true)
            textSize = 12.5f
            setTextColor(Color.parseColor(TEXT_MAIN))
            ellipsize = TextUtils.TruncateAt.END
        }
        val tvLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val close = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.parseColor(TEXT_DIM))
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = null
        }
        val closeLp = LinearLayout.LayoutParams(dp(26), dp(26))
        row.addView(tv, tvLp)
        row.addView(close, closeLp)
        root.addView(strip, stripLp)
        root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val rootLp = LinearLayout.LayoutParams(dp(148), ViewGroup.LayoutParams.MATCH_PARENT)
        root.layoutParams = rootLp
        row.setOnClickListener { activateTab(info.path) }
        close.setOnClickListener { closeTab(info.path) }
        tabLabels[info.path] = tv
        styleTab(root, false)
        return root
    }

    private fun styleTab(v: LinearLayout, active: Boolean) {
        val strip = v.findViewWithTag<View>("strip") ?: return
        if (active) {
            v.setBackgroundColor(Color.parseColor("#1e1e1e"))
            strip.setBackgroundColor(Color.parseColor(ACCENT))
        } else {
            v.setBackgroundColor(Color.parseColor("#2d2d2d"))
            strip.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    // =========================================================
    // File Explorer drawer
    // =========================================================

    private fun setDrawer(open: Boolean) {
        drawerOpen = open
        dimView.visibility = if (open || previewOpen) View.VISIBLE else View.GONE
        drawerPanel.animate()
            .translationX(if (open) 0f else -drawerPanel.width.toFloat())
            .setDuration(200)
            .start()
    }

    private fun renderTree() {
        treeContainer.removeAllViews()
        val dir = projectDir ?: return
        tvProjectName.text = dir.name
        expandedDirs.add(dir.absolutePath)
        addTreeLevel(dir, 0)
    }

    private fun addTreeLevel(dir: File, depth: Int) {
        val entries = dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: return
        for (f in entries) {
            if (f.isDirectory) {
                val isOpen = expandedDirs.contains(f.absolutePath)
                addTreeRow(R.drawable.ic_folder, 0xFFDCB67A.toInt(), f.name, depth,
                    onClick = {
                        if (isOpen) expandedDirs.remove(f.absolutePath) else expandedDirs.add(f.absolutePath)
                        renderTree()
                    },
                    onLong = { folderMenu(f) }
                )
                if (isOpen) addTreeLevel(f, depth + 1)
            } else {
                addTreeRow(R.drawable.ic_file, FileManager.fileColor(f.absolutePath), f.name, depth,
                    onClick = {
                        addTab(f.absolutePath)
                        setDrawer(false)
                    },
                    onLong = { fileMenu(f) }
                )
            }
        }
    }

    private fun addTreeRow(
        iconRes: Int, tint: Int, label: String, depth: Int,
        onClick: () -> Unit, onLong: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(depth * 14 + 10), 0, dp(8), 0)
            minimumHeight = dp(36)
        }
        val iv = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(tint)
        }
        val ivLp = LinearLayout.LayoutParams(dp(17), dp(17))
        val tv = TextView(this).apply {
            text = label
            textSize = 13.5f
            setTextColor(Color.parseColor(TEXT_MAIN))
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
        val tvLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tvLp.marginStart = dp(9)
        row.addView(iv, ivLp)
        row.addView(tv, tvLp)
        row.setOnClickListener { onClick() }
        row.setOnLongClickListener { onLong(); true }
        treeContainer.addView(
            row,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun folderMenu(f: File) {
        val options = arrayOf(
            "فایل جدید در این پوشه",
            "پوشه جدید در این پوشه",
            "تغییر نام",
            "حذف"
        )
        AlertDialog.Builder(this)
            .setTitle(f.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> promptNewFile(f)
                    1 -> promptNewFolder(f)
                    2 -> promptRename(f)
                    3 -> confirmDelete(f)
                }
            }
            .show()
    }

    private fun fileMenu(f: File) {
        val options = arrayOf("باز کردن", "تغییر نام", "حذف")
        AlertDialog.Builder(this)
            .setTitle(f.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        addTab(f.absolutePath)
                        setDrawer(false)
                    }
                    1 -> promptRename(f)
                    2 -> confirmDelete(f)
                }
            }
            .show()
    }

    // =========================================================
    // File / folder / project dialogs
    // =========================================================

    private fun wrapDialog(input: EditText): View {
        val frame = FrameLayout(this)
        frame.setPadding(dp(24), dp(8), dp(24), 0)
        input.setSingleLine(true)
        frame.addView(
            input,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return frame
    }

    private fun promptNewFile(parentDir: File) {
        val input = EditText(this).apply { hint = "مثلاً about.html" }
        AlertDialog.Builder(this)
            .setTitle("فایل جدید")
            .setView(wrapDialog(input))
            .setPositiveButton("ساخت") { _, _ ->
                val name = FileManager.sanitizeName(input.text.toString())
                if (name.isNotEmpty()) {
                    val f = File(parentDir, name)
                    if (f.exists()) {
                        toast("فایلی با این نام وجود دارد")
                    } else {
                        FileManager.writeText(f, "")
                        renderTree()
                        addTab(f.absolutePath)
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun promptNewFolder(parentDir: File) {
        val input = EditText(this).apply { hint = "نام پوشه" }
        AlertDialog.Builder(this)
            .setTitle("پوشه جدید")
            .setView(wrapDialog(input))
            .setPositiveButton("ساخت") { _, _ ->
                val name = FileManager.sanitizeName(input.text.toString())
                if (name.isNotEmpty()) {
                    val f = File(parentDir, name)
                    if (!f.exists() && f.mkdirs()) {
                        expandedDirs.add(f.absolutePath)
                        renderTree()
                    } else {
                        toast("پوشه تکراری است یا ساخت آن ناموفق بود")
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun promptRename(f: File) {
        val input = EditText(this).apply { setText(f.name) }
        AlertDialog.Builder(this)
            .setTitle("تغییر نام")
            .setView(wrapDialog(input))
            .setPositiveButton("ثبت") { _, _ ->
                val newName = FileManager.sanitizeName(input.text.toString())
                if (newName.isNotEmpty() && newName != f.name) {
                    val target = File(f.parentFile, newName)
                    if (target.exists()) {
                        toast("نامی با این عنوان وجود دارد")
                    } else {
                        val abs = f.absolutePath
                        if (tabs.any { it.path == abs }) closeTab(abs)
                        if (f.renameTo(target)) {
                            renderTree()
                        } else {
                            toast("تغییر نام ناموفق بود")
                        }
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun confirmDelete(f: File) {
        AlertDialog.Builder(this)
            .setTitle("حذف")
            .setMessage("«${f.name}» حذف شود؟")
            .setPositiveButton("حذف") { _, _ ->
                val abs = f.absolutePath
                if (tabs.any { it.path == abs }) closeTab(abs)
                val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
                if (ok) {
                    renderTree()
                    toast("حذف شد")
                } else {
                    toast("حذف ناموفق بود")
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showProjectsDialog() {
        val projects = FileManager.listProjects(this)
        val items = mutableListOf<String>()
        items.add("+ پروژه جدید…")
        items.addAll(projects.map { it.name })
        AlertDialog.Builder(this)
            .setTitle("پروژه‌ها")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    promptNewProject()
                } else {
                    switchProject(projects[which - 1])
                }
            }
            .show()
    }

    private fun promptNewProject() {
        val input = EditText(this).apply { hint = "نام پروژه" }
        AlertDialog.Builder(this)
            .setTitle("پروژه جدید")
            .setView(wrapDialog(input))
            .setPositiveButton("ساخت") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) {
                    val d = FileManager.newProject(this, n)
                    if (d != null) {
                        switchProject(d)
                        toast("پروژه ساخته شد")
                    } else {
                        toast("خطا در ساخت پروژه")
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun switchProject(dir: File) {
        projectDir = dir
        prefs.edit().putString("last_project", dir.name).apply()
        closeAllTabs()
        expandedDirs.clear()
        setDrawer(false)
        renderTree()
        openInitialTab()
    }

    // =========================================================
    // Menus
    // =========================================================

    private fun showOverflowMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "پروژه جدید")
        pm.menu.add(0, 2, 0, "تغییر پروژه")
        pm.menu.add(0, 3, 0, "جستجو و جایگزینی")
        pm.menu.add(0, 4, 0, "اندازه فونت")
        pm.menu.add(0, 5, 0, "درباره CodeStudio")
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> promptNewProject()
                2 -> showProjectsDialog()
                3 -> js("window.Host.openSearch()")
                4 -> showFontDialog()
                5 -> showAbout()
            }
            true
        }
        pm.show()
    }

    private fun showFontDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        val tv = TextView(this).apply {
            text = "$fontSize sp"
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val minus = Button(this).apply { text = "−" }
        val plus = Button(this).apply { text = "+" }
        fun applySize(s: Int) {
            fontSize = s.coerceIn(10, 30)
            tv.text = "$fontSize sp"
            js("window.Host.setFontSize($fontSize)")
        }
        minus.setOnClickListener { applySize(fontSize - 1) }
        plus.setOnClickListener { applySize(fontSize + 1) }
        container.addView(minus, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(plus, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this)
            .setTitle("اندازه فونت ادیتور")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                prefs.edit().putInt("font_size", fontSize).apply()
            }
            .setNegativeButton("انصراف") { _, _ ->
                js("window.Host.setFontSize(${prefs.getInt("font_size", 16)})")
            }
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("CodeStudio v1.1.0")
            .setMessage(
                "ویرایشگر کد وب برای اندروید\n\n" +
                    "• هسته ادیتور: CodeMirror 6 (آفلاین)\n" +
                    "• HTML / CSS / JavaScript / Bootstrap 5\n" +
                    "• پیشنهاد خودکار کامل: تگ‌های HTML، اتریبیوت‌ها، کلاس‌های بوت‌استرپ، کدهای JS\n" +
                    "• پیش‌نمایش زنده با لینک خودکار فایل‌ها\n" +
                    "• بستن پیش‌نمایش: کشیدن به پایین، دکمه ✕، تمام‌صفحه\n\n" +
                    "ساخته‌شده با Kotlin و WebView"
            )
            .setPositiveButton("باشه", null)
            .show()
    }

    // =========================================================
    // Live preview
    // =========================================================

    private fun runPreview() {
        js("window.Host.flushAll()")
        editorWeb.postDelayed({
            val dir = projectDir
            if (dir == null) {
                toast("پروژه‌ای باز نیست")
                return@postDelayed
            }
            var htmlFile: File? = null
            val active = activePath
            if (active != null && !active.startsWith("@") && FileManager.languageFor(active) == "html") {
                val f = File(active)
                if (f.exists()) htmlFile = f
            }
            if (htmlFile == null) {
                val idx = File(dir, "index.html")
                if (idx.exists()) htmlFile = idx
            }
            if (htmlFile == null) {
                toast("فایل HTML برای پیش‌نمایش پیدا نشد")
                return@postDelayed
            }
            previewLoadedPath = htmlFile.absolutePath
            val html = PreviewBuilder.build(dir, htmlFile, bootstrapInject)
            previewWeb.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
            showPreview(true)
        }, 350)
    }

    private fun reloadPreview() {
        val path = previewLoadedPath ?: return
        val dir = projectDir ?: return
        val f = File(path)
        if (!f.exists()) return
        val html = PreviewBuilder.build(dir, f, bootstrapInject)
        previewWeb.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
    }

    private fun showPreview(open: Boolean) {
        previewOpen = open
        dimView.visibility = if (open || drawerOpen) View.VISIBLE else View.GONE
        previewPanel.animate()
            .translationY(if (open) 0f else previewPanel.height.toFloat())
            .setDuration(220)
            .start()
    }

    private fun updateBootstrapBtn() {
        if (bootstrapInject) {
            btnBootstrap.setBackgroundResource(R.drawable.btn_toggle_bg_on)
            btnBootstrap.setTextColor(Color.WHITE)
        } else {
            btnBootstrap.setBackgroundResource(R.drawable.btn_toggle_bg)
            btnBootstrap.setTextColor(Color.parseColor("#7fb8e0"))
        }
    }

    // =========================================================
    // Utils
    // =========================================================

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
