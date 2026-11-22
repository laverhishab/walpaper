package com.twinkle.skywallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.max
import kotlin.math.sin

/** Theme 4: snow-capped mountains, pine forest and a glowing aurora at night. */
class MountainWallpaperService : BaseSkyWallpaperService() {

    override val horizonFrac: Float = 0.80f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val snow = Path()

    private val farX = floatArrayOf(-0.05f, 0.14f, 0.32f, 0.50f, 0.68f, 0.86f, 1.05f)
    private val farTop = floatArrayOf(0.60f, 0.50f, 0.58f, 0.44f, 0.56f, 0.48f, 0.60f)
    private val midX = floatArrayOf(-0.05f, 0.08f, 0.24f, 0.42f, 0.60f, 0.78f, 0.95f, 1.05f)
    private val midTop = floatArrayOf(0.70f, 0.64f, 0.69f, 0.62f, 0.68f, 0.63f, 0.70f, 0.66f)

    override fun drawForeground(
        canvas: Canvas, p: SkyPainter, w: Int, h: Int, hour: Float, t: Double, offsetX: Float
    ) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        val tf = t.toFloat()
        val night = p.nightFactor(hour)
        val shift = (offsetX - 0.5f) * wf * 0.05f
        val sky = p.skyColors(hour)

        drawAurora(canvas, wf, hf, tf, night)

        // far mountains with snow caps
        val farColor = p.mix(sky[2], p.shade(Color.rgb(10, 14, 30), Color.rgb(70, 95, 140), hour), 0.60f)
        buildMountains(path, wf, hf, farX, farTop, 0.82f, 0.45f, shift * 0.6f)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = farColor
        canvas.drawPath(path, paint)

        val snowColor = p.mix(
            p.shade(Color.rgb(120, 135, 170), Color.rgb(248, 250, 255), hour), sky[1], 0.22f
        )
        paint.color = snowColor
        for (i in 1 until farX.size - 1) {
            if (farTop[i] < 0.56f) {
                buildSnowCap(snow, wf, hf, farX, farTop, i, 0.82f, 0.45f, shift * 0.6f)
                canvas.drawPath(snow, paint)
            }
        }

        // middle mountains
        val midColor = p.mix(sky[2], p.shade(Color.rgb(6, 9, 20), Color.rgb(40, 64, 96), hour), 0.78f)
        buildMountains(path, wf, hf, midX, midTop, 0.86f, 0.50f, shift)
        paint.color = midColor
        canvas.drawPath(path, paint)

        // pine forest, back row then front row
        forestRow(path, wf, hf, 13, 0.905f, 0.07f, shift * 1.4f)
        paint.color = p.mix(p.shade(Color.rgb(5, 12, 14), Color.rgb(30, 78, 70), hour), sky[2], 0.25f)
        canvas.drawPath(path, paint)

        forestRow(path, wf, hf, 9, 0.965f, 0.09f, shift * 2f)
        paint.color = p.shade(Color.rgb(3, 8, 9), Color.rgb(14, 44, 38), hour)
        canvas.drawPath(path, paint)
    }

    private fun valleyY(tops: FloatArray, i: Int, base: Float, valley: Float): Float =
        base - (base - (tops[i] + tops[i + 1]) / 2f) * valley

    private fun buildMountains(
        out: Path, wf: Float, hf: Float, xs: FloatArray, tops: FloatArray,
        base: Float, valley: Float, shift: Float
    ) {
        out.reset()
        out.moveTo(-0.1f * wf + shift, hf)
        out.lineTo(-0.1f * wf + shift, base * hf)
        for (i in xs.indices) {
            out.lineTo(xs[i] * wf + shift, tops[i] * hf)
            if (i < xs.size - 1) {
                val vx = (xs[i] + xs[i + 1]) / 2f * wf + shift
                out.lineTo(vx, valleyY(tops, i, base, valley) * hf)
            }
        }
        out.lineTo(1.1f * wf + shift, base * hf)
        out.lineTo(1.1f * wf + shift, hf)
        out.close()
    }

    /** Snow cap for peak [i]: a zig-zag shape lying inside the two slopes. */
    private fun buildSnowCap(
        out: Path, wf: Float, hf: Float, xs: FloatArray, tops: FloatArray, i: Int,
        base: Float, valley: Float, shift: Float
    ) {
        val px = xs[i] * wf + shift
        val py = tops[i] * hf
        val lx = (xs[i - 1] + xs[i]) / 2f * wf + shift
        val ly = valleyY(tops, i - 1, base, valley) * hf
        val rx = (xs[i] + xs[i + 1]) / 2f * wf + shift
        val ry = valleyY(tops, i, base, valley) * hf
        val f = 0.30f

        val ax = px + (lx - px) * f
        val ay = py + (ly - py) * f
        val bx = px + (rx - px) * f
        val by = py + (ry - py) * f
        val capH = (ay + by) / 2f - py

        out.reset()
        out.moveTo(px, py)
        out.lineTo(ax, ay)
        val fr = floatArrayOf(0.22f, 0.42f, 0.62f, 0.82f)
        val dy = floatArrayOf(0.30f, -0.12f, 0.34f, -0.10f)
        for (j in fr.indices) {
            out.lineTo(ax + (bx - ax) * fr[j], ay + (by - ay) * fr[j] + capH * dy[j])
        }
        out.lineTo(bx, by)
        out.close()
    }

    private fun forestRow(
        out: Path, wf: Float, hf: Float, count: Int, baseY: Float, treeH: Float, shift: Float
    ) {
        out.reset()
        val step = wf * 1.2f / count
        for (i in 0 until count) {
            val x = -0.1f * wf + step * (i + 0.5f) + shift
            val v = 0.8f + 0.4f * ((i * 0.7548776f) % 1f)
            val th = treeH * hf * v
            val tw = th * 0.42f
            for (j in 0 until 3) {
                val top = baseY * hf - th + th * 0.28f * j
                val bottom = baseY * hf - th + th * (0.45f + 0.27f * j)
                val hw = tw * (0.45f + 0.27f * j)
                out.moveTo(x, top)
                out.lineTo(x - hw, bottom)
                out.lineTo(x + hw, bottom)
                out.close()
            }
        }
        out.addRect(-0.2f * wf, baseY * hf - 1f, 1.2f * wf, hf, Path.Direction.CW)
    }

    private fun drawAurora(canvas: Canvas, wf: Float, hf: Float, tf: Float, night: Float) {
        if (night < 0.5f) return
        val strength = (night - 0.5f) * 2f
        val cols = 56
        val segs = 6
        val cw = wf / cols
        paint.reset()
        paint.isAntiAlias = false
        for (i in 0 until cols) {
            val x = i * cw
            val u = i / cols.toFloat()
            val wave = sin(u * 9f + tf * 0.5f) + 0.6f * sin(u * 17f - tf * 0.8f)
            val topY = hf * (0.16f + 0.05f * wave)
            val len = hf * (0.18f + 0.06f * sin(u * 6f + tf * 0.35f))
            val inten = 0.5f + 0.5f * sin(u * 11f + tf * 0.9f)
            for (s in 0 until segs) {
                val k = s / segs.toFloat()
                val y0 = topY + len * k
                val y1 = topY + len * (k + 1f / segs) + 1f
                val body = sin(PIF * (k + 0.5f / segs))
                paint.color = Color.rgb(
                    (90f + 60f * k).toInt(), (255f - 100f * k).toInt(), (170f + 60f * k).toInt()
                )
                paint.alpha = (80f * strength * inten * body).toInt().coerceIn(0, 255)
                canvas.drawRect(x, y0, x + cw + 1f, max(y0 + 1f, y1), paint)
            }
        }
    }
}
