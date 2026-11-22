package com.twinkle.skywallpaper

import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import kotlin.math.max

private const val FRAME_MS = 40L // about 25 frames per second while the screen is visible

/**
 * Common base for all themes. It owns the animation loop and the shared sky;
 * a theme only decides where the horizon is and what to draw in front of the sky.
 */
abstract class BaseSkyWallpaperService : WallpaperService() {

    /** Where the sun and moon rise and set (fraction of screen height). */
    abstract val horizonFrac: Float

    /** Draw the theme's own scenery on top of the sky. */
    abstract fun drawForeground(
        canvas: Canvas, p: SkyPainter, w: Int, h: Int, hour: Float, t: Double, offsetX: Float
    )

    override fun onCreateEngine(): Engine = SceneEngine()

    private inner class SceneEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val painter = SkyPainter()
        private val startMs = SystemClock.uptimeMillis()
        private var visible = false
        private var offsetX = 0.5f

        init {
            painter.horizonFrac = horizonFrac
        }

        private val runner = object : Runnable {
            override fun run() {
                val begin = SystemClock.uptimeMillis()
                drawFrame()
                handler.removeCallbacks(this)
                if (visible) {
                    val spent = SystemClock.uptimeMillis() - begin
                    handler.postDelayed(this, max(1L, FRAME_MS - spent))
                }
            }
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            handler.removeCallbacks(runner)
            if (v) handler.post(runner)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(runner)
        }

        override fun onDestroy() {
            super.onDestroy()
            visible = false
            handler.removeCallbacks(runner)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int
        ) {
            offsetX = xOffset
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val w = canvas.width
                    val h = canvas.height
                    if (w > 0 && h > 0) {
                        val hour = painter.currentHour()
                        val t = (SystemClock.uptimeMillis() - startMs) / 1000.0
                        painter.draw(canvas, w, h, hour, t, offsetX)
                        drawForeground(canvas, painter, w, h, hour, t, offsetX)
                    }
                }
            } catch (e: Exception) {
                Log.e("SkyWallpaper", "draw failed", e)
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e("SkyWallpaper", "unlock failed", e)
                    }
                }
            }
        }
    }
}
