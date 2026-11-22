package com.twinkle.skywallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Theme 3: village scene with paddy field, hut, coconut palms, smoke and fireflies. */
class VillageWallpaperService : BaseSkyWallpaperService() {

    override val horizonFrac: Float = 0.77f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    override fun drawForeground(
        canvas: Canvas, p: SkyPainter, w: Int, h: Int, hour: Float, t: Double, offsetX: Float
    ) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        val tf = t.toFloat()
        val night = p.nightFactor(hour)
        val shift = (offsetX - 0.5f) * wf * 0.05f
        val sky = p.skyColors(hour)

        // far tree line
        p.buildRidge(path, w, h, 0.765f, 0.010f, 7f, 0.3f, 0.006f, 17f, 1f, shift * 0.5f)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = p.mix(p.shade(Color.rgb(8, 20, 22), Color.rgb(74, 128, 92), hour), sky[2], 0.18f)
        canvas.drawPath(path, paint)

        // paddy field
        p.buildRidge(path, w, h, 0.80f, 0.006f, 1.1f, 0f, 0.003f, 2.7f, 1f, shift)
        val fieldTop = p.shade(Color.rgb(12, 32, 24), Color.rgb(120, 178, 70), hour)
        val fieldBottom = p.shade(Color.rgb(4, 14, 12), Color.rgb(40, 104, 46), hour)
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(0f, hf * 0.78f, 0f, hf, fieldTop, fieldBottom, Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)

        // rows of crop swaying in the wind
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(2f, wf * 0.004f)
        val rowColor = p.mix(fieldTop, Color.WHITE, 0.12f)
        for (i in 0 until 9) {
            val f = i / 8f
            val y = hf * (0.815f + 0.17f * f * f)
            val amp = hf * (0.0015f + 0.004f * f)
            path.reset()
            var x = -10f
            path.moveTo(x, y)
            while (x <= wf + 20f) {
                x += 24f
                path.lineTo(x, y + amp * sin(x / (wf * (0.12f + 0.10f * f)) * PIF + tf * 0.9f + i))
            }
            paint.color = rowColor
            paint.alpha = 70
            canvas.drawPath(path, paint)
        }

        drawHut(canvas, p, wf, hf, tf, hour, night, shift * 1.2f)

        // coconut palms
        val palm = p.shade(Color.rgb(4, 9, 9), Color.rgb(18, 56, 38), hour)
        drawPalm(canvas, wf * 0.08f + shift * 1.5f, hf * 0.86f, hf * 0.30f, wf * 0.04f, 0.0f, tf, palm)
        drawPalm(canvas, wf * 0.21f + shift * 1.5f, hf * 0.83f, hf * 0.20f, -wf * 0.03f, 1.7f, tf, palm)
        drawPalm(canvas, wf * 0.92f + shift * 1.5f, hf * 0.86f, hf * 0.27f, -wf * 0.05f, 3.1f, tf, palm)

        // fireflies over the field at night
        if (night > 0.35f) {
            paint.reset()
            paint.isAntiAlias = true
            for (i in 0 until 16) {
                val fx = (i * 0.618034f) % 1f
                val fy = (i * 0.381966f + 0.17f) % 1f
                val x = wf * fx + sin(tf * 0.4f + i) * wf * 0.03f
                val y = hf * (0.80f + 0.15f * fy) + cos(tf * 0.5f + i * 1.3f) * hf * 0.01f
                val pulse = 0.5f + 0.5f * sin(tf * 2f + i * 2.1f)
                val a = night * (0.25f + 0.75f * pulse)
                paint.color = Color.rgb(210, 255, 120)
                paint.alpha = (60f * a).toInt().coerceIn(0, 255)
                canvas.drawCircle(x, y, wf * 0.012f, paint)
                paint.alpha = (230f * a).toInt().coerceIn(0, 255)
                canvas.drawCircle(x, y, wf * 0.0035f, paint)
            }
        }
    }

    private fun drawHut(
        canvas: Canvas, p: SkyPainter, wf: Float, hf: Float, tf: Float, hour: Float, night: Float, shift: Float
    ) {
        val hx = wf * 0.62f + shift
        val hy = hf * 0.83f
        val hw = wf * 0.20f
        val bh = hf * 0.042f
        val rh = hf * 0.038f
        val wall = p.shade(Color.rgb(16, 16, 20), Color.rgb(176, 128, 88), hour)
        val roof = p.shade(Color.rgb(10, 11, 13), Color.rgb(108, 82, 56), hour)

        paint.reset()
        paint.isAntiAlias = true

        // walls
        paint.color = wall
        rect.set(hx, hy - bh, hx + hw, hy)
        canvas.drawRect(rect, paint)

        // chimney
        val cx = hx + hw * 0.78f
        val cy = hy - bh - rh * 0.40f
        paint.color = roof
        rect.set(cx - wf * 0.008f, cy - hf * 0.014f, cx + wf * 0.008f, cy + hf * 0.02f)
        canvas.drawRect(rect, paint)

        // roof
        path.reset()
        path.moveTo(hx - hw * 0.12f, hy - bh)
        path.lineTo(hx + hw * 0.5f, hy - bh - rh)
        path.lineTo(hx + hw * 1.12f, hy - bh)
        path.close()
        canvas.drawPath(path, paint)

        // door
        paint.color = p.shade(Color.rgb(8, 8, 10), Color.rgb(84, 56, 36), hour)
        rect.set(hx + hw * 0.62f, hy - bh * 0.78f, hx + hw * 0.80f, hy)
        canvas.drawRect(rect, paint)

        // window: warm light at night
        rect.set(hx + hw * 0.18f, hy - bh * 0.72f, hx + hw * 0.38f, hy - bh * 0.34f)
        if (night > 0.3f) {
            paint.color = Color.rgb(255, 205, 100)
            paint.alpha = (255f * night).toInt().coerceIn(0, 255)
        } else {
            paint.color = p.shade(Color.rgb(8, 8, 10), Color.rgb(70, 50, 36), hour)
        }
        canvas.drawRect(rect, paint)

        if (night > 0.3f) {
            val gx = hx + hw * 0.28f
            val gy = hy - bh * 0.53f
            paint.reset()
            paint.isAntiAlias = true
            paint.shader = RadialGradient(
                gx, gy, hw * 0.45f,
                Color.argb((90f * night).toInt().coerceIn(0, 255), 255, 200, 90),
                Color.argb(0, 255, 200, 90), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(gx, gy, hw * 0.45f, paint)
        }

        // smoke rising from the chimney
        paint.reset()
        paint.isAntiAlias = true
        for (i in 0 until 6) {
            val k = (tf * 0.10f + i / 6f) % 1f
            val sx = cx + k * wf * 0.05f + sin(k * 6f + i) * wf * 0.008f
            val sy = cy - k * hf * 0.09f
            paint.color = p.mix(Color.rgb(200, 200, 205), Color.rgb(60, 66, 90), night)
            paint.alpha = (110f * (1f - k)).toInt().coerceIn(0, 255)
            canvas.drawCircle(sx, sy, wf * (0.006f + 0.016f * k), paint)
        }
    }

    private fun drawPalm(
        canvas: Canvas, bx: Float, by: Float, ht: Float, lean: Float, phase: Float, tf: Float, color: Int
    ) {
        val sway = sin(tf * 0.7f + phase) * ht * 0.012f
        val cx = bx + lean + sway
        val cy = by - ht

        // trunk
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = max(3f, ht * 0.045f)
        paint.color = color
        path.reset()
        path.moveTo(bx, by)
        path.quadTo(bx + lean * 0.15f - ht * 0.02f, by - ht * 0.55f, cx, cy)
        canvas.drawPath(path, paint)

        // leaves
        paint.style = Paint.Style.FILL
        val angles = floatArrayOf(-170f, -140f, -110f, -80f, -50f, -20f, 160f, 15f)
        val len = ht * 0.55f
        for (i in angles.indices) {
            val a = (angles[i] + sin(tf * 0.9f + phase + i * 0.6f) * 3f) * PIF / 180f
            val dx = cos(a)
            val dy = sin(a)
            val ex = cx + dx * len
            val ey = cy + dy * len + len * 0.30f
            val mx = cx + dx * len * 0.5f
            val my = cy + dy * len * 0.5f - len * 0.10f
            val px = -dy * len * 0.10f
            val py = dx * len * 0.10f
            path.reset()
            path.moveTo(cx, cy)
            path.quadTo(mx + px, my + py, ex, ey)
            path.quadTo(mx - px, my - py + len * 0.06f, cx, cy)
            canvas.drawPath(path, paint)
        }

        // coconuts
        canvas.drawCircle(cx, cy + ht * 0.02f, ht * 0.03f, paint)
    }
}
