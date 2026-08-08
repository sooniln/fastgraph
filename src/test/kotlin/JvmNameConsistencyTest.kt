package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.reflect.jvm.kotlinFunction

/**
 * Guards against KT-31420: Kotlin mangles the compiled JVM name of any public function/property whose
 * parameter or return type is a value class (e.g. [Vertex], [Edge]) unless `@JvmName` is explicitly applied,
 * silently breaking Java interop. This is checked directly against `api/fastgraph.api`, the project's Kotlin
 * ABI dump - it already reflects the exact, ground-truth public JVM API surface (internal/private/anonymous
 * declarations never appear in it), so no reflection or visibility filtering of our own is needed. The `test`
 * Gradle task depends on `updateKotlinAbi` so this file is always current when this test runs.
 */
class JvmNameConsistencyTest {

    private val abiFile = File("api/fastgraph.api")

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

    // Compiler-generated value-class boilerplate: these are never marked `synthetic` in the ABI dump, and have
    // no source-level declaration to attach @JvmName to. `equals-impl0` is deliberately NOT included here -
    // see jvmNameMatchesKotlinName().
    private val valueClassBoilerplateNames = setOf("constructor-impl", "equals-impl", "equals-impl0", "hashCode-impl")

    /**
     * Publicly visible APIs (any API in the API file) should never contain mangled names.
     */
    @Test
    fun unmangledPublicApis() {
        val violations = mutableListOf<String>()

        for ((className, modifiers, name) in parseAbiFunctions()) {
            if ("synthetic" in modifiers) continue
            if (name in valueClassBoilerplateNames) continue
            if ('-' !in name) continue

            violations += "$className.$name"
        }

        assertThat(violations).describedAs("Public APIs with mangled names").isEmpty()
    }

    /**
     * Top level methods are compiled into XXXKt classes by default - this results in non-idiomatic code in Java. This
     * should either be handled by renaming the class wrapping the top level methods (ie to just XXX), or by marking top
     * level methods as synthetic (and thus inaccessible from Java) if their functionality is available to Java in some
     * other fashion.
     */
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

    val allowedJvmNameMismatches = mapOf(
        "io/github/sooniln/fastgraph/Homomorphism.getVertex" to "get",
        "io/github/sooniln/fastgraph/Homomorphism.getEdge" to "get",
        "io/github/sooniln/fastgraph/IdentityIsomorphism.getVertex" to "get",
        "io/github/sooniln/fastgraph/IdentityIsomorphism.getEdge" to "get",
    )

    /**
     * JvmName annotations should match the Kotlin function name wherever possible to reduce API confusion.
     */
    @Test
    fun jvmNameMismatch() {
        val violations = mutableListOf<String>()

        for ((className, modifiers, name) in parseAbiFunctions()) {
            if ("synthetic" in modifiers) continue
            if (name in valueClassBoilerplateNames) continue

            val clazz = Class.forName(className.replace('/', '.'), false, javaClass.classLoader)
            val method = clazz.declaredMethods.firstOrNull { it.name == name } ?: continue
            val kotlinName = method.kotlinFunction?.name ?: continue

            if (name != kotlinName) {
                if (allowedJvmNameMismatches["$className.$name"] == kotlinName) continue

                violations += "Kotlin('$kotlinName') != JVM('$name') in $className"
            }
        }

        assertThat(violations).describedAs("@JvmName inconsistent with Kotlin name").isEmpty()
    }
}
