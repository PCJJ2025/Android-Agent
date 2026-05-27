package com.floatagent.analyzer

import android.util.Log
import com.floatagent.model.SavedPlace
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object GaodeSearcher {

    private const val TAG = "FloatAgent_Gaode"
    // 替换为你的高德 Web API Key
    internal const val GAODE_KEY = "30da1506315399017b59f0d0c41a3dad"

    private val client = OkHttpClient()

    // 搜索场所坐标
    fun searchCoordinate(place: SavedPlace): SavedPlace {
        if (GAODE_KEY == "YOUR_GAODE_API_KEY") return place
        return try {
            val query = URLEncoder.encode("${place.name} ${place.address}", "UTF-8")
            val url = "https://restapi.amap.com/v3/place/text?keywords=$query&key=$GAODE_KEY&output=json"
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val json = JSONObject(response.body?.string() ?: "{}")
            val pois = json.getJSONArray("pois")
            if (pois.length() == 0) return place
            val first = pois.getJSONObject(0)
            val location = first.getString("location").split(",")
            val address = first.optString("address", place.address)
            Log.d(TAG, "高德搜索 ${place.name} → ${location[1]},${location[0]}")
            place.copy(
                lat = location[1].toDouble(),
                lng = location[0].toDouble(),
                address = address
            )
        } catch (e: Exception) {
            Log.e(TAG, "高德搜索失败: ${e.message}")
            place
        }
    }

    // 行程规划：返回各段路线信息
    fun planRoute(
        currentLat: Double,
        currentLng: Double,
        destinations: List<SavedPlace>
    ): List<RouteSegment> {
        if (GAODE_KEY == "YOUR_GAODE_API_KEY") return emptyList()
        val segments = mutableListOf<RouteSegment>()
        var fromLat = currentLat
        var fromLng = currentLng
        var fromName = "当前位置"

        for (dest in destinations) {
            if (dest.lat == 0.0 && dest.lng == 0.0) continue
            try {
                val url = "https://restapi.amap.com/v3/direction/driving?" +
                    "origin=$fromLng,$fromLat&" +
                    "destination=${dest.lng},${dest.lat}&" +
                    "key=$GAODE_KEY&output=json"
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                val json = JSONObject(response.body?.string() ?: "{}")
                val route = json.getJSONObject("route")
                    .getJSONArray("paths").getJSONObject(0)
                val durationMin = route.getLong("duration") / 60
                val distanceKm = route.getLong("distance") / 1000.0

                segments.add(RouteSegment(
                    from = fromName,
                    to = dest.name,
                    durationMin = durationMin,
                    distanceKm = distanceKm
                ))

                fromLat = dest.lat
                fromLng = dest.lng
                fromName = dest.name
            } catch (e: Exception) {
                Log.e(TAG, "路线规划失败: ${e.message}")
            }
        }
        return segments
    }

    // 天气预报：先把坐标反查成 adcode，再查未来几天天气（高德免费预报为 4 天）
    fun fetchWeather(lat: Double, lng: Double): List<DayWeather> {
        if (GAODE_KEY == "YOUR_GAODE_API_KEY") return emptyList()
        return try {
            val adcode = regeoAdcode(lat, lng) ?: "110000"
            val url = "https://restapi.amap.com/v3/weather/weatherInfo?" +
                "city=$adcode&extensions=all&key=$GAODE_KEY&output=json"
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val json = JSONObject(response.body?.string() ?: "{}")
            val forecasts = json.optJSONArray("forecasts") ?: return emptyList()
            if (forecasts.length() == 0) return emptyList()
            val casts = forecasts.getJSONObject(0).optJSONArray("casts") ?: return emptyList()
            (0 until casts.length()).map {
                val c = casts.getJSONObject(it)
                DayWeather(
                    date = c.optString("date"),
                    week = c.optString("week"),
                    dayWeather = c.optString("dayweather"),
                    nightWeather = c.optString("nightweather"),
                    dayTemp = c.optString("daytemp"),
                    nightTemp = c.optString("nighttemp")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "天气查询失败: ${e.message}")
            emptyList()
        }
    }

    private fun regeoAdcode(lat: Double, lng: Double): String? {
        return try {
            val url = "https://restapi.amap.com/v3/geocode/regeo?" +
                "location=$lng,$lat&key=$GAODE_KEY&output=json"
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val json = JSONObject(response.body?.string() ?: "{}")
            json.optJSONObject("regeocode")
                ?.optJSONObject("addressComponent")
                ?.optString("adcode")
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}

data class RouteSegment(
    val from: String,
    val to: String,
    val durationMin: Long,
    val distanceKm: Double
)

data class DayWeather(
    val date: String,       // 2026-05-27
    val week: String,       // 1-7
    val dayWeather: String, // 多云
    val nightWeather: String,
    val dayTemp: String,    // 27
    val nightTemp: String   // 19
)
