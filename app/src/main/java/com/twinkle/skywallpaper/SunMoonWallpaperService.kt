package com.twinkle.skywallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Star(val x: Float, val y: Float, val base: Float, val phase: Float, val size: Float)

/**
 * Live wallpaper: sky color, sun and moon follow the phone clock over 24 hours.
 * Sun is up 05:00-19:00, moon is up 17:00-07:00.
 */
class SunMoonWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SkyEngine()

    private inner class SkyEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var offsetX = 0.5f

        private val rnd = Random(42)
        private val stars = List(110) {
            Star(rnd.nextFloat(), rnd.nextFloat() * 0.72f, 0.45f + rnd.nextFloat() * 0.55f,
                rnd.nextFloat() * 6.28f, 1.2f + rnd.nextFloat() * 1.8f)
        }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val hillPath = Path()

        private val drawRunner = object : Runnable {
            override fun run() {
                drawFrame()
                handler.removeCallbacks(this)
                if (visible) handler.postDelayed(this, 1000L)
            }
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            handler.removeCallbacks(drawRunner)
            if (v) handler.post(drawRunner)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunner)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunner)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int
        ) {
            offsetX = xOffset
        }

        // hour -> top / middle / bottom sky colors
        private val stops: List<Pair<Float, IntArray>> = listOf(
            0f to intArrayOf(c("#05070f"), c("#0b1130"), c("#141b3d")),
            5f to intArrayOf(c("#142048"), c("#35315f"), c("#6b4a6b")),
            6.5f to intArrayOf(c("#2b4a7a"), c("#ff9d6c"), c("#ffd18c")),
            9f to intArrayOf(c("#2f7bd1"), c("#6fb3e0"), c("#cfe9f7")),
            12f to intArrayOf(c("#1f6fd0"), c("#63a9e6"), c("#bfe3f5")),
            17f to intArrayOf(c("#2f6fc9"), c("#ffb066"), c("#ffdca0")),
            18.5f to intArrayOf(c("#1a2a5c"), c("#ff6f5e"), c("#ffb37a")),
            20f to intArrayOf(c("#0c1338"), c("#2c1f52"), c("#6b3a5a")),
            22f to intArrayOf(c("#05070f"), c("#0b1130"), c("#141b3d")),
            24f to intArrayOf(c("#05070f"), c("#0b1130"), c("#141b3d"))
        )

        private fun c(hex: String) = Color.parseColor(hex)

        private fun lerpColor(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        )

        private fun skyColors(hour: Float): IntArray {
            val h = ((hour % 24f) + 24f) % 24f
            var a = stops.first()
            var b = stops.last()
            for (i in 0 until stops.size - 1) {
                if (h >= stops[i].first && h <= stops[i + 1].first) {
                    a = stops[i]; b = stops[i + 1]; break
                }
            }
            val span = b.first - a.first
            val t = if (span == 0f) 0f else (h - a.first) / span
            return IntArray(3) { lerpColor(a.second[it], b.second[it], t) }
        }

        private fun currentHour(): Float {
            val cal = Calendar.getInstance()
            return cal.get(Calendar.HOUR_OF_DAY) +
                cal.get(Calendar.MINUTE) / 60f +
                cal.get(Calendar.SECOND) / 3600f
        }

        private fun arcX(t: Float, w: Int) = (0.08f + t * 0.84f) * w
        private fun arcY(t: Float, h: Int) = (0.80f - sin(PI * t).toFloat() * 0.62f) * h

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) drawScene(canvas)
            } catch (e: Exception) {
                // surface not ready
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) { }
                }
            }
        }

        private fun drawScene(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            if (w == 0 || h == 0) return
            val wf = w.toFloat()
            val hf = h.toFloat()
            val hour = currentHour()

            // sky
            val sc = skyColors(hour)
            paint.reset(); paint.isAntiAlias = true
            paint.shader = LinearGradient(0f, 0f, 0f, hf, sc, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, wf, hf, paint)
            paint.shader = null

            // stars
            val darkness = (1f - cos(((hour - 12f) / 12f) * PI).toFloat() * 0.9f).coerceIn(0f, 1f)
            if (darkness > 0.05f) {
                val now = System.currentTimeMillis() / 1000.0
                paint.color = Color.WHITE
                for (s in stars) {
                    val tw = 0.75f + 0.25f * sin(now * 0.8 + s.phase).toFloat()
                    paint.alpha = (255 * darkness * s.base * tw).toInt().coerceIn(0, 255)
                    canvas.drawCircle(s.x * wf, s.y * hf, s.size * (wf / 540f), paint)
                }
                paint.alpha = 255
            }

            val r = wf * 0.06f

            // sun (05:00-19:00)
            val sunT = (hour - 5f) / 14f
            if (sunT in 0f..1f) {
                val sx = arcX(sunT, w)
                val sy = arcY(sunT, h)
                paint.shader = RadialGradient(
                    sx, sy, r * 3.2f,
                    intArrayOf(Color.argb(150, 255, 177, 80), Color.argb(0, 255, 177, 80)),
                    floatArrayOf(0.25f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawCircle(sx, sy, r * 3.2f, paint)
                paint.shader = RadialGradient(
                    sx - r * 0.3f, sy - r * 0.3f, r * 1.3f,
                    intArrayOf(c("#fff6da"), c("#ffcf6b"), c("#ff9d42")),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawCircle(sx, sy, r, paint)
                paint.shader = null
            }

            // moon (17:00-07:00 next day)
            val mh = if (hour < 17f) hour + 24f else hour
            val moonT = (mh - 17f) / 14f
            if (moonT in 0f..1f) {
                val mx = arcX(moonT, w)
                val my = arcY(moonT, h)
                paint.shader = RadialGradient(
                    mx, my, r * 2.6f,
                    intArrayOf(Color.argb(110, 200, 210, 255), Color.argb(0, 200, 210, 255)),
                    floatArrayOf(0.3f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawCircle(mx, my, r * 2.6f, paint)
                paint.shader = RadialGradient(
                    mx - r * 0.3f, my - r * 0.3f, r * 1.3f,
                    intArrayOf(Color.WHITE, c("#dfe3f0"), c("#b9bfd6")),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawCircle(mx, my, r, paint)
                paint.shader = null
                paint.color = Color.argb(80, 150, 155, 185)
                canvas.drawCircle(mx + r * 0.3f, my - r * 0.25f, r * 0.2f, paint)
                canvas.drawCircle(mx - r * 0.3f, my + r * 0.3f, r * 0.13f, paint)
                canvas.drawCircle(mx + r * 0.1f, my + r * 0.45f, r * 0.09f, paint)
            }

            // hills (slight parallax when swiping home screens)
            val shift = (offsetX - 0.5f) * wf * 0.05f
            paint.color = c("#0d1a2b"); paint.alpha = 140
            hillPath.reset()
            hillPath.moveTo(-wf * 0.1f + shift, hf * 0.84f)
            hillPath.lineTo(wf * 0.12f + shift, hf * 0.76f)
            hillPath.lineTo(wf * 0.30f + shift, hf * 0.82f)
            hillPath.lineTo(wf * 0.52f + shift, hf * 0.73f)
            hillPath.lineTo(wf * 0.78f + shift, hf * 0.81f)
            hillPath.lineTo(wf * 1.1f + shift, hf * 0.75f)
            hillPath.lineTo(wf * 1.1f, hf)
            hillPath.lineTo(-wf * 0.1f, hf)
            hillPath.close()
            canvas.drawPath(hillPath, paint)

            paint.color = c("#081019"); paint.alpha = 255
            hillPath.reset()
            hillPath.moveTo(-wf * 0.1f + shift * 2f, hf * 0.90f)
            hillPath.lineTo(wf * 0.18f + shift * 2f, hf * 0.84f)
            hillPath.lineTo(wf * 0.42f + shift * 2f, hf * 0.91f)
            hillPath.lineTo(wf * 0.68f + shift * 2f, hf * 0.83f)
            hillPath.lineTo(wf * 0.9f + shift * 2f, hf * 0.89f)
            hillPath.lineTo(wf * 1.1f + shift * 2f, hf * 0.85f)
            hillPath.lineTo(wf * 1.1f, hf)
            hillPath.lineTo(-wf * 0.1f, hf)
            hillPath.close()
            canvas.drawPath(hillPath, paint)
        }
    }
}
