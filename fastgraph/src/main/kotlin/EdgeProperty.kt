/**
 * Methods dealing with edge properties.
 */
@file:JvmName("EdgeProperties")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.properties.AnyEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.AnyEdgeProperty
import io.github.sooniln.fastgraph.properties.AnyIdentityIndexedEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.AnyIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.AnyIndexedEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.AnyIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.BooleanEdgeProperty
import io.github.sooniln.fastgraph.properties.BooleanIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.BooleanIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ByteEdgeProperty
import io.github.sooniln.fastgraph.properties.ByteIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ByteIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.DoubleEdgeProperty
import io.github.sooniln.fastgraph.properties.DoubleIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.DoubleIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.EdgeEdgeProperty
import io.github.sooniln.fastgraph.properties.EdgeIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.EdgeIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.FloatEdgeProperty
import io.github.sooniln.fastgraph.properties.FloatIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.FloatIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableAnyEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableAnyIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableAnyIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableBooleanEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableBooleanIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableBooleanIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableByteEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableByteIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableByteIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableDoubleEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableDoubleIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableDoubleIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableEdgeEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableEdgeIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableEdgeIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableFloatEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableFloatIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableFloatIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableIntEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableIntIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableIntIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableLongEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableLongIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.ImmutableLongIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.IntEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.IntEdgeProperty
import io.github.sooniln.fastgraph.properties.IntIdentityIndexedEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.IntIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.IntIndexedEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.IntIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.LongEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.LongEdgeProperty
import io.github.sooniln.fastgraph.properties.LongIdentityIndexedEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.LongIdentityIndexedEdgeProperty
import io.github.sooniln.fastgraph.properties.LongIndexedEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.LongIndexedEdgeProperty
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
    public val type: PropertyType<E>

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
public operator fun <E> EdgeProperty<out E>.get(edgeReference: EdgeReference): E = get(edgeReference.unstable)

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
public operator fun <E> MutableEdgeProperty<in E>.set(edgeReference: EdgeReference, value: E): Unit =
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
        override val type: PropertyType<Unit> get() = propertyTypeOf()
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
    type: PropertyType<T>,
    defaultValueFunction: EdgeFunction<T>
): MutableEdgeProperty<T> {
    if (type.kType == typeOf<Unit>()) {
        return unitEdgeProperty(graph) as MutableEdgeProperty<T>
    }

    return if (graph is ImmutableGraph) {
        if (graph.isEmpty()) {
            emptyEdgeProperty(graph, type)
        } else if (graph is IdentityIndexedEdgeGraph) {
            when (type.kType) {
                typeOf<Boolean>() ->
                    ImmutableBooleanIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Boolean>
                    ) as MutableEdgeProperty<T>

                typeOf<Byte>() ->
                    ImmutableByteIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Byte>
                    ) as MutableEdgeProperty<T>

                typeOf<Int>() ->
                    ImmutableIntIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Int>
                    ) as MutableEdgeProperty<T>

                typeOf<Long>() ->
                    ImmutableLongIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Long>
                    ) as MutableEdgeProperty<T>

                typeOf<Float>() ->
                    ImmutableFloatIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Float>
                    ) as MutableEdgeProperty<T>

                typeOf<Double>() ->
                    ImmutableDoubleIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Double>
                    ) as MutableEdgeProperty<T>

                typeOf<Edge>() ->
                    ImmutableEdgeIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Edge>
                    ) as MutableEdgeProperty<T>

                else -> ImmutableAnyIdentityIndexedEdgeProperty(graph, type, defaultValueFunction)
            }
        } else if (graph is IndexedEdgeGraph) {
            when (type.kType) {
                typeOf<Boolean>() ->
                    ImmutableBooleanIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Boolean>
                    ) as MutableEdgeProperty<T>

                typeOf<Byte>() ->
                    ImmutableByteIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Byte>
                    ) as MutableEdgeProperty<T>

                typeOf<Int>() ->
                    ImmutableIntIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Int>
                    ) as MutableEdgeProperty<T>

                typeOf<Long>() ->
                    ImmutableLongIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Long>
                    ) as MutableEdgeProperty<T>

                typeOf<Float>() ->
                    ImmutableFloatIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Float>
                    ) as MutableEdgeProperty<T>

                typeOf<Double>() ->
                    ImmutableDoubleIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Double>
                    ) as MutableEdgeProperty<T>

                typeOf<Edge>() ->
                    ImmutableEdgeIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Edge>
                    ) as MutableEdgeProperty<T>

                else -> ImmutableAnyIndexedEdgeProperty(graph, type, defaultValueFunction)
            }
        } else {
            when (type.kType) {
                typeOf<Boolean>() ->
                    ImmutableBooleanEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Boolean>
                    ) as MutableEdgeProperty<T>

                typeOf<Byte>() ->
                    ImmutableByteEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Byte>
                    ) as MutableEdgeProperty<T>

                typeOf<Int>() ->
                    ImmutableIntEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Int>
                    ) as MutableEdgeProperty<T>

                typeOf<Long>() ->
                    ImmutableLongEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Long>
                    ) as MutableEdgeProperty<T>

                typeOf<Float>() ->
                    ImmutableFloatEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Float>
                    ) as MutableEdgeProperty<T>

                typeOf<Double>() ->
                    ImmutableDoubleEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Double>
                    ) as MutableEdgeProperty<T>

                typeOf<Edge>() ->
                    ImmutableEdgeEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Edge>
                    ) as MutableEdgeProperty<T>

                else -> ImmutableAnyEdgeProperty(graph, type, defaultValueFunction)
            }
        }
    } else {
        if (graph is IdentityIndexedEdgeGraph) {
            when (type.kType) {
                typeOf<Boolean>() ->
                    BooleanIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Boolean>
                    ) as MutableEdgeProperty<T>

                typeOf<Byte>() ->
                    ByteIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Byte>
                    ) as MutableEdgeProperty<T>

                typeOf<Int>() ->
                    IntIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Int>
                    ) as MutableEdgeProperty<T>

                typeOf<Long>() ->
                    LongIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Long>
                    ) as MutableEdgeProperty<T>

                typeOf<Float>() ->
                    FloatIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Float>
                    ) as MutableEdgeProperty<T>

                typeOf<Double>() ->
                    DoubleIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Double>
                    ) as MutableEdgeProperty<T>

                typeOf<Edge>() ->
                    EdgeIdentityIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Edge>
                    ) as MutableEdgeProperty<T>

                else -> AnyIdentityIndexedEdgeProperty(graph, type, defaultValueFunction)
            }
        } else if (graph is IndexedEdgeGraph) {
            when (type.kType) {
                typeOf<Boolean>() ->
                    BooleanIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Boolean>
                    ) as MutableEdgeProperty<T>

                typeOf<Byte>() ->
                    ByteIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Byte>
                    ) as MutableEdgeProperty<T>

                typeOf<Int>() ->
                    IntIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Int>
                    ) as MutableEdgeProperty<T>

                typeOf<Long>() ->
                    LongIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Long>
                    ) as MutableEdgeProperty<T>

                typeOf<Float>() ->
                    FloatIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Float>
                    ) as MutableEdgeProperty<T>

                typeOf<Double>() ->
                    DoubleIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Double>
                    ) as MutableEdgeProperty<T>

                typeOf<Edge>() ->
                    EdgeIndexedEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Edge>
                    ) as MutableEdgeProperty<T>

                else -> AnyIndexedEdgeProperty(graph, type, defaultValueFunction)
            }
        } else {
            when (type.kType) {
                typeOf<Boolean>() ->
                    BooleanEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Boolean>
                    ) as MutableEdgeProperty<T>

                typeOf<Byte>() ->
                    ByteEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Byte>
                    ) as MutableEdgeProperty<T>

                typeOf<Int>() ->
                    IntEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Int>
                    ) as MutableEdgeProperty<T>

                typeOf<Long>() ->
                    LongEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Long>
                    ) as MutableEdgeProperty<T>

                typeOf<Float>() ->
                    FloatEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Float>
                    ) as MutableEdgeProperty<T>

                typeOf<Double>() ->
                    DoubleEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Double>
                    ) as MutableEdgeProperty<T>

                typeOf<Edge>() ->
                    EdgeEdgeProperty(
                        graph,
                        defaultValueFunction as EdgeFunction<Edge>
                    ) as MutableEdgeProperty<T>

                else -> AnyEdgeProperty(graph, type, defaultValueFunction)
            }
        }
    }
}

/**
 * A specialization of [EdgeProperty] where each edge is identified by a unique key, and an [Edge] can thus be
 * retrieved for a key.
 *
 * An EdgeKeyProperty has no default value - an edge has no key until one is explicitly set via [set]. While any edge in
 * [graph] has no key, the property is incomplete and every read ([get], [hasEdge], [getEdge], [copy], [put], etc...)
 * will fail with [IllegalStateException].
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface EdgeKeyProperty<E> : EdgeProperty<E> {
    /** Returns true if some edge has the given key. */
    public fun hasEdge(key: E): Boolean

    /**
     * Retrieves the edge for the given key, or throws [NoSuchElementException] if there is no such edge.
     */
    @JvmName("getEdge")
    public fun getEdge(key: E): Edge

    /**
     * Creates a new [MutableEdgeKeyProperty] of the same type, with all keys copied from this property.
     */
    public fun copy(): MutableEdgeKeyProperty<E> {
        val copy = graph.createEdgeKeyProperty(type)
        copyInto(copy)
        return copy
    }
}

/**
 * A mutable specialization of [EdgeKeyProperty]. [set] is the only operation permitted while the property is
 * incomplete (see [EdgeKeyProperty]). [set] does nothing if the value is already the key of the given edge, and
 * throws [IllegalArgumentException] if the value is the key of a different edge. [put] behaves as [set] but
 * additionally returns the previous key.
 */
public interface MutableEdgeKeyProperty<E> : EdgeKeyProperty<E>, MutableEdgeProperty<E>

/**
 * Creates an edge key property that is as specialized and efficient as possible for the given graph and type. See
 * [EdgeKeyProperty] for the semantics of key properties.
 */
@Suppress("UNCHECKED_CAST")
@JvmName("createEdgeKeyProperty")
public fun <T> createEdgeKeyProperty(graph: Graph, type: PropertyType<T>): MutableEdgeKeyProperty<T> {
    return if (graph is IdentityIndexedEdgeGraph) {
        when (type.kType) {
            typeOf<Int>() -> IntIdentityIndexedEdgeKeyProperty(graph) as MutableEdgeKeyProperty<T>
            typeOf<Long>() -> LongIdentityIndexedEdgeKeyProperty(graph) as MutableEdgeKeyProperty<T>
            else -> AnyIdentityIndexedEdgeKeyProperty(graph, type)
        }
    } else if (graph is IndexedEdgeGraph) {
        when (type.kType) {
            typeOf<Int>() -> IntIndexedEdgeKeyProperty(graph) as MutableEdgeKeyProperty<T>
            typeOf<Long>() -> LongIndexedEdgeKeyProperty(graph) as MutableEdgeKeyProperty<T>
            else -> AnyIndexedEdgeKeyProperty(graph, type)
        }
    } else {
        when (type.kType) {
            typeOf<Int>() -> IntEdgeKeyProperty(graph) as MutableEdgeKeyProperty<T>
            typeOf<Long>() -> LongEdgeKeyProperty(graph) as MutableEdgeKeyProperty<T>
            else -> AnyEdgeKeyProperty(graph, type)
        }
    }
}

/**
 * Creates a new [EdgeProperty] which is a transformation of this [EdgeProperty]. The new [EdgeProperty] applies the
 * given [transform] on every [EdgeProperty.get] invocation. The new [EdgeProperty] thus does not actually store any
 * data, and references the input property indefinitely.
 */
@JvmName("map")
public fun <E, O> map(property: EdgeProperty<out E>, type: PropertyType<O>, transform: (E) -> O): EdgeProperty<out O> {
    return object : EdgeProperty<O> {
        override val graph: Graph get() = property.graph
        override val type: PropertyType<O> get() = type
        override fun get(edge: Edge): O  = transform(property[edge])
    }
}

/** See [map]. */
@JvmSynthetic
@JvmName("#map")
public fun <E, O> EdgeProperty<out E>.map(type: PropertyType<O>, transform: (E) -> O): EdgeProperty<out O> {
    return map(this, type, transform)
}

/** See [map]. */
@JvmSynthetic
public inline fun <E, reified O> EdgeProperty<out E>.map(noinline transform: (E) -> O): EdgeProperty<out O> {
    return map(this, propertyTypeOf(), transform)
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
    type: PropertyType<O>,
    transform: (E) -> O,
    reverseTransform: (O) -> E
): MutableEdgeProperty<O> {
    return object : MutableEdgeProperty<O> {
        override val graph: Graph get() = property.graph
        override val type: PropertyType<O> get() = type
        override fun get(edge: Edge): O = transform(property[edge])
        override fun set(edge: Edge, value: O) { property[edge] = reverseTransform(value)}
        override fun put(edge: Edge, value: O) = transform(property.put(edge, reverseTransform(value)))
    }
}

/** See [map]. */
@JvmSynthetic
@JvmName("#mutableEdgePropertyMap")
public fun <E, O> MutableEdgeProperty<E>.map(
    type: PropertyType<O>,
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
    return map(this, propertyTypeOf(), transform, reverseTransform)
}

/**
 * Convenience function that sets the value of [other] to the value of this property for every edge in [graph]. This
 * differs from [copyFrom] in which graph has its edges iterated. If both properties belong to the same graph, then
 * the two methods are interchangeable.
 *
 * Note that if the properties are from different graphs, this is only a meaningful operation if there is some
 * meaningful connection between the graphs.
 */
public fun <E> EdgeProperty<out E>.copyInto(other: MutableEdgeProperty<in E>) {
    for (edge in graph.edges) {
        other[edge] = get(edge)
    }
}

/**
 * Convenience function that sets the value of [other] to the value of this property for every edge in [graph]. This
 * differs from [copyInto] in which graph has its edges iterated. If both properties belong to the same graph, then
 * the two methods are interchangeable.
 *
 * Note that if the properties are from different graphs, this is only a meaningful operation if there is some
 * meaningful connection between the graphs.
 */
public fun <E> MutableEdgeProperty<in E>.copyFrom(other: EdgeProperty<out E>) {
    for (edge in graph.edges) {
        set(edge, other[edge])
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

/**
 * Convenience function that casts an [EdgeKeyProperty] to the given type safely (types must match). Does not support
 * casting directly to super-types of the real type - although this may be legal, this function does not have access to
 * enough information to do so safely. Instead, [safeCast] to the real type, and then implicit cast to the super type.
 */
@Suppress("UNCHECKED_CAST")
public inline fun <reified E> EdgeKeyProperty<*>.safeCast(): EdgeKeyProperty<E> {
    val desiredType = typeOf<E>()
    if (!type.mayCastTo(desiredType)) throw TypeCastException("$type cannot be safely cast to $desiredType")
    return this as EdgeKeyProperty<E>
}

/**
 * Convenience function that casts a [MutableEdgeKeyProperty] to the given type safely (types must match).
 */
@Suppress("UNCHECKED_CAST")
public inline fun <reified E> MutableEdgeKeyProperty<*>.safeCast(): MutableEdgeKeyProperty<E> {
    val desiredType = typeOf<E>()
    if (!type.mayCastTo(desiredType)) throw TypeCastException("$type cannot be safely cast to $desiredType")
    return this as MutableEdgeKeyProperty<E>
}

/** Returns an empty edge property to be associated with an empty [ImmutableGraph]. */
internal fun <T> emptyEdgeProperty(graph: ImmutableGraph, type: PropertyType<T>): MutableEdgeProperty<T> {
    require(graph.edges.isEmpty())

    return object : MutableEdgeProperty<T> {
        override val graph: Graph get() = graph
        override val type: PropertyType<T> get() = type

        override fun get(edge: Edge): T = throw IllegalArgumentException()
        override fun set(edge: Edge, value: T) = throw IllegalArgumentException()
    }
}

/** Returns an empty edge key property to be associated with an empty [ImmutableGraph]. */
internal fun <T> emptyEdgeKeyProperty(graph: ImmutableGraph, type: PropertyType<T>): MutableEdgeKeyProperty<T> {
    require(graph.edges.isEmpty())

    return object : MutableEdgeKeyProperty<T> {
        override val graph: Graph get() = graph
        override val type: PropertyType<T> get() = type

        override fun get(edge: Edge): T = throw IllegalArgumentException()
        override fun set(edge: Edge, value: T) = throw IllegalArgumentException()
        override fun hasEdge(key: T): Boolean = false
        override fun getEdge(key: T): Edge = throw NoSuchElementException()
    }
}
