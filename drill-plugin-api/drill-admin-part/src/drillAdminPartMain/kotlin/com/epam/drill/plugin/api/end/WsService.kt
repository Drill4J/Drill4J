package com.epam.drill.plugin.api.end

import com.epam.drill.common.AgentInfo

interface WsService {
    suspend fun convertAndSend(agentInfo: AgentInfo, destination: String, message: String, sessionId: String)
    fun getPlWsSession(): Set<String>
}
