package com.floatagent.agent

import android.content.Context
import android.util.Log
import com.floatagent.analyzer.ClaudeContextAnalyzer
import com.floatagent.model.Scene
import com.floatagent.service.ScreenReaderService
import com.floatagent.ui.FloatingResultCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AgentOrchestrator {

    private const val TAG = "FloatAgent_Orchestrator"

    fun analyze(context: Context) {
        val screenData = ScreenReaderService.instance?.getCurrentScreenData()
            ?: run {
                Log.e(TAG, "无障碍服务未开启")
                FloatingResultCard.showMessage(context, "无障碍服务未开启")
                return
            }

        Log.d(TAG, "当前 App 包名: ${screenData.packageName}")
        Log.d(TAG, "屏幕文字(前20条): ${screenData.allTexts.take(20)}")

        FloatingResultCard.showLoading(context)

        CoroutineScope(Dispatchers.IO).launch {
            val intent = ClaudeContextAnalyzer.analyze(context, screenData)

            Log.d(TAG, "识别场景: ${intent.scene}, 关键词: ${intent.keyword}, 价格: ${intent.extraInfo}")

            val agent: BaseAgent? = when (intent.scene) {
                Scene.SHOPPING   -> PriceComparisonAgent()
                Scene.RESTAURANT -> RestaurantAgent()
                Scene.VIDEO      -> null
                Scene.UNKNOWN    -> null
            }

            withContext(Dispatchers.Main) {
                if (agent == null) {
                    Log.w(TAG, "未匹配到任何 Agent，场景: ${intent.scene}")
                    FloatingResultCard.showMessage(context, "未识别到可操作的场景")
                } else {
                    agent.execute(context, intent)
                }
            }
        }
    }
}
