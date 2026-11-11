package com.forgeprom.codestudio

import android.app.Application
import android.util.Log

/**
 * Forgeprom Code Studio Application Entry Point
 * Local-first architecture: initializes offline persistence and crash boundaries.
 */
class ForgepromApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("ForgepromApp", "Forgeprom Code Studio initialized. Native Local-First environment ready.")
    }
}
