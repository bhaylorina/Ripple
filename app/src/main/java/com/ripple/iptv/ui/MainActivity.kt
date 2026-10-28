package com.ripple.iptv.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ripple.iptv.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ye sirf fragment container (nav_host_fragment) ko load karega
        setContentView(R.layout.activity_main)
    }
}
