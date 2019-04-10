package com.epam.drill.endpoints

import com.epam.drill.common.AgentInfo
import com.epam.drill.common.Message
import com.epam.drill.common.MessageType
import com.epam.drill.module
import com.epam.drill.plugin.api.end.WsService
import com.epam.drill.storage.MongoClient
import com.google.gson.Gson
import io.ktor.application.install
import io.ktor.config.MapApplicationConfig
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readReason
import io.ktor.http.cio.websocket.readText
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.util.KtorExperimentalAPI
import io.ktor.websocket.WebSockets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.junit.BeforeClass
import org.junit.Test
import org.kodein.di.generic.bind
import org.kodein.di.generic.eagerSingleton
import org.kodein.di.generic.singleton
import org.testcontainers.containers.GenericContainer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail


@KtorExperimentalAPI
@ObsoleteCoroutinesApi
@KtorExperimentalLocationsAPI
@ExperimentalCoroutinesApi
class DrillPluginWsTest {

    @ExperimentalCoroutinesApi
    @KtorExperimentalLocationsAPI
    @ObsoleteCoroutinesApi
    @KtorExperimentalAPI
    companion object {
        var engine: TestApplicationEngine? = null
        var wsPluginService: WsService? = null

        @BeforeClass
        @JvmStatic
        fun initTestEngine() {
            val genericContainer = GenericContainer<Nothing>("mongo")
            genericContainer.withExposedPorts(27017)
            genericContainer.start()
            engine = TestApplicationEngine(createTestEnvironment {
                config = MapApplicationConfig(
                    "mongo.host" to genericContainer.containerIpAddress,
                    "mongo.port" to genericContainer.firstMappedPort.toString()
                )
            })
            engine!!.start(wait = false)
            engine!!.application.module({
                install(WebSockets)
                install(Locations)
            }, {
                bind<WsService>() with eagerSingleton {
                    wsPluginService = DrillPluginWs(kodein)
                    wsPluginService!!
                }
                bind<WsTopic>() with singleton { WsTopic(kodein) }
                bind<MongoClient>() with eagerSingleton { MongoClient(kodein) }
            })
        }
    }

    @Test
    fun `should retrun CloseFrame if we subscribe without SubscribeInfo`() {
        with(engine) {
            this?.handleWebSocketConversation("/ws/drill-plugin-socket") { incoming, outgoing ->
                outgoing.send(Message(MessageType.SUBSCRIBE, "/pluginTopic", "").textFrame())
                val receive = incoming.receive()
                assertTrue(receive is Frame.Close)
                assertEquals(CloseReason.Codes.PROTOCOL_ERROR.code, receive.readReason()?.code)
            }
        }
    }


    @Test
    fun `should communicate with pluginWs and return the empty MESSAGE`() {
        with(engine) {
            this?.handleWebSocketConversation("/ws/drill-plugin-socket") { incoming, outgoing ->
                val destination = "/pluginTopic"
                val agentId = "testAgent"
                val buildVersion = "1.0.0"
                outgoing.send(
                    Message(
                        MessageType.SUBSCRIBE,
                        destination,
                        SubscribeInfo(agentId, buildVersion).stringify()
                    ).textFrame()
                )
                val receive = incoming.receive() as? Frame.Text ?: fail()
                val readText = receive.readText()
                val fromJson = Gson().fromJson(readText, Message::class.java)
                assertEquals(destination, fromJson.destination)
                assertEquals(MessageType.MESSAGE, fromJson.type)
                assertTrue { fromJson.message.isEmpty() }
                assertTrue { wsPluginService?.getPlWsSession()?.isNotEmpty() ?: false }
            }
        }
    }

    @Test
    fun `subscribe should return data from storage which we sent before via convertAndSend`() {
        with(engine) {
            val agentId = "testAgent"
            val buildVersion = "1.0.0"
            this?.handleWebSocketConversation("/ws/drill-plugin-socket") { incoming, outgoing ->
                val destination = "/pluginTopic"
                val messageForTest = "testMessage"
                val agentInfo = AgentInfo(
                    id = "id",
                    name = "test",
                    ipAddress = agentId,
                    groupName = "test",
                    description = "test",
                    isEnable = true,
                    buildVersion = buildVersion
                )
                wsPluginService?.convertAndSend(agentInfo, destination, messageForTest)
                outgoing.send(
                    Message(
                        MessageType.SUBSCRIBE,
                        destination,
                        SubscribeInfo(agentId, buildVersion).stringify()
                    ).textFrame()
                )

                val receive = incoming.receive() as? Frame.Text ?: fail()
                val readText = receive.readText()
                val fromJson = Gson().fromJson(readText, Message::class.java)
                assertEquals(destination, fromJson.destination)
                assertEquals(MessageType.MESSAGE, fromJson.type)
                assertEquals(messageForTest, fromJson.message)
            }
        }
    }
}