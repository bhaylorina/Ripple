package com.ripple.iptv.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ripple.iptv.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ye aapki activity_main.xml layout ko load karega.
        // Agle step mein hum activity_main.xml ko clean karke Fragment Container banayenge.
        setContentView(R.layout.activity_main)
    }
}
