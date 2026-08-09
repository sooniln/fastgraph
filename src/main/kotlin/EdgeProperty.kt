/**
 * Methods dealing with edge properties.
 */
@file:JvmName("EdgeProperties")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.properties.ArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ByteArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ByteMapEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableByteArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableByteMapEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableIntArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableIntMapEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableLongArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableLongMapEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableMapEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableShortArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableShortMapEdgeProperty
import io.github.sooniln.fastgraph.properties.IntArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.IntMapEdgeProperty
import io.github.sooniln.fastgraph.properties.LongArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.LongMapEdgeProperty
import io.github.sooniln.fastgraph.properties.MapEdgeProperty
import io.github.sooniln.fastgraph.properties.ShortArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ShortMapEdgeProperty

/**
 * A store of property values for edges. Conceptually this functions a map - mapping edges to values. Every edge
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
 * Creates an [EdgeProperty] for the [Unit] type. This is useful for cases where you are required to specify an
 * [EdgeProperty] but have no useful edge property to use (for example, with a [ValueGraph]). The resulting edge
 * property takes up very little constant space.
 */
public fun unitEdgeProperty(graph: Graph): MutableEdgeProperty<Unit> {
    return object : MutableEdgeProperty<Unit> {
        override val graph: Graph get() = graph
        override val type: Class<Unit> get() = Unit::class.java
        override fun get(edge: Edge): Unit = Unit
        override fun set(edge: Edge, value: Unit) {}
    }
}

/**
 * Creates an edge property that is as specialized and efficient as possible for the given graph and type. This method
 * guarantees that if [graph] is an [ImmutableGraph], then [defaultValueFunction] will not be referenced after this
 * method completes.
 */
@Suppress("UNCHECKED_CAST")
public fun <T> createEdgeProperty(
    graph: Graph,
    type: Class<T>,
    defaultValueFunction: EdgeFunction<T>
): MutableEdgeProperty<T> {
    return when (type) {
        Unit::class.java -> unitEdgeProperty(graph) as MutableEdgeProperty<T>
        Boolean::class.java ->
            createMapped<Byte, Boolean>(
                graph,
                defaultValueFunction as EdgeFunction<Boolean>,
                { it != 0.toByte() },
                { if (it) 1 else 0 }) as MutableEdgeProperty<T>

        Float::class.java ->
            createMapped<Int, Float>(
                graph,
                defaultValueFunction as EdgeFunction<Float>,
                { Float.fromBits(it) },
                { it.toRawBits() }) as MutableEdgeProperty<T>

        Double::class.java ->
            createMapped<Long, Double>(
                graph,
                defaultValueFunction as EdgeFunction<Double>,
                { Double.fromBits(it) },
                { it.toRawBits() }) as MutableEdgeProperty<T>

        else -> {
            if (graph is ImmutableGraph) {
                if (graph.isEmpty()) {
                    emptyEdgeProperty(graph, type)
                } else if (graph is IndexedEdgeGraph) {
                    when (type) {
                        Byte::class.java ->
                            ImmutableByteArrayEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Byte>
                            ) as MutableEdgeProperty<T>

                        Short::class.java ->
                            ImmutableShortArrayEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Short>
                            ) as MutableEdgeProperty<T>

                        Int::class.java ->
                            ImmutableIntArrayEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Int>
                            ) as MutableEdgeProperty<T>

                        Long::class.java ->
                            ImmutableLongArrayEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Long>
                            ) as MutableEdgeProperty<T>

                        else -> ImmutableArrayEdgeProperty(graph, type, defaultValueFunction)
                    }
                } else {
                    when (type) {
                        Byte::class.java ->
                            ImmutableByteMapEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Byte>
                            ) as MutableEdgeProperty<T>

                        Short::class.java ->
                            ImmutableShortMapEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Short>
                            ) as MutableEdgeProperty<T>

                        Int::class.java ->
                            ImmutableIntMapEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Int>
                            ) as MutableEdgeProperty<T>

                        Long::class.java ->
                            ImmutableLongMapEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Long>
                            ) as MutableEdgeProperty<T>

                        else -> ImmutableMapEdgeProperty(graph, type, defaultValueFunction)
                    }
                }
            } else {
                if (graph is IndexedEdgeGraph) {
                    when (type) {
                        Byte::class.java ->
                            ByteArrayEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Byte>
                            ) as MutableEdgeProperty<T>

                        Short::class.java ->
                            ShortArrayEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Short>
                            ) as MutableEdgeProperty<T>

                        Int::class.java ->
                            IntArrayEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Int>
                            ) as MutableEdgeProperty<T>

                        Long::class.java ->
                            LongArrayEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Long>
                            ) as MutableEdgeProperty<T>

                        else -> ArrayEdgeProperty(graph, type, defaultValueFunction)
                    }
                } else {
                    when (type) {
                        Byte::class.java ->
                            ByteMapEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Byte>
                            ) as MutableEdgeProperty<T>

                        Short::class.java ->
                            ShortMapEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Short>
                            ) as MutableEdgeProperty<T>

                        Int::class.java ->
                            IntMapEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Int>
                            ) as MutableEdgeProperty<T>

                        Long::class.java ->
                            LongMapEdgeProperty(
                                graph,
                                defaultValueFunction as EdgeFunction<Long>
                            ) as MutableEdgeProperty<T>

                        else -> MapEdgeProperty(graph, type, defaultValueFunction)
                    }
                }
            }
        }
    }
}

private inline fun <reified V, reified O> createMapped(
    graph: Graph,
    defaultValueFunction: EdgeFunction<O>,
    crossinline transform: (V) -> O,
    crossinline reverseTransform: (O) -> V
): MutableEdgeProperty<O> {
    val property = createEdgeProperty(graph, V::class.java) { reverseTransform(defaultValueFunction.apply(it)) }
    return object : MutableEdgeProperty<O> {
        override val graph: Graph get() = property.graph
        override val type: Class<O> get() = O::class.java
        override fun get(edge: Edge): O = transform(property[edge])
        override fun set(edge: Edge, value: O) { property[edge] = reverseTransform(value)}
        override fun put(edge: Edge, value: O) = transform(property.put(edge, reverseTransform(value)))
    }
}

/**
 * Creates a new [EdgeProperty] which is a transformation of this [EdgeProperty]. The new [EdgeProperty] applies the
 * given [transform] on every [EdgeProperty.get] invocation.
 */
public fun <V, O> map(property: EdgeProperty<V>, type: Class<O>, transform: (V) -> O): EdgeProperty<O> {
    return object : EdgeProperty<O> {
        override val graph: Graph get() = property.graph
        override val type: Class<O> get() = type
        override fun get(edge: Edge): O  = transform(property[edge])
    }
}

/** See [map]. */
@JvmSynthetic
@JvmName("#map")
public fun <V, O> EdgeProperty<V>.map(type: Class<O>, transform: (V) -> O): EdgeProperty<O> {
    return map(this, type, transform)
}

/** See [map]. */
@JvmSynthetic
public inline fun <V, reified O> EdgeProperty<V>.map(noinline transform: (V) -> O): EdgeProperty<O> {
    return map(this, O::class.java, transform)
}

/**
 * Creates a new [EdgeProperty] which is a transformation of this [EdgeProperty]. The new [EdgeProperty]
 * applies the given [transform] on every [EdgeProperty.get] invocation.
 */
public fun <V, O> map(
    property: MutableEdgeProperty<V>,
    type: Class<O>,
    transform: (V) -> O,
    reverseTransform: (O) -> V
): MutableEdgeProperty<O> {
    return object : MutableEdgeProperty<O> {
        override val graph: Graph get() = property.graph
        override val type: Class<O> get() = type
        override fun get(edge: Edge): O = transform(property[edge])
        override fun set(edge: Edge, value: O) { property[edge] = reverseTransform(value)}
        override fun put(edge: Edge, value: O) = transform(property.put(edge, reverseTransform(value)))
    }
}

/** See [map]. */
@JvmSynthetic
@JvmName("#mutableEdgePropertyMap")
public fun <V, O> MutableEdgeProperty<V>.map(
    type: Class<O>,
    transform: (V) -> O,
    reverseTransform: (O) -> V
): MutableEdgeProperty<O> {
    return map(this, type, transform, reverseTransform)
}

/** See [map]. */
@JvmSynthetic
public inline fun <V, reified O> MutableEdgeProperty<V>.map(
    noinline transform: (V) -> O,
    noinline reverseTransform: (O) -> V
): MutableEdgeProperty<O> {
    return map(this, O::class.java, transform, reverseTransform)
}

/** Returns an empty edge property to be associated with an empty [ImmutableGraph]. */
internal fun <T> emptyEdgeProperty(graph: ImmutableGraph, type: Class<T>): MutableEdgeProperty<T> {
    require(graph.edges.isEmpty())

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
