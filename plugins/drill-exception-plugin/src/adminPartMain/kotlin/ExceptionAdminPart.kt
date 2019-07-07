package com.epam.drill.plugins.exception

import com.epam.drill.common.AgentInfo
import com.epam.drill.plugin.api.SerDe
import com.epam.drill.plugin.api.end.AdminPluginPart
import com.epam.drill.plugin.api.end.Sender
import com.epam.drill.plugin.api.message.DrillMessage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer


@Suppress("unused")
class ExceptionAdminPart(private val ws: Sender, agentInfo: AgentInfo, id: String) :
    AdminPluginPart<String>(ws, agentInfo, id) {

    override val serDe = SerDe(String.serializer())

    override suspend fun doAction(action: String) {
    }

    override suspend fun processData(dm: DrillMessage): Any {
        println("$id got a message ${dm.content}")
        return ""
    }
}