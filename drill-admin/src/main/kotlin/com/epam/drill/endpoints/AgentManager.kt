package com.epam.drill.endpoints

import com.epam.drill.agentmanager.AgentInfoWebSocketSingle
import com.epam.drill.agentmanager.AgentStorage
import com.epam.drill.agentmanager.self
import com.epam.drill.common.AgentInfo
import com.epam.drill.common.AgentInfoDb
import com.epam.drill.common.Family
import com.epam.drill.common.PluginBeanDb
import com.epam.drill.common.merge
import com.epam.drill.common.toAgentInfo
import com.epam.drill.service.asyncTransaction
import io.ktor.http.cio.websocket.DefaultWebSocketSession
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.instance

class AgentManager(override val kodein: Kodein) : KodeinAware {
    val agentStorage: AgentStorage by instance()

    suspend fun agentConfiguration(agentId: String): AgentInfo {
        val agentInfo = asyncTransaction { AgentInfoDb.findById(agentId) }
        if (agentInfo != null)
            return asyncTransaction {
                val toAgentInfo = agentInfo.toAgentInfo()
                toAgentInfo
            }
        else {
            return asyncTransaction {
                addLogger(StdOutSqlLogger)
                AgentInfoDb.new(agentId) {
                    name = "???"
                    groupName = "???"
                    description = "???"
                    buildVersion = "???"
                    isEnable = true
                    adminUrl = ""
                    val elements = PluginBeanDb.new {
                        pluginId = "coverage"
                        name = "AwesomeCoveragePlugin"
                        description = "This is the awesome custom plugin"
                        type = "Custom"
                        family = Family.INSTRUMENTATION
                        enabled = true
                        config =
                            "{\"pathPrefixes\": [\"org/drilspringframework/samples/petclinic\",\"com/epam/ta/reportportal\"], \"message\": \"hello from default plugin config... This is 'plugin_config.json file\"}"
                    }
                    rawPluginNames = SizedCollection(elements)
                }
                    .toAgentInfo()
            }

        }

    }


    suspend fun updateAgent(agentId: String, au: AgentInfoWebSocketSingle) {
        val agentInfoDb = asyncTransaction { AgentInfoDb.findById(agentId) }

        asyncTransaction {
            addLogger(StdOutSqlLogger)
            agentInfoDb?.merge(au)
        }
        val byId = byId(agentId)
        byId?.apply {
            name = au.name
            groupName = au.group
            description = au.description
            buildVersion = au.buildVersion
        }
        agentStorage.update()
        agentStorage.singleUpdate(agentId)

    }

    suspend fun put(agentInfo: AgentInfo, session: DefaultWebSocketSession) {
        agentStorage.put(agentInfo.id, Pair(agentInfo, session))
    }

    suspend fun remove(agentInfo: AgentInfo) {
        agentStorage.remove(agentInfo.id)
    }

    operator fun get(k: String) = agentStorage.targetMap[k]?.second

    fun self(k: String) = agentStorage.self(k)

    fun byId(agentId: String) = agentStorage.targetMap[agentId]?.first


}


@Serializable
data class AgentUpdate(
    val name: String,
    val groupName: String,
    val description: String,
    val buildVersion: String
)