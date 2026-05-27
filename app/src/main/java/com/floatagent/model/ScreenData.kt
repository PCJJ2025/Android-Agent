package com.floatagent.model

data class ScreenData(
    val packageName: String,
    val allTexts: List<String>,
    val clickableTexts: List<String>
) {
    companion object {
        fun empty() = ScreenData("", emptyList(), emptyList())
    }

    fun toPromptText(): String = allTexts.take(60).joinToString("\n")
}

data class AgentIntent(
    val scene: Scene,
    val keyword: String,
    val extraInfo: Map<String, String> = emptyMap()
)

enum class Scene {
    SHOPPING,       // 淘宝/京东/拼多多商品页 → 全网比价
    RESTAURANT,     // 小红书/抖音餐厅内容 → 跳转美团
    VIDEO,          // 视频平台 → 查相关信息
    UNKNOWN
}
