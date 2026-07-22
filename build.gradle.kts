plugins {
    kotlin("jvm") version "2.4.0"
    id("me.champeau.jmh") version "0.7.3"
    id("com.vanniktech.maven.publish") version "0.35.0"
    id("org.jetbrains.dokka-javadoc") version "2.1.0"
}

group = "io.github.sooniln"
version = "0.2.2"

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("io.github.sooniln:fastcollect-kotlin-jvm:2.0.3")

    testImplementation(libs.bundles.testing)
    testImplementation(libs.guava)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    jmhImplementation(libs.guava)
    jmhImplementation(libs.jgrapht)
    jmhImplementation(libs.jol)
}

private object Generate {
    const val IN_DIR = "src/main/templates"
    const val OUT_DIR = "src/generated/kotlin"

    object HashSetTypes {
        val Files = listOf(
            "GraphHashSet.kte",
        )
        val Expansions = listOf(
            mapOf("Type" to "Int"),
            mapOf("Type" to "Long"),
        )
    }

    object HashMapTypes {
        val Files = listOf(
            "GraphHashMap.kte",
        )
        val Expansions = listOf(
            mapOf("KeyType" to "Int", "ValueType" to "Int", "DefaultValue" to "Int.MIN_VALUE"),
            mapOf("KeyType" to "Int", "ValueType" to "Long", "DefaultValue" to "Long.MIN_VALUE"),
            mapOf("KeyType" to "Int", "ValueType" to "V", "DefaultValue" to "null", "isReferenceValue" to true),
            mapOf("KeyType" to "Long", "ValueType" to "Int", "DefaultValue" to "Int.MIN_VALUE"),
            mapOf("KeyType" to "Long", "ValueType" to "Long", "DefaultValue" to "Long.MIN_VALUE"),
            mapOf("KeyType" to "Long", "ValueType" to "V", "DefaultValue" to "null", "isReferenceValue" to true),
        )
    }

    object FPHashMapTypes {
        val Files = listOf(
            "GraphFPHashMap.kte",
        )
        val Expansions = listOf(
            mapOf("KeyType" to "Int", "ValueType" to "Float", "DefaultValue" to "Float.NaN"),
            mapOf("KeyType" to "Int", "ValueType" to "Double", "DefaultValue" to "Double.NaN"),
            mapOf("KeyType" to "Long", "ValueType" to "Float", "DefaultValue" to "Float.NaN"),
            mapOf("KeyType" to "Long", "ValueType" to "Double", "DefaultValue" to "Double.NaN"),
        )
    }
}

private fun List<Map<String, Any>>.generateFullExpansion(): List<Map<String, Any>> {
    return map { expansion ->
        buildMap {
            putAll(expansion)

            val type = expansion["Type"] as String?
            if (type != null) {
                putIfAbsent("lowerType", type.lowercase())
                putIfAbsent("subpackage", type.lowercase() + "s")

                val isFPType = type == "Float" || type == "Double"
                putIfAbsent("isFPType", isFPType)
                if (isFPType) {
                    val nonFPType = if (type == "Float") "Int" else "Long"
                    putIfAbsent("NonFPType", nonFPType)
                    putIfAbsent("lowerNonFPType", nonFPType.lowercase())
                }
            }

            val keyType = expansion["KeyType"] as String?
            if (keyType != null) {
                putIfAbsent("lowerKeyType", keyType.lowercase())
                putIfAbsent("keySubpackage", keyType.lowercase() + "s")

                val isFPKey = keyType == "Float" || keyType == "Double"
                putIfAbsent("isFPKey", isFPKey)
                if (isFPKey) {
                    val nonFPKeyType = if (keyType == "Float") "Int" else "Long"
                    putIfAbsent("NonFPKeyType", nonFPKeyType)
                    putIfAbsent("lowerNonFPKeyType", nonFPKeyType.lowercase())
                }
            }

            val valueType = expansion["ValueType"] as String?
            if (valueType != null) {
                putIfAbsent("lowerValueType", valueType.lowercase())
                putIfAbsent("valueSubpackage", valueType.lowercase() + "s")

                val isFPValue = valueType == "Float" || valueType == "Double"
                putIfAbsent("isFPValue", isFPValue)
                if (isFPValue) {
                    val nonFPValueType = if (valueType == "Float") "Int" else "Long"
                    putIfAbsent("NonFPValueType", nonFPValueType)
                    putIfAbsent("lowerNonFPValueType", nonFPValueType.lowercase())
                }
            }

            if (keyType != null && valueType != null) {
                putIfAbsent("subpackage", getValue("keySubpackage"))

                val isReferenceValue = expansion["isReferenceValue"] as Boolean? ?: false
                putIfAbsent("isReferenceValue", isReferenceValue)

                if (isReferenceValue) {
                    putIfAbsent("Name", "${keyType}2Any")
                    putIfAbsent("lowerName", "${get("lowerKeyType")}2Any")
                    putIfAbsent("ValueCollectionType", "Collection")
                    putIfAbsent("ValueIteratorType", "Iterator")
                    putIfAbsent("Nullable", "?")
                    putIfAbsent("Generics", "<$valueType>")
                } else {
                    putIfAbsent("Name", "${keyType}2${valueType}")
                    putIfAbsent("lowerName", "${get("lowerKeyType")}2${valueType}")
                    putIfAbsent("ValueCollectionType", "${valueType}Collection")
                    putIfAbsent("ValueIteratorType", "${valueType}Iterator")
                    putIfAbsent("Nullable", "")
                    putIfAbsent("Generics", "")
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
}

private fun Sync.generate(
    inputFiles: List<String>,
    expansions: List<Map<String, Any>>,
    dir: String,
    outputFile: (String, Map<String, Any>) -> String
) {
    inputFiles.forEach { inputFile ->
        expansions.forEach { expansion ->
            into(dir) {
                from("${Generate.IN_DIR}/$inputFile")
                rename { filename -> outputFile(filename, expansion) }
                expand(*expansion.toList().toTypedArray())
            }
        }
    }
}

tasks.register<Sync>("GenerateCollections") {
    description = "Generates source code for primitively typed collection classes from templates."
    group = "build"
    into(Generate.OUT_DIR)

    generate(
        Generate.HashSetTypes.Files,
        Generate.HashSetTypes.Expansions.generateFullExpansion(),
        "primitives/collections"
    )
    { _, expansion ->
        "Graph${expansion["Type"]}HashSet.kt"
    }

    generate(
        Generate.HashMapTypes.Files,
        Generate.HashMapTypes.Expansions.generateFullExpansion(),
        "primitives/collections"
    )
    { _, expansion ->
        "Graph${expansion["Name"]}HashMap.kt"
    }

    generate(
        Generate.FPHashMapTypes.Files,
        Generate.FPHashMapTypes.Expansions.generateFullExpansion(),
        "primitives/collections"
    )
    { _, expansion ->
        "Graph${expansion["Name"]}HashMap.kt"
    }
}

sourceSets {
    main {
        kotlin.srcDir(tasks.named<Sync>("GenerateCollections"))
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
