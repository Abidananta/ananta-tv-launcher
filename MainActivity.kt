package com.example.tvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {

    data class App(val label: String, val pkg: String, val icon: android.graphics.drawable.Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.loadIcon(packageManager)) }
            .sortedBy { it.label.lowercase() }

        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#1c1c2e"), Color.BLACK)
            )
        }
        val title = TextView(this).apply {
            text = "Home"; textSize = 36f; setTextColor(Color.WHITE)
            setPadding(96, 80, 0, 0)
        }
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            clipToPadding = false
            setPadding(80, 0, 80, 0)
            adapter = Adapter(apps)
        }
        root.addView(title)
        root.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 420, Gravity.CENTER_VERTICAL))
        setContentView(root)
    }

    override fun onBackPressed() { /* launcher: do nothing */ }

    inner class Adapter(private val apps: List<App>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = apps.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val tile = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = RecyclerView.LayoutParams(340, 340).apply { setMargins(24, 24, 24, 24) }
                isFocusable = true
                isClickable = true
                background = GradientDrawable().apply {
                    cornerRadius = 36f; setColor(Color.parseColor("#33FFFFFF"))
                }
                val iv = ImageView(context).apply { tag = "icon"; layoutParams = LinearLayout.LayoutParams(180, 180) }
                val tv = TextView(context).apply {
                    tag = "label"; setTextColor(Color.WHITE); textSize = 16f
                    gravity = Gravity.CENTER; setPadding(0, 16, 0, 0)
                }
                addView(iv); addView(tv)
                onFocusChangeListener = View.OnFocusChangeListener { v, focused ->
                    v.animate().scaleX(if (focused) 1.15f else 1f)
                        .scaleY(if (focused) 1.15f else 1f)
                        .translationZ(if (focused) 24f else 0f).setDuration(150).start()
                    (v.background as GradientDrawable)
                        .setColor(Color.parseColor(if (focused) "#66FFFFFF" else "#33FFFFFF"))
                }
            }
            return object : RecyclerView.ViewHolder(tile) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val app = apps[position]
            holder.itemView.findViewWithTag<ImageView>("icon").setImageDrawable(app.icon)
            holder.itemView.findViewWithTag<TextView>("label").text = app.label
            holder.itemView.setOnClickListener {
                packageManager.getLeanbackLaunchIntentForPackage(app.pkg)?.let { startActivity(it) }
            }
        }
    }
}
