package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.properties.ArrayVertexProperty
import io.github.sooniln.fastgraph.properties.MapVertexProperty

/**
 * A store of property values for vertices. Conceptually this functions a map - mapping vertices to values. Every vertex
 * property is associated with a particular graph, and stores a value for every vertex in the graph. Vertex properties
 * are required to remain in sync with their respective graphs.
 *
 * It is not the property's responsibility to track vertices on your behalf - [VertexProperty] has undefined behavior if
 * you pass in a vertex that does not belong to the same graph as the property. Some implementations may throw
 * exceptions, and some implementations may silently return invalid data.
 */
public interface VertexProperty<out V> {
    /** The graph this property is associated with. */
    public val graph: Graph

    /** The type of this property. */
    public val type: Class<@UnsafeVariance V>

    /**
     * Retrieves the value associated with the given vertex, but has undefined behavior if the vertex does not belong to
     * [graph].
     */
    public operator fun get(vertex: Vertex): V
}

/** See [VertexProperty.get]. */
public operator fun <V> VertexProperty<V>.get(vertexReference: VertexReference): V = get(vertexReference.unstable)

/** A mutable specialization of VertexProperty. */
public interface MutableVertexProperty<V> : VertexProperty<V> {
    /**
     * Sets the value associated with the given vertex, but has undefined behavior if the vertex does not belong to
     * [graph].
     */
    public operator fun set(vertex: Vertex, value: V)

    /**
     * Sets the value associated with the given vertex and returns the previous value, but has undefined behavior if
     * the vertex does not belong to [graph].
     */
    public fun put(vertex: Vertex, value: V): V {
        val oldValue = get(vertex)
        set(vertex, value)
        return oldValue
    }
}

/** See [MutableVertexProperty.set]. */
public operator fun <V> MutableVertexProperty<V>.set(vertexReference: VertexReference, value: V): Unit =
    set(vertexReference.unstable, value)

/** See [MutableVertexProperty.put]. */
public fun <V> MutableVertexProperty<V>.put(vertexReference: VertexReference, value: V): V =
    put(vertexReference.unstable, value)

/**
 * Methods dealing with [VertexProperty].
 */
public object VertexProperties {

    /**
     * Creates a new [VertexProperty] which is a transformation of this [VertexProperty]. The new [VertexProperty]
     * applies the given [transform] on every [VertexProperty.get] invocation.
     */
    public fun <V, O> map(property: VertexProperty<V>, type: Class<O>, transform: (V) -> O): VertexProperty<O> {
        return object : VertexProperty<O> {
            override val graph: Graph get() = property.graph
            override val type: Class<O> get() = type
            override fun get(vertex: Vertex): O = transform(property[vertex])
        }
    }

    /**
     * Creates a [VertexProperty] for the [Unit] type. This is useful for cases where you are required to specify an
     * [VertexProperty] but have no useful vertex property to use (for example, with a [ValueGraph]). The resulting
     * vertex property takes up very little constant space.
     */
    public fun unitVertexProperty(graph: Graph): MutableVertexProperty<Unit> {
        return object : MutableVertexProperty<Unit> {
            override val graph: Graph get() = graph
            override val type: Class<Unit> get() = Unit::class.java

            @Suppress("INAPPLICABLE_JVM_NAME")
            @JvmName("get")
            override fun get(vertex: Vertex): Unit = Unit

            @Suppress("INAPPLICABLE_JVM_NAME")
            @JvmName("set")
            override fun set(vertex: Vertex, value: Unit) {}
        }
    }

    /** Returns an empty vertex property to be associated with an empty [ImmutableGraph]. */
    internal fun <T> emptyVertexProperty(graph: ImmutableGraph, type: Class<T>): MutableVertexProperty<T> {
        require(graph.vertices.isEmpty())

        return object : MutableVertexProperty<T> {
            override val graph: Graph get() = graph
            override val type: Class<T> get() = type

            @Suppress("INAPPLICABLE_JVM_NAME")
            @JvmName("get")
            override fun get(vertex: Vertex): T = throw IllegalArgumentException()

            @Suppress("INAPPLICABLE_JVM_NAME")
            @JvmName("set")
            override fun set(vertex: Vertex, value: T) = throw IllegalArgumentException()
        }
    }

    /** Returns an empty vertex property to be associated with an empty [ImmutableGraph]. */
    internal inline fun <reified T> emptyVertexProperty(graph: ImmutableGraph): MutableVertexProperty<T> {
        return emptyVertexProperty(graph, T::class.java)
    }

    /**
     * Creates an edge property that is as specialized and efficient as possible for the given graph and type.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T> createVertexProperty(
        graph: Graph,
        type: Class<T>,
        initializer: VertexInitializer<T>
    ): MutableVertexProperty<T> {
        return if (type == Unit::class.java) {
            unitVertexProperty(graph) as MutableVertexProperty<T>
        } else if (graph is ImmutableGraph && graph.isEmpty()) {
            emptyVertexProperty(graph, type)
        } else if (graph is IndexedVertexGraph) {
            ArrayVertexProperty(graph, type, initializer)
        } else {
            MapVertexProperty(graph, type, initializer)
        }
    }
}

/** See [VertexProperties.map]. */
@JvmSynthetic
public fun <V, O> VertexProperty<V>.map(type: Class<O>, transform: (V) -> O): VertexProperty<O> {
    return VertexProperties.map(this, type, transform)
}

/** See [VertexProperties.map]. */
@JvmSynthetic
public inline fun <V, reified O> VertexProperty<V>.map(noinline transform: (V) -> O): VertexProperty<O> {
    return VertexProperties.map(this, O::class.java, transform)
}

