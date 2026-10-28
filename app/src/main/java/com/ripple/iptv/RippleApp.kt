package com.ripple.iptv

import android.app.Application

class RippleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Future me agar ExoPlayer ka global cache ya crash analytics dalna ho toh yahan aayega
    }
}
