package com.example.tvlauncher

import android.accounts.AccountManager
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
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

/** Clean gear glyph used for the Settings tile (the stock Android one looks dated). */
class GearDrawable(private val color: Int = Color.WHITE) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var alphaValue = 255

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = minOf(b.width(), b.height()) / 2f
        val teeth = 8
        val outerR = r * 0.98f
        val innerR = r * 0.76f
        val step = (Math.PI * 2 / teeth).toFloat()
        val offs = floatArrayOf(0.00f, 0.10f, 0.40f, 0.50f)
        val rads = floatArrayOf(innerR, outerR, outerR, innerR)
        path.reset()
        for (k in 0 until teeth) {
            for (i in 0 until 4) {
                val ang = k * step + offs[i] * step
                val x = cx + rads[i] * cos(ang)
                val y = cy + rads[i] * sin(ang)
                if (k == 0 && i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        path.close()
        path.addCircle(cx, cy, r * 0.34f, Path.Direction.CCW)
        path.fillType = Path.FillType.EVEN_ODD
        paint.color = color
        paint.alpha = alphaValue * Color.alpha(color) / 255
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Wi-Fi glyph: three arcs + dot. [level] 0..3 lit arcs, slashed when offline. */
class WifiDrawable(private val color: Int = Color.WHITE) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    var level = 3
        private set
    var connected = true
        private set

    fun update(level: Int, connected: Boolean) {
        this.level = level
        this.connected = connected
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = b.width().toFloat()
        val cx = b.exactCenterX()
        val cy = b.top + b.height() * 0.84f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = w * 0.10f
        val radii = floatArrayOf(w * 0.27f, w * 0.50f, w * 0.73f)
        for (i in 0 until 3) {
            val lit = connected && i < level
            paint.style = Paint.Style.STROKE
            paint.color = color
            paint.alpha = if (lit) 255 else 70
            rect.set(cx - radii[i], cy - radii[i], cx + radii[i], cy + radii[i])
            canvas.drawArc(rect, 225f, 90f, false, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = if (connected) 255 else 70
        canvas.drawCircle(cx, cy, w * 0.075f, paint)
        if (!connected) {
            paint.style = Paint.Style.STROKE
            paint.color = color
            paint.alpha = 255
            canvas.drawLine(b.left + w * 0.14f, b.top + w * 0.10f, b.right - w * 0.14f, b.bottom - w * 0.10f, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Round profile avatar: the account's initial on a colour, or a neutral silhouette. */
class AvatarDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private var letter: String? = null
    private var tint = Color.parseColor("#5b6cff")

    fun set(letter: String?, tint: Int) {
        this.letter = letter
        this.tint = tint
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = minOf(b.width(), b.height()) / 2f
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = tint
        canvas.drawCircle(cx, cy, r, paint)
        val l = letter
        if (l != null) {
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = r * 1.1f
            val y = cy - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(l, cx, y, paint)
        } else {
            clip.reset()
            clip.addCircle(cx, cy, r, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            paint.color = Color.argb(235, 255, 255, 255)
            canvas.drawCircle(cx, cy - r * 0.18f, r * 0.34f, paint)
            rectF.set(cx - r * 0.72f, cy + r * 0.28f, cx + r * 0.72f, cy + r * 1.5f)
            canvas.drawOval(rectF, paint)
            canvas.restore()
        }
        // thin rim
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.07f
        paint.color = Color.argb(110, 255, 255, 255)
        canvas.drawCircle(cx, cy, r - paint.strokeWidth / 2f, paint)
    }

    private val rectF = RectF()

    override fun setAlpha(alpha: Int) {
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
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
        val images: List<String>,          // candidates, tried in order
        val intentUri: String?
    )

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val TAG = "AnantaTV"
    private val UA = "Mozilla/5.0 (Linux; Android 9; MiBox4) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36"
    private val TV_PERM = "android.permission.READ_TV_LISTINGS"
    private val SETTINGS_PKG = "__settings"
    private val PREFS = "ananta_dock"
    private val KEY_DOCK = "force_dock"
    private val KEY_GRID = "force_grid"

    /** Display order inside the dock (first match wins). */
    private val priorityKeys = listOf(
        "netflix", "youtube", "primevideo", "amazon", "hotstar", "jio", "sony",
        "zee5", "plex", "disney", "hbo", "wbd", "spotify", "apple", "ananta",
        "bongo", "chorki", "toffee", "hoichoi", "binge", "mx"
    )

    /** Package fragments that mark an app as an OTT / streaming app (goes into the dock). */
    private val ottPkgKeys = listOf(
        "netflix", "google.android.youtube", "amazon.avod", "amazon.amazonvideo", "hotstar",
        "jio.media", "jiocinema", "sonyliv", "graymatrix", "zee5", "plexapp", "disney", "hbo",
        "wbd.stream", "spotify", "apple.atve", "bongo", "chorki", "toffee", "ananta",
        "hoichoi", "binge", "iflix", "vuclip", "tubi", "pluto", "crunchyroll", "voot",
        "peacock", "paramount", "hulu", "twitch", "mxtech", "jio.ott"
    )

    /** Label fragments that mark an app as an OTT / streaming app. */
    private val ottLabelKeys = listOf(
        "netflix", "youtube", "prime video", "hotstar", "jiocinema", "sony liv", "sonyliv",
        "zee5", "plex", "disney", "hbo", "spotify", "apple tv", "bongo", "chorki", "toffee",
        "ananta", "hoichoi", "binge", "iflix", "viu", "tubi", "pluto", "crunchyroll", "voot",
        "peacock", "paramount", "hulu", "twitch", "mx player"
    )

    private var screenW = 0
    private var screenH = 0
    private var tileW = 0
    private var tileH = 0
    private var gap = 0
    private var outer = 0
    private var edgePad = 0
    private var slotW = 0
    private var slotH = 0
    private var gridSpan = 1
    private var radius = 0f

    private val defaultTint = Color.parseColor("#2b3a67")
    private var currentTint = defaultTint
    private var tintAnimator: ValueAnimator? = null
    private var kenBurns: AnimatorSet? = null

    private lateinit var prefs: SharedPreferences
    private var forceDock = HashSet<String>()
    private var forceGrid = HashSet<String>()

    private lateinit var aurora: AuroraView
    private lateinit var heroA: ImageView
    private lateinit var heroB: ImageView
    private lateinit var heroFront: ImageView
    private lateinit var blurImg: ImageView
    private lateinit var brandCard: FrameLayout
    private lateinit var brandArt: ImageView
    private lateinit var brandBg: GradientDrawable
    private lateinit var dockBorder: GradientDrawable
    private lateinit var metaView: TextView
    private lateinit var titleView: TextView
    private lateinit var descView: TextView
    private lateinit var heroBtn: TextView
    private lateinit var wifiDrawable: WifiDrawable
    private lateinit var avatarDrawable: AvatarDrawable
    private val ACCT_PERM = "android.permission.GET_ACCOUNTS"
    private val statusTick = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 8000)
        }
    }
    private lateinit var dots: LinearLayout
    private lateinit var list: RecyclerView      // the dock (OTT apps)
    private lateinit var grid: RecyclerView      // everything else
    private lateinit var dockAdapter: TileAdapter
    private lateinit var gridAdapter: TileAdapter

    private var allApps: List<AppItem> = emptyList()
    private val dockApps = ArrayList<AppItem>()
    private val gridApps = ArrayList<AppItem>()
    private var lastDockPos = 0
    private var lastGridPos = 0

    private var heroMap: Map<String, List<HeroItem>> = emptyMap()
    private var focusedApp: AppItem? = null
    private var currentItem: HeroItem? = null
    private var heroShownPkg: String? = null
    private var heroIndex = 0
    private var heroToken = 0
    private val handler = Handler(Looper.getMainLooper())
    private val rotator = Runnable { rotateHero() }

    private val io = Executors.newFixedThreadPool(3)
    private val failed = HashSet<String>()
    private val cache = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        forceDock = HashSet<String>(prefs.getStringSet(KEY_DOCK, emptySet<String>()) ?: emptySet<String>())
        forceGrid = HashSet<String>(prefs.getStringSet(KEY_GRID, emptySet<String>()) ?: emptySet<String>())

        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        outer = (screenW * 0.035f).toInt()
        gap = (screenW * 0.0115f).toInt()
        tileW = (screenW * 0.088f).toInt()          // smaller dock tiles
        tileH = (tileW * 0.6f).toInt()
        radius = screenW * 0.0095f

        // Dock geometry
        val labelH = (screenW * 0.0115f * 1.6f + screenW * 0.006f).toInt()
        val padV = (tileH * 0.24f).toInt()
        val panelH = tileH + labelH + 2 * padV
        val panelW = screenW - 2 * outer
        val panelR = panelH * 0.3f
        edgePad = (panelR * 0.9f).toInt()
        slotW = tileW + 2 * gap
        slotH = tileH + labelH + (gap * 1.2f).toInt()
        val tileStartX = outer + edgePad + gap
        val dockTop = (screenH * 0.43f).toInt()

        // Apps section geometry (below the dock)
        val headerTop = dockTop + panelH + (screenH * 0.02f).toInt()
        val headerH = (screenW * 0.0115f * 2.2f).toInt()
        val gridTop = headerTop + headerH
        val gridH = screenH - gridTop
        gridSpan = ((panelW - 2 * edgePad) / slotW).coerceAtLeast(3)

        allApps = loadApps()
        rebuildLists()

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

        // ---- 3. hero artwork from OTT apps (two layers so images cross-fade)
        heroA = ImageView(this)
        heroA.scaleType = ImageView.ScaleType.CENTER_CROP
        heroA.alpha = 0f
        root.addView(heroA, FrameLayout.LayoutParams(MATCH, MATCH))
        heroB = ImageView(this)
        heroB.scaleType = ImageView.ScaleType.CENTER_CROP
        heroB.alpha = 0f
        root.addView(heroB, FrameLayout.LayoutParams(MATCH, MATCH))
        heroFront = heroA

        // ---- 3b. brand card: shown when an app has no artwork to display
        brandBg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(defaultTint, defaultTint))
        brandBg.cornerRadius = radius * 2.4f
        brandBg.setStroke(2, Color.argb(70, 255, 255, 255))
        brandArt = ImageView(this)
        brandCard = FrameLayout(this)
        brandCard.background = brandBg
        val brandR = radius * 2.4f
        brandCard.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, brandR)
            }
        }
        brandCard.clipToOutline = true
        brandCard.elevation = 24f
        brandCard.addView(brandArt, FrameLayout.LayoutParams(MATCH, MATCH))
        brandCard.alpha = 0f
        val bw = (screenW * 0.23f).toInt()
        val bh = (bw * 0.6f).toInt()
        val brandLp = FrameLayout.LayoutParams(bw, bh, Gravity.TOP or Gravity.END)
        brandLp.setMargins(0, (screenH * 0.17f).toInt(), tileStartX, 0)
        root.addView(brandCard, brandLp)

        // ---- 4. scrims for readability
        val leftScrim = View(this)
        leftScrim.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.argb(185, 0, 0, 0), Color.TRANSPARENT)
        )
        root.addView(
            leftScrim,
            FrameLayout.LayoutParams((screenW * 0.58f).toInt(), MATCH, Gravity.START)
        )
        val topScrim = View(this)
        topScrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(140, 0, 0, 0), Color.TRANSPARENT)
        )
        root.addView(
            topScrim,
            FrameLayout.LayoutParams(MATCH, (screenH * 0.22f).toInt(), Gravity.TOP)
        )
        val bottomScrim = View(this)
        bottomScrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.argb(130, 0, 0, 0), Color.argb(238, 0, 0, 0))
        )
        root.addView(
            bottomScrim,
            FrameLayout.LayoutParams(MATCH, (screenH * 0.66f).toInt(), Gravity.BOTTOM)
        )

        // ---- 5. brand mark + hero text block
        val brand = TextView(this)
        brand.text = "ANANTA  TV"
        brand.setTextColor(Color.argb(210, 255, 255, 255))
        brand.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0115f)
        brand.setTypeface(Typeface.DEFAULT_BOLD)
        brand.letterSpacing = 0.35f
        val brandMarkLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START)
        brandMarkLp.setMargins(tileStartX, (screenH * 0.055f).toInt(), 0, 0)
        root.addView(brand, brandMarkLp)

        metaView = TextView(this)
        metaView.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0105f)
        metaView.setTypeface(Typeface.DEFAULT_BOLD)
        metaView.letterSpacing = 0.2f
        metaView.visibility = View.GONE

        titleView = TextView(this)
        titleView.text = "HOME"
        titleView.setTextColor(Color.WHITE)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.034f)
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
        descView.maxLines = 2
        descView.ellipsize = TextUtils.TruncateAt.END
        descView.visibility = View.GONE

        heroBtn = TextView(this)
        heroBtn.text = "Open"
        heroBtn.setTextColor(Color.WHITE)
        heroBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0125f)
        heroBtn.setTypeface(Typeface.DEFAULT_BOLD)
        val btnBg = GradientDrawable()
        btnBg.cornerRadius = 400f
        btnBg.setColor(Color.argb(80, 255, 255, 255))
        btnBg.setStroke(2, Color.argb(110, 255, 255, 255))
        heroBtn.background = btnBg
        val bhPad = (screenW * 0.022f).toInt()
        val bvPad = (screenW * 0.0085f).toInt()
        heroBtn.setPadding(bhPad, bvPad, bhPad, bvPad)
        heroBtn.isFocusable = true
        heroBtn.isClickable = true
        heroBtn.visibility = View.GONE
        if (Build.VERSION.SDK_INT >= 26) heroBtn.defaultFocusHighlightEnabled = false
        heroBtn.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.08f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(150).start()
            btnBg.setColor(if (focused) Color.WHITE else Color.argb(80, 255, 255, 255))
            heroBtn.setTextColor(if (focused) Color.BLACK else Color.WHITE)
        }
        heroBtn.setOnClickListener { launchHero(currentItem) }
        heroBtn.setOnKeyListener { _, keyCode, ev ->
            if (ev.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusDock()
                true
            } else false
        }

        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        // Do not clip children: the focused button scales up and its edges used to be cut off.
        info.clipChildren = false
        info.clipToPadding = false
        val infoPad = (screenW * 0.012f).toInt()
        info.setPadding(infoPad, infoPad, infoPad, infoPad)
        info.addView(metaView)
        val titleLp = LinearLayout.LayoutParams(WRAP, WRAP)
        titleLp.topMargin = (screenW * 0.006f).toInt()
        info.addView(titleView, titleLp)
        val descLp = LinearLayout.LayoutParams((screenW * 0.36f).toInt(), WRAP)
        descLp.topMargin = (screenW * 0.007f).toInt()
        info.addView(descView, descLp)
        val btnLp = LinearLayout.LayoutParams(WRAP, WRAP)
        btnLp.topMargin = (screenW * 0.014f).toInt()
        info.addView(heroBtn, btnLp)
        val infoLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START)
        infoLp.setMargins(tileStartX - infoPad, (screenH * 0.105f).toInt() - infoPad, 0, 0)
        root.addView(info, infoLp)

        // ---- 6. carousel dots (just above the dock)
        dots = LinearLayout(this)
        dots.orientation = LinearLayout.HORIZONTAL
        dots.gravity = Gravity.CENTER
        val dotsLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        dotsLp.topMargin = dockTop - (screenW * 0.0055f).toInt().coerceAtLeast(6) - (screenH * 0.022f).toInt()
        root.addView(dots, dotsLp)

        // ---- 7. glass dock panel (the list lives INSIDE it, so tiles slide within the glass)
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
        blurImg.layoutParams = FrameLayout.LayoutParams(screenW, screenH)
        blurImg.translationX = -outer.toFloat()          // line the blur up with the hero image behind
        blurImg.translationY = -dockTop.toFloat()
        glass.addView(blurImg)

        val dim = View(this)
        dim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(70, 0, 0, 0), Color.argb(150, 0, 0, 0))
        )
        glass.addView(dim, FrameLayout.LayoutParams(MATCH, MATCH))

        val sheen = View(this)
        sheen.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(70, 255, 255, 255), Color.argb(14, 255, 255, 255))
        )
        glass.addView(sheen, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- 8. the dock row: OTT apps only, scrolls sideways INSIDE the glass
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
        list.setPadding(edgePad, 0, edgePad, 0)
        list.setItemViewCacheSize(8)
        list.itemAnimator = null
        dockAdapter = TileAdapter(dockApps, true)
        list.adapter = dockAdapter
        glass.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))

        // soft fade at both ends so tiles dissolve into the dock edge instead of hard-cutting
        val fadeW = (edgePad + gap * 0.5f).toInt()
        val fadeL = View(this)
        fadeL.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.argb(170, 6, 8, 16), Color.TRANSPARENT)
        )
        glass.addView(fadeL, FrameLayout.LayoutParams(fadeW, MATCH, Gravity.START))
        val fadeR = View(this)
        fadeR.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.TRANSPARENT, Color.argb(170, 6, 8, 16))
        )
        glass.addView(fadeR, FrameLayout.LayoutParams(fadeW, MATCH, Gravity.END))

        dockBorder = GradientDrawable()
        dockBorder.cornerRadius = panelR
        dockBorder.setStroke(2, Color.argb(95, 255, 255, 255))
        dockBorder.setColor(Color.TRANSPARENT)
        val border = View(this)
        border.background = dockBorder
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

        val glassLp = FrameLayout.LayoutParams(panelW, panelH, Gravity.TOP or Gravity.START)
        glassLp.setMargins(outer, dockTop, 0, 0)
        root.addView(glass, glassLp)

        // ---- 8b. "All apps" section below the dock: every non-OTT app, Apple TV style
        val appsHeader = TextView(this)
        appsHeader.text = "ALL APPS"
        appsHeader.setTextColor(Color.argb(190, 255, 255, 255))
        appsHeader.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0105f)
        appsHeader.setTypeface(Typeface.DEFAULT_BOLD)
        appsHeader.letterSpacing = 0.3f
        val headerLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START)
        headerLp.setMargins(tileStartX, headerTop, 0, 0)
        root.addView(appsHeader, headerLp)

        val hint = TextView(this)
        hint.text = "Hold OK on any app to move it between the dock and Apps"
        hint.setTextColor(Color.argb(95, 255, 255, 255))
        hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.009f)
        val hintLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END)
        hintLp.setMargins(0, headerTop, tileStartX, 0)
        root.addView(hint, hintLp)

        grid = RecyclerView(this)
        grid.layoutManager = GridLayoutManager(this, gridSpan)
        grid.clipChildren = false
        grid.clipToPadding = false
        // Content width is exactly gridSpan * slotW so every column lines up with the dock tiles above.
        val gridLeft = outer + edgePad
        grid.setPadding(gridLeft, 0, (screenW - gridLeft - gridSpan * slotW).coerceAtLeast(0), (screenH * 0.05f).toInt())
        grid.setItemViewCacheSize(gridSpan * 2)
        grid.itemAnimator = null
        grid.overScrollMode = View.OVER_SCROLL_NEVER
        gridAdapter = TileAdapter(gridApps, false)
        grid.adapter = gridAdapter
        val gridLp = FrameLayout.LayoutParams(MATCH, gridH, Gravity.TOP or Gravity.START)
        gridLp.setMargins(0, gridTop, 0, 0)
        root.addView(grid, gridLp)

        // ---- 9. top bar (Apple TV style): wifi, time, settings, profile
        val iconS = (screenW * 0.0185f).toInt()
        val iconPad = (iconS * 0.42f).toInt()
        val iconBox = iconS + 2 * iconPad

        wifiDrawable = WifiDrawable(Color.WHITE)
        val wifiBtn = topIcon(wifiDrawable, iconBox, iconPad, true) { openSystem(Settings.ACTION_WIFI_SETTINGS) }

        val clock = TextClock(this)
        clock.format12Hour = "h:mm"
        clock.format24Hour = "HH:mm"
        clock.setTextColor(Color.WHITE)
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0135f)
        clock.setTypeface(Typeface.DEFAULT_BOLD)
        clock.setPadding((screenW * 0.006f).toInt(), 0, (screenW * 0.006f).toInt(), 0)

        val gearBtn = topIcon(GearDrawable(Color.WHITE), iconBox, iconPad, true) { openSystem(Settings.ACTION_SETTINGS) }

        avatarDrawable = AvatarDrawable()
        val avatarBtn = topIcon(avatarDrawable, iconBox, (iconPad * 0.55f).toInt(), false) {
            openSystem(Settings.ACTION_SYNC_SETTINGS)
        }

        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.clipChildren = false
        val bp = (screenW * 0.004f).toInt()
        topBar.setPadding(bp * 2, bp, bp * 2, bp)
        topBar.background = glassPill()
        topBar.addView(wifiBtn, LinearLayout.LayoutParams(iconBox, iconBox))
        topBar.addView(clock, LinearLayout.LayoutParams(WRAP, WRAP))
        topBar.addView(gearBtn, LinearLayout.LayoutParams(iconBox, iconBox))
        topBar.addView(avatarBtn, LinearLayout.LayoutParams(iconBox, iconBox))
        val topLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END)
        topLp.setMargins(0, (screenH * 0.04f).toInt(), tileStartX, 0)
        root.addView(topBar, topLp)

        setContentView(root)

        // entrance animation: dock and apps glide up and fade in
        val enter = DecelerateInterpolator(2f)
        val entering = listOf<Pair<View, Long>>(
            Pair(glass, 0L), Pair(appsHeader, 140L), Pair(hint, 140L), Pair(grid, 200L)
        )
        for ((v, delay) in entering) {
            v.alpha = 0f
            v.translationY = screenH * 0.04f
            v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(650)
                .setInterpolator(enter).start()
        }

        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val first = list.getChildAt(0) ?: grid.getChildAt(0)
                first?.requestFocus()
            }
        })

        requestMissingPermissions()
    }

    private fun hasAccountsPermission(): Boolean =
        Build.VERSION.SDK_INT < 23 ||
            checkSelfPermission(ACCT_PERM) == PackageManager.PERMISSION_GRANTED

    private fun requestMissingPermissions() {
        if (Build.VERSION.SDK_INT < 23) return
        val need = ArrayList<String>()
        if (!hasTvPermission()) need.add(TV_PERM)
        if (!hasAccountsPermission()) need.add(ACCT_PERM)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 77)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshAccount()
        if (hasTvPermission()) loadHeroAsync()
    }

    /** Small round, focusable icon button for the top bar. */
    private fun topIcon(d: Drawable, box: Int, pad: Int, whiteIcon: Boolean, onClick: () -> Unit): ImageView {
        val v = ImageView(this)
        v.setImageDrawable(d)
        v.scaleType = ImageView.ScaleType.FIT_CENTER
        v.setPadding(pad, pad, pad, pad)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.TRANSPARENT)
        v.background = bg
        v.isFocusable = true
        v.isClickable = true
        if (Build.VERSION.SDK_INT >= 26) v.defaultFocusHighlightEnabled = false
        v.setOnFocusChangeListener { view, focused ->
            val s = if (focused) 1.14f else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(150).start()
            bg.setColor(if (focused) Color.argb(240, 255, 255, 255) else Color.TRANSPARENT)
            if (whiteIcon) v.setColorFilter(if (focused) Color.BLACK else Color.WHITE)
        }
        v.setOnClickListener { onClick() }
        v.setOnKeyListener { _, keyCode, ev ->
            if (ev.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusDock()
                true
            } else false
        }
        return v
    }

    private fun openSystem(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                // ignore
            }
        }
    }

    /** Wi-Fi strength / connectivity for the top-bar icon. */
    @Suppress("DEPRECATION")
    private fun refreshStatus() {
        var connected = false
        var level = 0
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val ni = cm.activeNetworkInfo
            if (ni != null && ni.isConnected) {
                connected = true
                level = 3
                if (ni.type == ConnectivityManager.TYPE_WIFI) {
                    val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val rssi = wm.connectionInfo.rssi
                    level = if (rssi >= -60) 3 else if (rssi >= -72) 2 else 1
                }
            }
        } catch (e: Exception) {
            connected = true
            level = 3
        }
        wifiDrawable.update(level, connected)
    }

    /** Shows the signed-in Google account (initial on a colour) in the top bar. */
    private fun refreshAccount() {
        var name: String? = null
        try {
            if (hasAccountsPermission()) {
                name = AccountManager.get(this).getAccountsByType("com.google").firstOrNull()?.name
            }
        } catch (e: Exception) {
            name = null
        }
        if (name == null) {
            avatarDrawable.set(null, Color.parseColor("#4a4f5e"))
        } else {
            val palette = intArrayOf(
                Color.parseColor("#5b6cff"), Color.parseColor("#e2574c"), Color.parseColor("#1fa971"),
                Color.parseColor("#e8912d"), Color.parseColor("#a24bd6"), Color.parseColor("#1c9fd1")
            )
            val color = palette[(name.hashCode() and 0x7fffffff) % palette.size]
            avatarDrawable.set(name.substring(0, 1).uppercase(), color)
        }
    }

    override fun onResume() {
        super.onResume()
        failed.clear()
        refreshAccount()
        handler.removeCallbacks(statusTick)
        statusTick.run()
        if (hasTvPermission()) loadHeroAsync()
    }

    override fun onPause() {
        handler.removeCallbacks(rotator)
        handler.removeCallbacks(statusTick)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already here: jump back to the first dock app.
        lastDockPos = 0
        lastGridPos = 0
        list.scrollToPosition(0)
        grid.scrollToPosition(0)
        focusDock()
    }

    override fun onBackPressed() {
        // Launcher is the root screen: ignore Back.
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
                    // High cap on purpose: big apps (YouTube, Netflix) publish hundreds of rows and used to
                    // use up the old 400-row limit before smaller apps (Chorki, Toffee...) were ever reached.
                    while (cur.moveToNext() && rows < 8000) {
                        rows++
                        val pkg = cur.str("package_name") ?: continue
                        if (pkg == packageName) continue
                        val poster = cur.str("poster_art_uri")
                        val thumb = cur.str("thumbnail_uri")
                        val ratio = cur.num("poster_art_aspect_ratio")
                        val wide = ratio == 0 || ratio == 1 || ratio == 2
                        val images = (if (wide) listOf(poster, thumb) else listOf(thumb, poster))
                            .filterNotNull()
                            .filter { it.isNotBlank() }
                            .distinct()
                        if (images.isEmpty()) continue
                        val items = out.getOrPut(pkg) { mutableListOf() }
                        if (items.size < 8 && items.none { it.images[0] == images[0] }) {
                            items.add(
                                HeroItem(
                                    pkg,
                                    cur.str("title"),
                                    cur.str("short_description"),
                                    images,
                                    cur.str("intent_uri")
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TV provider query failed for $u", e)
            }
        }
        Log.i(TAG, "hero artwork per app: " + out.mapValues { it.value.size })
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
            showBrand(app)
            if (app.pkg != SETTINGS_PKG && !hasTvPermission()) {
                descView.text =
                    "To show artwork from your apps, run on a PC: adb shell pm grant $packageName $TV_PERM"
                descView.visibility = View.VISIBLE
            } else {
                descView.visibility = View.GONE
            }
            return
        }
        // Switching to another app: drop the previous app's artwork straight away so it never
        // sits next to the wrong title while the new image is still downloading.
        if (heroShownPkg != app.pkg) clearHeroImage()
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
        heroShownPkg = null
        heroA.animate().alpha(0f).setDuration(250).start()
        heroB.animate().alpha(0f).setDuration(250).start()
        blurImg.animate().alpha(0f).setDuration(250).start()
    }

    private fun showBrand(app: AppItem) {
        brandBg.setColors(intArrayOf(withValue(app.tint, 0.80f, 1.1f), withValue(app.tint, 0.26f, 1.1f)))
        brandArt.setImageDrawable(app.art)
        if (app.isBanner) {
            brandArt.scaleType = ImageView.ScaleType.CENTER_CROP
            brandArt.setPadding(0, 0, 0, 0)
        } else {
            brandArt.scaleType = ImageView.ScaleType.FIT_CENTER
            val p = (brandCard.layoutParams.height * 0.2f).toInt()
            brandArt.setPadding(p, p, p, p)
        }
        brandCard.animate().alpha(1f).setDuration(350).start()
    }

    private fun hideBrand() {
        brandCard.animate().alpha(0f).setDuration(250).start()
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
        hideBrand()

        val token = ++heroToken
        loadFirst(item.images, 0, token) { bmp ->
            if (token != heroToken) return@loadFirst
            if (bmp == null) {
                Log.w(TAG, "no usable artwork for ${app.pkg}: ${item.images}")
                clearHeroImage()
                showBrand(app)
            } else {
                heroShownPkg = app.pkg
                displayHero(bmp)
            }
        }
    }

    /** Try each candidate image of a hero item until one decodes. */
    private fun loadFirst(uris: List<String>, idx: Int, token: Int, done: (Bitmap?) -> Unit) {
        if (idx >= uris.size) {
            done(null)
            return
        }
        loadBitmap(uris[idx]) { bmp ->
            if (token != heroToken) return@loadBitmap
            if (bmp != null) done(bmp) else loadFirst(uris, idx + 1, token, done)
        }
    }

    private fun displayHero(bmp: Bitmap) {
        val incoming = if (heroFront === heroA) heroB else heroA
        val outgoing = heroFront
        val wide = bmp.width.toFloat() / bmp.height.toFloat() >= 1.4f
        val st = if (wide) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_END

        kenBurns?.cancel()
        incoming.animate().cancel()
        incoming.scaleType = st
        incoming.setImageBitmap(bmp)
        incoming.scaleX = 1f
        incoming.scaleY = 1f
        incoming.alpha = 0f
        incoming.animate().alpha(1f).setDuration(600).start()
        outgoing.animate().alpha(0f).setDuration(600).start()
        heroFront = incoming

        blurImg.scaleType = st
        blurImg.setImageBitmap(makeBlur(bmp))
        blurImg.animate().alpha(1f).setDuration(500).start()

        startKenBurns(incoming)
    }

    private fun makeBlur(src: Bitmap): Bitmap {
        val w = 48
        val h = (w * src.height.toFloat() / src.width.toFloat()).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun startKenBurns(v: ImageView) {
        kenBurns?.cancel()
        v.scaleX = 1f
        v.scaleY = 1f
        val sx = ObjectAnimator.ofFloat(v, View.SCALE_X, 1f, 1.08f)
        val sy = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f, 1.08f)
        val set = AnimatorSet()
        set.playTogether(sx, sy)
        set.duration = 14000
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
        if (failed.contains(uriStr)) {
            cb(null)
            return
        }
        io.execute {
            val bmp = try {
                decode(uriStr)
            } catch (e: Throwable) {
                Log.w(TAG, "image load failed: $uriStr", e)
                null
            }
            runOnUiThread {
                if (bmp != null) cache.put(uriStr, bmp) else failed.add(uriStr)
                cb(bmp)
            }
        }
    }

    /**
     * Downloads (or opens) the raw image bytes. Compared with the old version this:
     *  - sends a browser User-Agent (some CDNs answer 403 to the default Java agent),
     *  - asks for jpeg/png/webp only (a CDN may otherwise reply with AVIF, which Android 9 cannot decode),
     *  - follows redirects itself, including http -> https which HttpURLConnection refuses to do,
     *  - accepts "//host/path" and unescaped spaces.
     */
    private fun fetchBytes(uriStr: String): ByteArray? {
        var target = uriStr.trim().replace(" ", "%20")
        if (target.startsWith("//")) target = "https:$target"
        val parsed = Uri.parse(target)
        val scheme = parsed.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            var hops = 0
            while (hops < 6) {
                val conn = URL(target).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", UA)
                conn.setRequestProperty("Accept", "image/jpeg,image/png,image/webp,image/*;q=0.8,*/*;q=0.5")
                try {
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val loc = conn.getHeaderField("Location") ?: return null
                        target = URL(URL(target), loc).toString()
                        hops++
                        continue
                    }
                    if (code !in 200..299) {
                        Log.w(TAG, "HTTP $code for $target")
                        return null
                    }
                    return conn.inputStream.use { it.readBytes() }
                } finally {
                    conn.disconnect()
                }
            }
            return null
        }
        return contentResolver.openInputStream(parsed)?.use { it.readBytes() }
    }

    private fun decode(uriStr: String): Bitmap? {
        val bytes = fetchBytes(uriStr) ?: return null
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "undecodable image (${bytes.size} bytes, ${bounds.outMimeType}): $uriStr")
            return null
        }
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

    /** OTT apps live in the dock; the user can override any app by holding OK on it. */
    private fun isOtt(a: AppItem): Boolean {
        if (forceDock.contains(a.pkg)) return true
        if (forceGrid.contains(a.pkg)) return false
        val p = a.pkg.lowercase()
        val l = a.label.lowercase()
        if (ottPkgKeys.any { p.contains(it) }) return true
        if (ottLabelKeys.any { l.contains(it) }) return true
        return l == "max"
    }

    private fun rebuildLists() {
        dockApps.clear()
        gridApps.clear()
        val real = allApps.filter { it.pkg != SETTINGS_PKG }
        dockApps.addAll(
            real.filter { isOtt(it) }
                .sortedWith(compareBy<AppItem>({ prio(it) }, { it.label.lowercase() }))
        )
        allApps.firstOrNull { it.pkg == SETTINGS_PKG }?.let { gridApps.add(it) }
        gridApps.addAll(real.filter { !isOtt(it) }.sortedBy { it.label.lowercase() })
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
            .toMutableList()

        apps.add(AppItem("Settings", SETTINGS_PKG, GearDrawable(Color.argb(235, 255, 255, 255)), false, Color.parseColor("#4a4f5e")))
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

    /** Long-press OK: move an app between the dock and the Apps grid (remembered). */
    private fun toggleDock(app: AppItem) {
        if (app.pkg == SETTINGS_PKG) return
        val toDock = !isOtt(app)
        if (toDock) {
            forceDock.add(app.pkg)
            forceGrid.remove(app.pkg)
        } else {
            forceGrid.add(app.pkg)
            forceDock.remove(app.pkg)
        }
        prefs.edit()
            .putStringSet(KEY_DOCK, HashSet<String>(forceDock))
            .putStringSet(KEY_GRID, HashSet<String>(forceGrid))
            .apply()
        rebuildLists()
        dockAdapter.notifyDataSetChanged()
        gridAdapter.notifyDataSetChanged()
        Toast.makeText(
            this,
            if (toDock) "${app.label} moved to the dock" else "${app.label} moved to Apps",
            Toast.LENGTH_SHORT
        ).show()
        if (toDock) {
            focusPosition(list, dockApps.indexOfFirst { it.pkg == app.pkg })
        } else {
            focusPosition(grid, gridApps.indexOfFirst { it.pkg == app.pkg })
        }
    }

    // =====================================================================
    // Focus helpers
    // =====================================================================

    private fun focusPosition(rv: RecyclerView, idx: Int) {
        if (idx < 0) return
        val now = rv.findViewHolderForAdapterPosition(idx)?.itemView
        if (now != null) {
            now.requestFocus()
            return
        }
        rv.scrollToPosition(idx)
        rv.postDelayed({ rv.findViewHolderForAdapterPosition(idx)?.itemView?.requestFocus() }, 120)
    }

    private fun focusDock() {
        if (dockApps.isEmpty()) {
            if (gridApps.isNotEmpty()) focusPosition(grid, lastGridPos.coerceIn(0, gridApps.size - 1))
            return
        }
        focusPosition(list, lastDockPos.coerceIn(0, dockApps.size - 1))
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
            val c = a.animatedValue as Int
            aurora.setTint(withValue(c, 0.75f, 1.15f))
            // the dock rim picks up the focused app's colour
            val rim = withValue(c, 1f, 0.55f)
            dockBorder.setStroke(
                2,
                Color.argb(120, Color.red(rim), Color.green(rim), Color.blue(rim))
            )
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
    // Tiles (shared by the dock and the Apps grid)
    // =====================================================================

    private fun centerOn(v: View) {
        val slot = v.parent as? View ?: return
        val dx = (slot.left + slot.width / 2) - list.width / 2
        if (dx != 0) list.smoothScrollBy(dx, 0)
    }

    private fun buildTile(app: AppItem, inDock: Boolean): View {
        val r = radius
        val glowColor = withValue(app.tint, 1f, 1.3f)

        // soft coloured glow that appears behind the focused tile
        val glow = View(this)
        val glowBg = GradientDrawable()
        glowBg.gradientType = GradientDrawable.RADIAL_GRADIENT
        glowBg.gradientRadius = tileW * 0.9f
        glowBg.setColors(
            intArrayOf(
                Color.argb(120, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                Color.argb(0, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
            )
        )
        glow.background = glowBg
        glow.alpha = 0f
        val glowSize = (tileW * 1.8f).toInt()
        glow.layoutParams = FrameLayout.LayoutParams(glowSize, glowSize, Gravity.CENTER)

        val card = FrameLayout(this)
        card.layoutParams = FrameLayout.LayoutParams(tileW, tileH)
        val cardBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(withValue(app.tint, 0.55f, 1.1f), withValue(app.tint, 0.22f, 1.1f))
        )
        cardBg.cornerRadius = r
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

        val gloss = View(this)
        gloss.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(46, 255, 255, 255), Color.TRANSPARENT)
        )
        gloss.layoutParams = FrameLayout.LayoutParams(MATCH, tileH / 2, Gravity.TOP)

        val ring = View(this)
        ring.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        val ringBg = GradientDrawable()
        ringBg.cornerRadius = r
        ringBg.setStroke((screenW * 0.0022f).toInt().coerceAtLeast(3), Color.WHITE)
        ringBg.setColor(Color.TRANSPARENT)
        ring.background = ringBg
        ring.alpha = 0f

        card.addView(art)
        card.addView(gloss)
        card.addView(ring)

        val wrap = FrameLayout(this)
        wrap.clipChildren = false
        wrap.layoutParams = LinearLayout.LayoutParams(tileW, tileH)
        wrap.addView(glow)
        wrap.addView(card)

        val restingLabel = if (inDock) 0f else 0.72f
        val label = TextView(this)
        label.text = app.label
        label.setTextColor(Color.WHITE)
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0115f)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.setPadding(0, (screenW * 0.006f).toInt(), 0, 0)
        label.alpha = restingLabel
        label.layoutParams = LinearLayout.LayoutParams(tileW, WRAP)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.clipChildren = false
        col.isFocusable = true
        col.isClickable = true
        col.isLongClickable = true
        col.addView(wrap)
        col.addView(label)
        col.pivotX = tileW / 2f
        col.pivotY = tileH / 2f
        if (Build.VERSION.SDK_INT >= 26) {
            col.defaultFocusHighlightEnabled = false
        }

        col.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.12f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(160).start()
            // lift the whole slot above its neighbours while focused
            (v.parent as? View)?.translationZ = if (focused) 20f else 0f
            label.animate().alpha(if (focused) 1f else restingLabel).setDuration(160).start()
            ring.animate().alpha(if (focused) 1f else 0f).setDuration(160).start()
            glow.animate().alpha(if (focused) 1f else 0f).setDuration(220).start()
            if (focused) {
                if (inDock) {
                    lastDockPos = dockApps.indexOfFirst { it.pkg == app.pkg }.coerceAtLeast(0)
                    centerOn(v)
                } else {
                    lastGridPos = gridApps.indexOfFirst { it.pkg == app.pkg }.coerceAtLeast(0)
                }
                onAppFocused(app)
            }
        }
        col.setOnClickListener { launchApp(app) }
        col.setOnLongClickListener {
            toggleDock(app)
            true
        }
        col.setOnKeyListener { _, keyCode, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) {
                false
            } else if (inDock && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (gridApps.isNotEmpty()) {
                    focusPosition(grid, lastGridPos.coerceIn(0, gridApps.size - 1))
                    true
                } else false
            } else if (!inDock && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                val pos = gridApps.indexOfFirst { it.pkg == app.pkg }
                if (pos in 0 until gridSpan) {
                    focusDock()
                    true
                } else false
            } else false
        }
        return col
    }

    inner class TileAdapter(private val apps: List<AppItem>, private val inDock: Boolean) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount() = apps.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val slot = FrameLayout(parent.context)
            slot.layoutParams = RecyclerView.LayoutParams(slotW, if (inDock) MATCH else slotH)
            slot.clipChildren = false
            return object : RecyclerView.ViewHolder(slot) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val slot = holder.itemView as FrameLayout
            slot.removeAllViews()
            val lp = FrameLayout.LayoutParams(
                tileW, WRAP,
                if (inDock) Gravity.CENTER else (Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            )
            if (!inDock) lp.topMargin = (gap * 0.4f).toInt()
            slot.addView(buildTile(apps[position], inDock), lp)
        }
    }
}
