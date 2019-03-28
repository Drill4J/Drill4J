plugins {
    id("kotlin-multiplatform")
    id("kotlinx-serialization")
}

repositories {
    mavenCentral()
    mavenLocal()
    jcenter()
    maven(url = "https://dl.bintray.com/kodein-framework/Kodein-DI")
    maven(url = "https://dl.bintray.com/soywiz/soywiz")
    maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
    maven(url = "https://kotlin.bintray.com/ktor")
    maven(url = "https://dl.bintray.com/spekframework/spek-dev")
    maven(url = "https://kotlin.bintray.com/kotlinx")
    maven(url = "https://mymavenrepo.com/repo/OgSYgOfB6MOBdJw3tWuX/")
}

kotlin {

    targets {
        jvm("admin")
    }

    sourceSets {
        jvm("admin").compilations["main"].defaultSourceSet {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.9.1")
                implementation("org.litote.kmongo:kmongo:3.9.0")
                implementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo:2.1.1")
                implementation("io.ktor:ktor-auth:1.1.2")
                implementation("io.ktor:ktor-auth-jwt:1.1.2")
                //fixme temp solution
                implementation(fileTree(file("testLib/kodein-di-generic-jvm-6.0.1.jar")))
                implementation(fileTree(file("testLib/kodein-di-core-jvm-6.0.1.jar")))
                implementation("io.ktor:ktor-server-netty:1.1.2")
                implementation("io.ktor:ktor-locations:1.1.2")
                implementation("io.ktor:ktor-gson:1.1.2")
                implementation("io.ktor:ktor-server-core:1.1.2")
                implementation("io.ktor:ktor-websockets:1.1.2")
                implementation("io.ktor:ktor-html-builder:1.1.2")
                implementation("ch.qos.logback:logback-classic:1.2.1")
                implementation("ch.qos.logback:logback-classic:1.2.1")
                implementation(project(":drill-common"))
                implementation(project(":drill-plugin-api:drill-admin-part"))

            }
        }

        jvm("admin").compilations["test"].defaultSourceSet {
            dependencies {
                implementation("io.ktor:ktor-server-test-host:1.1.2")
                implementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
                implementation("org.spekframework.spek2:spek-dsl-jvm:2.0.0-alpha.2")
                implementation("org.junit.jupiter:junit-jupiter-engine:5.3.1")
                implementation("org.spekframework.spek2:spek-runner-junit5:2.0.0-alpha.2")
            }
        }


    }
}

tasks {


    val adminJar = "adminJar"(Jar::class) {
        manifest {
            attributes(mapOf("Main-Class" to "io.ktor.server.netty.EngineMain"))
        }

        from(provider {
            kotlin.targets["admin"].compilations["main"].compileDependencyFiles.map {
                if (it.isDirectory) it else zipTree(it)
            }
        })

        archiveFileName.set("drillAdmin.jar")
    }
    register("runDrillAdmin") {
        group = "application"
        dependsOn(adminJar)
        doLast {
            javaexec {
                classpath = (kotlin.targets["admin"].compilations["main"].compileDependencyFiles)
                classpath(adminJar)
                main = "io.ktor.server.netty.EngineMain"
                jvmArgs(
                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006",
                    "-Xmx2g"
                )
            }
        }

    }
}
