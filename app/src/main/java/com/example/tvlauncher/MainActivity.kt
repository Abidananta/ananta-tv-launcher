package com.example.tvlauncher

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin

/** Soft drifting colored lights, used when there is no artwork. */
class AuroraView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var base = Color.parseColor("#2b3a67")
    private var phase = 0f
    private var shaders: Array<RadialGradient>? = null
    private var radius = 0f
    private val anim = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat())

    init {
        anim.duration = 30000
        anim.repeatCount = ValueAnimator.INFINITE
        anim.interpolator = LinearInterpolator()
        anim.addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    fun setTint(c: Int) {
        base = c
        shaders = null
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    private fun shift(c: Int, deg: Float, v: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[0] = (hsv[0] + deg + 360f) % 360f
        hsv[1] = (hsv[1] * 1.1f).coerceIn(0.25f, 1f)
        hsv[2] = v
        return Color.HSVToColor(hsv)
    }

    private fun fade(c: Int, a: Int): Int = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    private fun build(w: Float) {
        radius = w * 0.55f
        val cols = intArrayOf(shift(base, 0f, 0.95f), shift(base, 50f, 0.9f), shift(base, -45f, 0.85f))
        shaders = Array(3) { i ->
            RadialGradient(
                0f, 0f, radius,
                intArrayOf(fade(cols[i], 210), fade(cols[i], 0)),
                null, Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        canvas.drawColor(Color.parseColor("#05060a"))
        if (shaders == null) build(w)
        val s = shaders ?: return
        val xs = floatArrayOf(
            w * (0.22f + 0.10f * sin(phase)),
            w * (0.78f + 0.08f * cos(phase * 1.2f)),
            w * (0.52f + 0.12f * sin(phase * 0.6f))
        )
        val ys = floatArrayOf(
            h * (0.28f + 0.08f * cos(phase * 0.9f)),
            h * (0.22f + 0.10f * sin(phase * 0.7f)),
            h * (0.62f + 0.08f * cos(phase * 1.1f))
        )
        for (i in 0 until 3) {
            canvas.save()
            canvas.translate(xs[i], ys[i])
            paint.shader = s[i]
            canvas.drawCircle(0f, 0f, radius, paint)
            canvas.restore()
        }
    }
}

class MainActivity : Activity() {

    data class AppItem(
        val label: String,
        val pkg: String,
        val art: Drawable,
        val isBanner: Boolean,
        val tint: Int
    )

    data class HeroItem(
        val pkg: String,
        val title: String?,
        val desc: String?,
        val image: String?,
        val intentUri: String?
    )

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val TV_PERM = "android.permission.READ_TV_LISTINGS"
    private val SETTINGS_PKG = "__settings"
    private val priorityKeys = listOf(
        "netflix", "youtube", "primevideo", "amazon", "hotstar", "jio", "sony",
        "zee5", "plex", "disney", "hbo", "spotify", "kodi", "mx"
    )

    private var screenW = 0
    private var screenH = 0
    private var tileW = 0
    private var tileH = 0
    private var gap = 0
    private var outer = 0
    private var pad = 0
    private var radius = 0f

    private val defaultTint = Color.parseColor("#2b3a67")
    private var currentTint = defaultTint
    private var tintAnimator: ValueAnimator? = null
    private var kenBurns: AnimatorSet? = null

    private lateinit var aurora: AuroraView
    private lateinit var heroImg: ImageView
    private lateinit var blurImg: ImageView
    private lateinit var metaView: TextView
    private lateinit var titleView: TextView
    private lateinit var descView: TextView
    private lateinit var heroBtn: TextView
    private lateinit var defaultPill: TextView
    private lateinit var dots: LinearLayout
    private lateinit var list: RecyclerView

    private var heroMap: Map<String, List<HeroItem>> = emptyMap()
    private var focusedApp: AppItem? = null
    private var currentItem: HeroItem? = null
    private var heroIndex = 0
    private var heroToken = 0
    private val handler = Handler(Looper.getMainLooper())
    private val rotator = Runnable { rotateHero() }

    private val io = Executors.newFixedThreadPool(3)
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        outer = (screenW * 0.035f).toInt()
        pad = (screenW * 0.02f).toInt()
        gap = (screenW * 0.012f).toInt()
        tileW = (screenW * 0.112f).toInt()
        tileH = (tileW * 0.6f).toInt()
        radius = screenW * 0.011f

        // Dock geometry
        val labelH = (screenW * 0.0115f * 1.6f + screenW * 0.006f).toInt()
        val padV = (tileH * 0.30f).toInt()
        val panelH = tileH + labelH + 2 * padV
        val panelW = screenW - 2 * outer
        val extra = (tileH * 0.4f).toInt()          // room for the lifted tile: nothing gets cut
        val bottomMargin = (screenH * 0.07f).toInt()
        val panelTop = screenH - bottomMargin - panelH
        val panelR = panelH * 0.3f

        val apps = loadApps()

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        // ---- 1. animated aurora background
        aurora = AuroraView(this)
        aurora.setTint(withValue(defaultTint, 0.75f, 1.15f))
        root.addView(aurora, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- 2. optional static wallpaper (res/drawable-nodpi/hero.jpg)
        val heroRes = resources.getIdentifier("hero", "drawable", packageName)
        if (heroRes != 0) {
            val wall = ImageView(this)
            wall.scaleType = ImageView.ScaleType.CENTER_CROP
            wall.setImageResource(heroRes)
            root.addView(wall, FrameLayout.LayoutParams(MATCH, MATCH))
        }

        // ---- 3. hero artwork from OTT apps
        heroImg = ImageView(this)
        heroImg.scaleType = ImageView.ScaleType.CENTER_CROP
        heroImg.alpha = 0f
        root.addView(heroImg, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- 4. scrims for readability
        val leftScrim = View(this)
        leftScrim.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.argb(175, 0, 0, 0), Color.TRANSPARENT)
        )
        root.addView(
            leftScrim,
            FrameLayout.LayoutParams((screenW * 0.55f).toInt(), MATCH, Gravity.START)
        )
        val bottomScrim = View(this)
        bottomScrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.argb(225, 0, 0, 0))
        )
        root.addView(
            bottomScrim,
            FrameLayout.LayoutParams(MATCH, (screenH * 0.5f).toInt(), Gravity.BOTTOM)
        )

        // ---- 5. hero text block
        metaView = TextView(this)
        metaView.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0105f)
        metaView.setTypeface(Typeface.DEFAULT_BOLD)
        metaView.letterSpacing = 0.2f
        metaView.visibility = View.GONE

        titleView = TextView(this)
        titleView.text = "HOME"
        titleView.setTextColor(Color.WHITE)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.042f)
        titleView.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        titleView.letterSpacing = 0.03f
        titleView.setShadowLayer(14f, 0f, 4f, Color.BLACK)
        titleView.maxLines = 2
        titleView.ellipsize = TextUtils.TruncateAt.END
        titleView.maxWidth = (screenW * 0.42f).toInt()

        descView = TextView(this)
        descView.setTextColor(Color.argb(230, 255, 255, 255))
        descView.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0125f)
        descView.setShadowLayer(8f, 0f, 2f, Color.BLACK)
        descView.maxLines = 3
        descView.ellipsize = TextUtils.TruncateAt.END
        descView.visibility = View.GONE

        heroBtn = TextView(this)
        heroBtn.text = "Open"
        heroBtn.setTextColor(Color.BLACK)
        heroBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0125f)
        heroBtn.setTypeface(Typeface.DEFAULT_BOLD)
        val btnBg = GradientDrawable()
        btnBg.cornerRadius = radius * 1.4f
        btnBg.setColor(Color.argb(190, 255, 255, 255))
        heroBtn.background = btnBg
        val bh = (screenW * 0.022f).toInt()
        val bv = (screenW * 0.0095f).toInt()
        heroBtn.setPadding(bh, bv, bh, bv)
        heroBtn.isFocusable = true
        heroBtn.isClickable = true
        heroBtn.visibility = View.GONE
        if (Build.VERSION.SDK_INT >= 26) heroBtn.defaultFocusHighlightEnabled = false
        heroBtn.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.1f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(150).start()
            btnBg.setColor(if (focused) Color.WHITE else Color.argb(190, 255, 255, 255))
        }
        heroBtn.setOnClickListener { launchHero(currentItem) }

        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        info.addView(metaView)
        val titleLp = LinearLayout.LayoutParams(WRAP, WRAP)
        titleLp.topMargin = (screenW * 0.006f).toInt()
        info.addView(titleView, titleLp)
        val descLp = LinearLayout.LayoutParams((screenW * 0.38f).toInt(), WRAP)
        descLp.topMargin = (screenW * 0.008f).toInt()
        info.addView(descView, descLp)
        val btnLp = LinearLayout.LayoutParams(WRAP, WRAP)
        btnLp.topMargin = (screenW * 0.016f).toInt()
        info.addView(heroBtn, btnLp)
        val infoLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START)
        infoLp.setMargins(outer + pad, (screenH * 0.12f).toInt(), 0, 0)
        root.addView(info, infoLp)

        // ---- 6. carousel dots (above the dock)
        dots = LinearLayout(this)
        dots.orientation = LinearLayout.HORIZONTAL
        dots.gravity = Gravity.CENTER
        val dotsLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        dotsLp.bottomMargin = bottomMargin + panelH + (extra * 0.6f).toInt()
        root.addView(dots, dotsLp)

        // ---- 7. glass dock panel
        val glass = FrameLayout(this)
        glass.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, panelR)
            }
        }
        glass.clipToOutline = true

        blurImg = ImageView(this)
        blurImg.alpha = 0f
        blurImg.scaleType = ImageView.ScaleType.CENTER_CROP
        val blurLp = FrameLayout.LayoutParams(screenW, screenH)
        blurImg.layoutParams = blurLp
        blurImg.translationX = -outer.toFloat()          // line the blur up with the hero image behind
        blurImg.translationY = -panelTop.toFloat()
        glass.addView(blurImg)

        val dim = View(this)
        dim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(50, 0, 0, 0), Color.argb(125, 0, 0, 0))
        )
        glass.addView(dim, FrameLayout.LayoutParams(MATCH, MATCH))

        val sheen = View(this)
        sheen.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(75, 255, 255, 255), Color.argb(18, 255, 255, 255))
        )
        glass.addView(sheen, FrameLayout.LayoutParams(MATCH, MATCH))

        val border = View(this)
        val borderBg = GradientDrawable()
        borderBg.cornerRadius = panelR
        borderBg.setStroke(2, Color.argb(95, 255, 255, 255))
        borderBg.setColor(Color.TRANSPARENT)
        border.background = borderBg
        glass.addView(border, FrameLayout.LayoutParams(MATCH, MATCH))

        val highlight = View(this)
        highlight.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.TRANSPARENT, Color.argb(175, 255, 255, 255), Color.TRANSPARENT)
        )
        val hlLp = FrameLayout.LayoutParams(
            (panelW - 2 * panelR).toInt().coerceAtLeast(10), 2,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        )
        hlLp.topMargin = 1
        glass.addView(highlight, hlLp)

        val glassLp = FrameLayout.LayoutParams(panelW, panelH, Gravity.BOTTOM or Gravity.START)
        glassLp.setMargins(outer, 0, 0, bottomMargin)
        root.addView(glass, glassLp)

        // ---- 8. the dock row: ALL apps, scrolls sideways
        val lm = object : LinearLayoutManager(this, RecyclerView.HORIZONTAL, false) {
            // We centre the focused tile ourselves, so skip the default "just make it visible" scroll.
            override fun requestChildRectangleOnScreen(
                parent: RecyclerView,
                child: View,
                rect: Rect,
                immediate: Boolean,
                focusedChildVisible: Boolean
            ): Boolean = false
        }
        list = RecyclerView(this)
        list.layoutManager = lm
        list.clipChildren = false
        list.clipToPadding = false
        list.setPadding(pad, 0, pad, 0)
        list.setItemViewCacheSize(8)
        list.adapter = DockAdapter(apps)
        val listLp = FrameLayout.LayoutParams(
            panelW, panelH + 2 * extra, Gravity.BOTTOM or Gravity.START
        )
        listLp.setMargins(outer, 0, 0, bottomMargin - extra)
        root.addView(list, listLp)

        // ---- 9. top bar: default-home pill + clock
        defaultPill = TextView(this)
        defaultPill.text = "Set as default home"
        defaultPill.setTextColor(Color.WHITE)
        defaultPill.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0115f)
        val pillBg = glassPill()
        defaultPill.background = pillBg
        val ph = (screenW * 0.014f).toInt()
        val pv = (screenW * 0.007f).toInt()
        defaultPill.setPadding(ph, pv, ph, pv)
        defaultPill.isFocusable = true
        defaultPill.isClickable = true
        defaultPill.visibility = View.GONE
        if (Build.VERSION.SDK_INT >= 26) defaultPill.defaultFocusHighlightEnabled = false
        defaultPill.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.08f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(150).start()
            pillBg.setColor(if (focused) Color.argb(200, 255, 255, 255) else Color.argb(110, 30, 30, 40))
            defaultPill.setTextColor(if (focused) Color.BLACK else Color.WHITE)
        }
        defaultPill.setOnClickListener { showDefaultHelp() }

        val clock = TextClock(this)
        clock.format12Hour = "EEE  h:mm"
        clock.format24Hour = "EEE  HH:mm"
        clock.setTextColor(Color.WHITE)
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0125f)
        clock.background = glassPill()
        clock.setPadding(ph, pv, ph, pv)

        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.addView(defaultPill)
        val clockLp = LinearLayout.LayoutParams(WRAP, WRAP)
        clockLp.leftMargin = (screenW * 0.012f).toInt()
        topBar.addView(clock, clockLp)
        val topLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END)
        topLp.setMargins(0, (screenH * 0.05f).toInt(), outer + pad, 0)
        root.addView(topBar, topLp)

        setContentView(root)

        list.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                list.viewTreeObserver.removeOnGlobalLayoutListener(this)
                list.getChildAt(0)?.requestFocus()
            }
        })

        if (!hasTvPermission() && Build.VERSION.SDK_INT >= 23) {
            requestPermissions(arrayOf(TV_PERM), 77)
        }
    }

    override fun onResume() {
        super.onResume()
        defaultPill.visibility = if (isDefaultHome()) View.GONE else View.VISIBLE
        if (hasTvPermission()) loadHeroAsync()
    }

    override fun onPause() {
        handler.removeCallbacks(rotator)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already here: jump back to the first app.
        list.scrollToPosition(0)
        list.post { list.getChildAt(0)?.requestFocus() }
    }

    override fun onBackPressed() {
        // Launcher is the root screen: ignore Back.
    }

    // =====================================================================
    // Default home helpers
    // =====================================================================

    private fun homeIntent(): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    private fun isDefaultHome(): Boolean {
        val ri = packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == packageName
    }

    private fun showDefaultHelp() {
        AlertDialog.Builder(this)
            .setTitle("Make this your default home")
            .setMessage(
                "1) Tap \"Open chooser\", pick this launcher and choose Always.\n\n" +
                    "2) If your box just opens the old home screen, run this on a PC:\n" +
                    "adb shell cmd package set-home-activity $packageName/.MainActivity"
            )
            .setPositiveButton("Open chooser") { _, _ ->
                try {
                    startActivity(homeIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    // ignore
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun glassPill(): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = 200f
        g.setColor(Color.argb(110, 30, 30, 40))
        g.setStroke(2, Color.argb(60, 255, 255, 255))
        return g
    }

    // =====================================================================
    // Hero data (TV provider)
    // =====================================================================

    private fun hasTvPermission(): Boolean =
        Build.VERSION.SDK_INT < 23 ||
            checkSelfPermission(TV_PERM) == PackageManager.PERMISSION_GRANTED

    private fun loadHeroAsync() {
        io.execute {
            val map = queryHero()
            runOnUiThread {
                heroMap = map
                focusedApp?.let { showHeroFor(it) }
            }
        }
    }

    private fun Cursor.str(name: String): String? {
        val i = getColumnIndex(name)
        return if (i >= 0 && !isNull(i)) getString(i) else null
    }

    private fun Cursor.num(name: String): Int {
        val i = getColumnIndex(name)
        return if (i >= 0 && !isNull(i)) getInt(i) else -1
    }

    private fun queryHero(): Map<String, List<HeroItem>> {
        val out = LinkedHashMap<String, MutableList<HeroItem>>()
        if (Build.VERSION.SDK_INT < 26) return out
        val uris = arrayOf(
            "content://android.media.tv/preview_program",
            "content://android.media.tv/watch_next_program"
        )
        for (u in uris) {
            try {
                val c = contentResolver.query(Uri.parse(u), null, null, null, null) ?: continue
                c.use { cur ->
                    var rows = 0
                    while (cur.moveToNext() && rows < 400) {
                        rows++
                        val pkg = cur.str("package_name") ?: continue
                        if (pkg == packageName) continue
                        val poster = cur.str("poster_art_uri")
                        val thumb = cur.str("thumbnail_uri")
                        val ratio = cur.num("poster_art_aspect_ratio")
                        val wide = ratio == 0 || ratio == 1 || ratio == 2
                        val image = (if (wide) (poster ?: thumb) else (thumb ?: poster)) ?: continue
                        val items = out.getOrPut(pkg) { mutableListOf() }
                        if (items.size < 8 && items.none { it.image == image }) {
                            items.add(
                                HeroItem(
                                    pkg,
                                    cur.str("title"),
                                    cur.str("short_description"),
                                    image,
                                    cur.str("intent_uri")
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // No access: fall back to the aurora background.
            }
        }
        return out
    }

    private fun launchHero(item: HeroItem?) {
        if (item == null) return
        try {
            val i = Intent.parseUri(item.intentUri.orEmpty(), Intent.URI_INTENT_SCHEME)
            i.selector = null
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            packageManager.getLeanbackLaunchIntentForPackage(item.pkg)?.let { startActivity(it) }
        }
    }

    // =====================================================================
    // Hero display
    // =====================================================================

    private fun showHeroFor(app: AppItem) {
        handler.removeCallbacks(rotator)
        val items = heroMap[app.pkg].orEmpty()
        heroIndex = 0
        if (items.isEmpty()) {
            currentItem = null
            heroToken++
            metaView.visibility = View.GONE
            titleView.text = app.label.uppercase()
            heroBtn.visibility = View.GONE
            updateDots(0, 0)
            clearHeroImage()
            if (app.pkg != SETTINGS_PKG && !hasTvPermission()) {
                descView.text =
                    "To show artwork from your apps, run on a PC: adb shell pm grant $packageName $TV_PERM"
                descView.visibility = View.VISIBLE
            } else {
                descView.visibility = View.GONE
            }
            return
        }
        showItem(app, items[0], 0, items.size)
        if (items.size > 1) handler.postDelayed(rotator, 7000)
    }

    private fun rotateHero() {
        val app = focusedApp ?: return
        val items = heroMap[app.pkg].orEmpty()
        if (items.size < 2) return
        if (!heroBtn.hasFocus()) {
            heroIndex = (heroIndex + 1) % items.size
            showItem(app, items[heroIndex], heroIndex, items.size)
        }
        handler.postDelayed(rotator, 7000)
    }

    private fun clearHeroImage() {
        kenBurns?.cancel()
        heroImg.animate().alpha(0f).setDuration(250).start()
        blurImg.animate().alpha(0f).setDuration(250).start()
    }

    private fun showItem(app: AppItem, item: HeroItem, index: Int, total: Int) {
        currentItem = item
        metaView.text = "FROM ${app.label.uppercase()}"
        metaView.setTextColor(withValue(app.tint, 1f, 0.45f))
        metaView.visibility = View.VISIBLE
        titleView.text = (item.title ?: app.label).uppercase()
        descView.text = item.desc.orEmpty()
        descView.visibility = if (item.desc.isNullOrBlank()) View.GONE else View.VISIBLE
        heroBtn.text = "Open in ${app.label}"
        heroBtn.visibility = View.VISIBLE
        updateDots(total, index)

        val token = ++heroToken
        val img = item.image
        if (img == null) {
            clearHeroImage()
            return
        }
        loadBitmap(img) { bmp ->
            if (token != heroToken) return@loadBitmap
            if (bmp == null) {
                clearHeroImage()
                return@loadBitmap
            }
            heroImg.animate().alpha(0f).setDuration(180).withEndAction {
                if (token == heroToken) {
                    val wide = bmp.width.toFloat() / bmp.height.toFloat() >= 1.4f
                    val st = if (wide) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_END
                    heroImg.scaleType = st
                    heroImg.setImageBitmap(bmp)
                    blurImg.scaleType = st
                    blurImg.setImageBitmap(makeBlur(bmp))
                    blurImg.animate().alpha(1f).setDuration(400).start()
                    heroImg.animate().alpha(1f).setDuration(400).start()
                    startKenBurns()
                }
            }.start()
        }
    }

    private fun makeBlur(src: Bitmap): Bitmap {
        val w = 48
        val h = (w * src.height.toFloat() / src.width.toFloat()).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun startKenBurns() {
        kenBurns?.cancel()
        heroImg.scaleX = 1f
        heroImg.scaleY = 1f
        val sx = ObjectAnimator.ofFloat(heroImg, View.SCALE_X, 1f, 1.07f)
        val sy = ObjectAnimator.ofFloat(heroImg, View.SCALE_Y, 1f, 1.07f)
        val set = AnimatorSet()
        set.playTogether(sx, sy)
        set.duration = 12000
        set.interpolator = LinearInterpolator()
        set.start()
        kenBurns = set
    }

    private fun updateDots(count: Int, index: Int) {
        dots.removeAllViews()
        if (count < 2) return
        val d = (screenW * 0.0055f).toInt().coerceAtLeast(6)
        for (i in 0 until count) {
            val v = View(this)
            val bg = GradientDrawable()
            bg.cornerRadius = d.toFloat()
            bg.setColor(if (i == index) Color.WHITE else Color.argb(110, 255, 255, 255))
            v.background = bg
            val lp = LinearLayout.LayoutParams(if (i == index) d * 3 else d, d)
            lp.setMargins(d / 2, 0, d / 2, 0)
            dots.addView(v, lp)
        }
    }

    // =====================================================================
    // Image loading
    // =====================================================================

    private fun loadBitmap(uriStr: String, cb: (Bitmap?) -> Unit) {
        val cached = cache.get(uriStr)
        if (cached != null) {
            cb(cached)
            return
        }
        io.execute {
            val bmp = try {
                decode(uriStr)
            } catch (e: Exception) {
                null
            }
            if (bmp != null) cache.put(uriStr, bmp)
            runOnUiThread { cb(bmp) }
        }
    }

    private fun decode(uriStr: String): Bitmap? {
        val u = Uri.parse(uriStr)
        val bytes: ByteArray = if (u.scheme == "http" || u.scheme == "https") {
            val conn = URL(uriStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            try {
                conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        } else {
            contentResolver.openInputStream(u)?.use { it.readBytes() } ?: return null
        }
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= screenW) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    // =====================================================================
    // Apps
    // =====================================================================

    private fun prio(a: AppItem): Int {
        val s = (a.pkg + " " + a.label).lowercase()
        val i = priorityKeys.indexOfFirst { s.contains(it) }
        return if (i >= 0) i else 100
    }

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val apps = pm.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { ri ->
                val banner = ri.activityInfo.loadBanner(pm)
                val art: Drawable = banner ?: ri.loadIcon(pm)
                AppItem(
                    ri.loadLabel(pm).toString(),
                    ri.activityInfo.packageName,
                    art,
                    banner != null,
                    dominantColor(art)
                )
            }
            .distinctBy { it.pkg }
            .sortedWith(compareBy<AppItem>({ prio(it) }, { it.label.lowercase() }))
            .toMutableList()

        val gear = getDrawable(android.R.drawable.ic_menu_preferences)
        if (gear != null) {
            apps.add(AppItem("Settings", SETTINGS_PKG, gear, false, Color.parseColor("#4a4f5e")))
        }
        return apps
    }

    private fun dominantColor(d: Drawable): Int {
        return try {
            val size = 24
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val copy = d.constantState?.newDrawable()?.mutate() ?: d
            copy.setBounds(0, 0, size, size)
            copy.draw(canvas)
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val p = bmp.getPixel(x, y)
                    if (Color.alpha(p) > 128) {
                        r += Color.red(p)
                        g += Color.green(p)
                        b += Color.blue(p)
                        n++
                    }
                }
            }
            bmp.recycle()
            if (n == 0) defaultTint else Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        } catch (e: Exception) {
            defaultTint
        }
    }

    private fun launchApp(app: AppItem) {
        try {
            if (app.pkg == SETTINGS_PKG) {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                val i = packageManager.getLeanbackLaunchIntentForPackage(app.pkg)
                    ?: packageManager.getLaunchIntentForPackage(app.pkg)
                if (i != null) startActivity(i)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    // =====================================================================
    // Colors
    // =====================================================================

    private fun withValue(color: Int, v: Float, satBoost: Float = 1f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * satBoost).coerceIn(0f, 1f)
        hsv[2] = v
        return Color.HSVToColor(hsv)
    }

    private fun setTint(target: Int) {
        if (target == currentTint) return
        tintAnimator?.cancel()
        val anim = ValueAnimator.ofObject(ArgbEvaluator(), currentTint, target)
        anim.duration = 450
        anim.addUpdateListener { a ->
            aurora.setTint(withValue(a.animatedValue as Int, 0.75f, 1.15f))
        }
        anim.start()
        tintAnimator = anim
        currentTint = target
    }

    private fun onAppFocused(app: AppItem) {
        focusedApp = app
        setTint(app.tint)
        showHeroFor(app)
    }

    // =====================================================================
    // Dock tiles
    // =====================================================================

    private fun centerOn(v: View) {
        val slot = v.parent as? View ?: return
        val dx = (slot.left + slot.width / 2) - list.width / 2
        if (dx != 0) list.smoothScrollBy(dx, 0)
    }

    private fun buildTile(app: AppItem): View {
        val r = radius

        val card = FrameLayout(this)
        card.layoutParams = LinearLayout.LayoutParams(tileW, tileH)
        val cardBg = GradientDrawable()
        cardBg.cornerRadius = r
        cardBg.setColor(withValue(app.tint, 0.35f, 1.1f))
        card.background = cardBg
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        card.clipToOutline = true

        val art = ImageView(this)
        art.setImageDrawable(app.art)
        if (app.isBanner) {
            art.scaleType = ImageView.ScaleType.CENTER_CROP
            art.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        } else {
            art.scaleType = ImageView.ScaleType.FIT_CENTER
            val s = (tileH * 0.55f).toInt()
            art.layoutParams = FrameLayout.LayoutParams(s, s, Gravity.CENTER)
        }

        val ring = View(this)
        ring.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        val ringBg = GradientDrawable()
        ringBg.cornerRadius = r
        ringBg.setStroke((screenW * 0.0022f).toInt().coerceAtLeast(3), Color.WHITE)
        ringBg.setColor(Color.TRANSPARENT)
        ring.background = ringBg
        ring.alpha = 0f

        card.addView(art)
        card.addView(ring)

        val label = TextView(this)
        label.text = app.label
        label.setTextColor(Color.WHITE)
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0115f)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.setPadding(0, (screenW * 0.006f).toInt(), 0, 0)
        label.alpha = 0f
        label.layoutParams = LinearLayout.LayoutParams(tileW, WRAP)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.clipChildren = false
        col.isFocusable = true
        col.isClickable = true
        col.addView(card)
        col.addView(label)
        col.pivotX = tileW / 2f
        col.pivotY = tileH / 2f
        if (Build.VERSION.SDK_INT >= 26) {
            col.defaultFocusHighlightEnabled = false
        }

        col.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.12f else 1f
            v.animate().scaleX(s).scaleY(s)
                .translationZ(if (focused) 20f else 0f)
                .setDuration(160).start()
            label.animate().alpha(if (focused) 1f else 0f).setDuration(160).start()
            ring.animate().alpha(if (focused) 1f else 0f).setDuration(160).start()
            if (focused) {
                centerOn(v)
                onAppFocused(app)
            }
        }
        col.setOnClickListener { launchApp(app) }
        return col
    }

    inner class DockAdapter(private val apps: List<AppItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount() = apps.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val slot = FrameLayout(parent.context)
            slot.layoutParams = RecyclerView.LayoutParams(tileW + gap, MATCH)
            slot.clipChildren = false
            return object : RecyclerView.ViewHolder(slot) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val slot = holder.itemView as FrameLayout
            slot.removeAllViews()
            slot.addView(buildTile(apps[position]), FrameLayout.LayoutParams(tileW, WRAP, Gravity.CENTER))
        }
    }
}
