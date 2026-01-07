plugins {
    kotlin("jvm") version "2.2.21"
    id("me.champeau.jmh") version "0.7.3"
}

group = "io.github.sooniln"
version = "0.1.0-SNAPSHOT"

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

tasks.test {
    useJUnitPlatform()
}

jmh {
    includeTests = false
    verbosity = "EXTRA"

    //jvmArgs.add("-Djdk.attach.allowAttachSelf")
}
