package com.floatagent.agent

import android.content.Context
import com.floatagent.model.AgentIntent

abstract class BaseAgent {
    abstract suspend fun execute(context: Context, intent: AgentIntent)
}
