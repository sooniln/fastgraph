package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexReference

/**
 * A store of property values for vertices. Conceptually this functions a map - mapping vertices to values. Every vertex
 * property is associated with a particular graph, and stores a value for every vertex in the graph. Vertex properties
 * are required to remain in sync with their respective graphs.
 */
interface VertexProperty<V> {
    /**
     * The graph this property is associated with.
     */
    val graph: Graph

    /**
     * Retrieves the value associated with the given vertex.
     */
    operator fun get(vertex: Vertex): V
}

interface MutableVertexProperty<V> : VertexProperty<V> {
    /**
     * Sets the value associated with the given vertex.
     */
    operator fun set(vertex: Vertex, value: V)

    /**
     * Sets the value associated with the given vertex.
     */
    fun put(vertex: Vertex, value: V): V {
        val oldValue = get(vertex)
        set(vertex, value)
        return oldValue
    }
}

/**
 * See [VertexProperty.get].
 */
operator fun <V> VertexProperty<V>.get(vertexReference: VertexReference): V = get(vertexReference.unstable)

/**
 * See [VertexProperty.set].
 */
operator fun <V> MutableVertexProperty<V>.set(vertexReference: VertexReference, value: V) =
    set(vertexReference.unstable, value)

/**
 * Returns an unusable [VertexProperty].
 */
@Suppress("UNCHECKED_CAST")
fun <T> nothingVertexProperty(graph: Graph): MutableVertexProperty<T> = NothingVertexProperty(graph) as MutableVertexProperty<T>

private class NothingVertexProperty(override val graph: Graph) : MutableVertexProperty<Nothing> {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): Nothing = throw IllegalStateException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: Nothing) = throw IllegalStateException()
}

internal fun VertexProperty<*>.isNothingProperty() = this is NothingVertexProperty
