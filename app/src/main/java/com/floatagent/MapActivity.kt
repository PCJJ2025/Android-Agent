package com.floatagent

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.floatagent.analyzer.GaodeSearcher
import com.floatagent.model.SavedPlace
import com.floatagent.storage.CollectionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        webView = findViewById(R.id.mapWebView)
        tvEmpty = findViewById(R.id.tvMapEmpty)
        tvCount = findViewById(R.id.tvMapCount)

        val allPlaces = try { CollectionStorage.getAll(this) } catch (e: Exception) {
            showEmpty("加载失败：${e.message}")
            return
        }

        if (allPlaces.isEmpty()) {
            showEmpty("还没有收藏的场所")
            return
        }

        val mappable = allPlaces.filter { it.lat != 0.0 && it.lng != 0.0 }
        // 只定位「还没坐标 且 从没尝试过」的收藏，失败过的不再重试
        val needGeocode = allPlaces.filter { it.lat == 0.0 && it.lng == 0.0 && !it.geocodeTried }

        if (needGeocode.isEmpty()) {
            // 没有新增需要定位的收藏，直接读取缓存坐标渲染
            if (mappable.isEmpty()) {
                showEmpty("收藏的场所暂无可显示的坐标\n高德未能匹配到店名/地址")
            } else {
                renderMap(mappable)
            }
        } else {
            // 有新收藏需要定位，后台调用高德补全坐标
            showEmpty("正在定位 ${needGeocode.size} 处新收藏...")
            geocodeAndRender(allPlaces)
        }
    }

    private fun geocodeAndRender(allPlaces: List<SavedPlace>) {
        CoroutineScope(Dispatchers.IO).launch {
            val updated = allPlaces.map { place ->
                if (place.lat == 0.0 && place.lng == 0.0 && !place.geocodeTried) {
                    val located = GaodeSearcher.searchCoordinate(place)
                    Log.d("FloatAgent_Map", "定位 ${place.name} → ${located.lat},${located.lng}")
                    located.copy(geocodeTried = true)  // 标记已尝试，无论成功与否
                } else place
            }
            CollectionStorage.replaceAll(this@MapActivity, updated)

            val mappable = updated.filter { it.lat != 0.0 && it.lng != 0.0 }
            withContext(Dispatchers.Main) {
                if (mappable.isEmpty()) {
                    showEmpty("高德未能匹配到任何坐标\n请确认收藏的店名/地址是否准确")
                } else {
                    renderMap(mappable)
                }
            }
        }
    }

    private fun showEmpty(msg: String) {
        webView.visibility = View.GONE
        tvEmpty.visibility = View.VISIBLE
        tvEmpty.text = msg
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun renderMap(places: List<SavedPlace>) {
        tvCount.text = "${places.size} 处"
        webView.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.webViewClient = WebViewClient()
        webView.loadDataWithBaseURL(
            "https://webapi.amap.com",
            buildMapHtml(places),
            "text/html",
            "utf-8",
            null
        )
    }

    private fun buildMapHtml(places: List<SavedPlace>): String {
        val centerLng = places.map { it.lng }.average()
        val centerLat = places.map { it.lat }.average()

        val markersJs = places.joinToString("\n") { p ->
            val itemsText = if (p.items.isNotEmpty())
                "<br><span style='color:#E53935'>推荐：${p.items.take(3).joinToString("、")}</span>"
            else ""
            val addrText = if (p.address.isNotBlank()) "<br>📍 ${p.address}" else ""
            val info = "${p.category.emoji} <b>${p.name}</b>$addrText$itemsText"

            """
            (function() {
                var marker = new AMap.Marker({
                    position: [${p.lng}, ${p.lat}],
                    title: '${p.name.replace("'", "\\'")}',
                    label: {
                        content: '${p.category.emoji}',
                        offset: new AMap.Pixel(-10, -30)
                    }
                });
                var infoWin = new AMap.InfoWindow({
                    content: '<div style="padding:8px;font-size:14px;max-width:220px">${info.replace("'", "\\'")}</div>',
                    offset: new AMap.Pixel(0, -30)
                });
                marker.on('click', function() { infoWin.open(map, marker.getPosition()); });
                map.add(marker);
            })();
            """.trimIndent()
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
                <style>
                    html, body, #container { margin:0; padding:0; width:100%; height:100%; }
                </style>
            </head>
            <body>
                <div id="container"></div>
                <script src="https://webapi.amap.com/maps?v=2.0&key=89713d2ac746f8b895e0b5b2363e181a&plugin=AMap.ToolBar,AMap.Scale"></script>
                <script>
                    var map = new AMap.Map('container', {
                        zoom: 13,
                        zooms: [3, 19],
                        center: [$centerLng, $centerLat]
                    });
                    AMap.plugin(['AMap.ToolBar', 'AMap.Scale'], function() {
                        map.addControl(new AMap.ToolBar({ position: { right: '16px', bottom: '30px' } }));
                        map.addControl(new AMap.Scale());
                    });
                    $markersJs
                    map.setFitView();
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
