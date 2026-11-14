package com.example.tvmask

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var tvOpacityValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnToggle = findViewById<Button>(R.id.btnToggleMask)
        val btnAdjust = findViewById<Button>(R.id.btnAdjustMask)
        val btnOpacityDown = findViewById<Button>(R.id.btnOpacityDown)
        val btnOpacityUp = findViewById<Button>(R.id.btnOpacityUp)
        tvOpacityValue = findViewById(R.id.tvOpacityValue)

        updateOpacityText()

        btnToggle.setOnClickListener {
            if (checkOverlayPermission()) {
                sendServiceCommand(TvMaskService.ACTION_TOGGLE)
            }
        }

        btnAdjust.setOnClickListener {
            if (checkOverlayPermission()) {
                sendServiceCommand(TvMaskService.ACTION_ADJUST)
            }
        }

        btnOpacityDown.setOnClickListener {
            changeOpacity(-15)
        }

        btnOpacityUp.setOnClickListener {
            changeOpacity(15)
        }
    }

    override fun onResume() {
        super.onResume()
        updateOpacityText()
    }

    private fun changeOpacity(delta: Int) {
        val prefs = getSharedPreferences("mask_prefs", Context.MODE_PRIVATE)
        val currentAlpha = prefs.getInt("alpha", 85)
        val newAlpha = (currentAlpha + delta).coerceIn(15, 245)
        prefs.edit().putInt("alpha", newAlpha).apply()
        updateOpacityText()

        if (TvMaskService.isRunning) {
            sendServiceCommand(TvMaskService.ACTION_REFRESH_OPACITY)
        }
    }

    private fun updateOpacityText() {
        val prefs = getSharedPreferences("mask_prefs", Context.MODE_PRIVATE)
        val alpha = prefs.getInt("alpha", 85)
        val percent = ((alpha / 255.0) * 100).roundToInt()
        tvOpacityValue.text = "遮罩濃度：$percent%"
    }

    private fun checkOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "請先開啟「顯示在其他應用程式上層」權限", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "此裝置需透過 ADB 指令開啟懸浮權限", Toast.LENGTH_LONG).show()
            }
            return false
        }
        return true
    }

    private fun sendServiceCommand(action: String) {
        val intent = Intent(this, TvMaskService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
