package com.wanderwk.d3saveeditor.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.wanderwk.d3saveeditor.BuildConfig
import java.security.MessageDigest

/**
 * Layer 2 of a two-layer signing-certificate check (see also the native
 * JNI_OnLoad check in `app/src/main/cpp/storage_sync.cpp`). Deliberately
 * decoupled from that native check in every way that matters: different
 * code path (typed `PackageManager` API here vs raw JNI reflection there),
 * different trigger timing (a few seconds after startup here vs
 * library-load time there), and a deliberately unrelated-looking name/
 * exception message -- so defeating one layer doesn't imply the other was
 * defeated too. Release builds only; see [com.wanderwk.d3saveeditor.App].
 */
object CacheWarmup {

    fun scheduleDeferredCheck(context: Context) {
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            if (!signatureLooksValid(appContext)) {
                throw RuntimeException("Unexpected application state")
            }
        }, 4000L)
    }

    private fun signatureLooksValid(context: Context): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            val signature = info.signatures?.firstOrNull() ?: return true
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            val hex = digest.take(8).joinToString("") { "%02x".format(it) }
            hex == BuildConfig.EXPECTED_SIG_HASH_HEX
        } catch (e: Exception) {
            true // fail open: an unexpected PackageManager error shouldn't brick a legit install
        }
    }
}
