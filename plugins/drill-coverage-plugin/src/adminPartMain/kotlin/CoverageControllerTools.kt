package com.epam.drill.plugins.coverage

import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.IBundleCoverage
import org.jacoco.core.analysis.IClassCoverage
import org.jacoco.core.analysis.IMethodCoverage
import org.jacoco.core.data.ExecutionData
import org.jacoco.core.data.ExecutionDataStore
import kotlin.math.abs

fun testUsages(bundleMap: Map<String, IBundleCoverage>): List<TestUsagesInfo> =
    bundleMap.map { (k, v) ->
        TestUsagesInfo(k, v.methodCounter.coveredCount, "Test type", "30.02.2019")
    }

fun testUsageBundles(
    initialClassBytes: Map<String, ByteArray>,
    probes: Collection<ExDataTemp>
): Map<String, IBundleCoverage> = probes
    .filter { it.testName != null }
    .groupBy { it.testName!! }
    .mapValues { (_, v) ->
        val dataStore = ExecutionDataStore()
        v.forEach {
            val probeArray = it.probes.toBooleanArray()
            val executionData = ExecutionData(it.id, it.className, probeArray)
            dataStore.put(executionData)
        }
        generateBundleForTestUsages(initialClassBytes, dataStore)
    }

fun generateBundleForTestUsages(
    initialClassBytes: Map<String, ByteArray>,
    dataStore: ExecutionDataStore
): IBundleCoverage {
    val coverageBuilder = CoverageBuilder()
    val analyzer = Analyzer(dataStore, coverageBuilder)
    dataStore.contents.forEach { execData ->
        analyzer.analyzeClass(initialClassBytes[execData.name], execData.name)
    }
    return coverageBuilder.getBundle("all")
}

fun generateBundleForInfosByTestType(
    initialClassBytes: Map<String, ByteArray>,
    dataStore: ExecutionDataStore
): IBundleCoverage {
    val coverageBuilder = CoverageBuilder()
    val analyzer = Analyzer(dataStore, coverageBuilder)
    initialClassBytes.forEach { (name, bytes) ->
        analyzer.analyzeClass(bytes, name)
    }
    return coverageBuilder.getBundle("all")
}


fun infosByTestType(
    initialClassBytes: Map<String, ByteArray>,
    probes: Collection<ExDataTemp>
): List<InfoByTestType> = probes
    .groupBy { it.testType }
    .map { (testType, v) ->
        val dataStore = ExecutionDataStore()
        v.forEach {
            val probeArray = it.probes.toBooleanArray()
            val executionData = ExecutionData(it.id, it.className, probeArray)
            dataStore.put(executionData)
        }
        generateBundleForInfosByTestType(initialClassBytes, dataStore).toInfoByTestType(testType, v.testsCount())
    }

fun Collection<ExDataTemp>.testsCount() = groupBy { it.testName }.keys.size

fun IBundleCoverage.toInfoByTestType(type: TestType, tests: Int) =
    InfoByTestType(
        type,
        coverage,
        tests
    )

fun collectAssocTestPairs(
    initialClassBytes: Map<String, ByteArray>,
    executionData: ExecutionData,
    testName: String
): List<Pair<CoverageKey, String>> {
    val cb = CoverageBuilder()
    Analyzer(ExecutionDataStore().apply { put(executionData) }, cb).analyzeClass(
        initialClassBytes[executionData.name],
        executionData.name
    )
    return cb.getBundle("").packages.flatMap { p ->
        listOf(p.coverageKey() to testName) + p.classes.flatMap { c ->
            listOf(c.coverageKey() to testName) + c.methods.flatMap { m ->
                if (m.instructionCounter.coveredCount > 0) {
                    listOf(m.coverageKey(c) to testName)
                } else emptyList()
            }
        }
    }
}

fun packageCoverage(
    bundleCoverage: IBundleCoverage,
    assocTestsMap: Map<CoverageKey, List<String>>
): List<JavaPackageCoverage> = bundleCoverage.packages
    .map { packageCoverage ->
        val packageKey = packageCoverage.coverageKey()
        JavaPackageCoverage(
            id = packageKey.id,
            name = packageCoverage.name,
            coverage = packageCoverage.coverage,
            totalClassesCount = packageCoverage.classCounter.totalCount,
            coveredClassesCount = packageCoverage.classCounter.coveredCount,
            totalMethodsCount = packageCoverage.methodCounter.totalCount,
            coveredMethodsCount = packageCoverage.methodCounter.coveredCount,
            assocTestsCount = assocTestsMap[packageKey]?.count(),
            classes = classCoverage(packageCoverage.classes, assocTestsMap)
        )
    }.toList()

fun classCoverage(
    classCoverages: Collection<IClassCoverage>,
    assocTestsMap: Map<CoverageKey, List<String>>
): List<JavaClassCoverage> = classCoverages
    .map { classCoverage ->
        val classKey = classCoverage.coverageKey()
        JavaClassCoverage(
            id = classKey.id,
            name = classCoverage.name.substringAfterLast('/'),
            path = classCoverage.name,
            coverage = classCoverage.coverage,
            totalMethodsCount = classCoverage.methodCounter.totalCount,
            coveredMethodsCount = classCoverage.methodCounter.coveredCount,
            assocTestsCount = assocTestsMap[classKey]?.count(),
            methods = classCoverage.methods.map { methodCoverage ->
                val methodKey = methodCoverage.coverageKey(classCoverage)
                JavaMethodCoverage(
                    id = methodKey.id,
                    name = methodCoverage.name,
                    desc = methodCoverage.desc,
                    decl = declaration(methodCoverage.desc),
                    coverage = methodCoverage.coverage,
                    assocTestsCount = assocTestsMap[methodKey]?.count()
                )
            }.toList()
        )
    }.toList()

fun methodCoverageId(
    classCoverage: IClassCoverage,
    methodCoverage: IMethodCoverage
) = "${classCoverage.name}.${methodCoverage.name}${methodCoverage.desc}".crc64

fun getAssociatedTestMap(
    scopeProbes: Set<ExDataTemp>,
    dataStore: ExecutionDataStore,
    initialClassBytes: Map<String, ByteArray>
): Map<CoverageKey, List<String>> {
    return scopeProbes.flatMap { exData ->
        val probeArray = exData.probes.toBooleanArray()
        val executionData = ExecutionData(exData.id, exData.className, probeArray.copyOf())
        dataStore.put(executionData)
        when (exData.testName) {
            null -> emptyList()
            else -> collectAssocTestPairs(
                initialClassBytes,
                ExecutionData(exData.id, exData.className, probeArray.copyOf()),
                exData.testName
            )
        }
    }.groupBy({ it.first }) { it.second } //group by test names
        .mapValues { (_, tests) -> tests.distinct() }
}

fun Map<CoverageKey, List<String>>.getAssociatedTests() = map { (key, tests) ->
    AssociatedTests(
        id = key.id,
        packageName = key.packageName,
        className = key.className,
        methodName = key.methodName,
        tests = tests
    )
}

fun arrowType(
    totalCoveragePercent: Double?,
    classesData: ClassesData
): ArrowType? {
    return if (totalCoveragePercent != null) {
        val prevCoverage = classesData.execData.coverage ?: 0.0
        classesData.execData.coverage = totalCoveragePercent
        val diff = totalCoveragePercent - prevCoverage
        when {
            abs(diff) < 1E-7 -> null
            diff > 0.0 -> ArrowType.INCREASE
            else -> ArrowType.DECREASE
        }
    } else null
}

fun calculateNewCoverageBlock(
    newMethods: List<JavaMethod>,
    bundleCoverage: IBundleCoverage
) = if (newMethods.isNotEmpty()) {
    println("New methods count: ${newMethods.count()}")
    val newMethodSet = newMethods.toSet()
    val newMethodsCoverages = bundleCoverage.packages
        .flatMap { it.classes }
        .flatMap { c -> c.methods.map { Pair(JavaMethod(c.name, it.name, it.desc), it) } }
        .filter { it.first in newMethodSet }
    val totalCount = newMethodsCoverages.sumBy { it.second.instructionCounter.totalCount }
    val coveredCount = newMethodsCoverages.sumBy { it.second.instructionCounter.coveredCount }
    //bytecode instruction coverage
    val newCoverage = if (totalCount > 0) coveredCount.toDouble() / totalCount * 100 else null

    val coverages = newMethodsCoverages.map { (jm, mc) ->
        mc.simpleMethodCoverage(jm.ownerClass)
    }
    NewCoverageBlock(
        methodsCount = newMethodsCoverages.count(),
        methodsCovered = newMethodsCoverages.count { it.second.methodCounter.coveredCount > 0 },
        coverage = newCoverage
    ) to coverages
} else NewCoverageBlock() to emptyList()