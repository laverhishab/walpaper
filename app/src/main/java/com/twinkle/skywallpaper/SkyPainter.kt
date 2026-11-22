package com.twinkle.skywallpaper

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

internal val PIF: Float = Math.PI.toFloat()
internal val TWO_PI_F: Float = (2.0 * Math.PI).toFloat()

/**
 * Shared sky drawing for every wallpaper theme:
 * sky gradient, twinkling stars, shooting stars, sun, moon and drifting clouds.
 * Each theme draws its own foreground (hills, sea, village...) on top.
 */
class SkyPainter {

    /** Where the sun and moon rise and set, as a fraction of screen height. */
    var horizonFrac: Float = 0.80f

    private class Star(val x: Float, val y: Float, val base: Float, val phase: Float, val size: Float)

    private class Meteor(
        val x0: Float, val y0: Float, val ux: Float, val uy: Float,
        val speed: Float, val born: Double, val life: Float, val tail: Float
    )

    private class Cloud(
        val shape: Int, val y: Float, val x0: Float, val speed: Float,
        val scale: Float, val alphaMul: Float
    )

    private val rnd = Random(42)

    private val stars = List(120) {
        Star(
            rnd.nextFloat(), rnd.nextFloat() * 0.72f, 0.45f + rnd.nextFloat() * 0.55f,
            rnd.nextFloat() * TWO_PI_F, 1.2f + rnd.nextFloat() * 1.8f
        )
    }

    private val clouds = List(9) { i ->
        Cloud(
            i % 3, 0.04f + rnd.nextFloat() * 0.36f, rnd.nextFloat(),
            0.006f + rnd.nextFloat() * 0.014f, 0.55f + rnd.nextFloat() * 0.9f,
            0.55f + rnd.nextFloat() * 0.45f
        )
    }

    private val meteors = ArrayList<Meteor>()
    private var nextMeteorAt = 1.5

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var cloudBitmaps: Array<Bitmap>? = null
    private var cloudBuiltFor = 0

    // hour -> top / middle / bottom sky colours
    private val stops: List<Pair<Float, IntArray>> = listOf(
        0f to intArrayOf(c("#05070f"), c("#0b1130"), c("#141b3d")),
        5f to intArrayOf(c("#142048"), c("#35315f"), c("#6b4a6b")),
        6f to intArrayOf(c("#2b4a7a"), c("#ff9d6c"), c("#ffd18c")),
        8f to intArrayOf(c("#2f7bd1"), c("#6fb3e0"), c("#cfe9f7")),
        12f to intArrayOf(c("#1f6fd0"), c("#63a9e6"), c("#bfe3f5")),
        15f to intArrayOf(c("#2f6fc9"), c("#7fb6e6"), c("#e6e2c8")),
        17f to intArrayOf(c("#2f6fc9"), c("#ffb066"), c("#ffdca0")),
        18f to intArrayOf(c("#1a2a5c"), c("#ff6f5e"), c("#ffb37a")),
        19.5f to intArrayOf(c("#0c1338"), c("#2c1f52"), c("#6b3a5a")),
        21f to intArrayOf(c("#05070f"), c("#0b1130"), c("#141b3d")),
        24f to intArrayOf(c("#05070f"), c("#0b1130"), c("#141b3d"))
    )

    private fun c(hex: String): Int = Color.parseColor(hex)

    // ------------------------------------------------------------------
    // helpers shared with the themes
    // ------------------------------------------------------------------

    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
        )
    }

    fun currentHour(): Float {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) +
            cal.get(Calendar.MINUTE) / 60f +
            cal.get(Calendar.SECOND) / 3600f
    }

    fun skyColors(hour: Float): IntArray {
        val h = ((hour % 24f) + 24f) % 24f
        var a = stops.first()
        var b = stops.last()
        for (i in 0 until stops.size - 1) {
            if (h >= stops[i].first && h <= stops[i + 1].first) {
                a = stops[i]
                b = stops[i + 1]
                break
            }
        }
        val span = b.first - a.first
        val t = if (span == 0f) 0f else (h - a.first) / span
        return IntArray(3) { mix(a.second[it], b.second[it], t) }
    }

    /** 0 in daylight, 1 in full night. */
    fun nightFactor(hour: Float): Float {
        val h = ((hour % 24f) + 24f) % 24f
        return when {
            h >= 20.5f || h <= 4.5f -> 1f
            h > 18.5f -> (h - 18.5f) / 2f
            h < 6.5f -> 1f - (h - 4.5f) / 2f
            else -> 0f
        }
    }

    /** 0 at night, 1 at noon. */
    fun daylight(hour: Float): Float {
        val h = ((hour % 24f) + 24f) % 24f
        if (h <= 6f || h >= 18f) return 0f
        return sin(PIF * (h - 6f) / 12f)
    }

    /** Eased daylight, so morning and evening still keep some colour. */
    fun dayMix(hour: Float): Float = Math.pow(daylight(hour).toDouble(), 0.6).toFloat()

    /** Colour for foreground objects: dark at night, [dayColor] in daylight. */
    fun shade(nightColor: Int, dayColor: Int, hour: Float): Int = mix(nightColor, dayColor, dayMix(hour))

    private fun arcX(t: Float, w: Int): Float = (0.08f + t * 0.84f) * w

    private fun arcY(t: Float, h: Int): Float =
        (horizonFrac - sin(PIF * t) * (horizonFrac - 0.14f)) * h

    /** Returns [x, y, progress] of the sun or null when it is below the horizon. */
    fun sunPosition(hour: Float, w: Int, h: Int): FloatArray? {
        val t = (hour - 5.5f) / 13f
        if (t < 0f || t > 1f) return null
        return floatArrayOf(arcX(t, w), arcY(t, h), t)
    }

    /** Returns [x, y, progress] of the moon or null when it is below the horizon. */
    fun moonPosition(hour: Float, w: Int, h: Int): FloatArray? {
        val mh = if (hour < 17.5f) hour + 24f else hour
        val t = (mh - 17.5f) / 13f
        if (t < 0f || t > 1f) return null
        return floatArrayOf(arcX(t, w), arcY(t, h), t)
    }

    /** Fills [path] with a smooth wavy ridge covering the bottom of the screen. */
    fun buildRidge(
        path: Path, w: Int, h: Int, baseFrac: Float,
        a1: Float, f1: Float, p1: Float, a2: Float, f2: Float, p2: Float, shift: Float
    ) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        val step = max(8f, wf / 90f)
        path.reset()
        var x = -0.15f * wf
        path.moveTo(x + shift, hf)
        while (x <= 1.15f * wf) {
            val u = x / wf
            val y = (baseFrac + a1 * sin(u * f1 * TWO_PI_F + p1) + a2 * sin(u * f2 * TWO_PI_F + p2)) * hf
            path.lineTo(x + shift, y)
            x += step
        }
        path.lineTo(1.15f * wf + shift, hf)
        path.close()
    }

    // ------------------------------------------------------------------
    // main entry
    // ------------------------------------------------------------------

    fun draw(canvas: Canvas, w: Int, h: Int, hour: Float, t: Double, offsetX: Float) {
        val night = nightFactor(hour)
        drawSky(canvas, w, h, hour)
        drawStars(canvas, w, h, night, t)
        drawMeteors(canvas, w, h, night, t)
        drawSun(canvas, w, h, hour)
        drawMoon(canvas, w, h, hour, night)
        drawClouds(canvas, w, h, hour, t, offsetX, night)
    }

    private fun drawSky(canvas: Canvas, w: Int, h: Int, hour: Float) {
        val sc = skyColors(hour)
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(), sc, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private fun drawStars(canvas: Canvas, w: Int, h: Int, night: Float, t: Double) {
        if (night < 0.03f) return
        val wf = w.toFloat()
        val hf = h.toFloat()
        val scale = wf / 540f
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        for (s in stars) {
            val tw = 0.7f + 0.3f * sin((t * 1.3).toFloat() + s.phase)
            paint.alpha = (255f * night * s.base * tw).toInt().coerceIn(0, 255)
            canvas.drawCircle(s.x * wf, s.y * hf, s.size * scale * 0.9f, paint)
        }
    }

    private fun spawnMeteor(w: Int, h: Int, born: Double) {
        val ang = (20f + rnd.nextFloat() * 25f) * PIF / 180f
        val speed = w * (0.55f + rnd.nextFloat() * 0.35f)
        meteors.add(
            Meteor(
                w * (0.35f + rnd.nextFloat() * 0.65f),
                h * (0.02f + rnd.nextFloat() * 0.30f),
                -cos(ang), sin(ang), speed, born,
                0.9f + rnd.nextFloat() * 0.4f, w * 0.16f
            )
        )
    }

    private fun drawMeteors(canvas: Canvas, w: Int, h: Int, night: Float, t: Double) {
        if (night > 0.6f && t >= nextMeteorAt) {
            spawnMeteor(w, h, t)
            if (rnd.nextFloat() < 0.2f) spawnMeteor(w, h, t + 0.2)
            nextMeteorAt = t + 2.5 + rnd.nextDouble() * 4.5
        }
        if (meteors.isEmpty()) return
        meteors.removeAll { t - it.born > it.life }

        val stroke = max(2f, w * 0.0035f)
        paint.reset()
        paint.isAntiAlias = true
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.WHITE
        for (m in meteors) {
            val age = (t - m.born).toFloat()
            if (age < 0f) continue
            val k = age / m.life
            val fade = sin(PIF * k).coerceIn(0f, 1f)
            val hx = m.x0 + m.ux * m.speed * age
            val hy = m.y0 + m.uy * m.speed * age
            val segs = 14
            for (i in 0 until segs) {
                val a0 = i / segs.toFloat()
                val a1 = (i + 1) / segs.toFloat()
                paint.alpha = (255f * fade * (1f - a0) * (1f - a0)).toInt().coerceIn(0, 255)
                paint.strokeWidth = stroke * (1f - a0 * 0.75f)
                canvas.drawLine(
                    hx - m.ux * m.tail * a0, hy - m.uy * m.tail * a0,
                    hx - m.ux * m.tail * a1, hy - m.uy * m.tail * a1, paint
                )
            }
            paint.alpha = (255f * fade).toInt().coerceIn(0, 255)
            canvas.drawCircle(hx, hy, stroke * 0.9f, paint)
        }
    }

    private fun drawSun(canvas: Canvas, w: Int, h: Int, hour: Float) {
        val pos = sunPosition(hour, w, h) ?: return
        val sx = pos[0]
        val sy = pos[1]
        val r = w * 0.06f
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = RadialGradient(
            sx, sy, r * 3.4f,
            intArrayOf(Color.argb(150, 255, 190, 90), Color.argb(0, 255, 190, 90)),
            floatArrayOf(0.2f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sx, sy, r * 3.4f, paint)
        paint.shader = RadialGradient(
            sx - r * 0.3f, sy - r * 0.3f, r * 1.3f,
            intArrayOf(c("#fff6da"), c("#ffcf6b"), c("#ff9d42")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sx, sy, r, paint)
    }

    private fun drawMoon(canvas: Canvas, w: Int, h: Int, hour: Float, night: Float) {
        val pos = moonPosition(hour, w, h) ?: return
        val mx = pos[0]
        val my = pos[1]
        val r = w * 0.055f
        val a = (140f + 115f * night).toInt().coerceIn(0, 255)
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = RadialGradient(
            mx, my, r * 2.6f,
            intArrayOf(Color.argb(110, 200, 210, 255), Color.argb(0, 200, 210, 255)),
            floatArrayOf(0.3f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = a
        canvas.drawCircle(mx, my, r * 2.6f, paint)
        paint.shader = RadialGradient(
            mx - r * 0.3f, my - r * 0.3f, r * 1.3f,
            intArrayOf(Color.WHITE, c("#dfe3f0"), c("#b9bfd6")),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = a
        canvas.drawCircle(mx, my, r, paint)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.argb(80, 150, 155, 185)
        canvas.drawCircle(mx + r * 0.3f, my - r * 0.25f, r * 0.2f, paint)
        canvas.drawCircle(mx - r * 0.3f, my + r * 0.3f, r * 0.13f, paint)
        canvas.drawCircle(mx + r * 0.1f, my + r * 0.45f, r * 0.09f, paint)
    }

    // ------------------------------------------------------------------
    // clouds
    // ------------------------------------------------------------------

    private fun ensureClouds(w: Int) {
        if (cloudBitmaps != null && cloudBuiltFor == w) return
        cloudBitmaps?.forEach { it.recycle() }
        cloudBitmaps = Array(3) { makeCloud(w, it) }
        cloudBuiltFor = w
    }

    private fun makeCloud(w: Int, variant: Int): Bitmap {
        val cw = max(64, (w * 0.5f).toInt())
        val ch = max(32, (w * 0.2f).toInt())
        val bmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        p.maskFilter = BlurMaskFilter(ch * 0.06f, BlurMaskFilter.Blur.NORMAL)
        val r = Random(100 + variant)
        val puffs = 6 + variant
        cv.drawRoundRect(RectF(cw * 0.10f, ch * 0.52f, cw * 0.90f, ch * 0.80f), ch * 0.14f, ch * 0.14f, p)
        for (i in 0 until puffs) {
            val tt = i / (puffs - 1f)
            val cx = cw * (0.18f + 0.64f * tt)
            val bump = sin(PIF * tt)
            val rad = ch * (0.14f + 0.17f * bump) * (0.85f + 0.3f * r.nextFloat())
            val cy = ch * (0.58f - 0.10f * bump * (0.5f + r.nextFloat() * 0.5f))
            cv.drawCircle(cx, cy, rad, p)
        }
        return bmp
    }

    private fun drawClouds(
        canvas: Canvas, w: Int, h: Int, hour: Float, t: Double, offsetX: Float, night: Float
    ) {
        ensureClouds(w)
        val bmps = cloudBitmaps ?: return
        val sc = skyColors(hour)
        val base = mix(Color.rgb(60, 70, 108), Color.WHITE, 1f - night)
        val tint = mix(base, sc[1], 0.28f)
        val baseAlpha = 95f + 125f * (1f - night)
        val wf = w.toFloat()
        val hf = h.toFloat()
        val tf = t.toFloat()

        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        val dst = RectF()
        for (cl in clouds) {
            val bmp = bmps[cl.shape]
            val dw = bmp.width * cl.scale
            val dh = bmp.height * cl.scale
            val span = wf + dw * 2f
            val pos = (cl.x0 * span + cl.speed * wf * tf) % span
            val x = pos - dw + (0.5f - offsetX) * wf * 0.06f * cl.scale
            val y = cl.y * hf
            dst.set(x, y, x + dw, y + dh)
            paint.alpha = (baseAlpha * cl.alphaMul).toInt().coerceIn(0, 255)
            canvas.drawBitmap(bmp, null, dst, paint)
        }
    }
}
