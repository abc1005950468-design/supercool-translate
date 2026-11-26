package com.mine.offlinetranslate.core

import android.content.Context

/** Mainland phones usually ship without Google Play services. */
object Gms {
    fun available(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo("com.google.android.gms", 0)
        true
    }.getOrDefault(false)
}
