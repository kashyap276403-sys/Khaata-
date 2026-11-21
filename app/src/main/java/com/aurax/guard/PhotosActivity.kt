package com.aurax.guard

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-app viewer for intruder photos, with delete. Needs the app PIN if one is set. */
class PhotosActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var header: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Gate.isOpen(this)) {
            finish()
            return
        }
        if (Prefs.secureScreen(this)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(40))
        }
        header = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(header)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        if (!Gate.isOpen(this)) {
            finish()
            return
        }
        render()
    }

    private fun render() {
        list.removeAllViews()
        val photos = SelfieCapture.listPhotos(this)
        header.text = "Intruder photos (${photos.size})"
        if (photos.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Abhi koi photo nahi hai."
                setTextColor(Color.argb(185, 255, 255, 255))
                setPadding(0, dp(16), 0, 0)
            })
            return
        }
        for (p in photos.take(30)) list.addView(photoCard(p))
    }

    private fun photoCard(p: Photo): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.argb(26, 255, 255, 255))
                setStroke(dp(1), Color.argb(46, 255, 255, 255))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(14) }
        }
        val img = ImageView(this).apply {
            adjustViewBounds = true
            maxHeight = dp(380)
            setBackgroundColor(Color.argb(30, 255, 255, 255))
        }
        card.addView(img)
        card.addView(TextView(this).apply {
            text = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(p.timeMs))
            setTextColor(Color.argb(185, 255, 255, 255))
            setPadding(0, dp(8), 0, dp(4))
        })
        card.addView(Button(this).apply {
            text = "Ye photo mitayein"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(110, 255, 110, 110))
            }
            setOnClickListener { confirmDelete(p) }
        })
        Thread {
            val bmp = decode(p)
            runOnUiThread { if (bmp != null) img.setImageBitmap(bmp) }
        }.start()
        return card
    }

    private fun decode(p: Photo): Bitmap? = try {
        contentResolver.openInputStream(p.uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
        }
    } catch (_: Exception) {
        null
    }

    private fun confirmDelete(p: Photo) {
        AlertDialog.Builder(this)
            .setMessage("Ye photo hamesha ke liye mit jayegi. Mitayein?")
            .setPositiveButton("Mitayein") { _, _ ->
                SelfieCapture.deletePhoto(this, p)
                render()
            }
            .setNegativeButton("Rehne dein", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
