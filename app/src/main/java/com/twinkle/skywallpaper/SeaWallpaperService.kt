package com.twinkle.skywallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.sin

/** Theme 2: open sea with moving waves, glittering reflection and a sailing boat. */
class SeaWallpaperService : BaseSkyWallpaperService() {

    override val horizonFrac: Float = 0.60f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wave = Path()
    private val boat = Path()
    private val rect = RectF()

    override fun drawForeground(
        canvas: Canvas, p: SkyPainter, w: Int, h: Int, hour: Float, t: Double, offsetX: Float
    ) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        val tf = t.toFloat()
        val hy = horizonFrac * hf
        val depth = hf - hy
        val night = p.nightFactor(hour)
        val sky = p.skyColors(hour)
        val waterTop = p.mix(sky[2], Color.rgb(24, 70, 110), 0.40f)
        val waterBottom = p.mix(sky[0], Color.rgb(3, 14, 30), 0.55f)
        val crest = p.mix(waterTop, Color.WHITE, 0.55f)

        // water body
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(0f, hy, 0f, hf, waterTop, waterBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, hy, wf, hf, paint)

        // glittering reflection of the sun / moon
        val sun = p.sunPosition(hour, w, h)
        if (sun != null) {
            drawGlitter(canvas, sun[0], hy, wf, hf, tf, Color.rgb(255, 214, 140), 1f)
        }
        val moon = p.moonPosition(hour, w, h)
        if (moon != null) {
            drawGlitter(canvas, moon[0], hy, wf, hf, tf, Color.rgb(215, 225, 255), 0.5f + 0.5f * night)
        }

        // layered waves, far to near
        val layers = 6
        val step = max(10f, wf / 48f)
        for (k in 0 until layers) {
            val f = k / (layers - 1f)
            val baseY = hy + depth * (0.05f + 0.80f * f * f)
            val amp = hf * (0.002f + 0.010f * f)
            val len = wf * (0.28f + 0.55f * f)
            val ph = tf * (0.6f + 0.25f * f)

            wave.reset()
            wave.moveTo(-10f, hf + 10f)
            var x = -10f
            while (x <= wf + step) {
                val y = baseY +
                    amp * sin(TWO_PI_F * x / len + ph) +
                    amp * 0.5f * sin(TWO_PI_F * x / (len * 0.43f) - ph * 1.3f)
                wave.lineTo(x, y)
                x += step
            }
            wave.lineTo(wf + step, hf + 10f)
            wave.close()

            paint.reset()
            paint.isAntiAlias = true
            paint.color = p.mix(waterTop, waterBottom, 0.25f + 0.70f * f)
            paint.alpha = 120 + k * 22
            canvas.drawPath(wave, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1.5f, wf * 0.0025f)
            paint.color = crest
            paint.alpha = 55
            canvas.drawPath(wave, paint)

            if (k == 0) {
                // a small boat sailing slowly across, behind the nearer waves
                val frac = ((tf / 240f) % 1f)
                val bx = wf * (-0.1f + 1.2f * frac)
                val by = baseY - amp
                drawBoat(canvas, p, bx, by, wf * 0.045f, hour, night, tf)
            }
        }
    }

    private fun drawGlitter(
        canvas: Canvas, cx: Float, hy: Float, wf: Float, hf: Float, tf: Float, color: Int, strength: Float
    ) {
        val rows = 30
        val depth = hf - hy
        paint.reset()
        paint.isAntiAlias = true
        for (i in 0 until rows) {
            val k = i / rows.toFloat()
            val yy = hy + depth * k * k * 0.9f + 2f
            val shimmer = 0.5f + 0.5f * sin(tf * 2.2f + i * 1.9f)
            val bw = wf * (0.05f + 0.20f * k) * (0.35f + 0.65f * shimmer)
            val jitter = sin(tf * 1.1f + i * 0.7f) * wf * 0.012f * (0.3f + k)
            val bh = max(2f, depth * 0.006f * (0.6f + k))
            paint.color = color
            paint.alpha = (200f * strength * (1f - k * 0.75f) * (0.4f + 0.6f * shimmer)).toInt().coerceIn(0, 255)
            rect.set(cx + jitter - bw / 2f, yy, cx + jitter + bw / 2f, yy + bh)
            canvas.drawRoundRect(rect, bh, bh, paint)
        }
    }

    private fun drawBoat(
        canvas: Canvas, p: SkyPainter, x: Float, y: Float, s: Float, hour: Float, night: Float, tf: Float
    ) {
        val hull = p.shade(Color.rgb(6, 10, 18), Color.rgb(46, 32, 28), hour)
        val sail = p.shade(Color.rgb(50, 56, 80), Color.rgb(250, 244, 232), hour)
        val bob = sin(tf * 1.4f) * s * 0.06f

        canvas.save()
        canvas.rotate(sin(tf * 0.9f) * 2.5f, x, y + bob)

        paint.reset()
        paint.isAntiAlias = true

        // hull
        boat.reset()
        boat.moveTo(x - s * 0.9f, y + bob)
        boat.lineTo(x + s * 0.9f, y + bob)
        boat.lineTo(x + s * 0.6f, y + bob + s * 0.3f)
        boat.lineTo(x - s * 0.6f, y + bob + s * 0.3f)
        boat.close()
        paint.color = hull
        canvas.drawPath(boat, paint)

        // mast
        paint.strokeWidth = max(2f, s * 0.06f)
        canvas.drawLine(x, y + bob, x, y + bob - s * 2.0f, paint)

        // main sail
        boat.reset()
        boat.moveTo(x + s * 0.08f, y + bob - s * 1.95f)
        boat.lineTo(x + s * 0.08f, y + bob - s * 0.15f)
        boat.lineTo(x + s * 0.85f, y + bob - s * 0.15f)
        boat.close()
        paint.color = sail
        canvas.drawPath(boat, paint)

        // front sail
        boat.reset()
        boat.moveTo(x - s * 0.08f, y + bob - s * 1.6f)
        boat.lineTo(x - s * 0.08f, y + bob - s * 0.15f)
        boat.lineTo(x - s * 0.6f, y + bob - s * 0.15f)
        boat.close()
        canvas.drawPath(boat, paint)

        // lantern at night
        if (night > 0.4f) {
            paint.color = Color.rgb(255, 220, 120)
            paint.alpha = (230f * night).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y + bob - s * 2.05f, max(2f, s * 0.07f), paint)
        }

        canvas.restore()
    }
}
