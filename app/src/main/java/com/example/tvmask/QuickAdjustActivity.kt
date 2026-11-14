package com.example.tvmask

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings

class QuickAdjustActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            val intent = Intent(this, TvMaskService::class.java).apply {
                action = TvMaskService.ACTION_ADJUST
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        finish()
    }
}
