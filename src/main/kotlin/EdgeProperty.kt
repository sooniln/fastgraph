package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.properties.ArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.MapEdgeProperty

/**
 * A store of property values for vertices. Conceptually this functions a map - mapping edges to values. Every edge
 * property is associated with a particular graph, and stores a value for every edge in the graph. Edge properties
 * are required to remain in sync with their respective graphs.
 *
 * It is not the property's responsibility to track edges on your behalf - [EdgeProperty] has undefined behavior if
 * you pass in an edge that does not belong to the same graph as the property. Some implementations may throw
 * exceptions, and some implementations may silently return invalid data.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface EdgeProperty<out V> {
    /** The graph this property is associated with. */
    public val graph: Graph

    /** The type of this property. */
    public val type: Class<@UnsafeVariance V>

    /**
     * Retrieves the value associated with the given edge, but has undefined behavior if the edge does not belong to
     * [graph].
     */
    @JvmName("get")
    public operator fun get(edge: Edge): V
}

/** See [EdgeProperty.get]. */
@JvmSynthetic
public operator fun <V> EdgeProperty<V>.get(edgeReference: EdgeReference): V = get(edgeReference.unstable)

/** A mutable specialization of [EdgeProperty]. */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface MutableEdgeProperty<V> : EdgeProperty<V> {
    /**
     * Sets the value associated with the given edge, but has undefined behavior if the edge does not belong to [graph].
     */
    @JvmName("set")
    public operator fun set(edge: Edge, value: V)

    /**
     * Sets the value associated with the given edge and returns the previous value, but has undefined behavior if the
     * edge does not belong to [graph].
     */
    @JvmName("put")
    public fun put(edge: Edge, value: V): V {
        val oldValue = get(edge)
        set(edge, value)
        return oldValue
    }
}

/** See [MutableEdgeProperty.set]. */
@JvmSynthetic
public operator fun <V> MutableEdgeProperty<V>.set(edgeReference: EdgeReference, value: V): Unit =
    set(edgeReference.unstable, value)

/** See [MutableEdgeProperty.put]. */
@JvmSynthetic
public fun <V> MutableEdgeProperty<V>.put(edgeReference: EdgeReference, value: V): V =
    put(edgeReference.unstable, value)

/**
 * Methods dealing with [EdgeProperty].
 */
public object EdgeProperties {
    /**
     * Creates a new [EdgeProperty] which is a transformation of this [EdgeProperty]. The new [EdgeProperty] applies the
     * given [transform] on every [EdgeProperty.get] invocation.
     */
    @JvmStatic
    public fun <V, O> map(property: EdgeProperty<V>, type: Class<O>, transform: (V) -> O): EdgeProperty<O> {
        return object : EdgeProperty<O> {
            override val graph: Graph get() = property.graph
            override val type: Class<O> get() = type
            override fun get(edge: Edge): O  = transform(property[edge])
        }
    }

    /**
     * Creates an [EdgeProperty] for the [Unit] type. This is useful for cases where you are required to specify an
     * [EdgeProperty] but have no useful edge property to use (for example, with a [ValueGraph]). The resulting edge
     * property takes up very little constant space.
     */
    @JvmStatic
    public fun unitEdgeProperty(graph: Graph): MutableEdgeProperty<Unit> {
        return object : MutableEdgeProperty<Unit> {
            override val graph: Graph get() = graph
            override val type: Class<Unit> get() = Unit::class.java
            override fun get(edge: Edge): Unit = Unit
            override fun set(edge: Edge, value: Unit) {}
        }
    }

    /**
     * Creates an edge property that is as specialized and efficient as possible for the given graph and type.
     */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    public fun <T> createEdgeProperty(
        graph: Graph,
        type: Class<T>,
        initializer: EdgeInitializer<T>
    ): MutableEdgeProperty<T> {
        return if (type == Unit::class.java) {
            unitEdgeProperty(graph) as MutableEdgeProperty<T>
        } else if (graph is ImmutableGraph && graph.isEmpty()) {
            emptyEdgeProperty(graph, type)
        } else if (graph is IndexedEdgeGraph) {
            ArrayEdgeProperty(graph, type, initializer)
        } else {
            MapEdgeProperty(graph, type, initializer)
        }
    }
}

/** See [EdgeProperties.map]. */
@JvmSynthetic
public fun <V, O> EdgeProperty<V>.map(type: Class<O>, transform: (V) -> O): EdgeProperty<O> {
    return EdgeProperties.map(this, type, transform)
}

/** See [EdgeProperties.map]. */
@JvmSynthetic
public inline fun <V, reified O> EdgeProperty<V>.map(noinline transform: (V) -> O): EdgeProperty<O> {
    return EdgeProperties.map(this, O::class.java, transform)
}

/** Returns an empty edge property to be associated with an empty [ImmutableGraph]. */
internal fun <T> emptyEdgeProperty(graph: ImmutableGraph, type: Class<T>): MutableEdgeProperty<T> {
    require(graph.vertices.isEmpty())

    return object : MutableEdgeProperty<T> {
        override val graph: Graph get() = graph
        override val type: Class<T> get() = type

        override fun get(edge: Edge): T = throw IllegalArgumentException()

        override fun set(edge: Edge, value: T) = throw IllegalArgumentException()
    }
}

/** Returns an empty edge property to be associated with an empty [ImmutableGraph]. */
internal inline fun <reified T> emptyEdgeProperty(graph: ImmutableGraph): MutableEdgeProperty<T> {
    return emptyEdgeProperty(graph, T::class.java)
}
