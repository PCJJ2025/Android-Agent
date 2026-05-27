package com.floatagent.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.floatagent.model.AgentIntent
import com.floatagent.ui.FloatingResultCard
import java.net.URLEncoder

class RestaurantAgent : BaseAgent() {

    override suspend fun execute(context: Context, intent: AgentIntent) {
        val keyword = intent.keyword
        if (keyword.isBlank()) {
            FloatingResultCard.showMessage(context, "未能识别餐厅名称")
            return
        }

        // 优先尝试美团 Deep Link
        val meituanDeepLink = "imeituan://www.meituan.com/search?q=${URLEncoder.encode(keyword, "UTF-8")}"
        val meituanIntent = Intent(Intent.ACTION_VIEW, Uri.parse(meituanDeepLink)).apply {
            setPackage("com.sankuai.meituan")
        }

        if (meituanIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(meituanIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            // 美团未安装，降级到大众点评
            val dianpingDeepLink = "dianping://searchshoplist?keyword=${URLEncoder.encode(keyword, "UTF-8")}"
            val dianpingIntent = Intent(Intent.ACTION_VIEW, Uri.parse(dianpingDeepLink)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (dianpingIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(dianpingIntent)
            } else {
                FloatingResultCard.showMessage(context, "请先安装美团或大众点评")
            }
        }
    }
}
