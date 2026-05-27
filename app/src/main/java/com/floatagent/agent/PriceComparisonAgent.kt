package com.floatagent.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.floatagent.model.AgentIntent
import com.floatagent.ui.FloatingResultCard
import java.net.URLEncoder

class PriceComparisonAgent : BaseAgent() {

    private val TAG = "FloatAgent_PriceAgent"

    override suspend fun execute(context: Context, intent: AgentIntent) {
        val keyword = intent.keyword

        if (keyword.isBlank()) {
            Log.w(TAG, "商品名提取失败，keyword 为空")
            FloatingResultCard.showMessage(context, "未能识别商品名，请在商品详情页重试")
            return
        }

        Log.d(TAG, "识别到商品名: $keyword，准备展示跳转选项")

        FloatingResultCard.showShoppingOptions(
            context = context,
            keyword = keyword,
            currentPrice = intent.extraInfo["currentPrice"] ?: "",
            currentPlatform = detectPlatform(intent.extraInfo["packageName"] ?: ""),
            onPlatformClick = { platform -> openPlatform(context, platform, keyword) }
        )
    }

    private fun detectPlatform(pkg: String): String = when (pkg) {
        "com.jingdong.app.mall" -> "京东"
        "com.taobao.taobao"     -> "淘宝"
        "com.xunmeng.pinduoduo" -> "拼多多"
        else -> ""
    }

    private fun openPlatform(context: Context, platform: String, keyword: String) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        Log.d(TAG, "跳转到 $platform 搜索: $keyword")

        val deepLink = when (platform) {
            "淘宝"   -> "taobao://s.taobao.com/search?q=$encoded"
            "拼多多" -> "pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key=$encoded"
            "京东"   -> "openapp.jdmobile://virtual?params={\"category\":\"jump\",\"des\":\"search\",\"keyWord\":\"$keyword\"}"
            "微信"   -> "weixin://dl/search?s=$encoded"
            else     -> return
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // App 未安装，降级到网页版
            val webUrl = when (platform) {
                "淘宝"   -> "https://s.taobao.com/search?q=$encoded"
                "拼多多" -> "https://mobile.yangkeduo.com/search_result.html?search_key=$encoded"
                "京东"   -> "https://search.jd.com/Search?keyword=$encoded"
                else     -> return
            }
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
