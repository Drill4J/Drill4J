package com.epam.drill.plugin.api.end

import com.epam.drill.common.AgentInfo
import com.epam.drill.common.parse
import com.epam.drill.plugin.api.DrillPlugin
import com.epam.drill.plugin.api.message.DrillMessage

abstract class AdminPluginPart<A>(val sender: Sender, val agentInfo: AgentInfo, override val id: String) :
    DrillPlugin<A> {
    abstract val actionSerializer: kotlinx.serialization.KSerializer<A>

    abstract suspend fun processData(dm: DrillMessage): Any

    abstract suspend fun doAction(action: A)

    open suspend fun doRawAction(action: String) = doAction(
        actionSerializer parse action
    )
}
