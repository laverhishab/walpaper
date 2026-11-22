package com.twinkle.skywallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.os.Bundle

/** Tapping the app icon opens the system live wallpaper list, where all four themes appear. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (e: Exception) {
            // no chooser available on this device
        }
        finish()
    }
}
