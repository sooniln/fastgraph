/**
 * Methods dealing with vertex properties.
 */
@file:JvmName("VertexProperties")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.properties.ArrayVertexProperty
import io.github.sooniln.fastgraph.properties.BooleanArrayVertexProperty
import io.github.sooniln.fastgraph.properties.BooleanMapVertexProperty
import io.github.sooniln.fastgraph.properties.ByteArrayVertexProperty
import io.github.sooniln.fastgraph.properties.ByteMapVertexProperty
import io.github.sooniln.fastgraph.properties.DoubleArrayVertexProperty
import io.github.sooniln.fastgraph.properties.DoubleMapVertexProperty
import io.github.sooniln.fastgraph.properties.FloatArrayVertexProperty
import io.github.sooniln.fastgraph.properties.FloatMapVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableArrayVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableBooleanArrayVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableBooleanMapVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableByteArrayVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableByteMapVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableDoubleArrayVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableDoubleMapVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableFloatArrayVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableFloatMapVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableIntArrayVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableIntMapVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableLongArrayVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableLongMapVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableMapVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableShortArrayVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableShortMapVertexProperty
import io.github.sooniln.fastgraph.properties.IntArrayVertexProperty
import io.github.sooniln.fastgraph.properties.IntMapVertexProperty
import io.github.sooniln.fastgraph.properties.LongArrayVertexProperty
import io.github.sooniln.fastgraph.properties.LongMapVertexProperty
import io.github.sooniln.fastgraph.properties.MapVertexProperty
import io.github.sooniln.fastgraph.properties.ShortArrayVertexProperty
import io.github.sooniln.fastgraph.properties.ShortMapVertexProperty
import kotlin.reflect.typeOf

/**
 * A store of property values for vertices. Conceptually this functions a map - mapping vertices to values. Every vertex
 * property is associated with a particular graph, and stores a value for every vertex in the graph. Vertex properties
 * are required to remain in sync with their respective graphs.
 *
 * It is not the property's responsibility to track vertices on your behalf - [VertexProperty] has undefined behavior if
 * you pass in a vertex that does not belong to the same graph as the property. Some implementations may throw
 * exceptions, and some implementations may silently return invalid data.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface VertexProperty<out V> {
    /** The graph this property is associated with. */
    public val graph: Graph

    /** The type of this property. */
    @get:JvmName("getType")
    public val type: TypeReference<@UnsafeVariance V>

    /**
     * Retrieves the value associated with the given vertex, but has undefined behavior if the vertex does not belong to
     * [graph].
     */
    @JvmName("get")
    public operator fun get(vertex: Vertex): V
}

/** See [VertexProperty.get]. */
@JvmSynthetic
public operator fun <V> VertexProperty<V>.get(vertexReference: VertexReference): V = get(vertexReference.unstable)

/** A mutable specialization of VertexProperty. */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface MutableVertexProperty<V> : VertexProperty<V> {
    /**
     * Sets the value associated with the given vertex, but has undefined behavior if the vertex does not belong to
     * [graph].
     */
    @JvmName("set")
    public operator fun set(vertex: Vertex, value: V)

    /**
     * Sets the value associated with the given vertex and returns the previous value, but has undefined behavior if
     * the vertex does not belong to [graph].
     */
    @JvmName("put")
    public fun put(vertex: Vertex, value: V): V {
        val oldValue = get(vertex)
        set(vertex, value)
        return oldValue
    }
}

/** See [MutableVertexProperty.set]. */
@JvmSynthetic
public operator fun <V> MutableVertexProperty<V>.set(vertexReference: VertexReference, value: V): Unit =
    set(vertexReference.unstable, value)

/** See [MutableVertexProperty.put]. */
@JvmSynthetic
public fun <V> MutableVertexProperty<V>.put(vertexReference: VertexReference, value: V): V =
    put(vertexReference.unstable, value)

/**
 * Creates a [VertexProperty] for the [Unit] type. This is useful for cases where you are required to specify an
 * [VertexProperty] but have no useful vertex property to use (for example, with a [ValueGraph]). The resulting
 * vertex property takes up very little constant space.
 */
public fun unitVertexProperty(graph: Graph): MutableVertexProperty<Unit> {
    return object : MutableVertexProperty<Unit> {
        override val graph: Graph get() = graph
        override val type: TypeReference<Unit> get() = TypeReference.of()
        override fun get(vertex: Vertex): Unit = Unit
        override fun set(vertex: Vertex, value: Unit) {}
    }
}

/**
 * Creates a vertex property that is as specialized and efficient as possible for the given graph and type. This method
 * guarantees that if [graph] is an [ImmutableGraph], then [defaultValueFunction] will not be referenced after this
 * method completes.
 */
@Suppress("UNCHECKED_CAST")
@JvmName("createVertexProperty")
public fun <T> createVertexProperty(
    graph: Graph,
    type: TypeReference<T>,
    defaultValueFunction: VertexFunction<T>
): MutableVertexProperty<T> {
    if (type.kType == typeOf<Unit>()) {
        return unitVertexProperty(graph) as MutableVertexProperty<T>
    }

    return if (graph is ImmutableGraph) {
        if (graph.isEmpty()) {
            emptyVertexProperty(graph, type)
        } else if (graph is IndexedVertexGraph) {
            when (type.kType) {
                typeOf<Boolean>() ->
                    ImmutableBooleanArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Boolean>
                    ) as MutableVertexProperty<T>

                typeOf<Byte>() ->
                    ImmutableByteArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Byte>
                    ) as MutableVertexProperty<T>

                typeOf<Short>() ->
                    ImmutableShortArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Short>
                    ) as MutableVertexProperty<T>

                typeOf<Int>() ->
                    ImmutableIntArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Int>
                    ) as MutableVertexProperty<T>

                typeOf<Long>() ->
                    ImmutableLongArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Long>
                    ) as MutableVertexProperty<T>

                typeOf<Float>() ->
                    ImmutableFloatArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Float>
                    ) as MutableVertexProperty<T>

                typeOf<Double>() ->
                    ImmutableDoubleArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Double>
                    ) as MutableVertexProperty<T>

                else -> ImmutableArrayVertexProperty(graph, type, defaultValueFunction)
            }
        } else {
            when (type.kType) {
                typeOf<Boolean>() ->
                    ImmutableBooleanMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Boolean>
                    ) as MutableVertexProperty<T>

                typeOf<Byte>() ->
                    ImmutableByteMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Byte>
                    ) as MutableVertexProperty<T>

                typeOf<Short>() ->
                    ImmutableShortMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Short>
                    ) as MutableVertexProperty<T>

                typeOf<Int>() ->
                    ImmutableIntMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Int>
                    ) as MutableVertexProperty<T>

                typeOf<Long>() ->
                    ImmutableLongMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Long>
                    ) as MutableVertexProperty<T>

                typeOf<Float>() ->
                    ImmutableFloatMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Float>
                    ) as MutableVertexProperty<T>

                typeOf<Double>() ->
                    ImmutableDoubleMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Double>
                    ) as MutableVertexProperty<T>

                else -> ImmutableMapVertexProperty(graph, type, defaultValueFunction)
            }
        }
    } else {
        if (graph is IndexedVertexGraph) {
            when (type.kType) {
                typeOf<Boolean>() ->
                    BooleanArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Boolean>
                    ) as MutableVertexProperty<T>

                typeOf<Byte>() ->
                    ByteArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Byte>
                    ) as MutableVertexProperty<T>

                typeOf<Short>() ->
                    ShortArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Short>
                    ) as MutableVertexProperty<T>

                typeOf<Int>() ->
                    IntArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Int>
                    ) as MutableVertexProperty<T>

                typeOf<Long>() ->
                    LongArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Long>
                    ) as MutableVertexProperty<T>

                typeOf<Float>() ->
                    FloatArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Float>
                    ) as MutableVertexProperty<T>

                typeOf<Double>() ->
                    DoubleArrayVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Double>
                    ) as MutableVertexProperty<T>

                else -> ArrayVertexProperty(graph, type, defaultValueFunction)
            }
        } else {
            when (type.kType) {
                typeOf<Boolean>() ->
                    BooleanMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Boolean>
                    ) as MutableVertexProperty<T>

                typeOf<Byte>() ->
                    ByteMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Byte>
                    ) as MutableVertexProperty<T>

                typeOf<Short>() ->
                    ShortMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Short>
                    ) as MutableVertexProperty<T>

                typeOf<Int>() ->
                    IntMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Int>
                    ) as MutableVertexProperty<T>

                typeOf<Long>() ->
                    LongMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Long>
                    ) as MutableVertexProperty<T>

                typeOf<Float>() ->
                    FloatMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Float>
                    ) as MutableVertexProperty<T>

                typeOf<Double>() ->
                    DoubleMapVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Double>
                    ) as MutableVertexProperty<T>

                else -> MapVertexProperty(graph, type, defaultValueFunction)
            }
        }
    }
}

/**
 * Creates a new [VertexProperty] which is a transformation of this [VertexProperty]. The new [VertexProperty]
 * applies the given [transform] on every [VertexProperty.get] invocation.
 */
@JvmName("map")
public fun <V, O> map(property: VertexProperty<V>, type: TypeReference<O>, transform: (V) -> O): VertexProperty<O> {
    return object : VertexProperty<O> {
        override val graph: Graph get() = property.graph
        override val type: TypeReference<O> get() = type
        override fun get(vertex: Vertex): O = transform(property[vertex])
    }
}

/** See [map]. */
@JvmSynthetic
@JvmName("#vertexPropertyMap")
public fun <V, O> VertexProperty<V>.map(type: TypeReference<O>, transform: (V) -> O): VertexProperty<O> {
    return map(this, type, transform)
}

/** See [map]. */
@JvmSynthetic
public inline fun <V, reified O> VertexProperty<V>.map(noinline transform: (V) -> O): VertexProperty<O> {
    return map(this, TypeReference.of(), transform)
}

/**
 * Creates a new [VertexProperty] which is a transformation of this [VertexProperty]. The new [VertexProperty]
 * applies the given [transform] on every [VertexProperty.get] invocation.
 */
@JvmName("map")
public fun <V, O> map(
    property: MutableVertexProperty<V>,
    type: TypeReference<O>,
    transform: (V) -> O,
    reverseTransform: (O) -> V
): MutableVertexProperty<O> {
    return object : MutableVertexProperty<O> {
        override val graph: Graph get() = property.graph
        override val type: TypeReference<O> get() = type
        override fun get(vertex: Vertex): O = transform(property[vertex])
        override fun set(vertex: Vertex, value: O) { property[vertex] = reverseTransform(value)}
        override fun put(vertex: Vertex, value: O) = transform(property.put(vertex, reverseTransform(value)))
    }
}

/** See [map]. */
@JvmSynthetic
@JvmName("#mutableVertexPropertyMap")
public fun <V, O> MutableVertexProperty<V>.map(
    type: TypeReference<O>,
    transform: (V) -> O,
    reverseTransform: (O) -> V
): MutableVertexProperty<O> {
    return map(this, type, transform, reverseTransform)
}

/** See [map]. */
@JvmSynthetic
public inline fun <V, reified O> MutableVertexProperty<V>.map(
    noinline transform: (V) -> O,
    noinline reverseTransform: (O) -> V
): MutableVertexProperty<O> {
    return map(this, TypeReference.of(), transform, reverseTransform)
}

/** Returns an empty vertex property to be associated with an empty [ImmutableGraph]. */
internal fun <T> emptyVertexProperty(graph: ImmutableGraph, type: TypeReference<T>): MutableVertexProperty<T> {
    require(graph.vertices.isEmpty())

    return object : MutableVertexProperty<T> {
        override val graph: Graph get() = graph
        override val type: TypeReference<T> get() = type

        override fun get(vertex: Vertex): T = throw IllegalArgumentException()

        override fun set(vertex: Vertex, value: T) = throw IllegalArgumentException()
    }
}

