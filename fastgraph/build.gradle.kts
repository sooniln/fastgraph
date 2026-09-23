import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import java.nio.file.Path as NioPath

plugins {
    kotlin("jvm")

    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish)
}

repositories {
    mavenCentral()
}

group = "io.github.sooniln"
version = "1.0.0"

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
    explicitApi()
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        binariesSource.set(BinariesSource.MAVEN_PUBLICATIONS)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(libs.fastcollect)

    testImplementation(libs.assertJ)
    testImplementation(libs.junitCore)
    testImplementation(libs.junitParams)
    testImplementation(kotlin("reflect"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.register<Sync>("GenerateMain") {
    description = "Generates source code for templates in main."
    group = "build"
    into("src/mainGenerated/kotlin")

    generate("main",
        listOf(
            TemplateInstantiation(
                "EdgeProperties.kte",
                listOf(
                    mapOf("Type" to "Boolean", "StorageType" to "Byte", "ReadLambda" to "{ return it != 0.toByte() }", "WriteLambda" to "{ return if (it) 1 else 0 }"),
                    mapOf("Type" to "Byte"),
                    mapOf("Type" to "Int"),
                    mapOf("Type" to "Long"),
                    mapOf("Type" to "Float"),
                    mapOf("Type" to "Double"),
                    mapOf("Type" to "Edge", "StorageType" to "Long", "ReadLambda" to "{ return Edge(it) }", "WriteLambda" to "{ return it.id }"),
                )) { expansion -> "properties/${expansion["Type"]}EdgeProperties.kt" },
            TemplateInstantiation(
                "VertexProperties.kte",
                listOf(
                    mapOf("Type" to "Boolean", "StorageType" to "Byte", "ReadLambda" to "{ return it != 0.toByte() }", "WriteLambda" to "{ return if (it) 1 else 0 }"),
                    mapOf("Type" to "Byte"),
                    mapOf("Type" to "Int"),
                    mapOf("Type" to "Long"),
                    mapOf("Type" to "Float"),
                    mapOf("Type" to "Double"),
                    mapOf("Type" to "Vertex", "StorageType" to "Int", "ReadLambda" to "{ return Vertex(it) }", "WriteLambda" to "{ return it.id }"),
                )) { expansion -> "properties/${expansion["Type"]}VertexProperties.kt" },
            TemplateInstantiation(
                "EdgeKeyProperties.kte",
                listOf(
                    mapOf("Type" to "Int"),
                    mapOf("Type" to "Long"),
                    mapOf("Type" to "Edge", "StorageType" to "Long", "ReadLambda" to "{ return Edge(it) }", "WriteLambda" to "{ return it.id }"),
                )) { expansion -> "properties/${expansion["Type"]}EdgeKeyProperties.kt" },
            TemplateInstantiation(
                "VertexKeyProperties.kte",
                listOf(
                    mapOf("Type" to "Int"),
                    mapOf("Type" to "Long"),
                    mapOf("Type" to "Vertex", "StorageType" to "Int", "ReadLambda" to "{ return Vertex(it) }", "WriteLambda" to "{ return it.id }"),
                )) { expansion -> "properties/${expansion["Type"]}VertexKeyProperties.kt" },
        ))
}

sourceSets {
    main {
        kotlin.srcDir(tasks.named<Sync>("GenerateMain"))
    }
}

tasks.named("compileJava", JavaCompile::class.java) {
    options.compilerArgumentProviders.add(CommandLineArgumentProvider {
        // provide compiled Kotlin classes to javac – needed for java/kotlin mixed sources to work
        listOf("--patch-module", "io.github.sooniln.fastgraph=${sourceSets["main"].output.asPath}")
    })
}

tasks.test {
    useJUnitPlatform()
    enableAssertions = true

    // some tests read the abi file for verifications
    dependsOn("checkKotlinAbi")
}

dokka {
    moduleName.set("FastGraph")
    dokkaPublications.html {
        failOnWarning.set(true)
    }

    dokkaSourceSets.all {
        includes.from("module.md")
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl.set(uri("https://github.com/sooniln/fastgraph/blob/main/fastgraph/src/main/kotlin"))
            remoteLineSuffix.set("#L")
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "fastgraph", version.toString())

    pom {
        name = "fastgraph"
        description = "A high performance mathematical graph-theory library for JVM."
        inceptionYear = "2026"
        url = "https://github.com/sooniln/fastgraph"
        licenses {
            license {
                name = "MIT License"
                url = "https://github.com/sooniln/fastgraph/blob/main/LICENSE"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "sooniln"
                name = "Soonil Nagarkar"
                email = "sooniln@gmail.com"
                organization = "Soonil Nagarkar"
                organizationUrl = "https://github.com/sooniln"
            }
        }
        scm {
            url = "https://github.com/sooniln/fastgraph/"
            connection = "scm:git:git://github.com/sooniln/fastgraph.git"
            developerConnection = "scm:git:ssh://git@github.com/sooniln/fastgraph.git"
        }
    }
}

private data class TemplateInstantiation(
    val inputFile: String,
    val expansions: List<Map<String, Any>>,
    val outputFile: (Map<String, Any>) -> String,
)

private fun Map<String, Any>.generateFullExpansion(): Map<String, Any> {
    val map = this
    return buildMap {
        putAll(map)

        val type = map["Type"] as String?
        if (type != null) {
            putIfAbsent("StorageType", type)
            putIfAbsent("ReadLambda", "{ return it }")
            putIfAbsent("WriteLambda", "{ return it }")
        }

        val keyType = map["KeyType"] as String?
        val valueType = map["ValueType"] as String?
        if (keyType != null && valueType != null) {
            putIfAbsent("Name", "${map["KeyType"]}2${map["ValueType"]}")
            putIfAbsent("lowerKeyType", keyType.replaceFirstChar { it.lowercase() })
            if (valueType == "V") {
                putIfAbsent("isReferenceValue", true)
                putIfAbsent("Generics", "<V>")
                putIfAbsent("Nullable", "?")
                putIfAbsent("ValueCollectionType", "Collection")
                putIfAbsent("ValueIteratorType", "Iterator")
            } else {
                putIfAbsent("isReferenceValue", false)
                putIfAbsent("Generics", "")
                putIfAbsent("Nullable", "")
                putIfAbsent("ValueCollectionType", "${map["ValueType"]}Collection")
                putIfAbsent("ValueIteratorType", "${map["ValueType"]}Iterator")
            }
        }
    }
}

private fun Sync.generate(sourceSet: String, templates: List<TemplateInstantiation>) {
    templates.forEach { template ->
        template.expansions.forEach { expansion ->
            val fullExpansion = expansion.generateFullExpansion()
            val path = NioPath.of(template.outputFile(fullExpansion))
            val outputFolder = (path.parent ?: NioPath.of(".")).toString()
            val outputFile = path.fileName.toString()
            check(outputFile.endsWith(".kt")) { "$outputFile must end with .kt" }
            into(outputFolder) {
                from("src/$sourceSet/templates/${template.inputFile}")
                rename { outputFile }
                expand(*fullExpansion.toList().toTypedArray())
            }
        }
    }
}
