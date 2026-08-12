package io.github.sooniln.fastgraph.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.reflect.jvm.kotlinFunction

/**
 * Tests the exposed JVM API for mistakes. See fastgraph's ApiTest for more detail on the intent of each check.
 */
class ApiTest {

    private val abiFile = File("api/fastgraph-io.api")

    private data class AbiFunction(val className: String, val modifiers: List<String>, val name: String)

    private fun parseAbiFunctions(): List<AbiFunction> {
        val functions = mutableListOf<AbiFunction>()
        var currentClass: String? = null

        for (line in abiFile.readLines()) {
            if (line.isEmpty()) continue

            if (!line.startsWith("\t")) {
                currentClass = if (line == "}") null else Regex("""\bclass\s+(\S+)""").find(line)?.groupValues?.get(1)
                continue
            }

            val clazz = currentClass ?: continue
            val tokens = line.trim().split(Regex("\\s+"))
            val funIndex = tokens.indexOf("fun")
            if (funIndex == -1 || funIndex + 1 >= tokens.size) continue

            functions += AbiFunction(clazz, tokens.subList(0, funIndex), tokens[funIndex + 1])
        }

        return functions
    }

    private val valueClassBoilerplateSuffixes = setOf("-impl", "-impl0")

    @Test
    fun unmangledPublicApis() {
        val violations = mutableListOf<String>()

        for ((className, modifiers, name) in parseAbiFunctions()) {
            if ("synthetic" in modifiers) continue
            if (valueClassBoilerplateSuffixes.any { name.endsWith(it) }) continue
            if ('-' !in name) continue

            violations += "$className.$name"
        }

        assertThat(violations).describedAs("Public APIs with mangled names").isEmpty()
    }

    @Test
    fun syntheticTopLevelFunctions() {
        val violations = mutableListOf<String>()

        for ((className, modifiers, name) in parseAbiFunctions()) {
            if (!className.endsWith("Kt")) continue
            if ("public" !in modifiers) continue
            if ("synthetic" in modifiers) continue

            violations += "$className.$name"
        }

        assertThat(violations).describedAs("Top-level functions").isEmpty()
    }

    @Test
    fun jvmNameMismatch() {
        val violations = mutableListOf<String>()

        for ((className, modifiers, name) in parseAbiFunctions()) {
            if ("synthetic" in modifiers) continue
            if (valueClassBoilerplateSuffixes.any { name.endsWith(it) }) continue

            val clazz = Class.forName(className.replace('/', '.'), false, javaClass.classLoader)
            val method = clazz.declaredMethods.firstOrNull { it.name == name } ?: continue
            val kotlinName = method.kotlinFunction?.name ?: continue

            if (name != kotlinName) {
                violations += "Kotlin('$kotlinName') != JVM('$name') in $className"
            }
        }

        assertThat(violations).describedAs("@JvmName inconsistent with Kotlin name").isEmpty()
    }
}
