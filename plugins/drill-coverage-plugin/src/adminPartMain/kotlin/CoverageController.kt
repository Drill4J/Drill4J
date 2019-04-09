package com.epam.drill.plugins.coverage


import com.epam.drill.plugin.api.end.AdminPluginPart
import com.epam.drill.plugin.api.end.WsService
import com.epam.drill.plugin.api.message.DrillMessage
import kotlinx.serialization.json.JSON
import kotlinx.serialization.list
import org.jacoco.core.analysis.*
import org.jacoco.core.data.ExecutionData
import org.jacoco.core.data.ExecutionDataStore
import org.javers.core.JaversBuilder
import org.javers.core.diff.changetype.NewObject

@Suppress("unused")
class CoverageController(private val ws: WsService, val name: String) : AdminPluginPart(ws, name) {

    val initialClassBytes = mutableMapOf<String, ByteArray>()
    
    val javaClasses = mutableMapOf<String, JavaClass>()

    //TODO Only the last prev state at this moment - use JaVers repositories
    val prevJavaClasses = mutableMapOf<String, JavaClass>()

    private val javers = JaversBuilder.javers().build()

    override suspend fun processData(dm: DrillMessage): Any {
        val sessionId = dm.sessionId
        val content = dm.content
        val parse = JSON.parse(CoverageMessage.serializer(), content!!)
        when(parse.type) {
            CoverageEventType.INIT -> {
                println(parse.data) //log init message
                //change maps
                initialClassBytes.clear()
                prevJavaClasses.clear()
                prevJavaClasses.putAll(javaClasses)
                javaClasses.clear()
            }
            CoverageEventType.COVERAGE_DATA -> {
                val coverageBuilder = CoverageBuilder()
                val dataStore = ExecutionDataStore()
                val analyzer = Analyzer(dataStore, coverageBuilder)

                // Get new probes from message and populate dataStore with them
                val probes = JSON.parse(ExDataTemp.serializer().list, parse.data)
                probes.forEach { exData ->
                    dataStore.put(ExecutionData(exData.id, exData.className, exData.probes.toBooleanArray()))
                }

                // Analyze all existing classes
                initialClassBytes.forEach { (name, bytes) ->
                    analyzer.analyzeClass(bytes, name)
                }

                // TODO possible to store existing bundles to work with obsolete coverage results
                val bundleCoverage = coverageBuilder.getBundle("all")

                val totalCoverage = bundleCoverage.instructionCounter.coveredRatio
                val totalCoveragePercent = if (totalCoverage.isFinite()) totalCoverage * 100 else null

                fillJavaClasses(bundleCoverage)

                val classesCount = bundleCoverage.classCounter.totalCount
                val methodsCount = bundleCoverage.methodCounter.totalCount
                val uncoveredMethodsCount = bundleCoverage.methodCounter.missedCount

                val coverageBlock = CoverageBlock(
                    coverage = totalCoveragePercent,
                    classesCount = classesCount,
                    methodsCount = methodsCount,
                    uncoveredMethodsCount = uncoveredMethodsCount
                )
                println(coverageBlock)
                ws.convertAndSend("/coverage", JSON.stringify(CoverageBlock.serializer(), coverageBlock))

                //TODO Diff should be calculated after all classes has been parsed
                val diff = javers.compareCollections(
                    prevJavaClasses.values.toList(),
                    javaClasses.values.toList(),
                    JavaClass::class.java
                )
                val newMethods = diff.getObjectsByChangeType(NewObject::class.java).filterIsInstance<JavaMethod>()
                val newCoverageBlock = if (newMethods.isNotEmpty()) {
                    println("New methods count: ${newMethods.count()}")
                    val newMethodSet = newMethods.toSet()
                    val newMethodsCoverages = bundleCoverage.packages
                        .flatMap { it.classes }
                        .flatMap { c -> c.methods.map { Pair(JavaMethod(c.name, it.name, it.desc), it) } }
                        .filter { it.first in newMethodSet }
                        .map { it.second }
                    val totalCount = newMethodsCoverages.sumBy { it.instructionCounter.totalCount }
                    val coveredCount = newMethodsCoverages.sumBy { it.instructionCounter.coveredCount }
                    //line coverage
                    val newCoverage = if (totalCount > 0) coveredCount.toDouble() / totalCount else 0.0
                    NewCoverageBlock(
                        newMethodsCoverages.count(),
                        newMethodsCoverages.count { it.methodCounter.coveredCount > 0 },
                        newCoverage * 100
                    )
                } else NewCoverageBlock()
                println(newCoverageBlock)

                // TODO extend destination with plugin id
                ws.convertAndSend(
                    "/coverage-new",
                    JSON.stringify(NewCoverageBlock.serializer(), newCoverageBlock)
                )

                val classCoverage = classCoverage(bundleCoverage)
                println(classCoverage)
                ws.convertAndSend(
                    "/coverage-by-classes",
                    JSON.stringify(JavaClassCoverage.serializer().list, classCoverage)
                )

                val packageCoverage = packageCoverage(bundleCoverage)
                println(packageCoverage)
                ws.convertAndSend(
                    "/coverage-by-packages",
                    JSON.stringify(JavaPackageCoverage.serializer().list, packageCoverage)
                )
            }
            CoverageEventType.CLASS_BYTES -> {
                val classData = JSON.parse(ClassBytes.serializer(), parse.data)
                val className = classData.className
                val bytes = classData.bytes.toByteArray()
                initialClassBytes[className] = bytes
            }
        }
        return ""
    }

    private fun fillJavaClasses(bundleCoverage: IBundleCoverage) {
        javaClasses.clear()
        bundleCoverage.packages
            .flatMap { it.classes }
            .map { cc ->
                cc.name to JavaClass(
                    name = cc.name.substringAfterLast('/'),
                    path = cc.name,
                    methods = cc.methods.map {
                        JavaMethod(
                            ownerClass = cc.name,
                            name = it.name,
                            desc = it.desc
                        )
                    }.toSet()

                )
            }.toMap(javaClasses)
    }

    private fun classCoverage(bundleCoverage: IBundleCoverage): List<JavaClassCoverage> = bundleCoverage.packages
        .flatMap { it.classes}
        .map { classCoverage ->
            JavaClassCoverage(
                name = classCoverage.name.substringAfterLast('/'),
                path = classCoverage.name,
                coverage = classCoverage.coverage(),
                totalMethodsCount = classCoverage.methodCounter.totalCount,
                coveredMethodsCount = classCoverage.methodCounter.coveredCount,
                methods = classCoverage.methods.map { methodCoverage ->
                    JavaMethodCoverage(
                        name = methodCoverage.name,
                        desc = methodCoverage.desc,
                        coverage = methodCoverage.coverage()
                    )
                }.toList()
            )
        }.toList()

    private fun packageCoverage(bundleCoverage: IBundleCoverage): List<JavaPackageCoverage> = bundleCoverage.packages
        .map { packageCoverage ->
            JavaPackageCoverage(
                name = packageCoverage.name,
                coverage = packageCoverage.coverage(),
                totalClassesCount = packageCoverage.classCounter.totalCount,
                coveredClassesCount = packageCoverage.classCounter.coveredCount,
                totalMethodsCount = packageCoverage.methodCounter.totalCount,
                coveredMethodsCount = packageCoverage.methodCounter.coveredCount,
                classes = classCoverage(bundleCoverage)
            )
        }.toList()
}

fun ICoverageNode.coverage() : Double? {
    val ratio = this.instructionCounter.coveredRatio
    return if (ratio.isFinite()) ratio * 100.0 else null
}