package com.floatagent

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray

class RouteMapActivity : AppCompatActivity() {

    private data class Stop(val name: String, val lat: Double, val lng: Double)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_map)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val webView = findViewById<WebView>(R.id.routeWebView)
        val tvEmpty = findViewById<TextView>(R.id.tvRouteEmpty)
        val tvInfo = findViewById<TextView>(R.id.tvRouteInfo)

        val stops = parseStops(intent.getStringExtra("stops"))
        if (stops.size < 2) {
            webView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "路线信息不足，无法绘制地图"
            return
        }

        tvInfo.text = "${stops.size} 个点"
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
            buildHtml(stops),
            "text/html",
            "utf-8",
            null
        )
    }

    private fun parseStops(json: String?): List<Stop> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Stop(o.optString("name"), o.optDouble("lat"), o.optDouble("lng"))
            }.filter { it.lat != 0.0 || it.lng != 0.0 }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildHtml(stops: List<Stop>): String {
        val start = stops.first()
        val end = stops.last()
        val waypoints = if (stops.size > 2) stops.subList(1, stops.size - 1) else emptyList()

        val waypointsJs = waypoints.joinToString(",") { "[${it.lng},${it.lat}]" }
        val allPath = stops.joinToString(",") { "[${it.lng},${it.lat}]" }

        // 自定义标记：起点绿、途经橙色编号、终点红
        val markersJs = stops.mapIndexed { i, s ->
            val (label, color) = when (i) {
                0 -> "起" to "#34A853"
                stops.lastIndex -> "终" to "#EA4335"
                else -> "$i" to "#FF8F00"
            }
            val safeName = s.name.replace("'", "\\'")
            """
            (function() {
                var marker = new AMap.Marker({
                    position: [${s.lng}, ${s.lat}],
                    content: '<div style="background:$color;color:#fff;border-radius:50%;width:28px;height:28px;line-height:28px;text-align:center;font-size:13px;font-weight:bold;border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,0.4)">$label</div>',
                    offset: new AMap.Pixel(-14, -14),
                    zIndex: 200
                });
                var info = new AMap.InfoWindow({
                    content: '<div style="padding:6px 10px;font-size:13px">$safeName</div>',
                    offset: new AMap.Pixel(0, -16)
                });
                marker.on('click', function() { info.open(map, marker.getPosition()); });
                map.add(marker);
            })();
            """.trimIndent()
        }.joinToString("\n")

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
                <style>html,body,#container{margin:0;padding:0;width:100%;height:100%;}</style>
            </head>
            <body>
                <div id="container"></div>
                <script src="https://webapi.amap.com/maps?v=2.0&key=89713d2ac746f8b895e0b5b2363e181a&plugin=AMap.Driving"></script>
                <script>
                    var map = new AMap.Map('container', { zoom: 12, center: [${start.lng}, ${start.lat}] });
                    var allPath = [$allPath];

                    function drawFallback() {
                        var pl = new AMap.Polyline({
                            path: allPath, strokeColor: '#1A73E8',
                            strokeWeight: 6, strokeOpacity: 0.85, lineJoin: 'round'
                        });
                        map.add(pl);
                        map.setFitView();
                    }

                    AMap.plugin('AMap.Driving', function() {
                        try {
                            var driving = new AMap.Driving({ map: map, hideMarkers: true, autoFitView: true });
                            driving.search(
                                [${start.lng}, ${start.lat}],
                                [${end.lng}, ${end.lat}],
                                { waypoints: [$waypointsJs] },
                                function(status, result) {
                                    if (status !== 'complete') drawFallback();
                                    addMarkers();
                                }
                            );
                        } catch (e) {
                            drawFallback();
                            addMarkers();
                        }
                    });

                    function addMarkers() {
                        $markersJs
                        map.setFitView();
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
