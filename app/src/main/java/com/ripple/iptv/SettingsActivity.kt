package com.ripple.iptv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ripple.iptv.core.BackupHelper

class SettingsActivity : AppCompatActivity() {

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> BackupHelper.backupData(this, uri) }
        }
    }

    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                if (BackupHelper.restoreData(this, uri)) {
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(layout)
        }

        // Title
        val title = TextView(this).apply {
            text = "Video Quality & Bitrate"
            textSize = 22f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, 30, 0, 30)
        }

        val rbAuto = RadioButton(this).apply {
            text = "Auto Quality (Adaptive)"
            textSize = 18f
            setTextColor(Color.WHITE)
        }

        val rbHighest = RadioButton(this).apply {
            text = "Force Highest Bitrate (Best Quality)"
            textSize = 18f
            setTextColor(Color.WHITE)
        }

        radioGroup.addView(rbAuto)
        radioGroup.addView(rbHighest)
        layout.addView(radioGroup)

        val btnSave = Button(this).apply {
            text = "Save Settings"
            setBackgroundColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
        }
        layout.addView(btnSave)

        // Backup & Restore Section
        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 60) }
        layout.addView(spacer)

        val backupTitle = TextView(this).apply {
            text = "Backup & Restore"
            textSize = 22f
            setTextColor(Color.CYAN)
            gravity = Gravity.CENTER
        }
        layout.addView(backupTitle)

        val btnBackup = Button(this).apply {
            text = "Backup Settings"
            setBackgroundColor(Color.parseColor("#2C3E50"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 20, 0, 0) }
        }
        layout.addView(btnBackup)

        val btnRestore = Button(this).apply {
            text = "Restore Settings"
            setBackgroundColor(Color.parseColor("#2C3E50"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 20, 0, 0) }
        }
        layout.addView(btnRestore)

        setContentView(scrollView)

        val forceHighest = prefs.getBoolean("force_highest_bitrate", false)
        if (forceHighest) rbHighest.isChecked = true else rbAuto.isChecked = true

        btnSave.setOnClickListener {
            val highest = rbHighest.isChecked
            prefs.edit().putBoolean("force_highest_bitrate", highest).apply()
            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
        }

        btnBackup.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "ripple_backup_${System.currentTimeMillis()}.json")
            }
            backupLauncher.launch(intent)
        }

        btnRestore.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            restoreLauncher.launch(intent)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }
}
