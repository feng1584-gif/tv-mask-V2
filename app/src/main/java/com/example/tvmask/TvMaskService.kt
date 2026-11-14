package com.example.tvmask

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt

class TvMaskService : Service() {

    companion object {
        const val ACTION_TOGGLE = "ACTION_TOGGLE"
        const val ACTION_ADJUST = "ACTION_ADJUST"
        const val ACTION_REFRESH_OPACITY = "ACTION_REFRESH_OPACITY"
        private const val CHANNEL_ID = "tv_mask_channel"
        var isRunning = false
    }

    private lateinit var windowManager: WindowManager
    private var maskView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    // 0 = 觀看穿透模式, 1 = 移動位置(藍框), 2 = 調整大小(黃框), 3 = 調整透明度(綠框)
    private var adjustMode = 0
    private var currentAlpha = 85

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                if (maskView == null) {
                    createMask()
                } else {
                    removeMask()
                    stopSelf()
                }
            }
            ACTION_ADJUST -> {
                if (maskView == null) createMask()
                setMode(1)
            }
            ACTION_REFRESH_OPACITY -> {
                val prefs = getSharedPreferences("mask_prefs", Context.MODE_PRIVATE)
                currentAlpha = prefs.getInt("alpha", 85)
                if (maskView != null) {
                    updateMaskAppearance(adjustMode)
                    windowManager.updateViewLayout(maskView, params)
                }
            }
        }
        return START_STICKY
    }

    private fun createMask() {
        val prefs = getSharedPreferences("mask_prefs", Context.MODE_PRIVATE)
        currentAlpha = prefs.getInt("alpha", 85)

        maskView = object : View(this) {
            override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
                if (adjustMode == 0) return super.onKeyDown(keyCode, event)
                return handleRemoteKey(keyCode)
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }

        params = WindowManager.LayoutParams(
            prefs.getInt("width", 900),
            prefs.getInt("height", 130),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", 510)
            y = prefs.getInt("y", 860)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.blurBehindRadius = 25
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }

        updateMaskAppearance(0)
        windowManager.addView(maskView, params)
    }

    private fun handleRemoteKey(keyCode: Int): Boolean {
        val step = 20
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                when (adjustMode) {
                    1 -> params.y -= step
                    2 -> params.height = maxOf(50, params.height - step)
                    3 -> updateAlphaInAdjust(15)
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                when (adjustMode) {
                    1 -> params.y += step
                    2 -> params.height += step
                    3 -> updateAlphaInAdjust(-15)
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                when (adjustMode) {
                    1 -> params.x -= step
                    2 -> params.width = maxOf(150, params.width - step)
                    3 -> updateAlphaInAdjust(-15)
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                when (adjustMode) {
                    1 -> params.x += step
                    2 -> params.width += step
                    3 -> updateAlphaInAdjust(15)
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                val nextMode = when (adjustMode) {
                    1 -> 2
                    2 -> 3
                    else -> 0
                }
                setMode(nextMode)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                setMode(0)
                return true
            }
            else -> return false
        }
        windowManager.updateViewLayout(maskView, params)
        return true
    }

    private fun updateAlphaInAdjust(delta: Int) {
        currentAlpha = (currentAlpha + delta).coerceIn(15, 245)
        updateMaskAppearance(3)
        val percent = ((currentAlpha / 255.0) * 100).roundToInt()
        Toast.makeText(this, "遮罩濃度：$percent%", Toast.LENGTH_SHORT).show()
    }

    private fun setMode(mode: Int) {
        adjustMode = mode
        updateMaskAppearance(mode)

        when (mode) {
            1 -> Toast.makeText(this, "1/3 移動位置（方向鍵移動，按 OK 下一步）", Toast.LENGTH_SHORT).show()
            2 -> Toast.makeText(this, "2/3 調整大小（方向鍵調寬高，按 OK 下一步）", Toast.LENGTH_SHORT).show()
            3 -> {
                val percent = ((currentAlpha / 255.0) * 100).roundToInt()
                Toast.makeText(this, "3/3 透明度（左右鍵調濃淡：$percent%，按 OK 完成）", Toast.LENGTH_SHORT).show()
            }
            0 -> Toast.makeText(this, "已鎖定遮罩設定", Toast.LENGTH_SHORT).show()
        }

        if (mode == 0) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            savePreferences()
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        }
        windowManager.updateViewLayout(maskView, params)
        if (mode != 0) maskView?.requestFocus()
    }

    private fun updateMaskAppearance(mode: Int) {
        val drawable = GradientDrawable().apply {
            cornerRadius = 16f
            setColor(Color.argb(currentAlpha, 255, 255, 255))
            when (mode) {
                1 -> setStroke(5, Color.CYAN)
                2 -> setStroke(5, Color.YELLOW)
                3 -> setStroke(5, Color.GREEN)
                else -> setStroke(2, Color.argb(120, 255, 255, 255))
            }
        }
        maskView?.background = drawable
    }

    private fun savePreferences() {
        getSharedPreferences("mask_prefs", Context.MODE_PRIVATE).edit().apply {
            putInt("x", params.x)
            putInt("y", params.y)
            putInt("width", params.width)
            putInt("height", params.height)
            putInt("alpha", currentAlpha)
            apply()
        }
    }

    private fun removeMask() {
        maskView?.let {
            windowManager.removeView(it)
            maskView = null
        }
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "TV 字幕遮罩服務", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TV 字幕遮罩運行中")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        isRunning = false
        removeMask()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
