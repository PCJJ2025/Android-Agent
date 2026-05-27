package com.floatagent.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.floatagent.R
import com.floatagent.model.SavedPlace

object FloatingResultCard {

    private var cardView: View? = null
    private var windowManager: WindowManager? = null

    fun showLoading(context: Context, msg: String = "正在分析...") {
        showCard(context) { layout ->
            layout.addView(TextView(context).apply {
                text = msg
                textSize = 16f
                gravity = Gravity.CENTER
            })
        }
    }

    fun showCollectionSaved(context: Context, place: SavedPlace) {
        showCollectionSaved(context, listOf(place))
    }

    fun showCollectionSaved(context: Context, places: List<SavedPlace>, duplicateCount: Int = 0) {
        if (places.isEmpty()) {
            showMessage(context, "没有新的场所被收藏")
            return
        }

        showCard(context) { layout ->
            layout.addView(TextView(context).apply {
                text = if (places.size == 1) "★ 收藏成功" else "★ 已收藏 ${places.size} 处"
                textSize = 13f
                setTextColor(0xFFFFD700.toInt())
            })

            if (places.size == 1) {
                val place = places.first()
                layout.addView(TextView(context).apply {
                    text = "${place.category.emoji} ${place.name}"
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 4, 0, 0)
                })

                if (place.address.isNotBlank()) {
                    layout.addView(TextView(context).apply {
                        text = "📍 ${place.address}"
                        textSize = 13f
                        setTextColor(0xFF666666.toInt())
                        setPadding(0, 4, 0, 0)
                    })
                }
                if (place.items.isNotEmpty()) {
                    layout.addView(TextView(context).apply {
                        text = "推荐：${place.items.take(3).joinToString("、")}"
                        textSize = 13f
                        setTextColor(0xFFE53935.toInt())
                        setPadding(0, 4, 0, 0)
                    })
                }
            } else {
                places.take(4).forEachIndexed { index, place ->
                    layout.addView(TextView(context).apply {
                        text = "${index + 1}. ${place.category.emoji} ${place.name}"
                        textSize = 15f
                        if (index == 0) setTypeface(null, android.graphics.Typeface.BOLD)
                        setPadding(0, 6, 0, 0)
                    })
                }

                if (places.size > 4) {
                    layout.addView(TextView(context).apply {
                        text = "另有 ${places.size - 4} 处已加入收藏"
                        textSize = 12f
                        setTextColor(0xFF666666.toInt())
                        setPadding(0, 6, 0, 0)
                    })
                }
            }

            if (duplicateCount > 0) {
                layout.addView(TextView(context).apply {
                    text = "已跳过 $duplicateCount 处重复收藏"
                    textSize = 12f
                    setTextColor(0xFF888888.toInt())
                    setPadding(0, 8, 0, 0)
                })
            }

            layout.addView(TextView(context).apply {
                text = "点击关闭"
                textSize = 11f
                setTextColor(0xFFBBBBBB.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 12, 0, 0)
            })
        }
        cardView?.postDelayed({ dismiss() }, 4000)
    }

    fun showMessage(context: Context, message: String) {
        showCard(context) { layout ->
            layout.addView(TextView(context).apply {
                text = message
                textSize = 15f
                gravity = Gravity.CENTER
            })
        }
        cardView?.postDelayed({ dismiss() }, 3000)
    }

    // 购物跳转卡片：显示识别到的商品 + 各平台按钮
    fun showShoppingOptions(
        context: Context,
        keyword: String,
        currentPrice: String,
        currentPlatform: String,
        onPlatformClick: (String) -> Unit
    ) {
        showCard(context) { layout ->

            // 标题
            layout.addView(TextView(context).apply {
                text = "识别到商品"
                textSize = 12f
                setTextColor(0xFF888888.toInt())
            })

            // 商品名
            layout.addView(TextView(context).apply {
                text = keyword
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 4, 0, 0)
            })

            // 当前价格
            if (currentPrice.isNotBlank()) {
                layout.addView(TextView(context).apply {
                    text = "当前价格：¥$currentPrice"
                    textSize = 13f
                    setTextColor(0xFFE53935.toInt())
                    setPadding(0, 4, 0, 0)
                })
            }

            // 分割线
            layout.addView(View(context).apply {
                setBackgroundColor(0xFFEEEEEE.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { setMargins(0, 16, 0, 12) }
            })

            // 提示文字
            layout.addView(TextView(context).apply {
                text = "去其他平台搜索比价："
                textSize = 12f
                setTextColor(0xFF666666.toInt())
                setPadding(0, 0, 0, 8)
            })

            // 平台按钮行
            val platforms = listOf("淘宝", "拼多多", "京东").filter { it != currentPlatform }
            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            platforms.forEach { platform ->
                val btn = Button(context).apply {
                    text = platform
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setBackgroundColor(platformColor(platform))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { setMargins(0, 0, if (platform != platforms.last()) 8 else 0, 0) }
                    setOnClickListener {
                        dismiss()
                        onPlatformClick(platform)
                    }
                }
                btnRow.addView(btn)
            }
            layout.addView(btnRow)

            // 关闭
            layout.addView(TextView(context).apply {
                text = "点击空白处关闭"
                textSize = 11f
                setTextColor(0xFFBBBBBB.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 12, 0, 0)
            })
        }
    }

    private fun platformColor(platform: String): Int = when (platform) {
        "淘宝"   -> 0xFFFF4400.toInt()
        "拼多多" -> 0xFFE02020.toInt()
        "京东"   -> 0xFFCC0000.toInt()
        else     -> 0xFF1A73E8.toInt()
    }

    private fun showCard(context: Context, builder: (LinearLayout) -> Unit) {
        dismiss()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_background)
            setPadding(48, 40, 48, 40)
            minimumWidth = 600
            builder(this)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 150
        }

        layout.setOnClickListener { dismiss() }
        wm.addView(layout, params)
        cardView = layout
    }

    fun dismiss() {
        cardView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        cardView = null
    }
}
