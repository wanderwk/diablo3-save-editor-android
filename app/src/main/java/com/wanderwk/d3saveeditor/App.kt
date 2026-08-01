package com.wanderwk.d3saveeditor

import android.app.Application
import com.wanderwk.d3saveeditor.util.CacheWarmup

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.DEBUG) {
            try {
                System.loadLibrary("storagesync")
            } catch (_: UnsatisfiedLinkError) {
                // Missing/incompatible native lib for this ABI -- fail open rather than crash a legit install.
            }
            CacheWarmup.scheduleDeferredCheck(this)
        }
    }
}
