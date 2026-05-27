package com.floatagent.agent

import android.util.Log
import com.floatagent.BuildConfig
import com.floatagent.model.SavedPlace
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object FoodRecommendAgent {

    private const val TAG = "FloatAgent_Recommend"
    private val API_KEY = BuildConfig.ANTHROPIC_API_KEY
    private const val API_URL = "https://api.anthropic.com/v1/messages"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class Match(val place: SavedPlace, val reason: String)

    suspend fun recommend(craving: String, places: List<SavedPlace>): List<Match> {
        if (places.isEmpty()) return emptyList()

        val placesDesc = places.joinToString("\n") { p ->
            val items = if (p.items.isNotEmpty()) "推荐：${p.items.joinToString("、")}" else ""
            "${p.category.emoji}${p.name}（${p.category.label}）$items ${p.note}".trim()
        }

        val prompt = """
            用户现在想吃/喝/体验：$craving

            以下是用户收藏的店铺列表：
            $placesDesc

            请从收藏列表中挑出最符合用户需求的店铺（最多 3 家，按匹配度从高到低排序）。
            返回严格 JSON，不要有其他文字：
            {
              "matches": [
                {
                  "placeName": "店铺名称（必须完全来自上面的列表）",
                  "reason": "一句话说明为什么推荐这家，要结合用户想要的"
                }
              ]
            }

            规则：
            1. placeName 必须和列表中的名称完全一致，不要编造。
            2. 如果没有任何店铺符合，matches 返回空数组。
            3. 优先匹配店铺的推荐菜品/服务，其次是店铺类型和特色。
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", "claude-sonnet-4-6")
            put("max_tokens", 500)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }.toString()

        return try {
            val request = Request.Builder()
                .url(API_URL)
                .header("x-api-key", API_KEY)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: "{}")
            val text = json.getJSONArray("content").getJSONObject(0).getString("text")
            Log.d(TAG, "推荐结果: $text")

            val cleanText = text.trim()
                .removePrefix("```json").removePrefix("```")
                .trimStart()
                .let { if (it.endsWith("```")) it.dropLast(3).trimEnd() else it }
            val result = JSONObject(cleanText)
            val matchesArr = result.optJSONArray("matches") ?: JSONArray()

            (0 until matchesArr.length()).mapNotNull { i ->
                val m = matchesArr.getJSONObject(i)
                val name = m.optString("placeName")
                val place = places.firstOrNull { it.name == name }
                    ?: places.firstOrNull { it.name.contains(name) || name.contains(it.name) }
                place?.let { Match(it, m.optString("reason")) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "推荐失败: ${e.message}")
            emptyList()
        }
    }
}
