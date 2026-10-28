package com.ripple.iptv

import android.app.Application

class RippleApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // App start hote hi jo cheezein load karni hain, wo hum yahan likhenge.
        // Jaise ExoPlayer ka cache ya database initialization.
    }
}

