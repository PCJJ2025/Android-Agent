package com.floatagent.scraper

import android.content.Context
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.floatagent.model.PriceResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import kotlin.coroutines.resume

object WebViewScraper {

    private const val TAG = "FloatAgent_Scraper"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    // 通用价格提取 JS：遍历页面所有文字，找符合价格格式的第一个
    private val GENERIC_JS = """
        (function() {
            var priceReg = /[¥￥]?\s*(\d+\.?\d*)/;
            var allText = Array.from(document.querySelectorAll('*'))
                .filter(function(el) {
                    return el.children.length === 0 && el.innerText && el.innerText.trim().length > 0;
                })
                .map(function(el) { return el.innerText.trim(); });

            var price = '';
            var title = '';

            // 找价格：优先找带¥符号的
            for (var i = 0; i < allText.length; i++) {
                if ((allText[i].includes('¥') || allText[i].includes('￥')) && priceReg.test(allText[i])) {
                    price = allText[i].replace(/[^0-9.]/g, '');
                    break;
                }
            }
            // 找标题：长度在10-60之间的第一条文字
            for (var j = 0; j < allText.length; j++) {
                if (allText[j].length >= 10 && allText[j].length <= 60) {
                    title = allText[j];
                    break;
                }
            }
            return JSON.stringify({price: price, title: title});
        })()
    """.trimIndent()

    suspend fun scrapeJD(context: Context, keyword: String): PriceResult? =
        scrape(
            context = context,
            url = "https://search.jd.com/Search?keyword=${encode(keyword)}&enc=utf-8",
            platform = "京东",
            js = GENERIC_JS
        )

    suspend fun scrapePDD(context: Context, keyword: String): PriceResult? =
        scrape(
            context = context,
            url = "https://mobile.yangkeduo.com/search_result.html?search_key=${encode(keyword)}",
            platform = "拼多多",
            js = GENERIC_JS
        )

    suspend fun scrapeTaobao(context: Context, keyword: String): PriceResult? =
        scrape(
            context = context,
            url = "https://s.taobao.com/search?q=${encode(keyword)}&sort=sale-desc",
            platform = "淘宝",
            js = GENERIC_JS
        )

    private suspend fun scrape(
        context: Context,
        url: String,
        platform: String,
        js: String
    ): PriceResult? {
        Log.d(TAG, "[$platform] 开始加载: $url")
        val result = withTimeoutOrNull(18_000) {
            suspendCancellableCoroutine { cont ->
                val webView = WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        userAgentString = USER_AGENT
                        domStorageEnabled = true
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        // 拦截 App 专属协议跳转（openapp://、jd://等），防止 WebView 迷路
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            Log.w(TAG, "[$platform] 拦截非 HTTP 跳转: $url")
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        Log.d(TAG, "[$platform] 页面加载完成，实际落地URL: $url")
                        // 先打印页面标题和部分正文，判断是否跳到了登录/验证码页
                        view.evaluateJavascript("document.title + '|||' + document.body.innerText.substring(0, 200)") { pageInfo ->
                            Log.d(TAG, "[$platform] 页面内容预览: $pageInfo")
                        }
                        view.evaluateJavascript(js.trimIndent()) { raw ->
                            Log.d(TAG, "[$platform] JS 原始返回: $raw")
                            val priceResult = parseResult(raw, platform)
                            Log.d(TAG, "[$platform] 解析结果: $priceResult")
                            webView.destroy()
                            if (cont.isActive) cont.resume(priceResult)
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        errorCode: Int,
                        description: String,
                        failingUrl: String
                    ) {
                        Log.e(TAG, "[$platform] 页面加载错误 $errorCode: $description, url: $failingUrl")
                    }
                }

                webView.loadUrl(url)
                cont.invokeOnCancellation { webView.destroy() }
            }
        }
        if (result == null) Log.w(TAG, "[$platform] 超时未获取到数据")
        return result
    }

    private fun parseResult(raw: String?, platform: String): PriceResult? {
        if (raw == null || raw == "null") return null
        return try {
            val cleaned = raw.trim('"').replace("\\\"", "\"")
            val json = org.json.JSONObject(cleaned)
            val price = json.optString("price").trim()
            val title = json.optString("title").trim()
            if (price.isBlank()) {
                Log.w(TAG, "[$platform] 价格为空，原始数据: $cleaned")
                null
            } else {
                PriceResult(platform = platform, price = price, title = title)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$platform] 解析异常: ${e.message}, 原始: $raw")
            null
        }
    }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
}
