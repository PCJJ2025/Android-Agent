package com.floatagent

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.drawable.GradientDrawable
import com.floatagent.agent.FoodRecommendAgent
import com.floatagent.model.Category
import com.floatagent.model.SavedPlace
import com.floatagent.storage.CollectionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CollectionListActivity : AppCompatActivity() {

    private lateinit var listContainer: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView
    private lateinit var etCraving: EditText
    private lateinit var filterRow: LinearLayout
    private var recommendMode = false
    private var selectedCategory: Category? = null  // null = 全部

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collection_list)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        listContainer = findViewById(R.id.listContainer)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvCount = findViewById(R.id.tvCount)
        etCraving = findViewById(R.id.etCraving)
        filterRow = findViewById(R.id.filterRow)

        findViewById<TextView>(R.id.btnMap).setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
        findViewById<Button>(R.id.btnSearch).setOnClickListener { doRecommend() }
        etCraving.setOnEditorActionListener { _, _, _ -> doRecommend(); true }
    }

    override fun onResume() {
        super.onResume()
        if (!recommendMode) refresh()
    }

    private fun doRecommend() {
        val craving = etCraving.text.toString().trim()
        if (craving.isEmpty()) {
            recommendMode = false
            refresh()
            return
        }
        // 收起键盘
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(etCraving.windowToken, 0)

        recommendMode = true
        listContainer.removeAllViews()
        tvEmpty.visibility = View.GONE
        listContainer.visibility = View.VISIBLE
        tvCount.text = "正在推荐..."

        val places = CollectionStorage.getAll(this)
        CoroutineScope(Dispatchers.Main).launch {
            val matches = withContext(Dispatchers.IO) {
                FoodRecommendAgent.recommend(craving, places)
            }
            renderRecommendations(craving, matches)
        }
    }

    private fun renderRecommendations(craving: String, matches: List<FoodRecommendAgent.Match>) {
        listContainer.removeAllViews()

        // 顶部：显示全部 的重置入口
        listContainer.addView(TextView(this).apply {
            text = "← 显示全部收藏"
            textSize = 13f
            setTextColor(0xFF1A73E8.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(12))
            setOnClickListener {
                etCraving.text.clear()
                recommendMode = false
                refresh()
            }
        })

        if (matches.isEmpty()) {
            tvCount.text = ""
            listContainer.addView(TextView(this).apply {
                text = "没有找到匹配「$craving」的收藏\n换个说法，或先收藏更多店铺试试"
                textSize = 14f
                setTextColor(0xFF888888.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(40), 0, 0)
            })
            return
        }

        tvCount.text = "为你推荐 ${matches.size} 家"
        matches.forEach { match ->
            listContainer.addView(buildCard(match.place, match.reason))
        }
    }

    private fun refresh() {
        listContainer.removeAllViews()
        val all = CollectionStorage.getAll(this)

        if (all.isEmpty()) {
            filterRow.removeAllViews()
            tvCount.text = "共 0 处"
            tvEmpty.visibility = View.VISIBLE
            listContainer.visibility = View.GONE
            return
        }

        buildFilterChips(all)

        val shown = selectedCategory?.let { cat -> all.filter { it.category == cat } } ?: all
        tvCount.text = if (selectedCategory == null) "共 ${all.size} 处" else "${shown.size} 处"

        tvEmpty.visibility = View.GONE
        listContainer.visibility = View.VISIBLE
        shown.forEach { place -> listContainer.addView(buildCard(place)) }
    }

    private fun buildFilterChips(all: List<SavedPlace>) {
        filterRow.removeAllViews()
        // 「全部」+ 收藏中实际出现的分类（按枚举顺序）
        val present = Category.entries.filter { cat -> all.any { it.category == cat } }

        filterRow.addView(makeChip("全部 ${all.size}", selectedCategory == null) {
            exitRecommendMode()
            selectedCategory = null
            refresh()
        })
        present.forEach { cat ->
            val count = all.count { it.category == cat }
            filterRow.addView(makeChip("${cat.emoji}${cat.label} $count", selectedCategory == cat) {
                exitRecommendMode()
                selectedCategory = cat
                refresh()
            })
        }
    }

    private fun exitRecommendMode() {
        if (recommendMode) {
            recommendMode = false
            etCraving.text.clear()
        }
    }

    private fun makeChip(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (selected) 0xFFFF8F00.toInt() else 0xFFEEEEEE.toInt())
            }
            setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF666666.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dp(8), 0) }
            setOnClickListener { onClick() }
        }
    }

    private fun buildCard(place: SavedPlace, reason: String? = null): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_background)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
        }

        // 推荐理由（仅推荐模式下显示）
        if (!reason.isNullOrBlank()) {
            card.addView(TextView(this).apply {
                text = "💡 $reason"
                textSize = 13f
                setTextColor(0xFFFF8F00.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, dp(8))
            })
        }

        // 标题行：emoji + 名称
        card.addView(TextView(this).apply {
            text = "${place.category.emoji} ${place.name}"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF212121.toInt())
        })

        // 分类标签
        card.addView(TextView(this).apply {
            text = place.category.label
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            setPadding(0, dp(2), 0, 0)
        })

        // 地址
        if (place.address.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = "📍 ${place.address}"
                textSize = 13f
                setTextColor(0xFF555555.toInt())
                setPadding(0, dp(6), 0, 0)
            })
        }

        // 推荐菜品/服务
        if (place.items.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = "推荐：${place.items.joinToString("、")}"
                textSize = 13f
                setTextColor(0xFFE53935.toInt())
                setPadding(0, dp(4), 0, 0)
            })
        }

        // 特色描述
        if (place.note.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = place.note
                textSize = 12f
                setTextColor(0xFF777777.toInt())
                setPadding(0, dp(4), 0, 0)
            })
        }

        // 来源 + 删除按钮行
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        bottomRow.addView(TextView(this).apply {
            text = "来自 ${place.source}"
            textSize = 11f
            setTextColor(0xFFBBBBBB.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        bottomRow.addView(TextView(this).apply {
            text = "删除"
            textSize = 12f
            setTextColor(0xFFE53935.toInt())
            setOnClickListener { confirmDelete(place) }
        })
        card.addView(bottomRow)

        return card
    }

    private fun confirmDelete(place: SavedPlace) {
        AlertDialog.Builder(this)
            .setTitle("删除收藏")
            .setMessage("确定删除「${place.name}」？")
            .setPositiveButton("删除") { _, _ ->
                CollectionStorage.delete(this, place.id)
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
