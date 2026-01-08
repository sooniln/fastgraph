plugins {
    kotlin("jvm") version "2.2.21"
    id("me.champeau.jmh") version "0.7.3"
    id("com.vanniktech.maven.publish") version "0.35.0"
    id("org.jetbrains.dokka-javadoc") version "2.1.0"
}

group = "io.github.sooniln"
version = "0.1.0"

repositories {
    mavenCentral()
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
    implementation(libs.fastutil)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.guava)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    jmhImplementation(libs.guava)
    jmhImplementation(libs.jgrapht)
    jmhImplementation(libs.jol)
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
}

jmh {
    includeTests = false
    verbosity = "EXTRA"

    //jvmArgs.add("-Djdk.attach.allowAttachSelf")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "fastgraph", version.toString())

    pom {
        name = "fastgraph"
        description = "A highly efficient mathematical graph-theory library for JVM."
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
