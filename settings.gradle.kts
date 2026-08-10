pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "fastgraph"

plugins {
    kotlin("jvm") version "2.4.0" apply false
}

include("fastgraph")
include("jmh")
