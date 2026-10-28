package com.example.tvlauncher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
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
    private lateinit var heroBg: GradientDrawable
    private lateinit var heroImg: ImageView
    private lateinit var titleView: TextView
    private lateinit var descView: TextView
    private lateinit var heroBtn: TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        outer = (screenW * 0.03f).toInt()
        pad = (screenW * 0.02f).toInt()
        gap = (screenW * 0.012f).toInt()
        val usable = screenW - 2 * outer - 2 * pad
        tileW = usable / 6 - gap
        tileH = (tileW * 0.58f).toInt()
        radius = screenW * 0.011f

        val apps = loadApps()

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        // Tinted gradient background
        heroBg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.DKGRAY, Color.BLACK, Color.BLACK)
        )
        applyTint(defaultTint)
        val bgView = View(this)
        bgView.background = heroBg
        root.addView(bgView, FrameLayout.LayoutParams(MATCH, MATCH))

        // Optional static wallpaper (res/drawable-nodpi/hero.jpg)
        val heroRes = resources.getIdentifier("hero", "drawable", packageName)
        if (heroRes != 0) {
            val wall = ImageView(this)
            wall.scaleType = ImageView.ScaleType.CENTER_CROP
            wall.setImageResource(heroRes)
            root.addView(wall, FrameLayout.LayoutParams(MATCH, MATCH))
        }

        // Dynamic hero artwork from installed OTT apps
        heroImg = ImageView(this)
        heroImg.scaleType = ImageView.ScaleType.CENTER_CROP
        heroImg.alpha = 0f
        root.addView(heroImg, FrameLayout.LayoutParams(MATCH, MATCH))

        // Left fade so text is readable
        val leftScrim = View(this)
        leftScrim.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.argb(210, 0, 0, 0), Color.TRANSPARENT)
        )
        root.addView(
            leftScrim,
            FrameLayout.LayoutParams((screenW * 0.6f).toInt(), MATCH, Gravity.START)
        )

        // Bottom fade behind the dock
        val scrim = View(this)
        scrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.argb(235, 0, 0, 0))
        )
        root.addView(
            scrim,
            FrameLayout.LayoutParams(MATCH, (screenH * 0.6f).toInt(), Gravity.BOTTOM)
        )

        // Title + description + button (top-left)
        titleView = TextView(this)
        titleView.text = "Home"
        titleView.setTextColor(Color.WHITE)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.032f)
        titleView.setTypeface(Typeface.DEFAULT_BOLD, Typeface.ITALIC)
        titleView.setShadowLayer(10f, 0f, 3f, Color.BLACK)
        titleView.maxLines = 2
        titleView.ellipsize = TextUtils.TruncateAt.END
        titleView.maxWidth = (screenW * 0.5f).toInt()

        descView = TextView(this)
        descView.setTextColor(Color.argb(225, 255, 255, 255))
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
        btnBg.cornerRadius = radius
        btnBg.setColor(Color.argb(170, 255, 255, 255))
        heroBtn.background = btnBg
        val bh = (screenW * 0.02f).toInt()
        val bv = (screenW * 0.009f).toInt()
        heroBtn.setPadding(bh, bv, bh, bv)
        heroBtn.isFocusable = true
        heroBtn.isClickable = true
        heroBtn.visibility = View.GONE
        if (Build.VERSION.SDK_INT >= 26) heroBtn.defaultFocusHighlightEnabled = false
        heroBtn.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.1f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(150).start()
            btnBg.setColor(if (focused) Color.WHITE else Color.argb(170, 255, 255, 255))
        }
        heroBtn.setOnClickListener { launchHero(currentItem) }

        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        info.addView(titleView)
        val descLp = LinearLayout.LayoutParams((screenW * 0.4f).toInt(), WRAP)
        descLp.topMargin = (screenW * 0.008f).toInt()
        info.addView(descView, descLp)
        val btnLp = LinearLayout.LayoutParams(WRAP, WRAP)
        btnLp.topMargin = (screenW * 0.014f).toInt()
        info.addView(heroBtn, btnLp)
        val infoLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START)
        infoLp.setMargins(outer + pad, (screenH * 0.07f).toInt(), 0, 0)
        root.addView(info, infoLp)

        // Clock pill (top-right)
        val clock = TextClock(this)
        clock.format12Hour = "h:mm"
        clock.format24Hour = "HH:mm"
        clock.setTextColor(Color.WHITE)
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenW * 0.0125f)
        val pill = GradientDrawable()
        pill.cornerRadius = 200f
        pill.setColor(Color.argb(110, 30, 30, 40))
        pill.setStroke(2, Color.argb(50, 255, 255, 255))
        clock.background = pill
        val ph = (screenW * 0.012f).toInt()
        val pv = (screenW * 0.006f).toInt()
        clock.setPadding(ph, pv, ph, pv)
        val clockLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END)
        clockLp.setMargins(0, (screenH * 0.05f).toInt(), outer + pad, 0)
        root.addView(clock, clockLp)

        // App rows
        list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        list.clipChildren = false
        list.clipToPadding = false
        list.setItemViewCacheSize(10)
        list.adapter = RowAdapter(apps.chunked(6))
        root.addView(
            list,
            FrameLayout.LayoutParams(MATCH, (screenH * 0.44f).toInt(), Gravity.BOTTOM)
        )

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
        if (hasTvPermission()) loadHeroAsync()
    }

    override fun onPause() {
        handler.removeCallbacks(rotator)
        super.onPause()
    }

    override fun onBackPressed() {
        // Launcher is the root screen: ignore Back.
    }

    // ---------- permission + hero data ----------

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

    /** Reads preview ("latest") and watch-next ("continue") programs published by OTT apps. */
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
                // No access or provider missing: ignore and fall back to gradient.
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

    // ---------- hero display ----------

    private fun showHeroFor(app: AppItem) {
        handler.removeCallbacks(rotator)
        val items = heroMap[app.pkg].orEmpty()
        heroIndex = 0
        if (items.isEmpty()) {
            currentItem = null
            heroToken++
            titleView.text = app.label
            heroBtn.visibility = View.GONE
            heroImg.animate().alpha(0f).setDuration(250).start()
            if (hasTvPermission()) {
                descView.visibility = View.GONE
            } else {
                descView.text = "Hero art needs: adb shell pm grant $packageName $TV_PERM"
                descView.visibility = View.VISIBLE
            }
            return
        }
        showItem(app, items[0])
        if (items.size > 1) handler.postDelayed(rotator, 7000)
    }

    private fun rotateHero() {
        val app = focusedApp ?: return
        val items = heroMap[app.pkg].orEmpty()
        if (items.size < 2) return
        if (!heroBtn.hasFocus()) {
            heroIndex = (heroIndex + 1) % items.size
            showItem(app, items[heroIndex])
        }
        handler.postDelayed(rotator, 7000)
    }

    private fun showItem(app: AppItem, item: HeroItem) {
        currentItem = item
        titleView.text = item.title ?: app.label
        descView.text = item.desc.orEmpty()
        descView.visibility = if (item.desc.isNullOrBlank()) View.GONE else View.VISIBLE
        heroBtn.visibility = View.VISIBLE
        val token = ++heroToken
        val img = item.image
        if (img == null) {
            heroImg.animate().alpha(0f).setDuration(250).start()
            return
        }
        loadBitmap(img) { bmp ->
            if (token != heroToken) return@loadBitmap
            if (bmp == null) {
                heroImg.animate().alpha(0f).setDuration(250).start()
                return@loadBitmap
            }
            heroImg.animate().alpha(0f).setDuration(180).withEndAction {
                if (token == heroToken) {
                    val wide = bmp.width.toFloat() / bmp.height.toFloat() >= 1.4f
                    heroImg.scaleType =
                        if (wide) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_END
                    heroImg.setImageBitmap(bmp)
                    heroImg.animate().alpha(1f).setDuration(350).start()
                }
            }.start()
        }
    }

    // ---------- image loading ----------

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

    // ---------- apps ----------

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(query, 0)
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
            .sortedBy { it.label.lowercase() }
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

    // ---------- colors ----------

    private fun withValue(color: Int, v: Float, satBoost: Float = 1f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * satBoost).coerceIn(0f, 1f)
        hsv[2] = v
        return Color.HSVToColor(hsv)
    }

    private fun applyTint(c: Int) {
        heroBg.setColors(intArrayOf(withValue(c, 0.60f, 1.1f), withValue(c, 0.22f), Color.BLACK))
    }

    private fun setTint(target: Int) {
        if (target == currentTint) return
        tintAnimator?.cancel()
        val anim = ValueAnimator.ofObject(ArgbEvaluator(), currentTint, target)
        anim.duration = 450
        anim.addUpdateListener { a -> applyTint(a.animatedValue as Int) }
        anim.start()
        tintAnimator = anim
        currentTint = target
    }

    private fun onAppFocused(app: AppItem) {
        focusedApp = app
        setTint(app.tint)
        showHeroFor(app)
    }

    // ---------- tiles ----------

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
        val colLp = LinearLayout.LayoutParams(tileW, WRAP)
        colLp.setMargins(gap / 2, 0, gap / 2, 0)
        col.layoutParams = colLp
        col.addView(card)
        col.addView(label)
        col.pivotX = tileW / 2f
        col.pivotY = tileH / 2f
        if (Build.VERSION.SDK_INT >= 26) {
            col.defaultFocusHighlightEnabled = false
        }

        col.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.1f else 1f
            v.animate().scaleX(s).scaleY(s)
                .translationZ(if (focused) 20f else 0f)
                .setDuration(160).start()
            label.animate().alpha(if (focused) 1f else 0f).setDuration(160).start()
            ring.animate().alpha(if (focused) 1f else 0f).setDuration(160).start()
            if (focused) onAppFocused(app)
        }
        col.setOnClickListener {
            packageManager.getLeanbackLaunchIntentForPackage(app.pkg)?.let { startActivity(it) }
        }
        return col
    }

    inner class RowAdapter(private val rows: List<List<AppItem>>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount() = rows.size

        override fun getItemViewType(position: Int) = if (position == 0) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val row = LinearLayout(parent.context)
            row.orientation = LinearLayout.HORIZONTAL
            row.clipChildren = false
            row.clipToPadding = false
            val rowGap = (screenW * 0.008f).toInt()
            val lp = RecyclerView.LayoutParams(MATCH, WRAP)
            lp.setMargins(outer, rowGap, outer, rowGap)
            row.layoutParams = lp
            if (viewType == 0) {
                val v = (pad * 0.8f).toInt()
                row.setPadding(pad, v, pad, v)
                val glass = GradientDrawable()
                glass.cornerRadius = radius * 1.6f
                glass.setColor(Color.argb(110, 20, 20, 28))
                glass.setStroke(2, Color.argb(45, 255, 255, 255))
                row.background = glass
            } else {
                val v = (pad * 0.5f).toInt()
                row.setPadding(pad, v, pad, v)
            }
            return object : RecyclerView.ViewHolder(row) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = holder.itemView as LinearLayout
            row.removeAllViews()
            rows[position].forEach { row.addView(buildTile(it)) }
        }
    }
}
