package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.Graph

/**
 * A store of property values for edges. Conceptually this functions a map - mapping edges to values. Every edge
 * property is associated with a particular graph, and stores a value for every edge in the graph. Edge properties are
 * required to remain in sync with their respective graphs.
 */
interface EdgeProperty<E> {
    /**
     * The graph this property is associated with.
     */
    val graph: Graph

    /**
     * Retrieves the value associated with the given edge.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    operator fun get(edge: Edge): E
}

interface MutableEdgeProperty<E> : EdgeProperty<E> {
    /**
     * Sets the value associated with the given edge.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    operator fun set(edge: Edge, value: E)

    fun put(edge: Edge, value: E): E {
        val oldValue = get(edge)
        set(edge, value)
        return oldValue
    }
}

/**
 * See [EdgeProperty.get].
 */
operator fun <E> EdgeProperty<E>.get(edgeReference: EdgeReference): E = get(edgeReference.unstable)

/**
 * See [EdgeProperty.set].
 */
operator fun <E> MutableEdgeProperty<E>.set(edgeReference: EdgeReference, value: E) = set(edgeReference.unstable, value)

/**
 * Returns an unusable [EdgeProperty].
 */
@Suppress("UNCHECKED_CAST")
fun <T> nothingEdgeProperty(graph: Graph): MutableEdgeProperty<T> = NothingEdgeProperty(graph) as MutableEdgeProperty<T>

private class NothingEdgeProperty(override val graph: Graph) : MutableEdgeProperty<Nothing> {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): Nothing = throw IllegalStateException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: Nothing) = throw IllegalStateException()
}

internal fun EdgeProperty<*>.isNothingProperty() = this is NothingEdgeProperty
