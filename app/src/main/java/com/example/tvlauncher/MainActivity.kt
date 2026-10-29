package com.example.tvlauncher

import android.accounts.AccountManager
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.location.LocationManager
import android.media.AudioManager
import android.os.Environment
import android.os.StatFs
import android.text.format.DateFormat
import android.text.format.Formatter
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.text.InputType
import android.widget.EditText
import android.widget.ScrollView
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
import android.graphics.drawable.AdaptiveIconDrawable
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
    private var bars = 3
    private var online = true

    fun update(bars: Int, online: Boolean) {
        this.bars = bars
        this.online = online
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
            val lit = online && i < bars
            paint.style = Paint.Style.STROKE
            paint.color = color
            paint.alpha = if (lit) 255 else 70
            rect.set(cx - radii[i], cy - radii[i], cx + radii[i], cy + radii[i])
            canvas.drawArc(rect, 225f, 90f, false, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = if (online) 255 else 70
        canvas.drawCircle(cx, cy, w * 0.075f, paint)
        if (!online) {
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

/** Simple white glyphs drawn on the coloured badges of the Settings menu. */
class GlyphDrawable(private val kind: Int) : Drawable() {
    companion object {
        const val TV = 0
        const val MOON = 1
        const val REMOTE = 2
        const val GRID = 3
        const val INFO = 4
        const val HOME = 5
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val x0 = b.left.toFloat()
        val y0 = b.top.toFloat()
        val cx = x0 + w / 2f
        paint.color = Color.WHITE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = w * 0.09f
        when (kind) {
            TV -> {
                paint.style = Paint.Style.STROKE
                r.set(x0 + w * 0.06f, y0 + h * 0.14f, x0 + w * 0.94f, y0 + h * 0.70f)
                c.drawRoundRect(r, w * 0.1f, w * 0.1f, paint)
                c.drawLine(x0 + w * 0.32f, y0 + h * 0.88f, x0 + w * 0.68f, y0 + h * 0.88f, paint)
            }
            MOON -> {
                paint.style = Paint.Style.FILL
                val rr = w * 0.40f
                val cy = y0 + h / 2f
                path.reset()
                path.addCircle(cx, cy, rr, Path.Direction.CW)
                val cut = Path()
                cut.addCircle(cx + rr * 0.55f, cy - rr * 0.35f, rr * 0.85f, Path.Direction.CW)
                path.op(cut, Path.Op.DIFFERENCE)
                c.drawPath(path, paint)
            }
            REMOTE -> {
                paint.style = Paint.Style.STROKE
                r.set(x0 + w * 0.30f, y0 + h * 0.04f, x0 + w * 0.70f, y0 + h * 0.96f)
                c.drawRoundRect(r, w * 0.14f, w * 0.14f, paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(cx, y0 + h * 0.30f, w * 0.085f, paint)
                c.drawCircle(cx, y0 + h * 0.58f, w * 0.05f, paint)
                c.drawCircle(cx, y0 + h * 0.76f, w * 0.05f, paint)
            }
            GRID -> {
                paint.style = Paint.Style.FILL
                for (i in 0 until 2) {
                    for (j in 0 until 2) {
                        val l = x0 + w * (0.08f + i * 0.44f)
                        val t = y0 + h * (0.08f + j * 0.44f)
                        r.set(l, t, l + w * 0.40f, t + h * 0.40f)
                        c.drawRoundRect(r, w * 0.1f, w * 0.1f, paint)
                    }
                }
            }
            INFO -> {
                paint.style = Paint.Style.STROKE
                c.drawCircle(cx, y0 + h / 2f, w * 0.42f, paint)
                c.drawLine(cx, y0 + h * 0.46f, cx, y0 + h * 0.72f, paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(cx, y0 + h * 0.30f, w * 0.055f, paint)
            }
            else -> { // HOME
                paint.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx, y0 + h * 0.08f)
                path.lineTo(x0 + w * 0.95f, y0 + h * 0.48f)
                path.lineTo(x0 + w * 0.84f, y0 + h * 0.48f)
                path.lineTo(x0 + w * 0.84f, y0 + h * 0.90f)
                path.lineTo(x0 + w * 0.16f, y0 + h * 0.90f)
                path.lineTo(x0 + w * 0.16f, y0 + h * 0.48f)
                path.lineTo(x0 + w * 0.05f, y0 + h * 0.48f)
                path.close()
                c.drawPath(path, paint)
            }
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

/**
 * Vertical scroller for the home screen. We scroll it ourselves (row by row), so the stock
 * "scroll just enough to show the focused child" and arrow-key scrolling are switched off.
 */
class HomeScroll(context: Context) : ScrollView(context) {
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int = 0
    override fun executeKeyEvent(event: KeyEvent): Boolean = false
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
        val intentUri: String?,
        val progress: Float = 0f,          // 0..1 watched (Watch Next only)
        val engaged: Long = -1L,           // last engagement time, for sorting "recent"
        val type: Int = -1                 // TvContract program type: 0 movie, 1-3 series/season/episode
    )

    class TvData(
        val hero: Map<String, List<HeroItem>>,
        val recent: List<HeroItem>,
        val recommended: List<HeroItem>,
        val movies: List<HeroItem> = emptyList(),
        val series: List<HeroItem> = emptyList()
    )

    /** One entry of the Apple-TV-style Settings menu. */
    class SItem(
        val title: String,
        val glyph: Int = -1,
        val color: Int = 0,
        val value: String? = null,
        val sub: (() -> List<SItem>)? = null,     // opens a sub page (rebuilt every time it is shown)
        val checked: Boolean = false,             // shows a tick (picker lists)
        val note: Boolean = false,                // plain grey info line, not selectable
        val step: ((Int) -> Unit)? = null,        // Left / Right on the remote adjusts the value
        val action: (() -> Unit)? = null
    )

    class Page(val title: String, val build: () -> List<SItem>, var focus: Int = 0)
    class RowViews(val row: LinearLayout, val title: TextView, val value: TextView?)
    class WNet(val ssid: String, val caps: String, val level: Int, val connected: Boolean, val netId: Int)

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val TAG = "AnantaTV"
    private val UA = "Mozilla/5.0 (Linux; Android 9; MiBox4) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36"
    private val TV_PERM = "android.permission.READ_TV_LISTINGS"
    private val LOC_PERM = "android.permission.ACCESS_FINE_LOCATION"
    private val SETTINGS_PKG = "__settings"
    private val PREFS = "ananta_dock"
    private val KEY_DOCK = "force_dock"
    private val KEY_GRID = "force_grid"
    private val KEY_24H = "clock_24h"
    private val KEY_PREV_VOL = "prev_volume"
    private val COARSE_PERM = "android.permission.ACCESS_COARSE_LOCATION"
    private val NEARBY_PERM = "android.permission.NEARBY_WIFI_DEVICES"
    private val BT_PERM = "android.permission.BLUETOOTH_CONNECT"
    private val SECURE_PERM = "android.permission.WRITE_SECURE_SETTINGS"

    // Home rows, top to bottom
    private val R_DOCK = 0
    private val R_RECENT = 1
    private val R_APPS = 2
    private val R_REC = 3
    private val R_STORE = 4

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
    private var cardW = 0
    private var cardH = 0
    private var slotCardW = 0
    private var panelWpx = 0
    private var dockTopPx = 0
    private var radius = 0f

    private val defaultTint = Color.parseColor("#2b3a67")
    private var currentTint = defaultTint
    private var tintAnimator: ValueAnimator? = null
    private var kenBurns: AnimatorSet? = null

    private lateinit var prefs: SharedPreferences
    private var forceDock = HashSet<String>()
    private var forceGrid = HashSet<String>()

    private lateinit var rootLayer: FrameLayout
    private lateinit var homeGroup: FrameLayout
    private lateinit var scroll: HomeScroll
    private lateinit var content: LinearLayout
    private lateinit var heroLayer: FrameLayout
    private lateinit var heroTextLayer: FrameLayout
    private lateinit var leftScrim: View
    private lateinit var dimOverlay: View

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
    private lateinit var wifiBtnView: View
    private lateinit var gearBtnView: View
    private lateinit var clockView: TextClock
    private val ACCT_PERM = "android.permission.GET_ACCOUNTS"
    private val statusTick = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 8000)
        }
    }
    private lateinit var dots: LinearLayout

    private val rowRv = arrayOfNulls<RecyclerView>(5)
    private val rowSection = arrayOfNulls<View>(5)
    private val rowEmpty = arrayOfNulls<TextView>(5)
    private val lastPos = IntArray(5)
    private lateinit var dockRv: RecyclerView
    private lateinit var appsRv: RecyclerView
    private lateinit var dockAdapter: TileAdapter
    private lateinit var appsAdapter: TileAdapter
    private lateinit var recentAdapter: FeedAdapter
    private lateinit var recAdapter: FeedAdapter

    private var allApps: List<AppItem> = emptyList()
    private val appByPkg = HashMap<String, AppItem>()
    private val dockApps = ArrayList<AppItem>()
    private val gridApps = ArrayList<AppItem>()
    private val recentItems = ArrayList<HeroItem>()
    private val recItems = ArrayList<HeroItem>()

    private var heroMap: Map<String, List<HeroItem>> = emptyMap()
    private var focusedApp: AppItem? = null
    private var currentItem: HeroItem? = null
    private var heroShownPkg: String? = null
    private var heroIndex = 0
    private var heroToken = 0
    private val handler = Handler(Looper.getMainLooper())
    private val rotator = Runnable { rotateHero() }

    // Wi-Fi dropdown
    private lateinit var wifiPanel: FrameLayout
    private lateinit var wifiScroll: ScrollView
    private lateinit var wifiList: LinearLayout
    private var wifiOpen = false
    private var wifiRecvRegistered = false
    private var wifiScanning = false
    private var wifiScanRejected = false
    private var wifiScanToken = 0
    private val WIFI_SCAN_EVERY_MS = 30000L     // Android allows ~4 scans per 2 minutes for a foreground app
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                wifiScanning = false
                wifiScanRejected = false
            }
            populateWifi()
        }
    }
    private val wifiTick = object : Runnable {
        override fun run() {
            if (!wifiOpen) return
            startWifiScan()
            populateWifi()
            handler.postDelayed(this, WIFI_SCAN_EVERY_MS)
        }
    }

    // Settings screen
    private lateinit var settingsLayer: FrameLayout
    private lateinit var settingsTitle: TextView
    private lateinit var settingsCrumb: TextView
    private lateinit var settingsScroll: ScrollView
    private lateinit var settingsList: LinearLayout
    private val settingsStack = ArrayList<Page>()
    private var settingsOpen = false

    // ---- top-left nav (Home / All Apps / Movies / Series) + full-screen pages
    data class Poster(val title: String, val year: String, val genre: String)
    private lateinit var pageLayer: FrameLayout
    private lateinit var pageTitle: TextView
    private lateinit var pageSub: TextView
    private lateinit var pageRv: RecyclerView
    private var pageOpen = false
    private var libMovies: List<HeroItem> = emptyList()
    private var libSeries: List<HeroItem> = emptyList()
    data class PageEntry(val header: String? = null, val hero: HeroItem? = null, val poster: Poster? = null)
    private var navSelected = 0
    private val navViews = ArrayList<View>()
    private val navPaint = ArrayList<(Boolean) -> Unit>()
    private val posterUrls = java.util.concurrent.ConcurrentHashMap<String, String>()

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
        tileW = (screenW * 0.118f).toInt()           // wider app tiles: exact 16:9 so TV banners fit with no crop
        tileH = (tileW * 9f / 16f).toInt()      // 16:9 = Android TV banner ratio
        radius = screenW * 0.0095f

        // Dock geometry: no label inside the dock, so the icons sit exactly in the middle of the glass.
        val padV = (tileH * 0.30f).toInt()
        val panelH = tileH + 2 * padV
        val panelW = screenW - 2 * outer
        panelWpx = panelW
        val panelR = panelH * 0.3f
        edgePad = (panelR * 0.9f).toInt()
        slotW = tileW + 2 * gap
        val labelH = (screenW * 0.0115f * 1.6f + screenW * 0.006f).toInt()
        slotH = tileH + labelH + (gap * 1.2f).toInt()
        // Every block (hero text, dock glass, row titles, first card, top bar) shares the same left and
        // right edge: the edge of the glass dock. Nothing sticks out further left or right than the dock.
        val tileStartX = outer
        val dockTop = (screenH * 0.60f).toInt()      // dock sits lower so the hero slider (artwork + text + dots) has room to breathe
        dockTopPx = dockTop

        // Feed cards (Recently watched / Recommended)
        cardW = (screenW * 0.165f).toInt()
        cardH = (cardW * 9f / 16f).toInt()
        slotCardW = cardW + 2 * gap
        val cardTextH = (screenW * 0.0115f * 1.5f + screenW * 0.0095f * 1.5f + screenW * 0.01f).toInt()
        val feedRvH = cardH + cardTextH + gap * 2
        val appsRvH = slotH + gap

        allApps = loadApps()
        for (a in allApps) appByPkg[a.pkg] = a
        rebuildLists()

        rootLayer = FrameLayout(this)
        rootLayer.setBackgroundColor(Color.BLACK)
        rootLayer.clipChildren = false
        homeGroup = FrameLayout(this)
        homeGroup.clipChildren = false
        homeGroup.clipToPadding = false
        rootLayer.addView(homeGroup, FrameLayout.LayoutParams(MATCH, MATCH))
        val root = homeGroup

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

        // ---- 3. hero layer: artwork from OTT apps (two layers so images cross-fade) + brand card
        heroLayer = FrameLayout(this)
        heroA = ImageView(this)
        heroA.scaleType = ImageView.ScaleType.CENTER_CROP
        heroA.alpha = 0f
        heroLayer.addView(heroA, FrameLayout.LayoutParams(MATCH, MATCH))
        heroB = ImageView(this)
        heroB.scaleType = ImageView.ScaleType.CENTER_CROP
        heroB.alpha = 0f
        heroLayer.addView(heroB, FrameLayout.LayoutParams(MATCH, MATCH))
        heroFront = heroA

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
        val bh = (bw * 9f / 16f).toInt()
        val brandLp = FrameLayout.LayoutParams(bw, bh, Gravity.TOP or Gravity.END)
        brandLp.setMargins(0, (screenH * 0.17f).toInt(), tileStartX, 0)
        heroLayer.addView(brandCard, brandLp)
        root.addView(heroLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- 4. scrims for readability
        leftScrim = View(this)
        leftScrim.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.argb(185, 0, 0, 0), Color.TRANSPARENT)
        )
        root.addView(leftScrim, FrameLayout.LayoutParams((screenW * 0.58f).toInt(), MATCH, Gravity.START))
        val topScrim = View(this)
        topScrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(140, 0, 0, 0), Color.TRANSPARENT)
        )
        root.addView(topScrim, FrameLayout.LayoutParams(MATCH, (screenH * 0.22f).toInt(), Gravity.TOP))
        val bottomScrim = View(this)
        bottomScrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.argb(130, 0, 0, 0), Color.argb(238, 0, 0, 0))
        )
        root.addView(bottomScrim, FrameLayout.LayoutParams(MATCH, (screenH * 0.66f).toInt(), Gravity.BOTTOM))
        // grows darker while the page scrolls down, so the lower rows stay readable
        dimOverlay = View(this)
        dimOverlay.setBackgroundColor(Color.BLACK)
        dimOverlay.alpha = 0f
        root.addView(dimOverlay, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- 5. brand mark + hero text block
        heroTextLayer = FrameLayout(this)
        heroTextLayer.clipChildren = false

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
        infoLp.setMargins(tileStartX - infoPad, (screenH * 0.155f).toInt() - infoPad, 0, 0)
        heroTextLayer.addView(info, infoLp)
        root.addView(heroTextLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- 6. the scrolling page: [spacer + dots] [dock] [recently watched] [all apps] [recommended]
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.clipChildren = false
        content.clipToPadding = false
        content.setPadding(0, 0, 0, (screenH * 0.06f).toInt())

        val spacer = FrameLayout(this)
        spacer.clipChildren = false
        dots = LinearLayout(this)
        dots.orientation = LinearLayout.HORIZONTAL
        dots.gravity = Gravity.CENTER
        val dotsLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        dotsLp.bottomMargin = (screenH * 0.022f).toInt()
        spacer.addView(dots, dotsLp)
        content.addView(spacer, LinearLayout.LayoutParams(MATCH, dockTop))

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

        dockRv = RecyclerView(this)
        dockRv.layoutManager = centeringLayoutManager()
        dockRv.clipChildren = false
        dockRv.clipToPadding = false
        dockRv.setPadding(edgePad, 0, edgePad, 0)
        dockRv.setItemViewCacheSize(8)
        dockRv.itemAnimator = null
        dockAdapter = TileAdapter(dockApps, R_DOCK)
        dockRv.adapter = dockAdapter
        rowRv[R_DOCK] = dockRv
        glass.addView(dockRv, FrameLayout.LayoutParams(MATCH, MATCH))
        layoutDockPadding()

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

        val glassLp = LinearLayout.LayoutParams(panelW, panelH)
        glassLp.leftMargin = outer
        content.addView(glass, glassLp)
        rowSection[R_DOCK] = glass

        // ---- 8. rows below the dock
        recentAdapter = FeedAdapter(recentItems, R_RECENT)
        recAdapter = FeedAdapter(recItems, R_REC)
        appsAdapter = TileAdapter(gridApps, R_APPS)

        content.addView(
            makeSection(
                R_RECENT, "RECENTLY WATCHED", null, feedRvH, recentAdapter, tileStartX,
                "Nothing here yet. Videos you start in your apps will appear here."
            ),
            LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (screenH * 0.035f).toInt() }
        )
        content.addView(
            makeSection(
                R_APPS, "ALL APPS", "Hold OK on any app to move it between the dock and All Apps",
                appsRvH, appsAdapter, tileStartX, ""
            ),
            LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (screenH * 0.03f).toInt() }
        )
        appsRv = rowRv[R_APPS]!!
        content.addView(
            makeSection(
                R_REC, "RECOMMENDED", null, feedRvH, recAdapter, tileStartX,
                "Recommendations from your apps will appear here."
            ),
            LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (screenH * 0.03f).toInt() }
        )
        storeItems.addAll(computeStore())
        storeAdapter = StoreAdapter()
        content.addView(
            makeSection(
                R_STORE, "APPS FOR YOU", "Install from Google Play", appsRvH, storeAdapter, tileStartX,
                "No suggestions right now."
            ),
            LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (screenH * 0.03f).toInt() }
        )
        updateEmpty(R_RECENT)
        updateEmpty(R_REC)
        updateEmpty(R_STORE)

        scroll = HomeScroll(this)
        scroll.isFocusable = false
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.clipChildren = false
        scroll.clipToPadding = false
        scroll.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        pageLayer = buildPageLayer(tileStartX)
        root.addView(pageLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        scroll.viewTreeObserver.addOnScrollChangedListener { applyScrollFx(scroll.scrollY) }

        // ---- 9. top bar (Apple TV style): wifi, time, settings, profile.
        // Every ancestor has clipChildren=false, so the focused circle is never cropped any more.
        val iconS = (screenW * 0.0185f).toInt()
        val iconPad = (iconS * 0.42f).toInt()
        val iconBox = iconS + 2 * iconPad

        wifiDrawable = WifiDrawable(Color.WHITE)
        val wifiBtn = topIcon(wifiDrawable, iconBox, iconPad, true) { toggleWifiPanel() }
        wifiBtnView = wifiBtn

        val clock = TextClock(this)
        clockView = clock
        applyClockFormat()
        clock.setTextColor(Color.WHITE)
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0135f)
        clock.setTypeface(Typeface.DEFAULT_BOLD)
        clock.setPadding((screenW * 0.007f).toInt(), 0, (screenW * 0.007f).toInt(), 0)

        val gearBtn = topIcon(GearDrawable(Color.WHITE), iconBox, iconPad, true) { openSettings() }
        gearBtnView = gearBtn

        avatarDrawable = AvatarDrawable()
        val avatarBtn = topIcon(avatarDrawable, iconBox, (iconPad * 0.55f).toInt(), false) {
            openSystem(Settings.ACTION_SYNC_SETTINGS)
        }

        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.clipChildren = false
        topBar.clipToPadding = false
        val bp = (screenW * 0.006f).toInt()
        topBar.setPadding(bp * 2, bp, bp * 2, bp)
        topBar.background = glassPill()
        topBar.addView(wifiBtn, LinearLayout.LayoutParams(iconBox, iconBox))
        topBar.addView(clock, LinearLayout.LayoutParams(WRAP, WRAP))
        topBar.addView(gearBtn, LinearLayout.LayoutParams(iconBox, iconBox))
        topBar.addView(avatarBtn, LinearLayout.LayoutParams(iconBox, iconBox))
        val topMarginPx = (screenH * 0.04f).toInt()
        val topLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END)
        topLp.setMargins(0, topMarginPx, tileStartX, 0)
        root.addView(topBar, topLp)

        // ---- top-left nav pill: same glass look as the Wi-Fi / Settings pill on the other side
        val navBar = LinearLayout(this)
        navBar.orientation = LinearLayout.HORIZONTAL
        navBar.gravity = Gravity.CENTER_VERTICAL
        navBar.clipChildren = false
        navBar.clipToPadding = false
        navBar.setPadding(bp * 2, bp, bp * 2, bp)
        navBar.background = glassPill()
        navBar.addView(navButton(0, GlyphDrawable(GlyphDrawable.HOME), null, iconBox, iconPad) { goHome() }, LinearLayout.LayoutParams(iconBox, iconBox))
        navBar.addView(navButton(1, GlyphDrawable(GlyphDrawable.GRID), null, iconBox, iconPad) { openPage(PAGE_APPS) }, LinearLayout.LayoutParams(iconBox, iconBox))
        navBar.addView(navButton(2, null, "Movies", iconBox, iconPad) { openPage(PAGE_MOVIES) }, LinearLayout.LayoutParams(WRAP, iconBox))
        navBar.addView(navButton(3, null, "Series", iconBox, iconPad) { openPage(PAGE_SERIES) }, LinearLayout.LayoutParams(WRAP, iconBox))
        val navLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START)
        navLp.setMargins(tileStartX, topMarginPx, 0, 0)
        root.addView(navBar, navLp)
        setNavSelected(0)

        // ---- 10. overlays: Wi-Fi dropdown + Apple-TV-style Settings
        wifiPanel = buildWifiPanel(topMarginPx + iconBox + 2 * bp + (screenH * 0.012f).toInt(), tileStartX)
        rootLayer.addView(wifiPanel)
        settingsLayer = buildSettingsLayer()
        rootLayer.addView(settingsLayer, FrameLayout.LayoutParams(MATCH, MATCH))

        setContentView(rootLayer)

        // entrance animation: the whole page glides up and fades in
        content.alpha = 0f
        content.translationY = screenH * 0.04f
        content.animate().alpha(1f).translationY(0f).setDuration(650)
            .setInterpolator(DecelerateInterpolator(2f)).start()

        rootLayer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rootLayer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val first = dockRv.getChildAt(0) ?: appsRv.getChildAt(0)
                first?.requestFocus()
            }
        })

        requestMissingPermissions()
    }

    private fun hasAccountsPermission(): Boolean =
        Build.VERSION.SDK_INT < 23 ||
            checkSelfPermission(ACCT_PERM) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < 23 ||
            checkSelfPermission(LOC_PERM) == PackageManager.PERMISSION_GRANTED

    private fun requestMissingPermissions() {
        if (Build.VERSION.SDK_INT < 23) return
        val need = ArrayList<String>()
        if (!hasTvPermission()) need.add(TV_PERM)
        if (!hasAccountsPermission()) need.add(ACCT_PERM)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 77)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 78) {
            // Wi-Fi scan permission granted (or refused): scan again and refresh the list either way
            if (wifiOpen) {
                startWifiScan()
                populateWifi()
            }
            return
        }
        if (requestCode == 79) {
            // Bluetooth permission answered: refresh the Settings page
            if (settingsOpen) showSettingsPage(true)
            return
        }
        refreshAccount()
        if (hasTvPermission()) loadHeroAsync()
    }

    /** Small round, focusable icon button for the top bar. */
    private fun topIcon(d: Drawable, box: Int, pad: Int, whiteIcon: Boolean, onClick: () -> Unit): ImageView {
        val v = ImageView(this)
        v.setImageDrawable(d)
        v.scaleType = ImageView.ScaleType.FIT_CENTER
        v.setPadding(pad, pad, pad, pad)
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT))
        bg.shape = GradientDrawable.OVAL
        v.background = bg
        v.isFocusable = true
        v.isClickable = true
        if (Build.VERSION.SDK_INT >= 26) v.defaultFocusHighlightEnabled = false
        v.setOnFocusChangeListener { view, focused ->
            val s = if (focused) 1.08f else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(150).start()
            glassFocus(bg, focused, false)
            if (whiteIcon) v.setColorFilter(Color.WHITE)
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

    private fun googleAccount(): String? {
        return try {
            if (hasAccountsPermission()) {
                AccountManager.get(this).getAccountsByType("com.google").firstOrNull()?.name
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /** Shows the signed-in Google account (initial on a colour) in the top bar. */
    private fun refreshAccount() {
        val name = googleAccount()
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
        if (::storeAdapter.isInitialized) refreshStore()
        refreshAccount()
        handler.removeCallbacks(statusTick)
        statusTick.run()
        if (hasTvPermission()) loadHeroAsync()
        // The permission dialog and other screens pause this activity, which drops the Wi-Fi receiver.
        // Put it back and rescan, otherwise the dropdown never gets the scan results.
        if (wifiOpen) {
            registerWifiReceiver()
            startWifiScan()
            populateWifi()
            handler.removeCallbacks(wifiTick)
            handler.postDelayed(wifiTick, WIFI_SCAN_EVERY_MS)
        }
        if (settingsOpen) showSettingsPage(true)
    }

    override fun onPause() {
        handler.removeCallbacks(rotator)
        handler.removeCallbacks(statusTick)
        handler.removeCallbacks(wifiTick)
        unregisterWifiReceiver()
        stopBtDiscovery()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already here: close any panel and jump back to the first dock app.
        if (wifiOpen) closeWifiPanel(false)
        if (settingsOpen) closeSettings(false)
        if (pageOpen) closePage()
        setNavSelected(0)
        lastPos.fill(0)
        dockRv.scrollToPosition(0)
        appsRv.scrollToPosition(0)
        scroll.scrollTo(0, 0)
        focusDock()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        // Launcher is the root screen: Back only closes the Wi-Fi dropdown / Settings.
        if (settingsOpen) settingsBack()
        else if (wifiOpen) closeWifiPanel(true)
        else if (pageOpen) goHome()
    }

    private fun glassPill(): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = 200f
        g.setColor(Color.argb(110, 30, 30, 40))
        g.setStroke(2, Color.argb(60, 255, 255, 255))
        return g
    }

    /** Hero text / artwork fade away as the page scrolls down to the lower rows. */
    private fun applyScrollFx(y: Int) {
        val p = (y / (screenH * 0.38f)).coerceIn(0f, 1f)
        heroLayer.alpha = 1f - 0.55f * p
        heroTextLayer.alpha = 1f - p
        leftScrim.alpha = 1f - p
        dimOverlay.alpha = 0.6f * p
    }

    // =====================================================================
    // TV provider data: hero artwork, Recently watched, Recommended
    // =====================================================================

    private fun hasTvPermission(): Boolean =
        Build.VERSION.SDK_INT < 23 ||
            checkSelfPermission(TV_PERM) == PackageManager.PERMISSION_GRANTED

    private fun feedKey(l: List<HeroItem>): List<String> =
        l.map { it.pkg + "|" + (it.title ?: "") + "|" + (it.images.firstOrNull() ?: "") + "|" + (it.progress * 100).toInt() }

    private fun loadHeroAsync() {
        io.execute {
            val data = queryTv()
            runOnUiThread {
                heroMap = data.hero
                libMovies = data.movies
                libSeries = data.series
                // Only touch the rows when something changed, otherwise the focused card would lose focus.
                if (feedKey(recentItems) != feedKey(data.recent)) {
                    recentItems.clear()
                    recentItems.addAll(data.recent)
                    recentAdapter.notifyDataSetChanged()
                    updateEmpty(R_RECENT)
                }
                if (feedKey(recItems) != feedKey(data.recommended)) {
                    recItems.clear()
                    recItems.addAll(data.recommended)
                    recAdapter.notifyDataSetChanged()
                    updateEmpty(R_REC)
                }
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

    private fun Cursor.lng(name: String): Long {
        val i = getColumnIndex(name)
        return if (i >= 0 && !isNull(i)) getLong(i) else -1L
    }

    private fun queryTv(): TvData {
        val hero = LinkedHashMap<String, MutableList<HeroItem>>()
        val recent = ArrayList<HeroItem>()
        val previewByPkg = LinkedHashMap<String, MutableList<HeroItem>>()
        val libMovieBy = LinkedHashMap<String, MutableList<HeroItem>>()
        val libSeriesBy = LinkedHashMap<String, MutableList<HeroItem>>()
        if (Build.VERSION.SDK_INT < 26) return TvData(hero, recent, emptyList())

        val sources = listOf(
            Pair("content://android.media.tv/preview_program", false),
            Pair("content://android.media.tv/watch_next_program", true)
        )
        for ((u, isWatchNext) in sources) {
            try {
                val c = contentResolver.query(Uri.parse(u), null, null, null, null) ?: continue
                c.use { cur ->
                    var rows = 0
                    // High cap on purpose: big apps (YouTube, Netflix) publish hundreds of rows.
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
                        val dur = cur.lng("duration_millis")
                        val pos = cur.lng("last_playback_position_millis")
                        val progress = if (dur > 0 && pos > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                        val item = HeroItem(
                            pkg,
                            cur.str("title"),
                            cur.str("short_description"),
                            images,
                            cur.str("intent_uri"),
                            progress,
                            cur.lng("last_engagement_time_utc_millis"),
                            cur.num("type")
                        )
                        // library of every installed app, split into movies and series (for the Movies / Series pages)
                        val tp = item.type
                        val libTitle = item.title
                        if (!libTitle.isNullOrBlank() && (tp == 0 || tp == 1 || tp == 2 || tp == 3)) {
                            val lib = if (tp == 0) libMovieBy else libSeriesBy
                            val l2 = lib.getOrPut(pkg) { mutableListOf() }
                            if (l2.size < 30 && l2.none { it.title == libTitle }) l2.add(item)
                        }
                        // hero artwork (per app)
                        val items = hero.getOrPut(pkg) { mutableListOf() }
                        if (items.size < 8 && items.none { it.images[0] == images[0] }) items.add(item)
                        // feeds
                        if (isWatchNext) {
                            if (recent.none { it.pkg == pkg && it.images[0] == images[0] }) recent.add(item)
                        } else {
                            val l = previewByPkg.getOrPut(pkg) { mutableListOf() }
                            if (l.size < 6 && l.none { it.images[0] == images[0] }) l.add(item)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TV provider query failed for $u", e)
            }
        }
        recent.sortByDescending { it.engaged }
        val recentOut = ArrayList<HeroItem>(recent.take(20))

        // YouTube / YouTube Music hero follows what the user actually played: their Watch Next
        // (most recently played first), then the apps' own picks.
        val ytGroup = listOf("com.google.android.youtube.tvmusic", "com.google.android.youtube.tv")
        for (ytPkg in ytGroup) {
            if (!hero.containsKey(ytPkg) && recent.none { it.pkg == ytPkg }) continue
            val merged = ArrayList<HeroItem>()
            fun addAll(src: List<HeroItem>) {
                for (it2 in src) {
                    if (merged.size >= 10) return
                    if (merged.none { m -> m.images[0] == it2.images[0] }) merged.add(it2)
                }
            }
            addAll(recent.filter { it.pkg == ytPkg }.take(5))
            addAll(hero[ytPkg].orEmpty())
            for (other in ytGroup) if (other != ytPkg) addAll(recent.filter { it.pkg == other }.take(3) + hero[other].orEmpty())
            if (merged.isNotEmpty()) hero[ytPkg] = merged
        }

        // Recommended: interleave the apps so one app doesn't fill the whole row
        val seenRecent = HashSet<String>()
        for (r in recentOut) seenRecent.add(r.pkg + "|" + (r.images.firstOrNull() ?: ""))
        val rec = ArrayList<HeroItem>()
        var round = 0
        var added = true
        while (added && rec.size < 40) {
            added = false
            for ((_, l) in previewByPkg) {
                if (round < l.size && rec.size < 40) {
                    val it2 = l[round]
                    if (!seenRecent.contains(it2.pkg + "|" + it2.images[0])) rec.add(it2)
                    added = true
                }
            }
            round++
        }
        Log.i(TAG, "hero per app: " + hero.mapValues { it.value.size } + " recent=" + recentOut.size + " rec=" + rec.size)
        fun weave(m: Map<String, List<HeroItem>>): List<HeroItem> {
            val out = ArrayList<HeroItem>()
            var rd = 0
            var more = true
            while (more && out.size < 48) {
                more = false
                for ((_, l) in m) if (rd < l.size && out.size < 48) { out.add(l[rd]); more = true }
                rd++
            }
            return out
        }
        return TvData(hero, recentOut, rec, weave(libMovieBy), weave(libSeriesBy))
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
            brandArt.scaleType = ImageView.ScaleType.FIT_CENTER
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
        loadBitmap(uris[idx], screenW) { bmp ->
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

    private fun loadBitmap(uriStr: String, targetW: Int, cb: (Bitmap?) -> Unit) {
        val key = "$uriStr#$targetW"
        val cached = cache.get(key)
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
                decode(uriStr, targetW)
            } catch (e: Throwable) {
                Log.w(TAG, "image load failed: $uriStr", e)
                null
            }
            runOnUiThread {
                if (bmp != null) cache.put(key, bmp) else failed.add(uriStr)
                cb(bmp)
            }
        }
    }

    /** Downloads (or opens) the raw image bytes: browser UA, manual redirects incl. http->https, jpeg/png/webp only. */
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

    private fun decode(uriStr: String, targetW: Int): Bitmap? {
        val bytes = fetchBytes(uriStr) ?: return null
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "undecodable image (${bytes.size} bytes, ${bounds.outMimeType}): $uriStr")
            return null
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetW) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /** Loads the first decodable candidate into [iv]; ignores late results for recycled views. */
    private fun loadInto(iv: ImageView, uris: List<String>, targetW: Int, onLoaded: () -> Unit) {
        val key = uris.firstOrNull() ?: return
        iv.tag = key
        fun step(i: Int) {
            if (i >= uris.size) return
            loadBitmap(uris[i], targetW) { bmp ->
                if (iv.tag != key) return@loadBitmap
                if (bmp != null) {
                    iv.setImageBitmap(bmp)
                    onLoaded()
                } else {
                    step(i + 1)
                }
            }
        }
        step(0)
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
                openSettings()
            } else {
                val i = packageManager.getLeanbackLaunchIntentForPackage(app.pkg)
                    ?: packageManager.getLaunchIntentForPackage(app.pkg)
                if (i != null) startActivity(i)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    /** Long-press OK: move an app between the dock and the All Apps row (remembered). */
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
        refreshAppRows()
        Toast.makeText(
            this,
            if (toDock) "${app.label} moved to the dock" else "${app.label} moved to All Apps",
            Toast.LENGTH_SHORT
        ).show()
        if (toDock) {
            focusPosition(dockRv, dockApps.indexOfFirst { it.pkg == app.pkg })
        } else {
            focusPosition(appsRv, gridApps.indexOfFirst { it.pkg == app.pkg })
        }
    }

    private fun refreshAppRows() {
        rebuildLists()
        dockAdapter.notifyDataSetChanged()
        appsAdapter.notifyDataSetChanged()
        layoutDockPadding()
    }

    private fun resetLayout() {
        forceDock.clear()
        forceGrid.clear()
        prefs.edit().remove(KEY_DOCK).remove(KEY_GRID).apply()
        refreshAppRows()
        Toast.makeText(this, "Dock and All Apps reset", Toast.LENGTH_SHORT).show()
    }

    /** Few apps in the dock: keep them centred in the glass instead of hugging the left edge. */
    private fun layoutDockPadding() {
        val contentW = dockApps.size * slotW
        val pad = if (contentW + 2 * edgePad < panelWpx) ((panelWpx - contentW) / 2).coerceAtLeast(edgePad) else edgePad
        dockRv.setPadding(pad, 0, pad, 0)
    }

    // =====================================================================
    // Rows, focus & scrolling
    // =====================================================================

    private fun rowCount(row: Int): Int = when (row) {
        R_DOCK -> dockApps.size
        R_RECENT -> recentItems.size
        R_APPS -> gridApps.size
        R_STORE -> storeItems.size
        else -> recItems.size
    }

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
        for (r in 0..3) {
            val rv = rowRv[r] ?: continue
            if (rowCount(r) > 0) {
                focusPosition(rv, lastPos[r].coerceIn(0, rowCount(r) - 1))
                return
            }
        }
    }

    /** D-pad up/down between rows (skips empty rows). Down at the very bottom is swallowed. */
    private fun moveVertical(from: Int, dir: Int): Boolean {
        var i = from + dir
        while (i in 0..4) {
            val rv = rowRv[i]
            if (rv != null && rowCount(i) > 0) {
                focusPosition(rv, lastPos[i].coerceIn(0, rowCount(i) - 1))
                return true
            }
            i += dir
        }
        return dir > 0
    }

    /** Scroll the page so the focused row sits near the top (dock row = page top). */
    private fun scrollToRow(row: Int) {
        val target = if (row == R_DOCK) 0 else ((rowSection[row]?.top ?: 0) - (screenH * 0.15f).toInt()).coerceAtLeast(0)
        val maxY = (content.height - scroll.height).coerceAtLeast(0)
        scroll.smoothScrollTo(0, target.coerceAtMost(maxY))
    }

    private fun centeringLayoutManager(): LinearLayoutManager =
        object : LinearLayoutManager(this, RecyclerView.HORIZONTAL, false) {
            // We centre the focused tile ourselves, so skip the default "just make it visible" scroll.
            override fun requestChildRectangleOnScreen(
                parent: RecyclerView,
                child: View,
                rect: Rect,
                immediate: Boolean,
                focusedChildVisible: Boolean
            ): Boolean = false
        }

    private fun centerOn(rv: RecyclerView, v: View) {
        val slot = v.parent as? View ?: return
        val dx = (slot.left + slot.width / 2) - rv.width / 2
        if (dx != 0) rv.smoothScrollBy(dx, 0)
    }

    private fun updateEmpty(row: Int) {
        val rv = rowRv[row] ?: return
        val empty = rowCount(row) == 0
        rv.visibility = if (empty) View.GONE else View.VISIBLE
        rowEmpty[row]?.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun sectionHeader(title: String, hint: String?, startX: Int): LinearLayout {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.HORIZONTAL
        h.gravity = Gravity.CENTER_VERTICAL
        val t = TextView(this)
        t.text = title
        t.setTextColor(Color.argb(190, 255, 255, 255))
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0105f)
        t.setTypeface(Typeface.DEFAULT_BOLD)
        t.letterSpacing = 0.3f
        h.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (hint != null) {
            val ht = TextView(this)
            ht.text = hint
            ht.setTextColor(Color.argb(95, 255, 255, 255))
            ht.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.009f)
            h.addView(ht, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        h.setPadding(startX, 0, startX, (screenW * 0.006f).toInt())
        return h
    }

    /** A titled, horizontally sliding row. Row 0 (dock) is built separately. */
    private fun makeSection(
        row: Int,
        title: String,
        hint: String?,
        rvHeight: Int,
        adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>,
        startX: Int,
        emptyText: String
    ): LinearLayout {
        val section = LinearLayout(this)
        section.orientation = LinearLayout.VERTICAL
        section.clipChildren = false
        section.clipToPadding = false
        section.addView(sectionHeader(title, hint, startX), LinearLayout.LayoutParams(MATCH, WRAP))

        val rv = RecyclerView(this)
        rv.layoutManager = centeringLayoutManager()
        rv.clipChildren = false
        rv.clipToPadding = false
        rv.setPadding(outer - gap, 0, outer - gap, 0)   // first card starts exactly at the dock edge
        rv.setItemViewCacheSize(6)
        rv.itemAnimator = null
        rv.overScrollMode = View.OVER_SCROLL_NEVER
        rv.adapter = adapter
        section.addView(rv, LinearLayout.LayoutParams(MATCH, rvHeight))
        rowRv[row] = rv

        val empty = TextView(this)
        empty.text = emptyText
        empty.setTextColor(Color.argb(120, 255, 255, 255))
        empty.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0115f)
        empty.setPadding(startX, (screenW * 0.006f).toInt(), startX, (screenW * 0.012f).toInt())
        section.addView(empty, LinearLayout.LayoutParams(MATCH, WRAP))
        rowEmpty[row] = empty

        rowSection[row] = section
        return section
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
    // Tiles (dock + All Apps) and cards (Recently watched / Recommended)
    // =====================================================================

    private fun buildTile(app: AppItem, row: Int): View {
        val inDock = row == R_DOCK
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
            art.scaleType = ImageView.ScaleType.FIT_CENTER   // never crop the banner
            art.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        } else {
            art.scaleType = ImageView.ScaleType.FIT_CENTER
            // Icon fills ~90% of the tile height. Adaptive icons only show the inner 72/108 of their
            // canvas (the rest is bleed outside the mask), so that canvas is enlarged to compensate;
            // the card's rounded clip trims anything beyond the tile.
            val target = tileH * 0.90f
            val adaptive = Build.VERSION.SDK_INT >= 26 && app.art is AdaptiveIconDrawable
            val s = (if (adaptive) target * 1.5f else target).toInt()
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
        // No label in the dock: the tile is then the only child and sits exactly in the middle of the glass.
        if (inDock) label.visibility = View.GONE

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
            (v.parent as? View)?.translationZ = if (focused) 20f else 0f
            label.animate().alpha(if (focused) 1f else restingLabel).setDuration(160).start()
            ring.animate().alpha(if (focused) 1f else 0f).setDuration(160).start()
            glow.animate().alpha(if (focused) 1f else 0f).setDuration(220).start()
            if (focused && row < 0) {
                setTint(app.tint)
            } else if (focused) {
                val list = if (inDock) dockApps else gridApps
                lastPos[row] = list.indexOfFirst { it.pkg == app.pkg }.coerceAtLeast(0)
                rowRv[row]?.let { centerOn(it, v) }
                scrollToRow(row)
                if (inDock) onAppFocused(app) else setTint(app.tint)
            }
        }
        col.setOnClickListener { launchApp(app) }
        if (row >= 0) {
            col.setOnLongClickListener {
                toggleDock(app)
                true
            }
            col.setOnKeyListener { _, keyCode, ev ->
                if (ev.action != KeyEvent.ACTION_DOWN) false
                else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) moveVertical(row, 1)
                else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) moveVertical(row, -1)
                else if (inDock && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    // End of the dock: loop back to the first app instead of leaving the dock.
                    val i = dockApps.indexOfFirst { it.pkg == app.pkg }
                    if (i == dockApps.size - 1) {
                        focusPosition(dockRv, 0)
                        true
                    } else false
                } else if (inDock && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    val i = dockApps.indexOfFirst { it.pkg == app.pkg }
                    if (i == 0) {
                        focusPosition(dockRv, dockApps.size - 1)
                        true
                    } else false
                } else false
            }
        }
        return col
    }

    private fun buildCard(item: HeroItem, row: Int, position: Int): View {
        val app = appByPkg[item.pkg]
        val tint = app?.tint ?: defaultTint
        val r = radius * 1.3f

        val card = FrameLayout(this)
        card.layoutParams = LinearLayout.LayoutParams(cardW, cardH)
        val cardBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(withValue(tint, 0.55f, 1.1f), withValue(tint, 0.22f, 1.1f))
        )
        cardBg.cornerRadius = r
        card.background = cardBg
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        card.clipToOutline = true

        // placeholder (app icon) shown until the artwork arrives
        val ph = ImageView(this)
        if (app != null) ph.setImageDrawable(app.art)
        ph.scaleType = ImageView.ScaleType.FIT_CENTER
        val ps = (cardH * 0.4f).toInt()
        card.addView(ph, FrameLayout.LayoutParams(ps, ps, Gravity.CENTER))

        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.alpha = 0f
        card.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))
        loadInto(img, item.images, (cardW * 1.3f).toInt()) {
            ph.visibility = View.GONE
            img.animate().alpha(1f).setDuration(250).start()
        }

        val gloss = View(this)
        gloss.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(40, 255, 255, 255), Color.TRANSPARENT)
        )
        card.addView(gloss, FrameLayout.LayoutParams(MATCH, cardH / 2, Gravity.TOP))

        if (item.progress > 0.01f) {
            val barH = (screenW * 0.0032f).toInt().coerceAtLeast(4)
            val track = View(this)
            track.setBackgroundColor(Color.argb(110, 255, 255, 255))
            card.addView(track, FrameLayout.LayoutParams(MATCH, barH, Gravity.BOTTOM))
            val fill = View(this)
            fill.setBackgroundColor(Color.WHITE)
            card.addView(
                fill,
                FrameLayout.LayoutParams((cardW * item.progress).toInt().coerceAtLeast(barH), barH, Gravity.BOTTOM or Gravity.START)
            )
        }

        val ring = View(this)
        val ringBg = GradientDrawable()
        ringBg.cornerRadius = r
        ringBg.setStroke((screenW * 0.0022f).toInt().coerceAtLeast(3), Color.WHITE)
        ringBg.setColor(Color.TRANSPARENT)
        ring.background = ringBg
        ring.alpha = 0f
        card.addView(ring, FrameLayout.LayoutParams(MATCH, MATCH))

        val title = TextView(this)
        title.text = item.title ?: app?.label.orEmpty()
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0115f)
        title.setTypeface(Typeface.DEFAULT_BOLD)
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        title.alpha = 0.75f
        title.setPadding(0, (screenW * 0.006f).toInt(), 0, 0)

        val sub = TextView(this)
        sub.text = app?.label.orEmpty()
        sub.setTextColor(Color.argb(150, 255, 255, 255))
        sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0095f)
        sub.maxLines = 1
        sub.ellipsize = TextUtils.TruncateAt.END

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.clipChildren = false
        col.isFocusable = true
        col.isClickable = true
        col.addView(card)
        col.addView(title, LinearLayout.LayoutParams(cardW, WRAP))
        col.addView(sub, LinearLayout.LayoutParams(cardW, WRAP))
        col.pivotX = cardW / 2f
        col.pivotY = cardH / 2f
        if (Build.VERSION.SDK_INT >= 26) col.defaultFocusHighlightEnabled = false

        col.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.08f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(160).start()
            (v.parent as? View)?.translationZ = if (focused) 20f else 0f
            ring.animate().alpha(if (focused) 1f else 0f).setDuration(160).start()
            title.animate().alpha(if (focused) 1f else 0.75f).setDuration(160).start()
            if (focused) {
                lastPos[row] = position
                rowRv[row]?.let { centerOn(it, v) }
                scrollToRow(row)
                setTint(tint)
            }
        }
        col.setOnClickListener { launchHero(item) }
        col.setOnKeyListener { _, keyCode, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) false
            else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) moveVertical(row, 1)
            else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) moveVertical(row, -1)
            else false
        }
        return col
    }

    inner class TileAdapter(private val apps: List<AppItem>, private val row: Int) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount() = apps.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val slot = FrameLayout(parent.context)
            slot.layoutParams = RecyclerView.LayoutParams(slotW, MATCH)
            slot.clipChildren = false
            return object : RecyclerView.ViewHolder(slot) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val slot = holder.itemView as FrameLayout
            slot.removeAllViews()
            val inDock = row == R_DOCK
            val lp = FrameLayout.LayoutParams(
                tileW, WRAP,
                if (inDock) Gravity.CENTER else (Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            )
            if (!inDock) lp.topMargin = (gap * 0.6f).toInt()
            slot.addView(buildTile(apps[position], row), lp)
        }
    }

    inner class FeedAdapter(private val items: List<HeroItem>, private val row: Int) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val slot = FrameLayout(parent.context)
            slot.layoutParams = RecyclerView.LayoutParams(slotCardW, MATCH)
            slot.clipChildren = false
            return object : RecyclerView.ViewHolder(slot) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val slot = holder.itemView as FrameLayout
            slot.removeAllViews()
            val lp = FrameLayout.LayoutParams(cardW, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            lp.topMargin = (gap * 0.6f).toInt()
            slot.addView(buildCard(items[position], row, position), lp)
        }
    }

    // =====================================================================
    // "Apps for you": Play Store suggestions that are not installed yet
    // =====================================================================

    data class StoreApp(val label: String, val pkg: String, val domain: String, val color: Int)

    private val STORE_CATALOG = listOf(
        StoreApp("Netflix", "com.netflix.ninja", "netflix.com", Color.parseColor("#E50914")),
        StoreApp("YouTube Music", "com.google.android.youtube.tvmusic", "music.youtube.com", Color.parseColor("#FF0033")),
        StoreApp("Prime Video", "com.amazon.amazonvideo.livingroom", "primevideo.com", Color.parseColor("#00A8E1")),
        StoreApp("Disney+", "com.disney.disneyplus", "disneyplus.com", Color.parseColor("#113CCF")),
        StoreApp("Disney+ Hotstar", "in.startv.hotstar", "hotstar.com", Color.parseColor("#1F80E0")),
        StoreApp("Max", "com.wbd.stream", "max.com", Color.parseColor("#002BE7")),
        StoreApp("Apple TV", "com.apple.atve.androidtv.appletv", "tv.apple.com", Color.parseColor("#3A3A3C")),
        StoreApp("Spotify", "com.spotify.tv.android", "spotify.com", Color.parseColor("#1DB954")),
        StoreApp("Crunchyroll", "com.crunchyroll.crunchyroid", "crunchyroll.com", Color.parseColor("#F47521")),
        StoreApp("MX Player", "com.mxtech.videoplayer.ad", "mxplayer.in", Color.parseColor("#2196F3")),
        StoreApp("Plex", "com.plexapp.android", "plex.tv", Color.parseColor("#E5A00D")),
        StoreApp("Twitch", "tv.twitch.android.app", "twitch.tv", Color.parseColor("#9146FF")),
        StoreApp("Tubi", "com.tubitv", "tubitv.com", Color.parseColor("#FA382F")),
        StoreApp("Pluto TV", "tv.pluto.android", "pluto.tv", Color.parseColor("#5B4FE9")),
        StoreApp("VLC", "org.videolan.vlc", "videolan.org", Color.parseColor("#FF8800")),
        StoreApp("Kodi", "org.xbmc.kodi", "kodi.tv", Color.parseColor("#17B2E7")),
        StoreApp("Downloader", "com.esaba.downloader", "", Color.parseColor("#3F51B5")),
        StoreApp("Send Files to TV", "com.yablio.sendfilestotv", "", Color.parseColor("#009688")),
        StoreApp("Aptoide TV", "cm.aptoide.pt", "aptoide.com", Color.parseColor("#FE6446"))
    )

    private val storeItems = ArrayList<StoreApp>()
    private lateinit var storeAdapter: StoreAdapter

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun computeStore(): List<StoreApp> = STORE_CATALOG.filter { !isInstalled(it.pkg) }.take(16)

    private fun refreshStore() {
        val fresh = computeStore()
        if (fresh.map { it.pkg } == storeItems.map { it.pkg }) return
        storeItems.clear()
        storeItems.addAll(fresh)
        storeAdapter.notifyDataSetChanged()
        updateEmpty(R_STORE)
    }

    private fun openStore(pkg: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.setPackage("com.android.vending")
            startActivity(i)
        } catch (e: Exception) {
            try {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            } catch (e2: Exception) {
                Toast.makeText(this, "Google Play Store not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class StoreAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = storeItems.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val slot = FrameLayout(parent.context)
            slot.layoutParams = RecyclerView.LayoutParams(slotW, MATCH)
            slot.clipChildren = false
            return object : RecyclerView.ViewHolder(slot) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val slot = holder.itemView as FrameLayout
            slot.removeAllViews()
            val lp = FrameLayout.LayoutParams(tileW, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            lp.topMargin = (gap * 0.6f).toInt()
            slot.addView(buildStoreTile(storeItems[position], position), lp)
        }
    }

    private fun buildStoreTile(a: StoreApp, position: Int): View {
        val r = radius
        val card = FrameLayout(this)
        card.layoutParams = FrameLayout.LayoutParams(tileW, tileH)
        val cardBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(withValue(a.color, 0.62f, 1.1f), withValue(a.color, 0.24f, 1.1f))
        )
        cardBg.cornerRadius = r
        card.background = cardBg
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        card.clipToOutline = true

        // logo on a white rounded plate (favicons come with all kinds of backgrounds)
        val plateS = (tileH * 0.50f).toInt()
        val plate = FrameLayout(this)
        val plateBg = GradientDrawable()
        plateBg.cornerRadius = plateS * 0.24f
        plateBg.setColor(Color.WHITE)
        plate.background = plateBg
        val initial = TextView(this)
        initial.text = a.label.take(1)
        initial.setTextColor(a.color)
        initial.setTextSize(TypedValue.COMPLEX_UNIT_PX, plateS * 0.55f)
        initial.setTypeface(Typeface.DEFAULT_BOLD)
        initial.gravity = Gravity.CENTER
        plate.addView(initial, FrameLayout.LayoutParams(MATCH, MATCH))
        val logo = ImageView(this)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        logo.alpha = 0f
        val lp2 = (plateS * 0.14f).toInt()
        logo.setPadding(lp2, lp2, lp2, lp2)
        plate.addView(logo, FrameLayout.LayoutParams(MATCH, MATCH))
        if (a.domain.isNotEmpty()) {
            loadInto(logo, listOf("https://www.google.com/s2/favicons?domain=${a.domain}&sz=128"), 128) {
                initial.visibility = View.GONE
                logo.animate().alpha(1f).setDuration(200).start()
            }
        }
        val plateLp = FrameLayout.LayoutParams(plateS, plateS, Gravity.CENTER_HORIZONTAL or Gravity.TOP)
        plateLp.topMargin = (tileH * 0.10f).toInt()
        card.addView(plate, plateLp)

        val gloss = View(this)
        gloss.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(46, 255, 255, 255), Color.TRANSPARENT)
        )
        card.addView(gloss, FrameLayout.LayoutParams(MATCH, tileH / 2, Gravity.TOP))

        // "Install" pill
        val pill = TextView(this)
        pill.text = "Install"
        pill.setTextColor(Color.WHITE)
        pill.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0082f)
        pill.setTypeface(Typeface.DEFAULT_BOLD)
        pill.gravity = Gravity.CENTER
        val pillBg = GradientDrawable()
        pillBg.cornerRadius = 100f
        pillBg.setColor(Color.argb(150, 0, 0, 0))
        pill.background = pillBg
        val pillH = (tileH * 0.20f).toInt()
        val pillLp = FrameLayout.LayoutParams((tileW * 0.34f).toInt(), pillH, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        pillLp.bottomMargin = (tileH * 0.07f).toInt()
        card.addView(pill, pillLp)

        val ring = View(this)
        val ringBg = GradientDrawable()
        ringBg.cornerRadius = r
        ringBg.setStroke((screenW * 0.0022f).toInt().coerceAtLeast(3), Color.WHITE)
        ringBg.setColor(Color.TRANSPARENT)
        ring.background = ringBg
        ring.alpha = 0f
        card.addView(ring, FrameLayout.LayoutParams(MATCH, MATCH))

        val label = TextView(this)
        label.text = a.label
        label.setTextColor(Color.WHITE)
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0115f)
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.setPadding(0, (screenW * 0.006f).toInt(), 0, 0)
        label.alpha = 0.72f

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.clipChildren = false
        col.isFocusable = true
        col.isClickable = true
        col.addView(card, LinearLayout.LayoutParams(tileW, tileH))
        col.addView(label, LinearLayout.LayoutParams(tileW, WRAP))
        col.pivotX = tileW / 2f
        col.pivotY = tileH / 2f
        if (Build.VERSION.SDK_INT >= 26) col.defaultFocusHighlightEnabled = false
        col.setOnFocusChangeListener { v, focused ->
            val sc = if (focused) 1.12f else 1f
            v.animate().scaleX(sc).scaleY(sc).setDuration(160).start()
            (v.parent as? View)?.translationZ = if (focused) 20f else 0f
            label.animate().alpha(if (focused) 1f else 0.72f).setDuration(160).start()
            ring.animate().alpha(if (focused) 1f else 0f).setDuration(160).start()
            if (focused) {
                lastPos[R_STORE] = position
                rowRv[R_STORE]?.let { centerOn(it, v) }
                scrollToRow(R_STORE)
                setTint(a.color)
            }
        }
        col.setOnClickListener { openStore(a.pkg) }
        col.setOnKeyListener { _, keyCode, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) false
            else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) moveVertical(R_STORE, 1)
            else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) moveVertical(R_STORE, -1)
            else false
        }
        return col
    }

    // =====================================================================
    // Top-left nav + full-screen pages (All Apps / Movies / Series)
    // =====================================================================

    private val PAGE_APPS = 0
    private val PAGE_MOVIES = 1
    private val PAGE_SERIES = 2

    private val MOVIES = listOf(
        Poster("The Shawshank Redemption", "1994", "Drama"),
        Poster("The Godfather", "1972", "Crime"),
        Poster("The Dark Knight", "2008", "Action"),
        Poster("The Godfather Part II", "1974", "Crime"),
        Poster("12 Angry Men", "1957", "Drama"),
        Poster("Schindler's List", "1993", "History"),
        Poster("The Lord of the Rings: The Return of the King", "2003", "Fantasy"),
        Poster("Pulp Fiction", "1994", "Crime"),
        Poster("Forrest Gump", "1994", "Drama"),
        Poster("Inception", "2010", "Sci-Fi"),
        Poster("Fight Club", "1999", "Drama"),
        Poster("The Matrix", "1999", "Sci-Fi"),
        Poster("Goodfellas", "1990", "Crime"),
        Poster("Interstellar", "2014", "Sci-Fi"),
        Poster("Parasite", "2019", "Thriller"),
        Poster("Gladiator", "2000", "Action"),
        Poster("The Lion King", "1994", "Animation"),
        Poster("Whiplash", "2014", "Drama"),
        Poster("Spirited Away", "2001", "Animation"),
        Poster("3 Idiots", "2009", "Comedy"),
        Poster("Dangal", "2016", "Sports"),
        Poster("Joker", "2019", "Drama"),
        Poster("Avengers: Endgame", "2019", "Action"),
        Poster("Django Unchained", "2012", "Western"),
        Poster("The Prestige", "2006", "Mystery"),
        Poster("Oppenheimer", "2023", "History"),
        Poster("Dune: Part Two", "2024", "Sci-Fi"),
        Poster("The Silence of the Lambs", "1991", "Thriller")
    )

    private val SERIES = listOf(
        Poster("Breaking Bad", "2008", "Crime"),
        Poster("Game of Thrones", "2011", "Fantasy"),
        Poster("The Wire", "2002", "Crime"),
        Poster("Chernobyl", "2019", "Drama"),
        Poster("Band of Brothers", "2001", "War"),
        Poster("Sherlock", "2010", "Mystery"),
        Poster("Better Call Saul", "2015", "Crime"),
        Poster("The Sopranos", "1999", "Crime"),
        Poster("Stranger Things", "2016", "Sci-Fi"),
        Poster("Peaky Blinders", "2013", "Crime"),
        Poster("Dark", "2017", "Sci-Fi"),
        Poster("Money Heist", "2017", "Thriller"),
        Poster("Squid Game", "2021", "Thriller"),
        Poster("Succession", "2018", "Drama"),
        Poster("True Detective", "2014", "Crime"),
        Poster("Narcos", "2015", "Crime"),
        Poster("Black Mirror", "2011", "Sci-Fi"),
        Poster("Mirzapur", "2018", "Crime"),
        Poster("Panchayat", "2020", "Comedy"),
        Poster("Sacred Games", "2018", "Crime"),
        Poster("Scam 1992", "2020", "Drama"),
        Poster("The Last of Us", "2023", "Drama"),
        Poster("Arcane", "2021", "Animation"),
        Poster("Attack on Titan", "2013", "Anime"),
        Poster("Death Note", "2006", "Anime"),
        Poster("Fargo", "2014", "Crime"),
        Poster("Friends", "1994", "Comedy"),
        Poster("The Office", "2005", "Comedy")
    )

    /** One nav button: round icon (Home, All Apps) or text pill (Movies, Series), same look as the Wi-Fi / Settings buttons. */
    private fun navButton(idx: Int, icon: Drawable?, label: String?, box: Int, pad: Int, onClick: () -> Unit): View {
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT))
        bg.cornerRadius = 200f
        val v: View
        val tintIt: (Int) -> Unit
        if (icon != null) {
            val iv = ImageView(this)
            iv.setImageDrawable(icon)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.setPadding(pad, pad, pad, pad)
            v = iv
            tintIt = { c -> iv.setColorFilter(c) }
        } else {
            val tv = TextView(this)
            tv.text = label
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0125f)
            tv.setTypeface(Typeface.DEFAULT_BOLD)
            tv.gravity = Gravity.CENTER
            val hp = (screenW * 0.014f).toInt()
            tv.setPadding(hp, 0, hp, 0)
            v = tv
            tintIt = { c -> tv.setTextColor(c) }
        }
        v.background = bg
        v.isFocusable = true
        v.isClickable = true
        if (Build.VERSION.SDK_INT >= 26) v.defaultFocusHighlightEnabled = false
        val paint: (Boolean) -> Unit = { focused ->
            glassFocus(bg, focused, navSelected == idx)
            tintIt(Color.WHITE)
        }
        navViews.add(v)
        navPaint.add(paint)
        paint(false)
        v.setOnFocusChangeListener { view, focused ->
            val sc = if (focused) 1.08f else 1f
            view.animate().scaleX(sc).scaleY(sc).setDuration(150).start()
            paint(focused)
        }
        v.setOnClickListener { onClick() }
        v.setOnKeyListener { _, keyCode, ev ->
            if (ev.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (pageOpen) pageRv.getChildAt(0)?.requestFocus() else focusDock()
                true
            } else false
        }
        return v
    }

    /** Frosted-glass highlight: translucent white sheen with a thin bright rim (no solid white block). */
    private fun glassFocus(bg: GradientDrawable, focused: Boolean, selected: Boolean) {
        if (focused) {
            bg.setColors(intArrayOf(Color.argb(120, 255, 255, 255), Color.argb(45, 255, 255, 255)))
            bg.setStroke(2, Color.argb(190, 255, 255, 255))
        } else if (selected) {
            bg.setColors(intArrayOf(Color.argb(60, 255, 255, 255), Color.argb(22, 255, 255, 255)))
            bg.setStroke(2, Color.argb(80, 255, 255, 255))
        } else {
            bg.setColors(intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT))
            bg.setStroke(0, Color.TRANSPARENT)
        }
    }

    private fun setNavSelected(idx: Int) {
        navSelected = idx
        for (i in navViews.indices) navPaint[i](navViews[i].isFocused)
    }

    private fun buildPageLayer(startX: Int): FrameLayout {
        val layer = FrameLayout(this)
        layer.clipChildren = false
        layer.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(9, 11, 19), Color.rgb(17, 13, 32))
        )
        pageTitle = TextView(this)
        pageTitle.setTextColor(Color.WHITE)
        pageTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.034f)
        pageTitle.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val tlp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START)
        tlp.setMargins(startX, (screenH * 0.15f).toInt(), 0, 0)
        layer.addView(pageTitle, tlp)

        pageSub = TextView(this)
        pageSub.setTextColor(Color.argb(170, 255, 255, 255))
        pageSub.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0115f)
        val slp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START)
        slp.setMargins(startX, (screenH * 0.15f + screenW * 0.045f).toInt(), 0, 0)
        layer.addView(pageSub, slp)

        pageRv = RecyclerView(this)
        pageRv.clipChildren = false
        pageRv.clipToPadding = false
        pageRv.overScrollMode = View.OVER_SCROLL_NEVER
        val side = (startX - gap).coerceAtLeast(0)
        pageRv.setPadding(side, gap, side, (screenH * 0.08f).toInt())
        val rlp = FrameLayout.LayoutParams(MATCH, MATCH)
        rlp.topMargin = (screenH * 0.15f + screenW * 0.065f).toInt()
        layer.addView(pageRv, rlp)

        layer.visibility = View.GONE
        return layer
    }

    private fun openPage(kind: Int) {
        if (wifiOpen) closeWifiPanel(false)
        val availW = screenW - 2 * outer + 2 * gap
        when (kind) {
            PAGE_APPS -> {
                val list = allApps.filter { it.pkg != SETTINGS_PKG }.sortedBy { it.label.lowercase() }
                pageTitle.text = "All Apps"
                pageSub.text = "${list.size} installed apps"
                val cols = (availW / slotW).coerceAtLeast(1)
                pageRv.layoutManager = GridLayoutManager(this, cols)
                pageRv.adapter = PageAdapter(list.size, availW / cols, { tileW }) { buildTile(list[it], -1) }
            }
            else -> {
                val series = kind == PAGE_SERIES
                val lib = if (series) libSeries else libMovies
                val curated = if (series) SERIES else MOVIES
                val entries = ArrayList<PageEntry>()
                if (lib.isNotEmpty()) {
                    entries.add(PageEntry(header = "FROM YOUR APPS"))
                    for (h in lib) entries.add(PageEntry(hero = h))
                }
                entries.add(PageEntry(header = if (series) "TOP-RATED SERIES" else "TOP-RATED MOVIES"))
                for (c in curated) entries.add(PageEntry(poster = c))
                pageTitle.text = if (series) "Series" else "Movies"
                pageSub.text = if (lib.isNotEmpty()) "Picked from your installed apps, plus the best of all time  \u00b7  press OK to watch"
                    else "The best of all time  \u00b7  press OK to watch"
                val cols = 12
                val u = availW / cols
                val libW = 3 * u - (gap * 1.2f).toInt()
                val libH = (libW * 9f / 16f).toInt()
                val posW = 2 * u - (gap * 1.2f).toInt()
                val posH = (posW * 1.5f).toInt()
                val gm = GridLayoutManager(this, cols)
                gm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        val e = entries[position]
                        return if (e.header != null) cols else if (e.hero != null) 3 else 2
                    }
                }
                pageRv.layoutManager = gm
                pageRv.adapter = PageAdapter(entries.size, u, { i ->
                    val e = entries[i]
                    if (e.header != null) availW - gap * 2 else if (e.hero != null) libW else posW
                }) { i ->
                    val e = entries[i]
                    when {
                        e.header != null -> buildPageHeader(e.header)
                        e.hero != null -> buildLibCard(e.hero, libW, libH)
                        else -> buildPoster(e.poster!!, series, posW, posH)
                    }
                }
            }
        }
        pageOpen = true
        setNavSelected(kind + 1)
        scroll.visibility = View.INVISIBLE
        heroTextLayer.visibility = View.INVISIBLE
        pageLayer.alpha = 0f
        pageLayer.visibility = View.VISIBLE
        pageLayer.animate().alpha(1f).setDuration(200).start()
        pageRv.scrollToPosition(0)
        handler.postDelayed({ if (pageOpen) pageRv.getChildAt(0)?.requestFocus() }, 120)
    }

    private fun closePage() {
        pageOpen = false
        pageLayer.visibility = View.GONE
        pageRv.adapter = null
        scroll.visibility = View.VISIBLE
        heroTextLayer.visibility = View.VISIBLE
    }

    private fun goHome() {
        if (pageOpen) closePage()
        setNavSelected(0)
        lastPos.fill(0)
        dockRv.scrollToPosition(0)
        appsRv.scrollToPosition(0)
        scroll.scrollTo(0, 0)
        handler.post { focusDock() }
    }

    inner class PageAdapter(
        private val n: Int,
        private val slotWpx: Int,
        private val widthOf: (Int) -> Int,
        private val build: (Int) -> View
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = n
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val slot = FrameLayout(parent.context)
            slot.layoutParams = RecyclerView.LayoutParams(slotWpx, WRAP)
            slot.clipChildren = false
            return object : RecyclerView.ViewHolder(slot) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val slot = holder.itemView as FrameLayout
            slot.removeAllViews()
            val lp = FrameLayout.LayoutParams(widthOf(position), WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            lp.topMargin = (gap * 0.6f).toInt()
            lp.bottomMargin = (gap * 0.6f).toInt()
            slot.addView(build(position), lp)
        }
    }

    private fun buildPageHeader(text: String): View {
        val t = TextView(this)
        t.text = text
        t.setTextColor(Color.argb(190, 255, 255, 255))
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0105f)
        t.setTypeface(Typeface.DEFAULT_BOLD)
        t.letterSpacing = 0.3f
        t.setPadding(gap, (screenW * 0.008f).toInt(), 0, (screenW * 0.004f).toInt())
        t.isFocusable = false
        return t
    }

    /** 16:9 card for something an installed app offers (movie / series); OK plays it in that app. */
    private fun buildLibCard(item: HeroItem, cw: Int, ch: Int): View {
        val app = appByPkg[item.pkg]
        val tint = app?.tint ?: defaultTint
        val r = radius * 1.3f
        val card = FrameLayout(this)
        card.layoutParams = LinearLayout.LayoutParams(cw, ch)
        val cardBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(withValue(tint, 0.55f, 1.1f), withValue(tint, 0.22f, 1.1f))
        )
        cardBg.cornerRadius = r
        card.background = cardBg
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        card.clipToOutline = true

        val ph = ImageView(this)
        if (app != null) ph.setImageDrawable(app.art)
        ph.scaleType = ImageView.ScaleType.FIT_CENTER
        val ps = (ch * 0.4f).toInt()
        card.addView(ph, FrameLayout.LayoutParams(ps, ps, Gravity.CENTER))
        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.alpha = 0f
        card.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))
        loadInto(img, item.images, (cw * 1.3f).toInt()) {
            ph.visibility = View.GONE
            img.animate().alpha(1f).setDuration(250).start()
        }
        val ring = View(this)
        val ringBg = GradientDrawable()
        ringBg.cornerRadius = r
        ringBg.setStroke((screenW * 0.0022f).toInt().coerceAtLeast(3), Color.WHITE)
        ringBg.setColor(Color.TRANSPARENT)
        ring.background = ringBg
        ring.alpha = 0f
        card.addView(ring, FrameLayout.LayoutParams(MATCH, MATCH))

        val title = TextView(this)
        title.text = item.title.orEmpty()
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0115f)
        title.setTypeface(Typeface.DEFAULT_BOLD)
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        title.alpha = 0.8f
        title.setPadding(0, (screenW * 0.006f).toInt(), 0, 0)
        val sub = TextView(this)
        sub.text = "on " + (app?.label ?: item.pkg)
        sub.setTextColor(Color.argb(150, 255, 255, 255))
        sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0095f)
        sub.maxLines = 1
        sub.ellipsize = TextUtils.TruncateAt.END

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.clipChildren = false
        col.isFocusable = true
        col.isClickable = true
        col.addView(card)
        col.addView(title, LinearLayout.LayoutParams(cw, WRAP))
        col.addView(sub, LinearLayout.LayoutParams(cw, WRAP))
        col.pivotX = cw / 2f
        col.pivotY = ch / 2f
        if (Build.VERSION.SDK_INT >= 26) col.defaultFocusHighlightEnabled = false
        col.setOnFocusChangeListener { v, focused ->
            val sc = if (focused) 1.06f else 1f
            v.animate().scaleX(sc).scaleY(sc).setDuration(160).start()
            (v.parent as? View)?.translationZ = if (focused) 20f else 0f
            ring.animate().alpha(if (focused) 1f else 0f).setDuration(160).start()
            title.animate().alpha(if (focused) 1f else 0.8f).setDuration(160).start()
        }
        col.setOnClickListener { launchHero(item) }
        return col
    }

    /** Poster card for Movies / Series. Artwork is looked up online; until it arrives the card shows the title. */
    private fun buildPoster(p: Poster, series: Boolean, cw: Int, ch: Int): View {
        val r = radius * 1.3f
        val hue = ((p.title.hashCode() and 0x7fffffff) % 360).toFloat()
        val tint = Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.7f))

        val card = FrameLayout(this)
        card.layoutParams = LinearLayout.LayoutParams(cw, ch)
        val cardBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(withValue(tint, 0.55f, 1.1f), withValue(tint, 0.22f, 1.1f))
        )
        cardBg.cornerRadius = r
        card.background = cardBg
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        card.clipToOutline = true

        val fallback = TextView(this)
        fallback.text = p.title
        fallback.setTextColor(Color.WHITE)
        fallback.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0125f)
        fallback.setTypeface(Typeface.DEFAULT_BOLD)
        fallback.gravity = Gravity.CENTER
        val fp = (cw * 0.1f).toInt()
        fallback.setPadding(fp, fp, fp, fp)
        card.addView(fallback, FrameLayout.LayoutParams(MATCH, MATCH))

        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.alpha = 0f
        img.contentDescription = p.title
        card.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))

        val key = (if (series) "s:" else "m:") + p.title
        val show = { url: String ->
            loadInto(img, listOf(url), (cw * 1.3f).toInt()) {
                fallback.visibility = View.GONE
                img.animate().alpha(1f).setDuration(250).start()
            }
        }
        val known = posterUrls[key]
        if (known != null) show(known)
        else io.execute {
            val u = lookupPoster(p, series)
            if (u != null) runOnUiThread { if (img.contentDescription == p.title) show(u) }
        }

        val gloss = View(this)
        gloss.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(40, 255, 255, 255), Color.TRANSPARENT)
        )
        card.addView(gloss, FrameLayout.LayoutParams(MATCH, ch / 3, Gravity.TOP))

        val ring = View(this)
        val ringBg = GradientDrawable()
        ringBg.cornerRadius = r
        ringBg.setStroke((screenW * 0.0022f).toInt().coerceAtLeast(3), Color.WHITE)
        ringBg.setColor(Color.TRANSPARENT)
        ring.background = ringBg
        ring.alpha = 0f
        card.addView(ring, FrameLayout.LayoutParams(MATCH, MATCH))

        val title = TextView(this)
        title.text = p.title
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0115f)
        title.setTypeface(Typeface.DEFAULT_BOLD)
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        title.alpha = 0.8f
        title.setPadding(0, (screenW * 0.006f).toInt(), 0, 0)

        val sub = TextView(this)
        sub.text = p.year + "  ·  " + p.genre
        sub.setTextColor(Color.argb(150, 255, 255, 255))
        sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0095f)
        sub.maxLines = 1
        sub.ellipsize = TextUtils.TruncateAt.END

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.clipChildren = false
        col.isFocusable = true
        col.isClickable = true
        col.addView(card)
        col.addView(title, LinearLayout.LayoutParams(cw, WRAP))
        col.addView(sub, LinearLayout.LayoutParams(cw, WRAP))
        col.pivotX = cw / 2f
        col.pivotY = ch / 2f
        if (Build.VERSION.SDK_INT >= 26) col.defaultFocusHighlightEnabled = false
        col.setOnFocusChangeListener { v, focused ->
            val sc = if (focused) 1.08f else 1f
            v.animate().scaleX(sc).scaleY(sc).setDuration(160).start()
            (v.parent as? View)?.translationZ = if (focused) 20f else 0f
            ring.animate().alpha(if (focused) 1f else 0f).setDuration(160).start()
            title.animate().alpha(if (focused) 1f else 0.8f).setDuration(160).start()
        }
        col.setOnClickListener { openTitle(p, series) }
        return col
    }

    private fun openTrailer(p: Poster, series: Boolean) {
        try {
            val q = java.net.URLEncoder.encode(p.title + " " + p.year + (if (series) " series" else " movie") + " official trailer", "UTF-8")
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + q))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Install YouTube to watch trailers", Toast.LENGTH_SHORT).show()
        }
    }

    // =====================================================================
    // Movies / Series -> open in the OTT app that has the title
    // =====================================================================

    /** Free key from themoviedb.org (Settings > API). With it the launcher knows which OTT streams a title. */
    private val TMDB_KEY = ""

    data class Ott(val label: String, val pkg: String, val tmdbName: String, val searchUrl: String)

    private val OTT_APPS = listOf(
        Ott("Netflix", "com.netflix.ninja", "netflix", "https://www.netflix.com/search?q=%s"),
        Ott("Prime Video", "com.amazon.amazonvideo.livingroom", "amazon prime", "https://www.primevideo.com/search/?phrase=%s"),
        Ott("Disney+", "com.disney.disneyplus", "disney plus", "https://www.disneyplus.com/search?q=%s"),
        Ott("Disney+ Hotstar", "in.startv.hotstar", "hotstar", "https://www.hotstar.com/in/search?q=%s"),
        Ott("Max", "com.wbd.stream", "max", "https://play.max.com/search?q=%s"),
        Ott("Apple TV", "com.apple.atve.androidtv.appletv", "apple tv", "https://tv.apple.com/search?term=%s"),
        Ott("YouTube", "com.google.android.youtube.tv", "youtube", "https://www.youtube.com/results?search_query=%s")
    )

    private fun openTitle(p: Poster, series: Boolean) {
        Toast.makeText(this, "Finding where to watch ${p.title}...", Toast.LENGTH_SHORT).show()
        io.execute {
            val names = tmdbProviders(p, series)       // lower-case provider names, empty when unknown
            runOnUiThread {
                val installed = OTT_APPS.filter { isInstalled(it.pkg) }
                var matches = if (names.isEmpty()) emptyList()
                else installed.filter { o -> names.any { n -> n.contains(o.tmdbName) } }
                val known = matches.isNotEmpty()
                if (!known) matches = installed.filter { it.pkg != "com.google.android.youtube.tv" }
                when {
                    known && matches.size == 1 -> openInOtt(matches[0], p)
                    matches.isEmpty() -> openTrailer(p, series)
                    else -> {
                        val labels = ArrayList<String>()
                        for (m in matches) labels.add("Watch on " + m.label)
                        labels.add("Watch trailer on YouTube")
                        android.app.AlertDialog.Builder(this)
                            .setTitle(p.title + "  (" + p.year + ")")
                            .setItems(labels.toTypedArray()) { _, which ->
                                if (which < matches.size) openInOtt(matches[which], p) else openTrailer(p, series)
                            }
                            .show()
                    }
                }
            }
        }
    }

    /** Opens the OTT app on a search for the title; if the app refuses the link, just opens the app. */
    private fun openInOtt(o: Ott, p: Poster) {
        try {
            val q = java.net.URLEncoder.encode(p.title, "UTF-8")
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(o.searchUrl.replace("%s", q)))
            i.setPackage(o.pkg)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            val i = packageManager.getLeanbackLaunchIntentForPackage(o.pkg) ?: packageManager.getLaunchIntentForPackage(o.pkg)
            if (i != null) {
                startActivity(i)
                Toast.makeText(this, "Search for \"${p.title}\" in ${o.label}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Names (lower-case) of the streaming services that carry the title, via TMDB. Needs [TMDB_KEY]. */
    private fun tmdbProviders(p: Poster, series: Boolean): List<String> {
        if (TMDB_KEY.isEmpty()) return emptyList()
        try {
            val kind = if (series) "tv" else "movie"
            val q = java.net.URLEncoder.encode(p.title, "UTF-8")
            val yearParam = if (series) "first_air_date_year" else "year"
            val search = httpText("https://api.themoviedb.org/3/search/$kind?api_key=$TMDB_KEY&query=$q&$yearParam=${p.year}")
                ?: return emptyList()
            val results = org.json.JSONObject(search).optJSONArray("results") ?: return emptyList()
            if (results.length() == 0) return emptyList()
            val id = results.getJSONObject(0).optInt("id", -1)
            if (id < 0) return emptyList()
            val prov = httpText("https://api.themoviedb.org/3/$kind/$id/watch/providers?api_key=$TMDB_KEY") ?: return emptyList()
            val all = org.json.JSONObject(prov).optJSONObject("results") ?: return emptyList()
            val region = java.util.Locale.getDefault().country.ifEmpty { "US" }
            val out = ArrayList<String>()
            for (code in listOf(region, "US")) {
                val flat = all.optJSONObject(code)?.optJSONArray("flatrate") ?: continue
                for (i in 0 until flat.length()) out.add(flat.getJSONObject(i).optString("provider_name").lowercase())
                if (out.isNotEmpty()) break
            }
            return out
        } catch (e: Exception) {
            Log.w(TAG, "TMDB lookup failed for ${p.title}", e)
            return emptyList()
        }
    }

    private fun httpText(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", UA)
        return try {
            if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Looks up a poster URL via the public iTunes Search API (no key needed). Runs on the io pool. */
    private fun lookupPoster(p: Poster, series: Boolean): String? {
        val key = (if (series) "s:" else "m:") + p.title
        posterUrls[key]?.let { return it }
        try {
            val q = java.net.URLEncoder.encode(p.title, "UTF-8")
            val api = if (series) "https://itunes.apple.com/search?term=$q&media=tvShow&entity=tvSeason&limit=10&country=US"
            else "https://itunes.apple.com/search?term=$q&media=movie&entity=movie&limit=10&country=US"
            val conn = URL(api).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", UA)
            val body = try {
                if (conn.responseCode !in 200..299) return null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val arr = org.json.JSONObject(body).optJSONArray("results") ?: return null
            val t = p.title.lowercase()
            var best: String? = null
            var bestScore = -1
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val art = o.optString("artworkUrl100")
                if (art.isEmpty()) continue
                val name = (if (series) o.optString("collectionName") else o.optString("trackName")).lowercase()
                var score = 0
                if (name == t || name.startsWith("$t, season 1")) score += 4
                else if (name.startsWith(t)) score += 2
                if (!series && o.optString("releaseDate").startsWith(p.year)) score += 3
                if (series && name.endsWith("season 1")) score += 1
                if (score > bestScore) {
                    bestScore = score
                    best = art
                }
            }
            val url = best?.replace(Regex("\\d+x\\d+bb"), "400x600bb") ?: return null
            posterUrls[key] = url
            return url
        } catch (e: Exception) {
            Log.w(TAG, "poster lookup failed for ${p.title}", e)
            return null
        }
    }

    // =====================================================================
    // Shared list row (Wi-Fi dropdown + Settings): white pill when focused, like tvOS
    // =====================================================================

    private fun menuRow(
        rowW: Int,
        rowH: Int,
        textPx: Float,
        lead: Drawable?,
        leadBg: Int?,          // coloured badge behind a white glyph (Settings); null = plain icon
        title: String,
        value: String?,
        chevron: Boolean,
        focusScale: Float,
        onClick: () -> Unit
    ): RowViews {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val lp = LinearLayout.LayoutParams(rowW, rowH)
        lp.bottomMargin = (rowH * 0.12f).toInt()
        row.layoutParams = lp
        val padH = (rowH * 0.30f).toInt()
        row.setPadding(padH, 0, padH, 0)
        row.clipChildren = false
        val idle = Color.argb(26, 255, 255, 255)
        val bg = GradientDrawable()
        bg.cornerRadius = rowH * 0.30f
        bg.setColor(idle)
        row.background = bg
        row.isFocusable = true
        row.isClickable = true
        if (Build.VERSION.SDK_INT >= 26) row.defaultFocusHighlightEnabled = false

        var leadView: ImageView? = null
        if (lead != null) {
            val size = (rowH * 0.62f).toInt()
            val iv = ImageView(this)
            iv.setImageDrawable(lead)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            val ip = if (leadBg != null) (size * 0.2f).toInt() else (size * 0.1f).toInt()
            iv.setPadding(ip, ip, ip, ip)
            if (leadBg != null) {
                val b = GradientDrawable()
                b.cornerRadius = size * 0.24f
                b.setColor(leadBg)
                iv.background = b
            }
            val llp = LinearLayout.LayoutParams(size, size)
            llp.rightMargin = (padH * 0.8f).toInt()
            row.addView(iv, llp)
            leadView = iv
        }

        val t = TextView(this)
        t.text = title
        t.setTextColor(Color.WHITE)
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        row.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))

        var v: TextView? = null
        if (value != null) {
            val vt = TextView(this)
            vt.text = value
            vt.setTextColor(Color.argb(150, 255, 255, 255))
            vt.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * 0.82f)
            vt.maxLines = 1
            vt.ellipsize = TextUtils.TruncateAt.END
            val vlp = LinearLayout.LayoutParams(WRAP, WRAP)
            vlp.leftMargin = padH / 2
            row.addView(vt, vlp)
            v = vt
        }
        var chev: TextView? = null
        if (chevron) {
            val ct = TextView(this)
            ct.text = "\u203A"
            ct.setTextColor(Color.argb(150, 255, 255, 255))
            ct.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * 1.3f)
            val clp = LinearLayout.LayoutParams(WRAP, WRAP)
            clp.leftMargin = padH / 2
            row.addView(ct, clp)
            chev = ct
        }

        val valueRef = v
        val chevRef = chev
        val leadRef = leadView
        row.setOnFocusChangeListener { view, focused ->
            bg.setColor(if (focused) Color.WHITE else idle)
            t.setTextColor(if (focused) Color.BLACK else Color.WHITE)
            val dim = if (focused) Color.argb(160, 0, 0, 0) else Color.argb(150, 255, 255, 255)
            valueRef?.setTextColor(dim)
            chevRef?.setTextColor(dim)
            if (leadRef != null && leadBg == null) leadRef.setColorFilter(if (focused) Color.BLACK else Color.WHITE)
            if (focusScale != 1f) {
                val s = if (focused) focusScale else 1f
                view.animate().scaleX(s).scaleY(s).setDuration(140).start()
            }
            (view.parent as? View)?.translationZ = if (focused) 10f else 0f
        }
        row.setOnClickListener { onClick() }
        return RowViews(row, t, v)
    }

    // =====================================================================
    // Wi-Fi dropdown (opens under the Wi-Fi icon, lets you pick / join a network)
    // =====================================================================

    private fun buildWifiPanel(topMargin: Int, rightMargin: Int): FrameLayout {
        val panel = FrameLayout(this)
        val bg = GradientDrawable()
        bg.cornerRadius = screenW * 0.014f
        bg.setColor(Color.argb(240, 26, 27, 36))
        bg.setStroke(2, Color.argb(70, 255, 255, 255))
        panel.background = bg
        panel.elevation = 40f
        panel.clipChildren = false

        val pad = (screenW * 0.008f).toInt()
        wifiScroll = ScrollView(this)
        wifiScroll.isFocusable = false
        wifiScroll.isVerticalScrollBarEnabled = false
        wifiScroll.overScrollMode = View.OVER_SCROLL_NEVER
        wifiScroll.clipToPadding = false
        wifiScroll.setPadding(pad, pad, pad, pad)
        wifiList = LinearLayout(this)
        wifiList.orientation = LinearLayout.VERTICAL
        wifiScroll.addView(wifiList, FrameLayout.LayoutParams(MATCH, WRAP))
        panel.addView(wifiScroll, FrameLayout.LayoutParams(MATCH, WRAP))

        val lp = FrameLayout.LayoutParams((screenW * 0.29f).toInt(), WRAP, Gravity.TOP or Gravity.END)
        lp.setMargins(0, topMargin, rightMargin, 0)
        panel.layoutParams = lp
        panel.visibility = View.GONE
        return panel
    }

    private fun wifiManager(): WifiManager =
        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private fun hasPerm(p: String): Boolean =
        Build.VERSION.SDK_INT < 23 || checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** True when Android will hand this app the list of nearby networks. */
    private fun hasWifiScanPermission(): Boolean {
        if (hasPerm(LOC_PERM)) return true
        if (Build.VERSION.SDK_INT < 29 && hasPerm(COARSE_PERM)) return true
        if (Build.VERSION.SDK_INT >= 33 && hasPerm(NEARBY_PERM)) return true
        return false
    }

    @Suppress("DEPRECATION")
    private fun locationServicesOn(): Boolean {
        return try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
            else Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE, 1) != Settings.Secure.LOCATION_MODE_OFF
        } catch (e: Exception) {
            true
        }
    }

    /** Android only returns scan results while Location is on, unless the Android 13 "Nearby Wi-Fi" permission is used. */
    private fun needsLocationOn(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && hasPerm(NEARBY_PERM)) return false
        return !locationServicesOn()
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String? {
        return try {
            val info = wifiManager().connectionInfo
            val s = info?.ssid?.trim('"')
            if (s.isNullOrBlank() || s == "<unknown ssid>" || info?.networkId == -1) null else s
        } catch (e: Exception) {
            null
        }
    }

    private fun toggleWifiPanel() {
        if (wifiOpen) closeWifiPanel(true) else openWifiPanel()
    }

    /** Starts a scan. Returns false when Android refuses (too many scans, Wi-Fi off, ...). */
    @Suppress("DEPRECATION")
    private fun startWifiScan(): Boolean {
        val ok = try {
            wifiManager().startScan()
        } catch (e: Exception) {
            Log.w(TAG, "startScan failed", e)
            false
        }
        wifiScanRejected = !ok
        wifiScanning = ok
        if (ok) {
            // some boxes never send the "results ready" broadcast: stop saying "Scanning" after a while
            val token = ++wifiScanToken
            handler.postDelayed({
                if (token == wifiScanToken && wifiScanning) {
                    wifiScanning = false
                    if (wifiOpen) populateWifi()
                }
            }, 10000)
        }
        return ok
    }

    private fun requestWifiPermissions() {
        if (Build.VERSION.SDK_INT < 23 || hasWifiScanPermission()) return
        val need = ArrayList<String>()
        need.add(LOC_PERM)
        need.add(COARSE_PERM)
        if (Build.VERSION.SDK_INT >= 33) need.add(NEARBY_PERM)
        try {
            requestPermissions(need.toTypedArray(), 78)
        } catch (e: Exception) {
            Log.w(TAG, "permission request failed", e)
        }
    }

    private fun registerWifiReceiver() {
        if (wifiRecvRegistered) return
        val f = IntentFilter()
        f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        registerReceiver(wifiReceiver, f)
        wifiRecvRegistered = true
    }

    private fun openWifiPanel() {
        wifiOpen = true
        homeGroup.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        requestWifiPermissions()
        registerWifiReceiver()
        startWifiScan()
        wifiPanel.alpha = 0f
        wifiPanel.visibility = View.VISIBLE
        wifiPanel.animate().alpha(1f).setDuration(160).start()
        populateWifi()
        handler.removeCallbacks(wifiTick)
        handler.postDelayed(wifiTick, WIFI_SCAN_EVERY_MS)
        wifiList.post { wifiList.getChildAt(0)?.requestFocus() }
    }

    private fun closeWifiPanel(refocus: Boolean) {
        wifiOpen = false
        handler.removeCallbacks(wifiTick)
        unregisterWifiReceiver()
        wifiPanel.visibility = View.GONE
        homeGroup.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        if (refocus) wifiBtnView.requestFocus()
    }

    private fun unregisterWifiReceiver() {
        if (wifiRecvRegistered) {
            try {
                unregisterReceiver(wifiReceiver)
            } catch (e: Exception) {
                // ignore
            }
            wifiRecvRegistered = false
        }
    }

    @Suppress("DEPRECATION")
    private fun collectNetworks(wm: WifiManager): List<WNet> {
        val connected = currentSsid()
        val saved = HashMap<String, Int>()
        try {
            val cfgs = wm.configuredNetworks
            if (cfgs != null) {
                for (c in cfgs) {
                    val s = c.SSID?.trim('"') ?: continue
                    saved[s] = c.networkId
                }
            }
        } catch (e: Exception) {
            // not allowed on newer Android
        }
        val results = try {
            wm.scanResults ?: emptyList<ScanResult>()
        } catch (e: Exception) {
            emptyList<ScanResult>()
        }
        val best = LinkedHashMap<String, ScanResult>()
        for (r in results.sortedByDescending { it.level }) {
            val s = r.SSID ?: continue
            if (s.isBlank()) continue
            if (!best.containsKey(s)) best[s] = r
        }
        val out = ArrayList<WNet>()
        for ((s, r) in best) out.add(WNet(s, r.capabilities ?: "", r.level, s == connected, saved[s] ?: -1))
        if (connected != null && out.none { it.connected }) {
            val rssi = try { wm.connectionInfo.rssi } catch (e: Exception) { -60 }
            out.add(WNet(connected, "", rssi, true, saved[connected] ?: -1))
        }
        out.sortWith(
            compareByDescending<WNet> { it.connected }
                .thenByDescending { it.netId >= 0 }
                .thenByDescending { it.level }
        )
        return out
    }

    private fun isSecured(caps: String): Boolean =
        caps.contains("WEP") || caps.contains("PSK") || caps.contains("EAP") || caps.contains("SAE")

    /** Switches Wi-Fi on/off. Returns false when Android does not let apps do that (Android 10+). */
    @Suppress("DEPRECATION")
    private fun setWifiEnabledByApp(enable: Boolean): Boolean = try {
        Build.VERSION.SDK_INT < 29 && wifiManager().setWifiEnabled(enable)
    } catch (e: Exception) {
        false
    }

    private fun wifiNote(text: String, textPx: Float, rowH: Int): TextView {
        val msg = TextView(this)
        msg.text = text
        msg.setTextColor(Color.argb(150, 255, 255, 255))
        msg.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * 0.8f)
        msg.setPadding((rowH * 0.3f).toInt(), (rowH * 0.12f).toInt(), (rowH * 0.3f).toInt(), (rowH * 0.18f).toInt())
        return msg
    }

    @Suppress("DEPRECATION")
    private fun populateWifi() {
        if (!wifiOpen) return
        val keepKey = (wifiList.focusedChild?.tag as? String)
        wifiList.removeAllViews()
        val wm = wifiManager()
        val on = try { wm.isWifiEnabled } catch (e: Exception) { true }
        val rowW = MATCH
        val rowH = (screenW * 0.036f).toInt()
        val textPx = screenW * 0.0125f
        var rows = 0

        val toggle = menuRow(rowW, rowH, textPx, WifiDrawable(Color.WHITE), null, "Wi-Fi",
            if (on) "On" else "Off", false, 1f) {
            if (!setWifiEnabledByApp(!on)) {
                // Android 10+ does not let apps switch Wi-Fi; hand over to the system screen
                closeWifiPanel(false)
                openSystem(Settings.ACTION_WIFI_SETTINGS)
            } else {
                handler.postDelayed({
                    if (wifiOpen) {
                        startWifiScan()
                        populateWifi()
                    }
                }, 1500)
                populateWifi()
            }
        }
        toggle.row.tag = "__toggle"
        toggle.row.setOnKeyListener { _, keyCode, ev ->
            if (ev.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                closeWifiPanel(true)
                true
            } else false
        }
        wifiList.addView(toggle.row)
        rows++

        if (on) {
            // Why would the list stay empty? Tell the user and give a one-click fix.
            if (!hasWifiScanPermission()) {
                val perm = menuRow(rowW, rowH, textPx, null, null, "Allow location access", null, false, 1f) {
                    requestWifiPermissions()
                }
                perm.row.tag = "__perm"
                wifiList.addView(perm.row)
                rows++
                wifiList.addView(wifiNote("Android only lets apps see nearby Wi-Fi after you allow Precise Location.", textPx, rowH), LinearLayout.LayoutParams(MATCH, WRAP))
                rows++
            } else if (needsLocationOn()) {
                val loc = menuRow(rowW, rowH, textPx, null, null, "Turn on Location", null, true, 1f) {
                    closeWifiPanel(false)
                    openSystem(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                }
                loc.row.tag = "__loc"
                wifiList.addView(loc.row)
                rows++
                wifiList.addView(wifiNote("Android hides nearby networks while Location is switched off.", textPx, rowH), LinearLayout.LayoutParams(MATCH, WRAP))
                rows++
            }

            val nets = collectNetworks(wm)
            val status = when {
                wifiScanning -> "Scanning for networks…"
                wifiScanRejected -> "Android limits how often it can scan. Showing the last results."
                nets.isEmpty() -> "No networks found yet."
                else -> "${nets.size} network" + (if (nets.size == 1) "" else "s") + " found"
            }
            wifiList.addView(wifiNote(status, textPx, rowH), LinearLayout.LayoutParams(MATCH, WRAP))
            rows++
            for (n in nets) {
                val d = WifiDrawable(Color.WHITE)
                d.update(if (n.level >= -60) 3 else if (n.level >= -72) 2 else 1, true)
                val sub = if (n.connected) "Connected ✓" else if (n.netId >= 0) "Saved" else if (isSecured(n.caps)) "Secured" else "Open"
                val rv = menuRow(rowW, rowH, textPx, d, null, n.ssid, sub, false, 1f) { connectToNet(n) }
                rv.row.tag = "net:" + n.ssid
                wifiList.addView(rv.row)
                rows++
            }

            val scan = menuRow(rowW, rowH, textPx, null, null, "Scan again",
                if (wifiScanning) "Scanning…" else null, false, 1f) {
                if (!startWifiScan()) {
                    Toast.makeText(this, "Android only allows a few scans per minute. Try again in a moment.", Toast.LENGTH_SHORT).show()
                }
                populateWifi()
            }
            scan.row.tag = "__scan"
            wifiList.addView(scan.row)
            rows++
        }

        val more = menuRow(rowW, rowH, textPx, null, null, "More Wi-Fi settings…", null, true, 1f) {
            closeWifiPanel(false)
            openSystem(Settings.ACTION_WIFI_SETTINGS)
        }
        more.row.tag = "__more"
        wifiList.addView(more.row)
        rows++

        val maxH = (screenH * 0.62f).toInt()
        val wantH = rows * (rowH + (rowH * 0.12f).toInt()) + 2 * (screenW * 0.008f).toInt() + (rowH * 0.2f).toInt()
        val slp = wifiScroll.layoutParams
        slp.height = if (wantH > maxH) maxH else WRAP
        wifiScroll.layoutParams = slp

        if (keepKey != null) {
            for (i in 0 until wifiList.childCount) {
                val c = wifiList.getChildAt(i)
                if (c.tag == keepKey) {
                    c.requestFocus()
                    break
                }
            }
        }
    }

    private fun connectToNet(n: WNet) {
        if (n.connected) {
            Toast.makeText(this, "Already connected to ${n.ssid}", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 29 || n.caps.contains("EAP")) {
            // Android 10+ no longer lets apps join networks silently; hand over to system Wi-Fi settings.
            Toast.makeText(this, "Choose ${n.ssid} in Wi-Fi settings", Toast.LENGTH_LONG).show()
            closeWifiPanel(false)
            openSystem(Settings.ACTION_WIFI_SETTINGS)
            return
        }
        if (n.netId >= 0) {
            joinSaved(n.netId, n.ssid)
        } else if (!isSecured(n.caps)) {
            joinNew(n.ssid, n.caps, null)
        } else {
            askPassword(n)
        }
    }

    private fun askPassword(n: WNet) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        input.hint = "Password"
        input.setSingleLine()
        val p = (screenW * 0.012f).toInt()
        val box = FrameLayout(this)
        box.setPadding(p, p, p, 0)
        box.addView(input, FrameLayout.LayoutParams(MATCH, WRAP))
        val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Join " + n.ssid)
            .setView(box)
            .setPositiveButton("Connect") { _, _ -> joinNew(n.ssid, n.caps, input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()
    }

    @Suppress("DEPRECATION")
    private fun joinNew(ssid: String, caps: String, pw: String?) {
        try {
            val wm = wifiManager()
            val c = WifiConfiguration()
            c.SSID = "\"" + ssid + "\""
            if (pw == null) {
                c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            } else if (caps.contains("WEP") && !caps.contains("WPA")) {
                c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                c.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                c.wepKeys[0] = "\"" + pw + "\""
                c.wepTxKeyIndex = 0
            } else {
                c.preSharedKey = "\"" + pw + "\""
            }
            val id = wm.addNetwork(c)
            if (id < 0) {
                Toast.makeText(this, "Couldn't add $ssid. Try Wi-Fi settings.", Toast.LENGTH_LONG).show()
                closeWifiPanel(false)
                openSystem(Settings.ACTION_WIFI_SETTINGS)
            } else {
                joinSaved(id, ssid)
            }
        } catch (e: Exception) {
            Log.w(TAG, "joinNew failed", e)
            closeWifiPanel(false)
            openSystem(Settings.ACTION_WIFI_SETTINGS)
        }
    }

    @Suppress("DEPRECATION")
    private fun joinSaved(netId: Int, ssid: String) {
        try {
            val wm = wifiManager()
            wm.disconnect()
            wm.enableNetwork(netId, true)
            wm.reconnect()
            Toast.makeText(this, "Connecting to $ssid\u2026", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w(TAG, "joinSaved failed", e)
        }
        closeWifiPanel(true)
        handler.postDelayed({ refreshStatus() }, 5000)
    }

    // =====================================================================
    // Settings screen (Apple TV style: big centred list, white pill on focus)
    // Everything you can change here happens inside the launcher; only a few
    // rarely used items still hand over to the Android screen (marked in README).
    // =====================================================================

    private fun settingsIcon(kind: Int): Drawable = when (kind) {
        0 -> GearDrawable(Color.WHITE)
        1 -> AvatarDrawable()
        2 -> GlyphDrawable(GlyphDrawable.TV)
        3 -> GlyphDrawable(GlyphDrawable.MOON)
        4 -> GlyphDrawable(GlyphDrawable.REMOTE)
        5 -> GlyphDrawable(GlyphDrawable.GRID)
        6 -> WifiDrawable(Color.WHITE)
        7 -> GlyphDrawable(GlyphDrawable.INFO)
        else -> GlyphDrawable(GlyphDrawable.HOME)
    }

    private fun sys(action: String): () -> Unit = { openSystem(action) }

    private fun onOff(b: Boolean): String = if (b) "On" else "Off"

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    /** Re-draws the Settings page that is on screen (e.g. after a value changed a moment later). */
    private fun refreshSettingsSoon(delayMs: Long) {
        handler.postDelayed({ if (settingsOpen) showSettingsPage(true) }, delayMs)
    }

    private fun settingsRoot(): List<SItem> {
        val acct = googleAccount() ?: "Not signed in"
        val net = currentSsid() ?: "Not connected"
        return listOf(
            SItem("General", 0, Color.parseColor("#8e8e93"), sub = {
                listOf(
                    SItem("Date and Time", value = zoneLabel(TimeZone.getDefault().id), sub = { dateTimeItems() }),
                    SItem("Language", value = Locale.getDefault().displayLanguage, sub = { languageItems() }),
                    SItem("Keyboard", sub = { keyboardItems() }),
                    SItem("Storage", sub = { storageItems() }),
                    SItem("Accessibility", sub = { accessibilityItems() }),
                    SItem("About", sub = { aboutItems() })
                )
            }),
            SItem("Users and Accounts", 1, Color.parseColor("#3478f6"), value = acct, sub = { accountItems() }),
            SItem("Video and Audio", 2, Color.parseColor("#30a46c"), sub = {
                listOf(
                    SItem("Sound", sub = { soundItems() }),
                    SItem("Video and Display", sub = { displayItems() })
                )
            }),
            SItem("Screen Saver", 3, Color.parseColor("#af52de"), sub = { screenSaverItems() }),
            SItem("Remotes and Devices", 4, Color.parseColor("#ff9f0a"), sub = { bluetoothItems() }),
            SItem("Apps", 5, Color.parseColor("#0a84ff"), sub = { appsItems() }),
            SItem("Network", 6, Color.parseColor("#30b0c7"), value = net, sub = { networkItems() }),
            SItem("Home Screen", 8, Color.parseColor("#ff375f"), sub = {
                listOf(SItem("Reset Dock and All Apps", action = { resetLayout() }))
            }),
            SItem("System", 7, Color.parseColor("#636366"), sub = {
                listOf(
                    SItem("About", sub = { aboutItems() }),
                    androidRow("Developer Options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                    androidRow("All Android Settings", Settings.ACTION_SETTINGS)
                )
            })
        )
    }

    // ---- Date and Time ----------------------------------------------------

    private fun globalInt(name: String, def: Int): Int =
        try {
            Settings.Global.getInt(contentResolver, name, def)
        } catch (e: Exception) {
            def
        }

    private fun canWriteSecure(): Boolean = hasPerm(SECURE_PERM)

    /** Writes a Settings.Global switch. Needs a one-time `adb shell pm grant` of WRITE_SECURE_SETTINGS. */
    private fun setGlobalInt(name: String, v: Int): Boolean =
        try {
            Settings.Global.putInt(contentResolver, name, v)
        } catch (e: SecurityException) {
            toast("One-time setup needed. On a PC run:  adb shell pm grant $packageName $SECURE_PERM")
            false
        } catch (e: Exception) {
            false
        }

    private fun is24(): Boolean =
        if (prefs.contains(KEY_24H)) prefs.getBoolean(KEY_24H, false) else DateFormat.is24HourFormat(this)

    private fun applyClockFormat() {
        val f = if (is24()) "HH:mm" else "h:mm"
        clockView.format12Hour = f
        clockView.format24Hour = f
    }

    private fun set24(on: Boolean) {
        prefs.edit().putBoolean(KEY_24H, on).apply()
        applyClockFormat()
        // also tell Android when it lets us (needs WRITE_SETTINGS); the launcher clock works either way
        try {
            if (Build.VERSION.SDK_INT >= 23 && Settings.System.canWrite(this)) {
                Settings.System.putString(contentResolver, Settings.System.TIME_12_24, if (on) "24" else "12")
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private val zoneIds = listOf(
        "Pacific/Pago_Pago", "Pacific/Honolulu", "America/Anchorage", "America/Los_Angeles", "America/Denver",
        "America/Chicago", "America/New_York", "America/Halifax", "America/Sao_Paulo", "Atlantic/Azores", "UTC",
        "Europe/London", "Europe/Paris", "Europe/Berlin", "Africa/Lagos", "Africa/Cairo", "Africa/Johannesburg",
        "Europe/Istanbul", "Europe/Moscow", "Africa/Nairobi", "Asia/Riyadh", "Asia/Dubai", "Asia/Tehran",
        "Asia/Karachi", "Asia/Tashkent", "Asia/Kolkata", "Asia/Colombo", "Asia/Kathmandu", "Asia/Dhaka",
        "Asia/Bangkok", "Asia/Jakarta", "Asia/Singapore", "Asia/Kuala_Lumpur", "Asia/Shanghai", "Asia/Hong_Kong",
        "Asia/Manila", "Asia/Tokyo", "Asia/Seoul", "Australia/Perth", "Australia/Adelaide", "Australia/Sydney",
        "Pacific/Guam", "Pacific/Auckland"
    )

    private fun zoneOffsetMs(id: String): Int = TimeZone.getTimeZone(id).getOffset(System.currentTimeMillis())

    private fun gmtLabel(id: String): String {
        val off = zoneOffsetMs(id)
        val a = Math.abs(off) / 60000
        return String.format(Locale.US, "GMT%s%02d:%02d", if (off < 0) "-" else "+", a / 60, a % 60)
    }

    private fun zoneLabel(id: String): String = gmtLabel(id) + "  " + id.substringAfter('/').replace('_', ' ')

    private fun zoneItems(): List<SItem> {
        val cur = TimeZone.getDefault().id
        val ids = ArrayList<String>(zoneIds)
        if (!ids.contains(cur)) ids.add(cur)
        ids.sortBy { zoneOffsetMs(it) }
        val items = ArrayList<SItem>()
        for (id in ids) {
            items.add(SItem(zoneLabel(id), checked = id == cur, action = {
                setTimeZoneId(id)
                settingsBack()
            }))
        }
        // open the list on the zone that is selected now
        settingsStack.lastOrNull()?.let { it.focus = ids.indexOf(cur).coerceAtLeast(0) }
        return items
    }

    private fun setTimeZoneId(id: String) {
        try {
            if (canWriteSecure() && globalInt(Settings.Global.AUTO_TIME_ZONE, 0) == 1) {
                Settings.Global.putInt(contentResolver, Settings.Global.AUTO_TIME_ZONE, 0)
            }
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).setTimeZone(id)
            TimeZone.setDefault(TimeZone.getTimeZone(id))
            Toast.makeText(this, "Time zone: " + zoneLabel(id), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w(TAG, "setTimeZone failed", e)
            toast("This device does not let apps change the time zone.")
        }
    }

    private fun dateTimeItems(): List<SItem> {
        val now = Date()
        val tz = TimeZone.getDefault()
        val dateFmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat(if (is24()) "HH:mm" else "h:mm a", Locale.getDefault())
        dateFmt.timeZone = tz
        timeFmt.timeZone = tz
        val auto = globalInt(Settings.Global.AUTO_TIME, 1) == 1
        val autoZone = globalInt(Settings.Global.AUTO_TIME_ZONE, 1) == 1
        val manualMsg = "Date and time come from the network. Android does not let apps set them by hand."
        val items = ArrayList<SItem>()
        items.add(SItem("Automatic Date and Time", value = onOff(auto), action = {
            setGlobalInt(Settings.Global.AUTO_TIME, if (auto) 0 else 1)
        }))
        items.add(SItem("Date", value = dateFmt.format(now), action = { toast(manualMsg) }))
        items.add(SItem("Time", value = timeFmt.format(now), action = { toast(manualMsg) }))
        items.add(SItem("Time Zone", value = zoneLabel(tz.id), sub = { zoneItems() }))
        items.add(SItem("Set Time Zone Automatically", value = onOff(autoZone), action = {
            setGlobalInt(Settings.Global.AUTO_TIME_ZONE, if (autoZone) 0 else 1)
        }))
        items.add(SItem("24-Hour Clock", value = onOff(is24()), action = { set24(!is24()) }))
        if (!canWriteSecure()) {
            items.add(SItem("To switch the automatic options on or off, run once on a PC:  adb shell pm grant $packageName $SECURE_PERM", note = true))
        }
        return items
    }

    // ---- Storage / About --------------------------------------------------

    private fun storageItems(): List<SItem> {
        return try {
            val st = StatFs(Environment.getDataDirectory().path)
            val total = st.totalBytes
            val free = st.availableBytes
            listOf(
                SItem("Total", value = Formatter.formatFileSize(this, total)),
                SItem("Used", value = Formatter.formatFileSize(this, total - free)),
                SItem("Available", value = Formatter.formatFileSize(this, free)),
                SItem("Installed apps", value = (allApps.size - 1).coerceAtLeast(0).toString())
            )
        } catch (e: Exception) {
            listOf(SItem("Storage information is not available on this device.", note = true))
        }
    }

    private fun localIp(): String? {
        return try {
            var found: String? = null
            for (nif in java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp || nif.isLoopback) continue
                for (a in java.util.Collections.list(nif.inetAddresses)) {
                    if (!a.isLoopbackAddress && a is java.net.Inet4Address) {
                        found = a.hostAddress
                        break
                    }
                }
                if (found != null) break
            }
            found
        } catch (e: Exception) {
            null
        }
    }

    private fun aboutItems(): List<SItem> {
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        val patch = if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "-"
        return listOf(
            SItem("Device", value = Build.MODEL),
            SItem("Manufacturer", value = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }),
            SItem("Android version", value = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            SItem("Security patch", value = patch),
            SItem("Build", value = Build.DISPLAY),
            SItem("IP address", value = localIp() ?: "Not connected"),
            SItem("Ananta TV Launcher", value = ver)
        )
    }

    // ---- Sound ------------------------------------------------------------

    private fun soundItems(): List<SItem> {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return listOf(
            SItem("Volume", value = "‹   $cur / $max   ›",
                step = { d ->
                    try {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, (cur + d).coerceIn(0, max), 0)
                    } catch (e: Exception) {
                        // fixed-volume outputs (HDMI / CEC) ignore this
                    }
                },
                action = {
                    try {
                        if (cur > 0) {
                            prefs.edit().putInt(KEY_PREV_VOL, cur).apply()
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                        } else {
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, prefs.getInt(KEY_PREV_VOL, max / 2).coerceIn(1, max), 0)
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }),
            SItem("Press ◀ / ▶ on the remote to change the volume, OK to mute.", note = true)
        )
    }

    // ---- Network ----------------------------------------------------------

    private fun networkItems(): List<SItem> {
        val wm = wifiManager()
        val on = try { wm.isWifiEnabled } catch (e: Exception) { true }
        val ssid = currentSsid()
        val items = ArrayList<SItem>()
        items.add(SItem("Wi-Fi", value = onOff(on), action = {
            if (!setWifiEnabledByApp(!on)) {
                toast("Android 10 and newer do not let apps switch Wi-Fi. Opening Wi-Fi settings.")
                openSystem(Settings.ACTION_WIFI_SETTINGS)
            } else {
                refreshSettingsSoon(1500)
            }
        }))
        items.add(SItem("Connected To", value = ssid ?: if (hasWifiScanPermission()) "Not connected" else "Allow location to show"))
        items.add(SItem("IP Address", value = localIp() ?: "-"))
        items.add(SItem("Available Networks", action = {
            closeSettings(false)
            openWifiPanel()
        }))
        return items
    }

    // ---- generic setting writers (each explains the one-time adb step when Android says no) ----------

    private fun secureInt(name: String, def: Int): Int =
        try {
            Settings.Secure.getInt(contentResolver, name, def)
        } catch (e: Exception) {
            def
        }

    private fun needSecureToast() =
        toast("One-time setup needed. On a PC run:  adb shell pm grant $packageName $SECURE_PERM")

    private fun putSecureInt(name: String, v: Int): Boolean =
        try {
            Settings.Secure.putInt(contentResolver, name, v)
        } catch (e: SecurityException) {
            needSecureToast()
            false
        } catch (e: Exception) {
            false
        }

    private fun putSecureString(name: String, v: String): Boolean =
        try {
            Settings.Secure.putString(contentResolver, name, v)
        } catch (e: SecurityException) {
            needSecureToast()
            false
        } catch (e: Exception) {
            false
        }

    private fun canWriteSystem(): Boolean {
        val ok = Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this)
        if (!ok) toast("One-time setup needed. On a PC run:  adb shell appops set $packageName WRITE_SETTINGS allow")
        return ok
    }

    private fun putSystemInt(name: String, v: Int): Boolean =
        try {
            canWriteSystem() && Settings.System.putInt(contentResolver, name, v)
        } catch (e: Exception) {
            false
        }

    private fun putSystemFloat(name: String, v: Float): Boolean =
        try {
            canWriteSystem() && Settings.System.putFloat(contentResolver, name, v)
        } catch (e: Exception) {
            false
        }

    /** Row that hands over to an Android screen. Only used where Android gives apps no other way. */
    private fun androidRow(title: String, action: String): SItem =
        SItem(title, value = "Opens Android", action = sys(action))

    // ---- Language -------------------------------------------------------------

    private fun languageItems(): List<SItem> {
        val l = Locale.getDefault()
        return listOf(
            SItem("Language", value = l.displayLanguage),
            SItem("Region", value = l.displayCountry.ifEmpty { "-" }),
            SItem("Android does not let apps switch the system language, so this page only shows it.", note = true),
            androidRow("Change Language", Settings.ACTION_LOCALE_SETTINGS)
        )
    }

    // ---- Keyboard ---------------------------------------------------------------

    private fun keyboardItems(): List<SItem> {
        val items = ArrayList<SItem>()
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val cur = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
            for (ime in imm.enabledInputMethodList) {
                val id = ime.id
                items.add(SItem(ime.loadLabel(packageManager).toString(), checked = id == cur, action = {
                    putSecureString(Settings.Secure.DEFAULT_INPUT_METHOD, id)
                }))
            }
        } catch (e: Exception) {
            items.add(SItem("Keyboards are not available on this device.", note = true))
        }
        if (!canWriteSecure()) {
            items.add(SItem("To switch keyboard, run once on a PC:  adb shell pm grant $packageName $SECURE_PERM", note = true))
        }
        items.add(androidRow("Add or Remove Keyboards", Settings.ACTION_INPUT_METHOD_SETTINGS))
        return items
    }

    // ---- Accessibility (text size) -----------------------------------------------

    private fun accessibilityItems(): List<SItem> {
        val cur = try { Settings.System.getFloat(contentResolver, Settings.System.FONT_SCALE, 1f) } catch (e: Exception) { 1f }
        val sizes = listOf("Small" to 0.85f, "Default" to 1f, "Large" to 1.15f, "Largest" to 1.3f)
        val label = sizes.minByOrNull { Math.abs(it.second - cur) }?.first ?: "Default"
        return listOf(
            SItem("Text Size", value = label, sub = {
                sizes.map { (name, v) ->
                    SItem(name, checked = Math.abs(v - cur) < 0.04f, action = {
                        if (putSystemFloat(Settings.System.FONT_SCALE, v)) settingsBack()
                    })
                }
            }),
            androidRow("More Accessibility Options", Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
    }

    // ---- Accounts ---------------------------------------------------------------

    private fun accountItems(): List<SItem> {
        val items = ArrayList<SItem>()
        try {
            if (hasAccountsPermission()) {
                for (a in AccountManager.get(this).accounts) {
                    items.add(SItem(a.name, value = if (a.type == "com.google") "Google" else a.type))
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        if (items.isEmpty()) items.add(SItem("No accounts found", note = true))
        items.add(androidRow("Add Account", Settings.ACTION_ADD_ACCOUNT))
        return items
    }

    // ---- Screen saver / sleep --------------------------------------------------

    private fun screenSaverItems(): List<SItem> {
        val on = secureInt("screensaver_enabled", 1) == 1
        val timeout = try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 600000) } catch (e: Exception) { 600000 }
        val opts = listOf("1 minute" to 60000, "5 minutes" to 300000, "15 minutes" to 900000,
            "30 minutes" to 1800000, "1 hour" to 3600000, "Never" to Int.MAX_VALUE)
        val label = opts.firstOrNull { it.second == timeout }?.first ?: (timeout / 60000).toString() + " minutes"
        val items = ArrayList<SItem>()
        items.add(SItem("Screen Saver", value = onOff(on), action = { putSecureInt("screensaver_enabled", if (on) 0 else 1) }))
        items.add(SItem("Start After", value = label, sub = {
            opts.map { (name, ms) ->
                SItem(name, checked = ms == timeout, action = {
                    if (putSystemInt(Settings.System.SCREEN_OFF_TIMEOUT, ms)) settingsBack()
                })
            }
        }))
        if (!canWriteSecure()) {
            items.add(SItem("To change these, run once on a PC:  adb shell pm grant $packageName $SECURE_PERM  and  adb shell appops set $packageName WRITE_SETTINGS allow", note = true))
        }
        return items
    }

    // ---- Video and Display ------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun displayItems(): List<SItem> {
        val items = ArrayList<SItem>()
        try {
            val d = (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
            val size = android.graphics.Point()
            d.getRealSize(size)
            items.add(SItem("Resolution", value = size.x.toString() + " x " + size.y))
            items.add(SItem("Refresh Rate", value = String.format(Locale.US, "%.0f Hz", d.refreshRate)))
            items.add(SItem("Screen Density", value = resources.displayMetrics.densityDpi.toString() + " dpi"))
        } catch (e: Exception) {
            items.add(SItem("Display information is not available.", note = true))
        }
        items.add(SItem("Resolution and HDR are decided by your TV and box. Android does not let apps change them.", note = true))
        items.add(androidRow("Advanced Display Options", Settings.ACTION_DISPLAY_SETTINGS))
        return items
    }

    // ---- Apps ---------------------------------------------------------------------

    private fun appsItems(): List<SItem> =
        allApps.filter { it.pkg != SETTINGS_PKG }
            .sortedBy { it.label.lowercase() }
            .map { a -> SItem(a.label, sub = { appDetailItems(a) }) }

    private fun appDetailItems(a: AppItem): List<SItem> {
        val inDock = isOtt(a)
        val ver = try { packageManager.getPackageInfo(a.pkg, 0).versionName ?: "" } catch (e: Exception) { "" }
        return listOf(
            SItem("Open", action = {
                closeSettings(false)
                launchApp(a)
            }),
            SItem("Show In", value = if (inDock) "Dock" else "All Apps", action = {
                if (inDock) {
                    forceGrid.add(a.pkg); forceDock.remove(a.pkg)
                } else {
                    forceDock.add(a.pkg); forceGrid.remove(a.pkg)
                }
                prefs.edit().putStringSet(KEY_DOCK, HashSet<String>(forceDock))
                    .putStringSet(KEY_GRID, HashSet<String>(forceGrid)).apply()
                refreshAppRows()
            }),
            SItem("Version", value = ver),
            SItem("Package", value = a.pkg),
            SItem("Uninstall", value = "Asks to confirm", action = {
                try {
                    startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    toast("This app cannot be uninstalled here.")
                }
            })
        )
    }

    // ---- Bluetooth pairing ---------------------------------------------------------

    private val btFound = ArrayList<android.bluetooth.BluetoothDevice>()
    private var btRecv = false
    private val btReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION", "MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                android.bluetooth.BluetoothDevice.ACTION_FOUND -> {
                    val d = intent.getParcelableExtra<android.bluetooth.BluetoothDevice>(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                    if (d != null && btFound.none { it.address == d.address }) btFound.add(d)
                }
            }
            if (settingsOpen && settingsStack.isNotEmpty() && settingsStack[settingsStack.size - 1].title == "Pair New Device") {
                showSettingsPage(true)
            }
        }
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun startBtDiscovery() {
        try {
            val bt = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!btRecv) {
                val f = IntentFilter()
                f.addAction(android.bluetooth.BluetoothDevice.ACTION_FOUND)
                f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                f.addAction(android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                registerReceiver(btReceiver, f)
                btRecv = true
            }
            if (bt.isEnabled && !bt.isDiscovering) {
                btFound.clear()
                bt.startDiscovery()
            }
        } catch (e: Exception) {
            Log.w(TAG, "bt discovery failed", e)
        }
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun stopBtDiscovery() {
        try {
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        } catch (e: Exception) {
            // ignore
        }
        if (btRecv) {
            try { unregisterReceiver(btReceiver) } catch (e: Exception) { /* ignore */ }
            btRecv = false
        }
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun pairItems(): List<SItem> {
        if (Build.VERSION.SDK_INT >= 31 && !hasPerm("android.permission.BLUETOOTH_SCAN")) {
            return listOf(
                SItem("Allow Nearby Devices", action = {
                    try { requestPermissions(arrayOf("android.permission.BLUETOOTH_SCAN", BT_PERM), 79) } catch (e: Exception) { /* ignore */ }
                })
            )
        }
        if (Build.VERSION.SDK_INT < 31 && !hasPerm(LOC_PERM)) {
            return listOf(
                SItem("Allow Location (Android needs it to find devices)", action = {
                    try { requestPermissions(arrayOf(LOC_PERM), 79) } catch (e: Exception) { /* ignore */ }
                })
            )
        }
        startBtDiscovery()
        val bt = BluetoothAdapter.getDefaultAdapter()
        val searching = try { bt?.isDiscovering == true } catch (e: Exception) { false }
        val items = ArrayList<SItem>()
        items.add(SItem(if (searching) "Searching… put your remote or device in pairing mode" else "Search Again",
            note = searching, action = { btFound.clear(); stopBtDiscovery(); startBtDiscovery() }))
        for (d in btFound.toList()) {
            val name = (try { d.name } catch (e: Exception) { null }) ?: d.address
            items.add(SItem(name, value = "Pair", action = {
                try {
                    bt?.cancelDiscovery()
                    if (!d.createBond()) toast("Could not start pairing with $name")
                } catch (e: Exception) {
                    toast("Could not start pairing with $name")
                }
                refreshSettingsSoon(2000)
            }))
        }
        return items
    }

    // ---- Bluetooth / remotes ----------------------------------------------

    @Suppress("DEPRECATION", "MissingPermission")
    private fun bluetoothItems(): List<SItem> {
        val bt = try { BluetoothAdapter.getDefaultAdapter() } catch (e: Exception) { null }
        if (bt == null) return listOf(SItem("This device has no Bluetooth.", note = true))
        if (Build.VERSION.SDK_INT >= 31 && !hasPerm(BT_PERM)) {
            return listOf(
                SItem("Allow Bluetooth Access", action = {
                    try { requestPermissions(arrayOf(BT_PERM), 79) } catch (e: Exception) { /* ignore */ }
                }),
                SItem("Android needs your OK before an app can see or switch Bluetooth.", note = true)
            )
        }
        val items = ArrayList<SItem>()
        val on = try { bt.isEnabled } catch (e: Exception) { false }
        items.add(SItem("Bluetooth", value = onOff(on), action = {
            try {
                val ok = if (on) bt.disable() else bt.enable()
                if (!ok) toast("Android did not allow switching Bluetooth from here.")
            } catch (e: Exception) {
                toast("Android did not allow switching Bluetooth from here.")
            }
            refreshSettingsSoon(1500)
        }))
        if (on) {
            try {
                for (d in bt.bondedDevices) {
                    items.add(SItem(d.name ?: d.address, value = "Paired"))
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        if (on) items.add(SItem("Pair New Device", sub = { pairItems() }))
        return items
    }

    // ---- screen ------------------------------------------------------------

    private fun buildSettingsLayer(): FrameLayout {
        val layer = FrameLayout(this)
        layer.setBackgroundColor(Color.BLACK)
        val bgAurora = AuroraView(this)
        bgAurora.setTint(Color.parseColor("#4b3f8f"))
        layer.addView(bgAurora, FrameLayout.LayoutParams(MATCH, MATCH))
        val dark = View(this)
        dark.setBackgroundColor(Color.argb(175, 0, 0, 0))
        layer.addView(dark, FrameLayout.LayoutParams(MATCH, MATCH))

        settingsCrumb = TextView(this)
        settingsCrumb.text = "SETTINGS"
        settingsCrumb.setTextColor(Color.argb(150, 255, 255, 255))
        settingsCrumb.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0105f)
        settingsCrumb.setTypeface(Typeface.DEFAULT_BOLD)
        settingsCrumb.letterSpacing = 0.3f
        val crumbLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        crumbLp.topMargin = (screenH * 0.065f).toInt()
        layer.addView(settingsCrumb, crumbLp)

        settingsTitle = TextView(this)
        settingsTitle.setTextColor(Color.WHITE)
        settingsTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.026f)
        settingsTitle.setTypeface(Typeface.DEFAULT_BOLD)
        val titleLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        titleLp.topMargin = (screenH * 0.095f).toInt()
        layer.addView(settingsTitle, titleLp)

        val colW = (screenW * 0.48f).toInt()
        val side = (screenW * 0.02f).toInt()
        settingsScroll = ScrollView(this)
        settingsScroll.isFocusable = false
        settingsScroll.isVerticalScrollBarEnabled = false
        settingsScroll.overScrollMode = View.OVER_SCROLL_NEVER
        settingsScroll.clipToPadding = false
        settingsScroll.clipChildren = false
        settingsScroll.setPadding(side, (screenW * 0.01f).toInt(), side, (screenH * 0.08f).toInt())
        settingsList = LinearLayout(this)
        settingsList.orientation = LinearLayout.VERTICAL
        settingsList.clipChildren = false
        settingsScroll.addView(settingsList, FrameLayout.LayoutParams(MATCH, WRAP))
        val sLp = FrameLayout.LayoutParams(colW + 2 * side, MATCH, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        sLp.topMargin = (screenH * 0.2f).toInt()
        layer.addView(settingsScroll, sLp)

        layer.visibility = View.GONE
        return layer
    }

    private fun openSettings() {
        if (wifiOpen) closeWifiPanel(false)
        settingsStack.clear()
        settingsStack.add(Page("Settings", { settingsRoot() }))
        settingsOpen = true
        homeGroup.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        settingsLayer.alpha = 0f
        settingsLayer.visibility = View.VISIBLE
        settingsLayer.animate().alpha(1f).setDuration(220).start()
        showSettingsPage()
    }

    private fun closeSettings(refocus: Boolean) {
        settingsOpen = false
        stopBtDiscovery()
        settingsLayer.visibility = View.GONE
        homeGroup.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        if (refocus) gearBtnView.requestFocus()
    }

    private fun settingsBack() {
        if (settingsStack.size > 1) {
            settingsStack.removeAt(settingsStack.size - 1)
            showSettingsPage()
        } else {
            closeSettings(true)
        }
    }

    /**
     * Draws the top Settings page. The rows come from the page's builder, so values are always current.
     * [keepPlace] = refresh in place (same scroll position, same focused row) after a value changed.
     */
    private fun showSettingsPage(keepPlace: Boolean = false) {
        val page = settingsStack[settingsStack.size - 1]
        val oldScroll = settingsScroll.scrollY
        var focusIdx = page.focus
        if (keepPlace) {
            val fc = settingsList.focusedChild
            if (fc != null) focusIdx = settingsList.indexOfChild(fc)
        }
        val items = page.build()          // (the time zone list also sets page.focus to the selected zone)
        if (!keepPlace) focusIdx = page.focus
        settingsTitle.text = page.title
        settingsCrumb.visibility = if (settingsStack.size > 1) View.VISIBLE else View.GONE
        settingsList.removeAllViews()
        val rowH = (screenW * 0.046f).toInt()
        val textPx = screenW * 0.0135f
        for (i in items.indices) {
            val item = items[i]
            if (item.note) {
                val tv = TextView(this)
                tv.text = item.title
                tv.setTextColor(Color.argb(140, 255, 255, 255))
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * 0.72f)
                val np = (rowH * 0.3f).toInt()
                tv.setPadding(np, (rowH * 0.12f).toInt(), np, (rowH * 0.12f).toInt())
                settingsList.addView(tv, LinearLayout.LayoutParams(MATCH, WRAP))
                continue
            }
            val icon = if (item.glyph >= 0) settingsIcon(item.glyph) else null
            val badge = if (item.glyph >= 0) item.color else null
            val subBuilder = item.sub
            val act = item.action
            val shownValue = if (item.checked) "✓" else item.value
            val onClick: () -> Unit = {
                val sb = subBuilder
                val ac = act
                if (sb != null) {
                    page.focus = i
                    settingsStack.add(Page(item.title, sb))
                    showSettingsPage()
                } else if (ac != null) {
                    page.focus = i
                    ac()
                    // still on the same page? redraw it with the new value
                    if (settingsOpen && settingsStack.isNotEmpty() && settingsStack[settingsStack.size - 1] === page) {
                        showSettingsPage(true)
                    }
                }
            }
            val rv = menuRow(MATCH, rowH, textPx, icon, badge, item.title, shownValue, subBuilder != null, 1.03f, onClick)
            val st = item.step
            if (st != null) {
                rv.row.setOnKeyListener { _, keyCode, ev ->
                    if (ev.action == KeyEvent.ACTION_DOWN &&
                        (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    ) {
                        st(if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
                        showSettingsPage(true)
                        true
                    } else false
                }
            }
            settingsList.addView(rv.row)
        }
        if (!keepPlace) settingsScroll.scrollTo(0, 0)
        settingsList.post {
            val n = settingsList.childCount
            if (n > 0) {
                // focus the wanted row, or the nearest row that can take focus (info lines can't)
                val want = focusIdx.coerceIn(0, n - 1)
                var target: View? = null
                var k = 0
                while (target == null && k < n) {
                    val a = settingsList.getChildAt(want + k)
                    val b = settingsList.getChildAt(want - k)
                    target = if (a != null && a.isFocusable) a else if (b != null && b.isFocusable) b else null
                    k++
                }
                target?.requestFocus()
            }
            if (keepPlace) settingsScroll.scrollTo(0, oldScroll)
        }
    }
}
