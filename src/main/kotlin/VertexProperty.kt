package io.github.sooniln.fastgraph

/**
 * A store of property values for vertices. Conceptually this functions a map - mapping vertices to values. Every vertex
 * property is associated with a particular graph, and stores a value for every vertex in the graph. Vertex properties
 * are required to remain in sync with their respective graphs.
 */
interface VertexProperty<out V> {
    /** The graph this property is associated with. */
    val graph: Graph

    /** Retrieves the value associated with the given vertex. */
    operator fun get(vertex: Vertex): V
}

/** See [VertexProperty.get]. */
operator fun <V> VertexProperty<V>.get(vertexReference: VertexReference): V = get(vertexReference.unstable)

interface MutableVertexProperty<V> : VertexProperty<V> {
    /** Sets the value associated with the given vertex. */
    operator fun set(vertex: Vertex, value: V)

    /** Sets the value associated with the given vertex. */
    fun put(vertex: Vertex, value: V): V {
        val oldValue = get(vertex)
        set(vertex, value)
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

/** See [MutableVertexProperty.set]. */
operator fun <V> MutableVertexProperty<V>.set(vertexReference: VertexReference, value: V) =
    set(vertexReference.unstable, value)

/** See [MutableVertexProperty.put]. */
fun <V> MutableVertexProperty<V>.put(vertexReference: VertexReference, value: V): V =
    put(vertexReference.unstable, value)

/**
 * A store of keys for vertices. Each key is enforced to be unique, meaning this [VertexProperty] also supports reverse
 * lookup (looking up a vertex by its key). Conceptually this functions a bimap - mapping vertices to keys and keys to
 * vertices.
 */
interface KeyedVertexProperty<V> : VertexProperty<V> {

    /** Retrieves the vertex associated with the given key. */
    operator fun get(key: V): Vertex
}

interface MutableKeyedVertexProperty<V> : KeyedVertexProperty<V>, MutableVertexProperty<V>

/**
 * Returns an unusable [VertexProperty].
 */
@Suppress("UNCHECKED_CAST")
fun <T> nothingVertexProperty(graph: Graph): MutableKeyedVertexProperty<T> =
    NothingVertexProperty(graph) as MutableKeyedVertexProperty<T>

private class NothingVertexProperty(override val graph: Graph) : MutableKeyedVertexProperty<Nothing> {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): Nothing = throw IllegalStateException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: Nothing) = throw IllegalStateException()

    override fun get(key: Nothing): Vertex = throw IllegalStateException()
}

