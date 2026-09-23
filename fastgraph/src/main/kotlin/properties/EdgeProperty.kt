/**
 * Methods dealing with edge properties.
 */
@file:JvmName("EdgeProperties")

package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastgraph.references.EdgeReference
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
    public val graph: io.github.sooniln.fastgraph.Graph

    /** The type of this property. */
    @get:JvmName("getType")
    public val type: PropertyType<E>

    /**
     * Retrieves the value associated with the given edge, but has undefined behavior if the edge does not belong to
     * [graph].
     */
    @JvmName("get")
    public operator fun get(edge: io.github.sooniln.fastgraph.Edge): E

    /**
     * Creates a new [MutableEdgeProperty] of the same type and with the given [defaultValueFunction], with all values
     * copied from this property.
     */
    public fun copy(defaultValueFunction: io.github.sooniln.fastgraph.EdgeFunction<E>): MutableEdgeProperty<E> {
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
    public operator fun set(edge: io.github.sooniln.fastgraph.Edge, value: E)

    /**
     * Sets the value associated with the given edge and returns the previous value, but has undefined behavior if the
     * edge does not belong to [graph].
     */
    @JvmName("put")
    public fun put(edge: io.github.sooniln.fastgraph.Edge, value: E): E {
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
 * Returns a new [MutableEdgeProperty] with the given graph owner which passes all property functionality through to
 * this property.
 */
internal fun <E> MutableEdgeProperty<E>.reparent(graph: io.github.sooniln.fastgraph.Graph): MutableEdgeProperty<E> {
    return object : MutableEdgeProperty<E> by this {
        override val graph: io.github.sooniln.fastgraph.Graph = graph
    }
}

/**
 * Creates an [EdgeProperty] for the [Unit] type. This is useful for cases where you are required to specify an
 * [EdgeProperty] but have no useful edge property to use (for example, with a [io.github.sooniln.fastgraph.ValueGraph]). The resulting edge
 * property takes up very little constant space.
 */
public fun unitEdgeProperty(graph: io.github.sooniln.fastgraph.Graph): MutableEdgeProperty<Unit> {
    return object : MutableEdgeProperty<Unit> {
        override val graph: io.github.sooniln.fastgraph.Graph get() = graph
        override val type: PropertyType<Unit> get() = propertyTypeOf()
        override fun get(edge: io.github.sooniln.fastgraph.Edge): Unit = Unit
        override fun set(edge: io.github.sooniln.fastgraph.Edge, value: Unit) {}
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T, K> io.github.sooniln.fastgraph.EdgeFunction<T>.unsafeCast() = this as io.github.sooniln.fastgraph.EdgeFunction<K>

/**
 * Creates an edge property that is as specialized and efficient as possible for the given graph and type. This method
 * guarantees that if [graph] is an [io.github.sooniln.fastgraph.ImmutableGraph], then [defaultValueFunction] will not be referenced after this
 * method completes.
 */
@Suppress("UNCHECKED_CAST")
@JvmName("createEdgeProperty")
public fun <T> createEdgeProperty(
    graph: io.github.sooniln.fastgraph.Graph,
    type: PropertyType<T>,
    defaultValueFunction: io.github.sooniln.fastgraph.EdgeFunction<T>
): MutableEdgeProperty<T> {
    if (type.kType == typeOf<Unit>()) {
        return unitEdgeProperty(graph) as MutableEdgeProperty<T>
    }

    if (graph is io.github.sooniln.fastgraph.ImmutableGraph) {
        if (graph.isEmpty()) {
            return emptyEdgeProperty(graph, type)
        }

        return when (val edges = graph.edges) {
            is io.github.sooniln.fastgraph.IdentityIndexedEdgeSet -> {
                when (type.kType) {
                    typeOf<Boolean>() -> ImmutableBooleanIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Byte>() -> ImmutableByteIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Int>() -> ImmutableIntIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Long>() -> ImmutableLongIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Float>() -> ImmutableFloatIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Double>() -> ImmutableDoubleIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<io.github.sooniln.fastgraph.Edge>() -> ImmutableEdgeIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    else -> ImmutableAnyIdentityIndexedEdgeProperty(graph, type, defaultValueFunction)
                }
            }
            is io.github.sooniln.fastgraph.IndexedEdgeSet -> {
                when (type.kType) {
                    typeOf<Boolean>() -> ImmutableBooleanIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    typeOf<Byte>() -> ImmutableByteIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    typeOf<Int>() -> ImmutableIntIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    typeOf<Long>() -> ImmutableLongIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    typeOf<Float>() -> ImmutableFloatIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    typeOf<Double>() -> ImmutableDoubleIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    typeOf<io.github.sooniln.fastgraph.Edge>() -> ImmutableEdgeIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    else -> ImmutableAnyIndexedEdgeProperty(graph, edges, type, defaultValueFunction)
                }
            }
            else -> {
                when (type.kType) {
                    typeOf<Boolean>() -> ImmutableBooleanEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Byte>() -> ImmutableByteEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Int>() -> ImmutableIntEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Long>() -> ImmutableLongEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Float>() -> ImmutableFloatEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Double>() -> ImmutableDoubleEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<io.github.sooniln.fastgraph.Edge>() -> ImmutableEdgeEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    else -> ImmutableAnyEdgeProperty(graph, type, defaultValueFunction)
                }
            }
        } as MutableEdgeProperty<T>
    } else {
        return when (val edges = graph.edges) {
            is io.github.sooniln.fastgraph.IdentityIndexedEdgeSet -> {
                when (type.kType) {
                    typeOf<Boolean>() -> BooleanIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Byte>() -> ByteIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Int>() -> IntIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Long>() -> LongIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Float>() -> FloatIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Double>() -> DoubleIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<io.github.sooniln.fastgraph.Edge>() -> EdgeIdentityIndexedEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    else -> AnyIdentityIndexedEdgeProperty(graph, type, defaultValueFunction)
                }
            }
            is io.github.sooniln.fastgraph.IndexedEdgeSet -> {
                when (type.kType) {
                    typeOf<Boolean>() -> BooleanIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    typeOf<Byte>() -> ByteIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    typeOf<Int>() -> IntIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    typeOf<Long>() -> LongIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    typeOf<Float>() -> FloatIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    typeOf<Double>() -> DoubleIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    typeOf<io.github.sooniln.fastgraph.Edge>() -> EdgeIndexedEdgeProperty(graph, edges, defaultValueFunction.unsafeCast())
                    else -> AnyIndexedEdgeProperty(graph, edges, type, defaultValueFunction)
                }
            }
            else -> {
                when (type.kType) {
                    typeOf<Boolean>() -> BooleanEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Byte>() -> ByteEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Int>() -> IntEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Long>() -> LongEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Float>() -> FloatEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<Double>() -> DoubleEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    typeOf<io.github.sooniln.fastgraph.Edge>() -> EdgeEdgeProperty(graph, defaultValueFunction.unsafeCast())
                    else -> AnyEdgeProperty(graph, type, defaultValueFunction)
                }
            }
        } as MutableEdgeProperty<T>
    }
}

/**
 * A specialization of [EdgeProperty] where each edge is identified by a unique key, and an [io.github.sooniln.fastgraph.Edge] can thus be
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
    public fun getEdge(key: E): io.github.sooniln.fastgraph.Edge

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
 * Returns a new [MutableEdgeKeyProperty] with the given graph owner which passes all property functionality through to
 * this property.
 */
internal fun <E> MutableEdgeKeyProperty<E>.reparent(graph: io.github.sooniln.fastgraph.Graph): MutableEdgeKeyProperty<E> {
    return object : MutableEdgeKeyProperty<E> by this {
        override val graph: io.github.sooniln.fastgraph.Graph = graph
    }
}

/**
 * Creates an edge key property that is as specialized and efficient as possible for the given graph and type. See
 * [EdgeKeyProperty] for the semantics of key properties.
 */
@Suppress("UNCHECKED_CAST")
@JvmName("createEdgeKeyProperty")
public fun <T> createEdgeKeyProperty(graph: io.github.sooniln.fastgraph.Graph, type: PropertyType<T>): MutableEdgeKeyProperty<T> {
    if (graph is io.github.sooniln.fastgraph.ImmutableGraph && graph.isEmpty()) {
        return emptyEdgeProperty(graph, type)
    }

    // TODO: specialize for immutable graphs?
    return when (val edges = graph.edges) {
        is io.github.sooniln.fastgraph.IdentityIndexedEdgeSet -> {
            when (type.kType) {
                typeOf<Int>() -> IntIdentityIndexedEdgeKeyProperty(graph)
                typeOf<Long>() -> LongIdentityIndexedEdgeKeyProperty(graph)
                typeOf<io.github.sooniln.fastgraph.Edge>() -> EdgeIdentityIndexedEdgeKeyProperty(graph)
                else -> AnyIdentityIndexedEdgeKeyProperty(graph, type)
            }
        }
        is io.github.sooniln.fastgraph.IndexedEdgeSet -> {
            when (type.kType) {
                typeOf<Int>() -> IntIndexedEdgeKeyProperty(graph, edges)
                typeOf<Long>() -> LongIndexedEdgeKeyProperty(graph, edges)
                typeOf<io.github.sooniln.fastgraph.Edge>() -> EdgeIndexedEdgeKeyProperty(graph, edges)
                else -> AnyIndexedEdgeKeyProperty(graph, edges, type)
            }
        }
        else -> {
            when (type.kType) {
                typeOf<Int>() -> IntEdgeKeyProperty(graph)
                typeOf<Long>() -> LongEdgeKeyProperty(graph)
                typeOf<io.github.sooniln.fastgraph.Edge>() -> EdgeEdgeKeyProperty(graph)
                else -> AnyEdgeKeyProperty(graph, type)
            }
        }
    } as MutableEdgeKeyProperty<T>
}

/**
 * Creates a new [EdgeProperty] which is a transformation of this [EdgeProperty]. The new [EdgeProperty] applies the
 * given [transform] on every [EdgeProperty.get] invocation. The new [EdgeProperty] thus does not actually store any
 * data, and references the input property indefinitely.
 */
@JvmName("map")
public fun <E, O> map(property: EdgeProperty<out E>, type: PropertyType<O>, transform: (E) -> O): EdgeProperty<out O> {
    return object : EdgeProperty<O> {
        override val graph: io.github.sooniln.fastgraph.Graph get() = property.graph
        override val type: PropertyType<O> get() = type
        override fun get(edge: io.github.sooniln.fastgraph.Edge): O  = transform(property[edge])
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
        override val graph: io.github.sooniln.fastgraph.Graph get() = property.graph
        override val type: PropertyType<O> get() = type
        override fun get(edge: io.github.sooniln.fastgraph.Edge): O = transform(property[edge])
        override fun set(edge: io.github.sooniln.fastgraph.Edge, value: O) { property[edge] = reverseTransform(value)}
        override fun put(edge: io.github.sooniln.fastgraph.Edge, value: O) = transform(property.put(edge, reverseTransform(value)))
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

/** Returns an empty edge property to be associated with an empty [io.github.sooniln.fastgraph.ImmutableGraph]. */
internal fun <T> emptyEdgeProperty(graph: io.github.sooniln.fastgraph.ImmutableGraph, type: PropertyType<T>): MutableEdgeKeyProperty<T> {
    require(graph.edges.isEmpty())

    return object : MutableEdgeKeyProperty<T> {
        override val graph: io.github.sooniln.fastgraph.Graph get() = graph
        override val type: PropertyType<T> get() = type

        override fun get(edge: io.github.sooniln.fastgraph.Edge): T = throw IllegalArgumentException()
        override fun set(edge: io.github.sooniln.fastgraph.Edge, value: T) = throw IllegalArgumentException()
        override fun hasEdge(key: T): Boolean = false
        override fun getEdge(key: T): io.github.sooniln.fastgraph.Edge = throw NoSuchElementException()
    }
}
