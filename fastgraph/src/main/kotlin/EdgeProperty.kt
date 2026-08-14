/**
 * Methods dealing with edge properties.
 */
@file:JvmName("EdgeProperties")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.properties.ArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.BooleanArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.BooleanMapEdgeProperty
import io.github.sooniln.fastgraph.properties.ByteArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ByteMapEdgeProperty
import io.github.sooniln.fastgraph.properties.DoubleArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.DoubleMapEdgeProperty
import io.github.sooniln.fastgraph.properties.FloatArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.FloatMapEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableBooleanArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableBooleanMapEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableByteArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableByteMapEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableDoubleArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableDoubleMapEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableFloatArrayEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableFloatMapEdgeProperty
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
import io.github.sooniln.fastgraph.properties.WrapperEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.WrapperIntEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.WrapperLongEdgeKeyProperty
import kotlin.reflect.typeOf

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
public interface EdgeProperty<E> {
    /** The graph this property is associated with. */
    public val graph: Graph

    /** The type of this property. */
    @get:JvmName("getType")
    public val type: StaticType<E>

    /**
     * Retrieves the value associated with the given edge, but has undefined behavior if the edge does not belong to
     * [graph].
     */
    @JvmName("get")
    public operator fun get(edge: Edge): E

    /**
     * Creates a new [MutableEdgeProperty] of the same type and with the given [defaultValueFunction], with all values
     * copied from this property.
     */
    public fun copy(defaultValueFunction: EdgeFunction<E>): MutableEdgeProperty<E> {
        val copy = graph.createEdgeProperty(type, defaultValueFunction)
        copyInto(copy)
        return copy
    }
}

/** See [EdgeProperty.get]. */
@JvmSynthetic
public operator fun <E> EdgeProperty<E>.get(edgeReference: EdgeReference): E = get(edgeReference.unstable)

/** A mutable specialization of [EdgeProperty]. */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface MutableEdgeProperty<E> : EdgeProperty<E> {
    /**
     * Sets the value associated with the given edge, but has undefined behavior if the edge does not belong to [graph].
     */
    @JvmName("set")
    public operator fun set(edge: Edge, value: E)

    /**
     * Sets the value associated with the given edge and returns the previous value, but has undefined behavior if the
     * edge does not belong to [graph].
     */
    @JvmName("put")
    public fun put(edge: Edge, value: E): E {
        val oldValue = get(edge)
        set(edge, value)
        return oldValue
    }
}

/** See [MutableEdgeProperty.set]. */
@JvmSynthetic
public operator fun <E> MutableEdgeProperty<E>.set(edgeReference: EdgeReference, value: E): Unit =
    set(edgeReference.unstable, value)

/** See [MutableEdgeProperty.put]. */
@JvmSynthetic
public fun <E> MutableEdgeProperty<E>.put(edgeReference: EdgeReference, value: E): E =
    put(edgeReference.unstable, value)

/**
 * Creates an [EdgeProperty] for the [Unit] type. This is useful for cases where you are required to specify an
 * [EdgeProperty] but have no useful edge property to use (for example, with a [ValueGraph]). The resulting edge
 * property takes up very little constant space.
 */
public fun unitEdgeProperty(graph: Graph): MutableEdgeProperty<Unit> {
    return object : MutableEdgeProperty<Unit> {
        override val graph: Graph get() = graph
        override val type: StaticType<Unit> get() = staticTypeOf()
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
@JvmName("createEdgeProperty")
public fun <T> createEdgeProperty(
    graph: Graph,
    type: StaticType<T>,
    defaultValueFunction: EdgeFunction<T>
): MutableEdgeProperty<T> {
    if (type.kType == typeOf<Unit>()) {
        return unitEdgeProperty(graph) as MutableEdgeProperty<T>
    }

    return if (graph is ImmutableGraph) {
        if (graph.isEmpty()) {
            emptyEdgeProperty(graph, type)
        } else if (graph is IndexedEdgeGraph) {
            when (type.kType) {
                typeOf<Boolean>() ->
                    ImmutableBooleanArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Boolean>
                    ) as MutableEdgeProperty<T>

                typeOf<Byte>() ->
                    ImmutableByteArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Byte>
                    ) as MutableEdgeProperty<T>

                typeOf<Short>() ->
                    ImmutableShortArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Short>
                    ) as MutableEdgeProperty<T>

                typeOf<Int>() ->
                    ImmutableIntArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Int>
                    ) as MutableEdgeProperty<T>

                typeOf<Long>() ->
                    ImmutableLongArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Long>
                    ) as MutableEdgeProperty<T>

                typeOf<Float>() ->
                    ImmutableFloatArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Float>
                    ) as MutableEdgeProperty<T>

                typeOf<Double>() ->
                    ImmutableDoubleArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Double>
                    ) as MutableEdgeProperty<T>

                else -> ImmutableArrayEdgeProperty(graph, type, defaultValueFunction)
            }
        } else {
            when (type.kType) {
                typeOf<Boolean>() ->
                    ImmutableBooleanMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Boolean>
                    ) as MutableEdgeProperty<T>

                typeOf<Byte>() ->
                    ImmutableByteMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Byte>
                    ) as MutableEdgeProperty<T>

                typeOf<Short>() ->
                    ImmutableShortMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Short>
                    ) as MutableEdgeProperty<T>

                typeOf<Int>() ->
                    ImmutableIntMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Int>
                    ) as MutableEdgeProperty<T>

                typeOf<Long>() ->
                    ImmutableLongMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Long>
                    ) as MutableEdgeProperty<T>

                typeOf<Float>() ->
                    ImmutableFloatMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Float>
                    ) as MutableEdgeProperty<T>

                typeOf<Double>() ->
                    ImmutableDoubleMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Double>
                    ) as MutableEdgeProperty<T>

                else -> ImmutableMapEdgeProperty(graph, type, defaultValueFunction)
            }
        }
    } else {
        if (graph is IndexedEdgeGraph) {
            when (type.kType) {
                typeOf<Boolean>() ->
                    BooleanArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Boolean>
                    ) as MutableEdgeProperty<T>

                typeOf<Byte>() ->
                    ByteArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Byte>
                    ) as MutableEdgeProperty<T>

                typeOf<Short>() ->
                    ShortArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Short>
                    ) as MutableEdgeProperty<T>

                typeOf<Int>() ->
                    IntArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Int>
                    ) as MutableEdgeProperty<T>

                typeOf<Long>() ->
                    LongArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Long>
                    ) as MutableEdgeProperty<T>

                typeOf<Float>() ->
                    FloatArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Float>
                    ) as MutableEdgeProperty<T>

                typeOf<Double>() ->
                    DoubleArrayEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Double>
                    ) as MutableEdgeProperty<T>

                else -> ArrayEdgeProperty(graph, type, defaultValueFunction)
            }
        } else {
            when (type.kType) {
                typeOf<Boolean>() ->
                    BooleanMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Boolean>
                    ) as MutableEdgeProperty<T>

                typeOf<Byte>() ->
                    ByteMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Byte>
                    ) as MutableEdgeProperty<T>

                typeOf<Short>() ->
                    ShortMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Short>
                    ) as MutableEdgeProperty<T>

                typeOf<Int>() ->
                    IntMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Int>
                    ) as MutableEdgeProperty<T>

                typeOf<Long>() ->
                    LongMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Long>
                    ) as MutableEdgeProperty<T>

                typeOf<Float>() ->
                    FloatMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Float>
                    ) as MutableEdgeProperty<T>

                typeOf<Double>() ->
                    DoubleMapEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Double>
                    ) as MutableEdgeProperty<T>

                else -> MapEdgeProperty(graph, type, defaultValueFunction)
            }
        }
    }
}

/**
 * A specialization of [EdgeProperty] where each edge is identified by a unique value, and an [Edge] can thus be
 * retrieved for a unique value.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface EdgeKeyProperty<E> : EdgeProperty<E> {
    public fun hasEdge(key: E): Boolean

    @JvmName("getEdge")
    public fun getEdge(key: E): Edge

    /**
     * Creates a new [MutableEdgeKeyProperty] of the same type and with the given [defaultValueFunction], with all
     * values copied from this property.
     */
    override fun copy(defaultValueFunction: EdgeFunction<E>): MutableEdgeKeyProperty<E> {
        return super.copy(defaultValueFunction).asEdgeKeyProperty()
    }
}

/**
 * A specialization of [EdgeProperty] where each edge is identified by a unique value, and an [Edge] can thus be
 * retrieved for a unique value.
 */
public interface MutableEdgeKeyProperty<E> : EdgeKeyProperty<E>, MutableEdgeProperty<E>

public fun <E> MutableEdgeProperty<E>.asEdgeKeyProperty(): MutableEdgeKeyProperty<E> {
    if (this is MutableEdgeKeyProperty<E>) {
        return this
    }

    @Suppress("UNCHECKED_CAST")
    return when (type.kType) {
        typeOf<Int>() -> WrapperIntEdgeKeyProperty(this as MutableEdgeProperty<Int>) as MutableEdgeKeyProperty<E>
        typeOf<Long>() -> WrapperLongEdgeKeyProperty(this as MutableEdgeProperty<Long>) as MutableEdgeKeyProperty<E>
        else -> WrapperEdgeKeyProperty(this)
    }
}

/**
 * Creates a new [EdgeProperty] which is a transformation of this [EdgeProperty]. The new [EdgeProperty] applies the
 * given [transform] on every [EdgeProperty.get] invocation. The new [EdgeProperty] thus does not actually store any
 * data, and references the input property indefinitely.
 */
@JvmName("map")
public fun <E, O> map(property: EdgeProperty<E>, type: StaticType<O>, transform: (E) -> O): EdgeProperty<O> {
    return object : EdgeProperty<O> {
        override val graph: Graph get() = property.graph
        override val type: StaticType<O> get() = type
        override fun get(edge: Edge): O  = transform(property[edge])
    }
}

/** See [map]. */
@JvmSynthetic
@JvmName("#map")
public fun <E, O> EdgeProperty<E>.map(type: StaticType<O>, transform: (E) -> O): EdgeProperty<O> {
    return map(this, type, transform)
}

/** See [map]. */
@JvmSynthetic
public inline fun <E, reified O> EdgeProperty<E>.map(noinline transform: (E) -> O): EdgeProperty<O> {
    return map(this, staticTypeOf(), transform)
}

/**
 * Creates a new [MutableEdgeProperty] which is a transformation of this [MutableEdgeProperty]. The new
 * [MutableEdgeProperty] applies the given [transform]/[reverseTransform] on every
 * [MutableEdgeProperty.get]/[MutableEdgeProperty.set] invocation. The new [EdgeProperty] thus does not actually store
 * any data, and references the input property indefinitely.
 */
@JvmName("map")
public fun <E, O> map(
    property: MutableEdgeProperty<E>,
    type: StaticType<O>,
    transform: (E) -> O,
    reverseTransform: (O) -> E
): MutableEdgeProperty<O> {
    return object : MutableEdgeProperty<O> {
        override val graph: Graph get() = property.graph
        override val type: StaticType<O> get() = type
        override fun get(edge: Edge): O = transform(property[edge])
        override fun set(edge: Edge, value: O) { property[edge] = reverseTransform(value)}
        override fun put(edge: Edge, value: O) = transform(property.put(edge, reverseTransform(value)))
    }
}

/** See [map]. */
@JvmSynthetic
@JvmName("#mutableEdgePropertyMap")
public fun <E, O> MutableEdgeProperty<E>.map(
    type: StaticType<O>,
    transform: (E) -> O,
    reverseTransform: (O) -> E
): MutableEdgeProperty<O> {
    return map(this, type, transform, reverseTransform)
}

/** See [map]. */
@JvmSynthetic
public inline fun <E, reified O> MutableEdgeProperty<E>.map(
    noinline transform: (E) -> O,
    noinline reverseTransform: (O) -> E
): MutableEdgeProperty<O> {
    return map(this, staticTypeOf(), transform, reverseTransform)
}

/**
 * Convenience function that sets the value of this property to the value from the given property for every edge in the
 * graph.
 */
public fun <E> EdgeProperty<out E>.copyInto(other: MutableEdgeProperty<in E>) {
    for (edge in graph.edges) {
        other[edge] = get(edge)
    }
}

/**
 * Convenience function that casts an [EdgeProperty] to the given type safely (types must match). Does not support
 * casting directly to super-types of the real type - although this may be legal, this function does not have access to
 * enough information to do so safely. Instead, [safeCast] to the real type, and then implicit cast to the super type.
 */
@Suppress("UNCHECKED_CAST")
public inline fun <reified E> EdgeProperty<*>.safeCast(): EdgeProperty<E> {
    val desiredType = typeOf<E>()
    if (!type.mayCastTo(desiredType)) throw TypeCastException("$type cannot be safely cast to $desiredType")
    return this as EdgeProperty<E>
}

/**
 * Convenience function that casts a [MutableEdgeProperty] to the given type safely (types must match). Does not support
 * casting to super-types - although this may be legal, this function does not have access to enough information to do
 * so safely.
 */
@Suppress("UNCHECKED_CAST")
public inline fun <reified E> MutableEdgeProperty<*>.safeCast(): MutableEdgeProperty<E> {
    val desiredType = typeOf<E>()
    if (!type.mayCastTo(desiredType)) throw TypeCastException("$type cannot be safely cast to $desiredType")
    return this as MutableEdgeProperty<E>
}

/** Returns an empty edge property to be associated with an empty [ImmutableGraph]. */
internal fun <T> emptyEdgeProperty(graph: ImmutableGraph, type: StaticType<T>): MutableEdgeProperty<T> {
    require(graph.edges.isEmpty())

    return object : MutableEdgeProperty<T> {
        override val graph: Graph get() = graph
        override val type: StaticType<T> get() = type

        override fun get(edge: Edge): T = throw IllegalArgumentException()

        override fun set(edge: Edge, value: T) = throw IllegalArgumentException()
    }
}
