import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

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
    api(project(":fastgraph"))
    implementation(libs.fastcollect)

    testImplementation(libs.assertJ)
    testImplementation(libs.junitCore)
    testImplementation(libs.junitParams)
    testImplementation(kotlin("reflect"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named("compileJava", JavaCompile::class.java) {
    options.compilerArgumentProviders.add(CommandLineArgumentProvider {
        // provide compiled Kotlin classes to javac – needed for java/kotlin mixed sources to work
        listOf("--patch-module", "io.github.sooniln.fastgraph.io=${sourceSets["main"].output.asPath}")
    })
}

tasks.test {
    useJUnitPlatform()
    enableAssertions = true

    // some tests read the abi file for verifications
    dependsOn("checkKotlinAbi")
}

dokka {
    moduleName.set("FastGraph IO")
    dokkaPublications.html {
        failOnWarning.set(true)
    }

    dokkaSourceSets.all {
        includes.from("module.md")
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl.set(uri("https://github.com/sooniln/fastgraph/blob/main/fastgraph-io/src/main/kotlin"))
            remoteLineSuffix.set("#L")
        }
        externalDocumentationLinks.register("fastgraph") {
            val fastgraph = project(":fastgraph")
            url.set(provider { uri("https://javadoc.io/doc/io.github.sooniln/fastgraph/${fastgraph.version}/") })
            packageListUrl.set(fastgraph.layout.buildDirectory.file("dokka/html/-fast-graph/package-list").map { it.asFile.toURI() })
        }
    }
}

tasks.named("dokkaGeneratePublicationHtml") {
    dependsOn(":fastgraph:dokkaGeneratePublicationHtml")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "fastgraph-io", version.toString())

    pom {
        name = "fastgraph-io"
        description = "I/O utilities for several common graph formats for the FastGraph library."
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
