package io.github.sooniln.fastgraph

import com.google.common.reflect.ClassPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.javaMethod

/**
 * Guards against KT-31420: Kotlin requires every override of a method whose signature involves a value
 * class (e.g. [Vertex], [Edge]) to manually repeat an identical `@JvmName`, and does not always catch it at
 * compile time when that repetition is missing or wrong. These tests verify the resulting bytecode directly,
 * since that's the level at which such a mistake actually breaks Java interop.
 */
class JvmNameConsistencyTest {

    private val basePackage = "io.github.sooniln.fastgraph"

    private fun libraryClasses(): List<Class<*>> {
        val classLoader = javaClass.classLoader
        return ClassPath.from(classLoader)
            .getTopLevelClassesRecursive(basePackage)
            .mapNotNull {
                try {
                    Class.forName(it.name, false, classLoader)
                } catch (e: Throwable) {
                    null
                }
            }
            .filter(::isMainClass)
    }

    private fun isMainClass(clazz: Class<*>): Boolean {
        val location = clazz.protectionDomain?.codeSource?.location?.path ?: return false
        return location.replace('\\', '/').contains("/classes/kotlin/main/")
    }

    private fun supertypesOf(clazz: Class<*>): List<Class<*>> {
        val result = mutableListOf<Class<*>>()
        val queue = ArrayDeque<Class<*>>()
        clazz.superclass?.let { queue.add(it) }
        queue.addAll(clazz.interfaces)

        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (next.name.startsWith(basePackage) && result.none { it == next }) {
                result.add(next)
                next.superclass?.let { queue.add(it) }
                queue.addAll(next.interfaces)
            }
        }
        return result
    }

    private fun isCheckableSupertypeMethod(method: Method): Boolean {
        return Modifier.isPublic(method.modifiers) &&
            !Modifier.isStatic(method.modifiers) &&
            !method.isSynthetic &&
            !method.isBridge
    }

    @Test
    fun `overrides preserve the JVM method name declared by their supertype`() {
        val violations = mutableListOf<String>()

        for (clazz in libraryClasses()) {
            if (clazz.isInterface || Modifier.isAbstract(clazz.modifiers) || clazz.isSynthetic) continue
            if (!Modifier.isPublic(clazz.modifiers)) continue

            for (supertype in supertypesOf(clazz)) {
                for (method in supertype.declaredMethods) {
                    if (!isCheckableSupertypeMethod(method)) continue

                    val signature = "${supertype.name}.${method.name}(${method.parameterTypes.joinToString { it.simpleName }})"

                    val override = try {
                        clazz.getMethod(method.name, *method.parameterTypes)
                    } catch (e: NoSuchMethodException) {
                        violations += "${clazz.name} has no method matching $signature - an override's " +
                            "@JvmName likely doesn't match its supertype's"
                        continue
                    }

                    if (Modifier.isAbstract(override.modifiers)) {
                        violations += "${clazz.name} does not concretely implement $signature - the JVM " +
                            "method name likely diverged (missing/incorrect @JvmName)"
                    }
                }
            }
        }

        assertThat(violations).describedAs("JVM method name mismatches between supertypes and their overrides").isEmpty()
    }

    @Test
    fun `JvmName does not silently diverge from the Kotlin declared name`() {
        val violations = mutableListOf<String>()

        for (clazz in libraryClasses()) {
            if (clazz.isSynthetic || !Modifier.isPublic(clazz.modifiers)) continue

            val kClass = try {
                clazz.kotlin
            } catch (e: Throwable) {
                continue
            }

            for (function in kClass.declaredMemberFunctions) {
                val javaMethod = try {
                    function.javaMethod
                } catch (e: Throwable) {
                    null
                } ?: continue

                if (!Modifier.isPublic(javaMethod.modifiers)) continue

                val kotlinName = function.name
                val jvmName = javaMethod.name

                if (jvmName.contains('-')) {
                    violations += "${clazz.name}.$kotlinName compiles to JVM name '$jvmName', which contains " +
                        "a mangling dash - @JvmName is likely completely missing on a value-class-typed member"
                    continue
                }

                if (!jvmName.contains(kotlinName)) {
                    violations += "${clazz.name}.$kotlinName compiles to JVM name '$jvmName', which does not " +
                        "contain the Kotlin declared name - @JvmName likely has the wrong value"
                }
            }
        }

        assertThat(violations).describedAs("@JvmName values inconsistent with their Kotlin declared name").isEmpty()
    }
}
