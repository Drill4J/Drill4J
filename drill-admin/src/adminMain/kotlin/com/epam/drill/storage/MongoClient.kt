package com.epam.drill.storage

import com.mongodb.MongoClient
import org.litote.kmongo.KMongo


class MongoClient {
    var client: MongoClient? = null

    init {
        val bindIp = if (System.getenv("isDocker") != null) {
            println("docker ENV!")
            "drillmongodb"
        } else "localhost"
        val port = 27017
        try {
            client = KMongo.createClient(bindIp, port)
        } catch (io: Exception) {
            client = KMongo.createClient(bindIp, port)
            client?.startSession()
        } finally {
        }
    }
}
