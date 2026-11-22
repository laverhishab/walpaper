package com.twinkle.skywallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/** Theme 1: sun and moon over rolling hills. */
class SunMoonWallpaperService : BaseSkyWallpaperService() {

    override val horizonFrac: Float = 0.80f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hills = Path()

    override fun drawForeground(
        canvas: Canvas, p: SkyPainter, w: Int, h: Int, hour: Float, t: Double, offsetX: Float
    ) {
        val shift = (offsetX - 0.5f) * w * 0.05f
        val sky = p.skyColors(hour)

        // far hills (slightly misty)
        p.buildRidge(hills, w, h, 0.80f, 0.030f, 1.3f, 0.5f, 0.012f, 3.1f, 1.7f, shift)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = p.mix(p.shade(Color.rgb(13, 26, 43), Color.rgb(96, 140, 128), hour), sky[2], 0.12f)
        canvas.drawPath(hills, paint)

        // near hills
        p.buildRidge(hills, w, h, 0.89f, 0.028f, 0.9f, 2.4f, 0.010f, 2.6f, 0.4f, shift * 2f)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = p.shade(Color.rgb(8, 16, 25), Color.rgb(52, 100, 70), hour)
        canvas.drawPath(hills, paint)
    }
}
