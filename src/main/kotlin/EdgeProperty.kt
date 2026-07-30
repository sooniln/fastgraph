package io.github.sooniln.fastgraph

/**
 * A store of property values for vertices. Conceptually this functions a map - mapping vertices to values. Every edge
 * property is associated with a particular graph, and stores a value for every edge in the graph. Edge properties
 * are required to remain in sync with their respective graphs.
 */
interface EdgeProperty<out V> {
    /** The graph this property is associated with. */
    val graph: Graph

    /** Retrieves the value associated with the given edge. */
    operator fun get(edge: Edge): V
}

/** See [EdgeProperty.get]. */
operator fun <V> EdgeProperty<V>.get(edgeReference: EdgeReference): V = get(edgeReference.unstable)

interface MutableEdgeProperty<V> : EdgeProperty<V> {
    /** Sets the value associated with the given edge. */
    operator fun set(edge: Edge, value: V)

    /** Sets the value associated with the given edge. */
    fun put(edge: Edge, value: V): V {
        val oldValue = get(edge)
        set(edge, value)
        return oldValue
    }

    /**
     * Makes a best effort to allocate enough space for the given number of values. Generally only sparse properties
     * will support this.
     */
    fun ensureCapacity(capacity: Int) {}

    /**
     * Makes a best effort to reduce storage capacity to a minimum. Generally only sparse properties
     * will support this.
     */
    fun trimToSize() {}
}

/** See [MutableEdgeProperty.set]. */
operator fun <V> MutableEdgeProperty<V>.set(edgeReference: EdgeReference, value: V) =
    set(edgeReference.unstable, value)

/** See [MutableEdgeProperty.put]. */
fun <V> MutableEdgeProperty<V>.put(edgeReference: EdgeReference, value: V): V =
    put(edgeReference.unstable, value)

/**
 * A store of keys for edges. Each key is enforced to be unique, meaning this [EdgeProperty] also supports reverse
 * lookup (looking up an edge by its key). Conceptually this functions a bimap - mapping edges to keys and keys to
 * edges.
 */
interface KeyedEdgeProperty<V> : EdgeProperty<V> {

    /** Retrieves the edge associated with the given key. */
    operator fun get(key: V): Edge
}

interface MutableKeyedEdgeProperty<V> : KeyedEdgeProperty<V>, MutableEdgeProperty<V>

/**
 * Returns an unusable [EdgeProperty].
 */
@Suppress("UNCHECKED_CAST")
fun <T> nothingEdgeProperty(graph: Graph): MutableKeyedEdgeProperty<T> =
    NothingEdgeProperty(graph) as MutableKeyedEdgeProperty<T>

private class NothingEdgeProperty(override val graph: Graph) : MutableKeyedEdgeProperty<Nothing> {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): Nothing = throw IllegalStateException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: Nothing) = throw IllegalStateException()

    override fun get(key: Nothing): Edge = throw IllegalStateException()
}
