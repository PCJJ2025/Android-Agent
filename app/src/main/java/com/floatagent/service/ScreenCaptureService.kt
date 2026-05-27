package com.floatagent.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream

object ScreenCaptureService {

    private const val TAG = "FloatAgent_Screenshot"
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    // 在 MainActivity 拿到用户授权后调用
    fun initialize(context: Context, resultCode: Int, data: Intent) {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data)
        Log.d(TAG, "MediaProjection 初始化成功")
    }

    fun isReady() = mediaProjection != null

    // 截取当前屏幕，返回 base64 编码的 JPEG 图片
    fun captureScreen(context: Context): String? {
        val mp = mediaProjection ?: run {
            Log.w(TAG, "MediaProjection 未初始化，跳过截图")
            return null
        }

        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            virtualDisplay = mp.createVirtualDisplay(
                "FloatAgentCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null
            )

            // 等待一帧
            Thread.sleep(300)

            val image = reader.acquireLatestImage() ?: run {
                Log.w(TAG, "获取图像帧失败")
                return null
            }

            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            // 缩小到一半分辨率减少传输大小
            val scaled = Bitmap.createScaledBitmap(bitmap, width / 2, height / 2, true)
            bitmap.recycle()

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            scaled.recycle()

            val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            Log.d(TAG, "截图成功，大小: ${out.size() / 1024}KB")

            cleanup()
            base64
        } catch (e: Exception) {
            Log.e(TAG, "截图失败: ${e.message}")
            cleanup()
            null
        }
    }

    private fun cleanup() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }
}
