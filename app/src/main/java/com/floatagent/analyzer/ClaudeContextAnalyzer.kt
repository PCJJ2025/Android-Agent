package com.floatagent.analyzer

import android.content.Context
import android.util.Log
import com.floatagent.BuildConfig
import com.floatagent.model.AgentIntent
import com.floatagent.model.Scene
import com.floatagent.model.ScreenData
import com.floatagent.service.ScreenCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ClaudeContextAnalyzer {

    private const val TAG = "FloatAgent_Analyzer"
    private val API_KEY = BuildConfig.ANTHROPIC_API_KEY
    private const val API_URL = "https://api.anthropic.com/v1/messages"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(context: Context, screenData: ScreenData): AgentIntent = withContext(Dispatchers.IO) {
        // 本地规则判断场景
        val localIntent = localAnalyze(screenData)

        // 购物场景：始终用 Claude（文字+截图）提取准确商品名
        if (localIntent.scene == Scene.SHOPPING) {
            Log.d(TAG, "购物场景，调用 Claude Vision 提取准确商品名")
            return@withContext claudeAnalyze(context, screenData, localIntent)
        }

        // 其他已识别场景直接返回
        if (localIntent.scene != Scene.UNKNOWN) return@withContext localIntent

        // 未知场景也用 Claude 判断
        return@withContext claudeAnalyze(context, screenData, null)
    }

    // 本地关键词快速匹配（零延迟）
    private fun localAnalyze(screenData: ScreenData): AgentIntent {
        val pkg = screenData.packageName
        val texts = screenData.allTexts.joinToString(" ")

        val isBrowser = pkg in listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.android.browser",
            "com.sec.android.app.sbrowser"
        )

        val shoppingApps = listOf("com.taobao.taobao", "com.jingdong.app.mall", "com.xunmeng.pinduoduo")
        val restaurantApps = listOf("com.xingin.xhs", "com.zhiliaoapp.musically", "com.douyin.discover")

        // 购物场景关键词
        val shoppingKeywords = Regex("¥|价格|加入购物车|立即购买|天猫|淘宝|京东|拼多多|购物车|收藏|店铺")
        // 餐厅场景关键词
        val restaurantKeywords = Regex("餐厅|好吃|推荐|人均|地址|营业|探店|美食|菜单|口味")

        return when {
            // 购物场景：App 或浏览器都支持
            (pkg in shoppingApps || (isBrowser && texts.contains(Regex("taobao|jd\\.com|pinduoduo|天猫|淘宝|京东"))))
                && texts.contains(shoppingKeywords) -> {
                val price = Regex("[¥￥]([\\d.]+)").find(texts)?.groupValues?.get(1) ?: ""
                val keyword = extractProductName(screenData.allTexts)
                AgentIntent(Scene.SHOPPING, keyword, mapOf("currentPrice" to price, "packageName" to pkg))
            }
            // 餐厅场景：App 或浏览器都支持
            (pkg in restaurantApps || (isBrowser && texts.contains(Regex("小红书|xiaohongshu|抖音"))))
                && texts.contains(restaurantKeywords) -> {
                val keyword = extractRestaurantName(screenData.allTexts)
                AgentIntent(Scene.RESTAURANT, keyword)
            }
            else -> AgentIntent(Scene.UNKNOWN, "")
        }
    }

    // Claude API 分析：文字 + 截图（如果有）
    private suspend fun claudeAnalyze(
        context: Context,
        screenData: ScreenData,
        localHint: AgentIntent?
    ): AgentIntent {
        val sceneHint = if (localHint?.scene == Scene.SHOPPING) "（已判断为购物场景，请重点提取准确的商品名称）" else ""
        val prompt = """
            用户手机当前屏幕内容如下$sceneHint：
            App包名：${screenData.packageName}
            屏幕文字：
            ${screenData.toPromptText()}

            请分析用户正在看什么，返回严格 JSON，不要有其他文字：
            {
              "scene": "SHOPPING 或 RESTAURANT 或 VIDEO 或 UNKNOWN",
              "keyword": "完整的商品名称 或 餐厅名（从屏幕文字或图片中提取，要求完整、具体，不要广告语）",
              "currentPrice": "当前价格数字，没有则空字符串",
              "reason": "一句话说明判断依据"
            }
        """.trimIndent()

        // 尝试截图，加入 vision 分析
        val screenshotBase64 = ScreenCaptureService.captureScreen(context)

        val contentArray = JSONArray()

        // 如果有截图，先放图片
        if (screenshotBase64 != null) {
            Log.d(TAG, "截图成功，使用 Vision 模式分析")
            contentArray.put(JSONObject().apply {
                put("type", "image")
                put("source", JSONObject().apply {
                    put("type", "base64")
                    put("media_type", "image/jpeg")
                    put("data", screenshotBase64)
                })
            })
        } else {
            Log.d(TAG, "无截图，仅使用文字分析")
        }

        // 再放文字
        contentArray.put(JSONObject().apply {
            put("type", "text")
            put("text", prompt)
        })

        val body = JSONObject().apply {
            put("model", "claude-sonnet-4-6")
            put("max_tokens", 300)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", contentArray)
                })
            })
        }.toString()

        val request = Request.Builder()
            .url(API_URL)
            .header("x-api-key", API_KEY)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val rawBody = response.body?.string() ?: "{}"
            Log.d(TAG, "Claude 原始响应: $rawBody")
            val json = JSONObject(rawBody)
            val content = json.getJSONArray("content")
                .getJSONObject(0).getString("text")
            Log.d(TAG, "Claude 返回文本: $content")
            val result = parseClaudeResponse(content)
            // Claude 返回 UNKNOWN 但本地判断是购物场景时，用本地提取兜底
            if (result.scene == Scene.UNKNOWN && localHint?.scene == Scene.SHOPPING) {
                Log.w(TAG, "Claude 返回 UNKNOWN，使用本地提取兜底")
                localHint
            } else {
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Claude API 调用异常: ${e.message}")
            // 异常时用本地结果兜底
            localHint ?: AgentIntent(Scene.UNKNOWN, "")
        }
    }

    private fun parseClaudeResponse(text: String): AgentIntent {
        return try {
            val json = JSONObject(text.trim())
            val scene = when (json.optString("scene")) {
                "SHOPPING"   -> Scene.SHOPPING
                "RESTAURANT" -> Scene.RESTAURANT
                "VIDEO"      -> Scene.VIDEO
                else         -> Scene.UNKNOWN
            }
            AgentIntent(
                scene = scene,
                keyword = json.optString("keyword"),
                extraInfo = mapOf("currentPrice" to json.optString("currentPrice"))
            )
        } catch (e: Exception) {
            AgentIntent(Scene.UNKNOWN, "")
        }
    }

    private fun extractProductName(texts: List<String>): String {
        val blacklist = Regex(
            "商品图片|图片|返回|搜索|购物车|首页|分享|客服|更多|关注|收藏|加入|立即|结算|" +
            "继续滑动|查看图文|详情|评价|规格|参数|配送|售后|活动|优惠|领券|" +
            "全网低价|相关搜索|选购指南|智能体验|已选中|进店|开启声音|" +
            "^\\d+$|^\\d+\\.\\d+$"
        )
        // 优先找 10~150 字符、包含品牌/型号特征的商品名
        return texts.firstOrNull {
            it.length in 10..150
            && !it.contains('\n')
            && !it.contains(blacklist)
            && !it.contains(Regex("^¥|^￥|^约|^AU\\$"))
            && !it.contains(Regex("^\\d+/\\d+$"))   // 过滤 "1/10" 类翻页文字
        } ?: ""
    }

    private fun extractRestaurantName(texts: List<String>): String =
        texts.firstOrNull { it.length in 2..20 && it.contains(Regex("餐|饭|馆|店|咖啡|火锅|烤")) } ?: ""
}
