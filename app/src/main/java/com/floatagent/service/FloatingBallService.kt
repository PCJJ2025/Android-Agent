package com.floatagent.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.floatagent.R
import com.floatagent.agent.AgentOrchestrator
import kotlin.math.abs

class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var starView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var starParams: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    companion object {
        const val CHANNEL_ID = "float_agent_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupFloatingBall()
    }

    private fun startForegroundWithNotification() {
        // 创建通知渠道（Android 8+）
        val channel = NotificationChannel(
            CHANNEL_ID, "FloatAgent 运行中",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "悬浮球正在运行" }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FloatAgent 运行中")
            .setContentText("点击悬浮球可识别当前页面")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Android 14+ 必须声明 mediaProjection 类型才能截图
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupFloatingBall() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)

        params = WindowManager.LayoutParams(
            80.dp, 80.dp,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        windowManager.addView(floatingView, params)
        setupStarButton()

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 5 || abs(dy) > 5) isDragging = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onBallClicked()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupStarButton() {
        starView = LayoutInflater.from(this).inflate(R.layout.floating_star, null)
        starParams = WindowManager.LayoutParams(
            70.dp, 70.dp,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 400  // 在蓝色圆点下方
        }
        windowManager.addView(starView, starParams)
        starView.setOnClickListener { onStarClicked() }
    }

    private fun onStarClicked() {
        val screenData = com.floatagent.service.ScreenReaderService.instance
            ?.getCurrentScreenData() ?: return
        com.floatagent.agent.CollectionAgent.collect(this, screenData)
    }

    private fun onBallClicked() {
        val ball = floatingView.findViewById<ImageView>(R.id.ivBall)
        ball.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).withEndAction {
            ball.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }.start()
        AgentOrchestrator.analyze(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        if (::starView.isInitialized) windowManager.removeView(starView)
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
