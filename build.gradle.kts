import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import kotlin.text.lowercase
import java.nio.file.Path as NioPath

plugins {
    kotlin("jvm") version "2.4.0"
    id("me.champeau.jmh") version "0.7.3"
    id("com.vanniktech.maven.publish") version "0.35.0"
    id("org.jetbrains.dokka-javadoc") version "2.1.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}

group = "io.github.sooniln"
version = "0.2.2"

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
    explicitApi()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("io.github.sooniln:fastcollect-kotlin-jvm:2.0.3")

    testImplementation(libs.bundles.testing)
    testImplementation(libs.guava)
    testImplementation(kotlin("reflect"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    jmhImplementation(libs.guava)
    jmhImplementation(libs.jgrapht)
    jmhImplementation(libs.jol)
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
            putIfAbsent("lowerType", type.lowercase())

            val isFPType = type == "Float" || type == "Double"
            putIfAbsent("isFPType", isFPType)
            if (isFPType) {
                val nonFPType = if (type == "Float") "Int" else "Long"
                putIfAbsent("NonFPType", nonFPType)
                putIfAbsent("lowerNonFPType", nonFPType.lowercase())
            }
        }

        val kvType = map["KVType"] as String?
        if (kvType != null) {
            putIfAbsent("lowerKVType", kvType.lowercase())
            putIfAbsent("Name", "${kvType}2${kvType}")
            putIfAbsent("lowerName", "${get("lowerKVType")}2${kvType}")

            val arrayType = map["ArrayType"] as String?
            if (arrayType != null) {
                putIfAbsent("lowerArrayType", arrayType.lowercase())
            }
        }

        val keyType = map["KeyType"] as String?
        if (keyType != null) {
            putIfAbsent("lowerKeyType", keyType.lowercase())

            val isFPKey = keyType == "Float" || keyType == "Double"
            putIfAbsent("isFPKey", isFPKey)
            if (isFPKey) {
                val nonFPKeyType = if (keyType == "Float") "Int" else "Long"
                putIfAbsent("NonFPKeyType", nonFPKeyType)
                putIfAbsent("lowerNonFPKeyType", nonFPKeyType.lowercase())
            }
        }

        val valueType = map["ValueType"] as String?
        if (valueType != null) {
            putIfAbsent("lowerValueType", valueType.lowercase())

            val isFPValue = valueType == "Float" || valueType == "Double"
            putIfAbsent("isFPValue", isFPValue)
            if (isFPValue) {
                val nonFPValueType = if (valueType == "Float") "Int" else "Long"
                putIfAbsent("NonFPValueType", nonFPValueType)
                putIfAbsent("lowerNonFPValueType", nonFPValueType.lowercase())
            }
        }

        if (keyType != null && valueType != null) {
            val isReferenceValue = map["isReferenceValue"] as Boolean? ?: false
            putIfAbsent("isReferenceValue", isReferenceValue)

            if (isReferenceValue) {
                putIfAbsent("Name", "${keyType}2Any")
                putIfAbsent("lowerName", "${get("lowerKeyType")}2Any")
                putIfAbsent("ValueCollectionType", "Collection")
                putIfAbsent("ValueIteratorType", "Iterator")
                putIfAbsent("Nullable", "?")
                putIfAbsent("Generics", "<$valueType>")
                putIfAbsent("StarGenerics", "<*>")
            } else {
                putIfAbsent("Name", "${keyType}2${valueType}")
                putIfAbsent("lowerName", "${get("lowerKeyType")}2${valueType}")
                putIfAbsent("ValueCollectionType", "${valueType}Collection")
                putIfAbsent("ValueIteratorType", "${valueType}Iterator")
                putIfAbsent("Nullable", "")
                putIfAbsent("Generics", "")
                putIfAbsent("StarGenerics", "")
            }

            val isFPKeyOrValue = get("isFPKey") as Boolean || get("isFPValue") as Boolean
            putIfAbsent("isFPKeyOrValue", isFPKeyOrValue)
            if (isFPKeyOrValue) {
                val nonFPKeyType = getOrElse("NonFPKeyType") { getValue("KeyType") } as String
                val nonFPValueType = getOrElse("NonFPValueType") { getValue("ValueType") } as String

                put("NonFPName", "${nonFPKeyType}2${nonFPValueType}")
                putIfAbsent("lowerNonFPName", "${get("lowernonFPKeyType")}2${valueType}")
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

tasks.register<Sync>("GenerateMain") {
    description = "Generates source code for templates in main."
    group = "build"
    into("src/mainGenerated/kotlin")

    generate("commonMain",
        listOf(
            TemplateInstantiation(
                "GraphHashSet.kte",
                listOf(
                    mapOf("Type" to "Int"),
                    mapOf("Type" to "Long"),
                )) { expansion -> "primitives/collections/Graph${expansion["Type"]}HashSet.kt" },
        TemplateInstantiation(
            "GraphHashMap.kte",
            listOf(
                mapOf("KeyType" to "Int", "ValueType" to "Int", "DefaultValue" to "Int.MIN_VALUE"),
                mapOf("KeyType" to "Int", "ValueType" to "Long", "DefaultValue" to "Long.MIN_VALUE"),
                mapOf("KeyType" to "Int", "ValueType" to "V", "DefaultValue" to "null", "isReferenceValue" to true),
            )) { expansion -> "primitives/collections/Graph${expansion["Name"]}HashMap.kt" },
        TemplateInstantiation(
            "GraphFPHashMap.kte",
            listOf(
                mapOf("KeyType" to "Int", "ValueType" to "Float", "DefaultValue" to "Float.NaN"),
                mapOf("KeyType" to "Int", "ValueType" to "Double", "DefaultValue" to "Double.NaN"),
            )) { expansion -> "primitives/collections/Graph${expansion["Name"]}HashMap.kt" },
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

dokka {
    dokkaPublications.javadoc {
        moduleName.set("FastGraph")
        outputDirectory.set(layout.buildDirectory.dir("documentation/javadoc"))
        includes.from("README.md")
    }

    dokkaSourceSets.main {
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl.set(uri("https://github.com/sooniln/fastgraph/blob/main/"))
            remoteLineSuffix.set("#L")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    enableAssertions = true
}

jmh {
    includeTests = false
    verbosity = "EXTRA"
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
