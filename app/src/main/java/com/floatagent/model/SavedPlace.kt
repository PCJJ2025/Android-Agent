package com.floatagent.model

data class SavedPlace(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,               // 店名
    val category: Category,         // 类型
    val address: String = "",       // 地址文字
    val lat: Double = 0.0,          // 纬度（高德搜索后填入）
    val lng: Double = 0.0,          // 经度
    val items: List<String> = emptyList(), // 推荐食物/饮品/服务
    val note: String = "",          // 原帖摘要
    val source: String = "",        // 来源平台（小红书/抖音）
    val savedAt: Long = System.currentTimeMillis(),
    val geocodeTried: Boolean = false  // 是否已尝试过高德定位（避免重复请求失败项）
)

enum class Category(val label: String, val emoji: String) {
    RESTAURANT("餐厅", "🍜"),
    DRINKS("饮品店", "🧋"),
    SPA("Spa/按摩", "💆"),
    CAFE("咖啡", "☕"),
    OTHER("其他", "📍");

    companion object {
        fun fromLabel(label: String) = entries.firstOrNull {
            label.contains(it.label) || label.contains(it.name, ignoreCase = true)
        } ?: OTHER
    }
}
