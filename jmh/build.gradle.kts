import me.champeau.jmh.JMHTask
import org.gradle.kotlin.dsl.kotlin
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm")
    alias(libs.plugins.jmh)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    jmhImplementation(project(":fastgraph"))
    jmhImplementation(libs.fastcollect)
    jmhImplementation(libs.guava)
    jmhImplementation(libs.jgrapht)
    jmhImplementation(libs.jol)
}

private val jmhIncludes: Provider<String> = providers.gradleProperty("jmhIncludes")

jmh {
    includeTests = false
    verbosity = "EXTRA"
    failOnError = true
    resultFormat = "JSON"

    if (jmhIncludes.isPresent) includes.set(decodeArgs(jmhIncludes.get()))
}

private fun registerJMHTask(name: String, configuration: JMHTask.()->Unit): TaskProvider<JMHTask> = tasks.register<JMHTask>("jmh$name") {
    group = "benchmark"
    description = "Run JMH benchmarks for $name"

    includeTests = false
    verbosity = "EXTRA"
    failOnError = true
    resultFormat = "JSON"

    val baseTask = tasks.named<JMHTask>("jmh")

    jmhClasspath = baseTask.get().jmhClasspath
    testRuntimeClasspath = baseTask.get().testRuntimeClasspath
    jarArchive = baseTask.get().jarArchive
    javaLauncher = baseTask.get().javaLauncher
    resultsFile = baseTask.get().resultsFile

    configuration()
}

private val copyTask = tasks.register<Copy>("CopyJmhResults") {
    description = "Copy last JMH results into benchmark-results directory."

    from("build/results/jmh/results.json")
    into("benchmark-results")
    rename { "${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))}.json" }
}

private abstract class ExclusiveTaskService : BuildService<BuildServiceParameters.None>
private val exclusiveServiceProvider = gradle.sharedServices.registerIfAbsent("exclusiveTask", ExclusiveTaskService::class.java) {
    maxParallelUsages.set(1)
}

private val jmhSize: Provider<String> = providers.gradleProperty("jmhSize")
private val jmhOrder: Provider<String> = providers.gradleProperty("jmhOrder")

tasks.withType<JMHTask> {
    // ensure JMH tasks are never cached
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }

    // ensure jmh tasks cannot run in parallel
    usesService(exclusiveServiceProvider)

    // save all benchmark data
    finalizedBy(copyTask)

    // forward various parameters to JMH
    if (jmhOrder.isPresent) benchmarkParameters.put("order", decodeArgs(jmhOrder.get()))
    if (jmhSize.isPresent) benchmarkParameters.put("size", decodeArgs(jmhSize.get()))
}

private fun decodeArgs(args: String): ListProperty<String> {
    return objects.listProperty(String::class.java).apply { addAll(args.split(",")) }
}
