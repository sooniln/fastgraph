/**
 * Methods dealing with vertex properties.
 */
@file:JvmName("VertexProperties")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.properties.AnyIdentityIndexedVertexKeyProperty
import io.github.sooniln.fastgraph.properties.AnyIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.AnyIndexedVertexKeyProperty
import io.github.sooniln.fastgraph.properties.AnyIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.AnyVertexKeyProperty
import io.github.sooniln.fastgraph.properties.AnyVertexProperty
import io.github.sooniln.fastgraph.properties.BooleanIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.BooleanIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.BooleanVertexProperty
import io.github.sooniln.fastgraph.properties.ByteIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ByteIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ByteVertexProperty
import io.github.sooniln.fastgraph.properties.DoubleIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.DoubleIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.DoubleVertexProperty
import io.github.sooniln.fastgraph.properties.FloatIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.FloatIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.FloatVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableAnyIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableAnyIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableAnyVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableBooleanIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableBooleanIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableBooleanVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableByteIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableByteIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableByteVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableDoubleIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableDoubleIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableDoubleVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableFloatIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableFloatIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableFloatVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableIntIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableIntIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableIntVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableLongIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableLongIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableLongVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableVertexIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableVertexIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.ImmutableVertexVertexProperty
import io.github.sooniln.fastgraph.properties.IntIdentityIndexedVertexKeyProperty
import io.github.sooniln.fastgraph.properties.IntIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.IntIndexedVertexKeyProperty
import io.github.sooniln.fastgraph.properties.IntIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.IntVertexKeyProperty
import io.github.sooniln.fastgraph.properties.IntVertexProperty
import io.github.sooniln.fastgraph.properties.LongIdentityIndexedVertexKeyProperty
import io.github.sooniln.fastgraph.properties.LongIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.LongIndexedVertexKeyProperty
import io.github.sooniln.fastgraph.properties.LongIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.LongVertexKeyProperty
import io.github.sooniln.fastgraph.properties.LongVertexProperty
import io.github.sooniln.fastgraph.properties.VertexIdentityIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.VertexIndexedVertexProperty
import io.github.sooniln.fastgraph.properties.VertexVertexProperty
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
public interface VertexProperty<V> {
    /** The graph this property is associated with. */
    public val graph: Graph

    /** The type of this property. */
    @get:JvmName("getType")
    public val type: PropertyType<V>

    /**
     * Retrieves the value associated with the given vertex, but has undefined behavior if the vertex does not belong to
     * [graph].
     */
    @JvmName("get")
    public operator fun get(vertex: Vertex): V

    /**
     * Creates a new [MutableVertexProperty] of the same type and with the given [defaultValueFunction], with all values
     * copied from this property.
     */
    public fun copy(defaultValueFunction: VertexFunction<V>): MutableVertexProperty<V> {
        val copy = graph.createVertexProperty(type, defaultValueFunction)
        copyInto(copy)
        return copy
    }
}

/** See [VertexProperty.get]. */
@JvmSynthetic
public operator fun <V> VertexProperty<out V>.get(vertexReference: VertexReference): V = get(vertexReference.unstable)

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
public operator fun <V> MutableVertexProperty<in V>.set(vertexReference: VertexReference, value: V): Unit =
    set(vertexReference.unstable, value)

/** See [MutableVertexProperty.put]. */
@JvmSynthetic
public fun <V> MutableVertexProperty<V>.put(vertexReference: VertexReference, value: V): V =
    put(vertexReference.unstable, value)

/**
 * Returns a new [MutableVertexProperty] with the given graph owner which passes all property functionality through to
 * this property.
 */
public fun <E> MutableVertexProperty<E>.reparent(graph: Graph): MutableVertexProperty<E> {
    return object : MutableVertexProperty<E> by this {
        override val graph: Graph = graph
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
        override val type: PropertyType<Unit> get() = propertyTypeOf()
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
    type: PropertyType<T>,
    defaultValueFunction: VertexFunction<T>
): MutableVertexProperty<T> {
    if (type.kType == typeOf<Unit>()) {
        return unitVertexProperty(graph) as MutableVertexProperty<T>
    }

    return if (graph is ImmutableGraph) {
        if (graph.isEmpty()) {
            emptyVertexProperty(graph, type)
        } else if (graph is IdentityIndexedVertexGraph) {
            when (type.kType) {
                typeOf<Boolean>() ->
                    ImmutableBooleanIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Boolean>
                    ) as MutableVertexProperty<T>

                typeOf<Byte>() ->
                    ImmutableByteIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Byte>
                    ) as MutableVertexProperty<T>

                typeOf<Int>() ->
                    ImmutableIntIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Int>
                    ) as MutableVertexProperty<T>

                typeOf<Long>() ->
                    ImmutableLongIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Long>
                    ) as MutableVertexProperty<T>

                typeOf<Float>() ->
                    ImmutableFloatIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Float>
                    ) as MutableVertexProperty<T>

                typeOf<Double>() ->
                    ImmutableDoubleIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Double>
                    ) as MutableVertexProperty<T>

                typeOf<Vertex>() ->
                    ImmutableVertexIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Vertex>
                    ) as MutableVertexProperty<T>

                else -> ImmutableAnyIdentityIndexedVertexProperty(graph, type, defaultValueFunction)
            }
        } else if (graph is IndexedVertexGraph) {
            when (type.kType) {
                typeOf<Boolean>() ->
                    ImmutableBooleanIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Boolean>
                    ) as MutableVertexProperty<T>

                typeOf<Byte>() ->
                    ImmutableByteIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Byte>
                    ) as MutableVertexProperty<T>

                typeOf<Int>() ->
                    ImmutableIntIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Int>
                    ) as MutableVertexProperty<T>

                typeOf<Long>() ->
                    ImmutableLongIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Long>
                    ) as MutableVertexProperty<T>

                typeOf<Float>() ->
                    ImmutableFloatIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Float>
                    ) as MutableVertexProperty<T>

                typeOf<Double>() ->
                    ImmutableDoubleIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Double>
                    ) as MutableVertexProperty<T>

                typeOf<Vertex>() ->
                    ImmutableVertexIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Vertex>
                    ) as MutableVertexProperty<T>

                else -> ImmutableAnyIndexedVertexProperty(graph, type, defaultValueFunction)
            }
        } else {
            when (type.kType) {
                typeOf<Boolean>() ->
                    ImmutableBooleanVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Boolean>
                    ) as MutableVertexProperty<T>

                typeOf<Byte>() ->
                    ImmutableByteVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Byte>
                    ) as MutableVertexProperty<T>

                typeOf<Int>() ->
                    ImmutableIntVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Int>
                    ) as MutableVertexProperty<T>

                typeOf<Long>() ->
                    ImmutableLongVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Long>
                    ) as MutableVertexProperty<T>

                typeOf<Float>() ->
                    ImmutableFloatVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Float>
                    ) as MutableVertexProperty<T>

                typeOf<Double>() ->
                    ImmutableDoubleVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Double>
                    ) as MutableVertexProperty<T>

                typeOf<Vertex>() ->
                    ImmutableVertexVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Vertex>
                    ) as MutableVertexProperty<T>

                else -> ImmutableAnyVertexProperty(graph, type, defaultValueFunction)
            }
        }
    } else {
        if (graph is IdentityIndexedVertexGraph) {
            when (type.kType) {
                typeOf<Boolean>() ->
                    BooleanIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Boolean>
                    ) as MutableVertexProperty<T>

                typeOf<Byte>() ->
                    ByteIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Byte>
                    ) as MutableVertexProperty<T>

                typeOf<Int>() ->
                    IntIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Int>
                    ) as MutableVertexProperty<T>

                typeOf<Long>() ->
                    LongIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Long>
                    ) as MutableVertexProperty<T>

                typeOf<Float>() ->
                    FloatIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Float>
                    ) as MutableVertexProperty<T>

                typeOf<Double>() ->
                    DoubleIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Double>
                    ) as MutableVertexProperty<T>

                typeOf<Vertex>() ->
                    VertexIdentityIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Vertex>
                    ) as MutableVertexProperty<T>

                else -> AnyIdentityIndexedVertexProperty(graph, type, defaultValueFunction)
            }
        } else if (graph is IndexedVertexGraph) {
            when (type.kType) {
                typeOf<Boolean>() ->
                    BooleanIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Boolean>
                    ) as MutableVertexProperty<T>

                typeOf<Byte>() ->
                    ByteIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Byte>
                    ) as MutableVertexProperty<T>

                typeOf<Int>() ->
                    IntIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Int>
                    ) as MutableVertexProperty<T>

                typeOf<Long>() ->
                    LongIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Long>
                    ) as MutableVertexProperty<T>

                typeOf<Float>() ->
                    FloatIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Float>
                    ) as MutableVertexProperty<T>

                typeOf<Double>() ->
                    DoubleIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Double>
                    ) as MutableVertexProperty<T>

                typeOf<Vertex>() ->
                    VertexIndexedVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Vertex>
                    ) as MutableVertexProperty<T>

                else -> AnyIndexedVertexProperty(graph, type, defaultValueFunction)
            }
        } else {
            when (type.kType) {
                typeOf<Boolean>() ->
                    BooleanVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Boolean>
                    ) as MutableVertexProperty<T>

                typeOf<Byte>() ->
                    ByteVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Byte>
                    ) as MutableVertexProperty<T>

                typeOf<Int>() ->
                    IntVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Int>
                    ) as MutableVertexProperty<T>

                typeOf<Long>() ->
                    LongVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Long>
                    ) as MutableVertexProperty<T>

                typeOf<Float>() ->
                    FloatVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Float>
                    ) as MutableVertexProperty<T>

                typeOf<Double>() ->
                    DoubleVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Double>
                    ) as MutableVertexProperty<T>

                typeOf<Vertex>() ->
                    VertexVertexProperty(
                        graph,
                        defaultValueFunction as VertexFunction<Vertex>
                    ) as MutableVertexProperty<T>

                else -> AnyVertexProperty(graph, type, defaultValueFunction)
            }
        }
    }
}

/**
 * A specialization of [VertexProperty] where each vertex is identified by a unique key, and a [Vertex] can thus be
 * retrieved for a key.
 *
 * A VertexKeyProperty has no default value - a vertex has no key until one is explicitly set via [set]. While any
 * vertex in [graph] has no key, the property is incomplete and any read operation ([get], [hasVertex], [getVertex],
 * [copy], [put], etc...) will fail with [IllegalStateException].
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface VertexKeyProperty<V> : VertexProperty<V> {
    /** Returns true if some vertex has the given key. */
    public fun hasVertex(key: V): Boolean

    /**
     * Retrieves the vertex for the given key, or throws [NoSuchElementException] if there is no such vertex.
     */
    @JvmName("getVertex")
    public fun getVertex(key: V): Vertex

    /**
     * Creates a new [MutableVertexKeyProperty] of the same type, with all keys copied from this property.
     */
    public fun copy(): MutableVertexKeyProperty<V> {
        val copy = graph.createVertexKeyProperty(type)
        copyInto(copy)
        return copy
    }
}

/**
 * A mutable specialization of [VertexKeyProperty]. [set] is the only operation permitted while the property is
 * incomplete (see [VertexKeyProperty]). [set] does nothing if the value is already the key of the given vertex, and
 * throws [IllegalArgumentException] if the value is the key of a different vertex. [put] behaves as [set] but
 * additionally returns the previous key.
 */
public interface MutableVertexKeyProperty<V> : VertexKeyProperty<V>, MutableVertexProperty<V>

/**
 * Returns a new [MutableVertexKeyProperty] with the given graph owner which passes all property functionality through
 * to this property.
 */
public fun <E> MutableVertexKeyProperty<E>.reparent(graph: Graph): MutableVertexKeyProperty<E> {
    return object : MutableVertexKeyProperty<E> by this {
        override val graph: Graph = graph
    }
}

/**
 * Creates a vertex key property that is as specialized and efficient as possible for the given graph and type. See
 * [VertexKeyProperty] for the semantics of key properties.
 */
@Suppress("UNCHECKED_CAST")
@JvmName("createVertexKeyProperty")
public fun <T> createVertexKeyProperty(graph: Graph, type: PropertyType<T>): MutableVertexKeyProperty<T> {
    return if (graph is IdentityIndexedVertexGraph) {
        when (type.kType) {
            typeOf<Int>() -> IntIdentityIndexedVertexKeyProperty(graph) as MutableVertexKeyProperty<T>
            typeOf<Long>() -> LongIdentityIndexedVertexKeyProperty(graph) as MutableVertexKeyProperty<T>
            else -> AnyIdentityIndexedVertexKeyProperty(graph, type)
        }
    } else if (graph is IndexedVertexGraph) {
        when (type.kType) {
            typeOf<Int>() -> IntIndexedVertexKeyProperty(graph) as MutableVertexKeyProperty<T>
            typeOf<Long>() -> LongIndexedVertexKeyProperty(graph) as MutableVertexKeyProperty<T>
            else -> AnyIndexedVertexKeyProperty(graph, type)
        }
    } else {
        when (type.kType) {
            typeOf<Int>() -> IntVertexKeyProperty(graph) as MutableVertexKeyProperty<T>
            typeOf<Long>() -> LongVertexKeyProperty(graph) as MutableVertexKeyProperty<T>
            else -> AnyVertexKeyProperty(graph, type)
        }
    }
}

/**
 * Creates a new [VertexProperty] which is a transformation of this [VertexProperty]. The new [VertexProperty] applies
 * the given [transform] on every [VertexProperty.get] invocation. The new [VertexProperty] thus does not actually store
 * any data, and references the input property indefinitely.
 */
@JvmName("map")
public fun <V, O> map(property: VertexProperty<out V>, type: PropertyType<O>, transform: (V) -> O): VertexProperty<out O> {
    return object : VertexProperty<O> {
        override val graph: Graph get() = property.graph
        override val type: PropertyType<O> get() = type
        override fun get(vertex: Vertex): O = transform(property[vertex])
    }
}

/** See [map]. */
@JvmSynthetic
@JvmName("#vertexPropertyMap")
public fun <V, O> VertexProperty<out V>.map(type: PropertyType<O>, transform: (V) -> O): VertexProperty<out O> {
    return map(this, type, transform)
}

/** See [map]. */
@JvmSynthetic
public inline fun <V, reified O> VertexProperty<out V>.map(noinline transform: (V) -> O): VertexProperty<out O> {
    return map(this, propertyTypeOf(), transform)
}

/**
 * Creates a new [MutableVertexProperty] which is a transformation of this [MutableVertexProperty]. The new
 * [MutableVertexProperty] applies the given [transform]/[reverseTransform] on every
 * [MutableVertexProperty.get]/[MutableVertexProperty.set] invocation. The new [VertexProperty] thus does not actually
 * store any data, and references the input property indefinitely.
 */
@JvmName("map")
public fun <V, O> map(
    property: MutableVertexProperty<V>,
    type: PropertyType<O>,
    transform: (V) -> O,
    reverseTransform: (O) -> V
): MutableVertexProperty<O> {
    return object : MutableVertexProperty<O> {
        override val graph: Graph get() = property.graph
        override val type: PropertyType<O> get() = type
        override fun get(vertex: Vertex): O = transform(property[vertex])
        override fun set(vertex: Vertex, value: O) { property[vertex] = reverseTransform(value)}
        override fun put(vertex: Vertex, value: O) = transform(property.put(vertex, reverseTransform(value)))
    }
}

/** See [map]. */
@JvmSynthetic
@JvmName("#mutableVertexPropertyMap")
public fun <V, O> MutableVertexProperty<V>.map(
    type: PropertyType<O>,
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
    return map(this, propertyTypeOf(), transform, reverseTransform)
}

/**
 * Convenience function that sets the value of [other] to the value of this property for every vertex in [graph]. This
 * differs from [copyFrom] in which graph has its vertices iterated. If both properties belong to the same graph, then
 * the two methods are interchangeable.
 *
 * Note that if the properties are from different graphs, this is only a meaningful operation if there is some
 * meaningful connection between the graphs.
 */
public fun <E> VertexProperty<out E>.copyInto(other: MutableVertexProperty<in E>) {
    for (vertex in graph.vertices) {
        other[vertex] = get(vertex)
    }
}

/**
 * Convenience function that sets the value of [other] to the value of this property for every vertex in [graph]. This
 * differs from [copyInto] in which graph has its vertices iterated. If both properties belong to the same graph, then
 * the two methods are interchangeable.
 *
 * Note that if the properties are from different graphs, this is only a meaningful operation if there is some
 * meaningful connection between the graphs.
 */
public fun <E> MutableVertexProperty<in E>.copyFrom(other: VertexProperty<out E>) {
    for (vertex in graph.vertices) {
        set(vertex, other[vertex])
    }
}

/**
 * Convenience function that casts a [VertexProperty] to the given type safely (types must match). Does not support
 * casting directly to super-types of the real type - although this may be legal, this function does not have access to
 * enough information to do so safely. Instead, [safeCast] to the real type, and then implicit cast to the super type.
 */
@Suppress("UNCHECKED_CAST")
public inline fun <reified E> VertexProperty<*>.safeCast(): VertexProperty<E> {
    val desiredType = typeOf<E>()
    if (!type.mayCastTo(desiredType)) throw TypeCastException("$type cannot be safely cast to $desiredType")
    return this as VertexProperty<E>
}

/**
 * Convenience function that casts a [MutableVertexProperty] to the given type safely (types must match).
 */
@Suppress("UNCHECKED_CAST")
public inline fun <reified E> MutableVertexProperty<*>.safeCast(): MutableVertexProperty<E> {
    val desiredType = typeOf<E>()
    if (!type.mayCastTo(desiredType)) throw TypeCastException("$type cannot be safely cast to $desiredType")
    return this as MutableVertexProperty<E>
}

/**
 * Convenience function that casts a [VertexKeyProperty] to the given type safely (types must match). Does not support
 * casting directly to super-types of the real type - although this may be legal, this function does not have access to
 * enough information to do so safely. Instead, [safeCast] to the real type, and then implicit cast to the super type.
 */
@Suppress("UNCHECKED_CAST")
public inline fun <reified E> VertexKeyProperty<*>.safeCast(): VertexKeyProperty<E> {
    val desiredType = typeOf<E>()
    if (!type.mayCastTo(desiredType)) throw TypeCastException("$type cannot be safely cast to $desiredType")
    return this as VertexKeyProperty<E>
}

/**
 * Convenience function that casts a [MutableVertexKeyProperty] to the given type safely (types must match).
 */
@Suppress("UNCHECKED_CAST")
public inline fun <reified E> MutableVertexKeyProperty<*>.safeCast(): MutableVertexKeyProperty<E> {
    val desiredType = typeOf<E>()
    if (!type.mayCastTo(desiredType)) throw TypeCastException("$type cannot be safely cast to $desiredType")
    return this as MutableVertexKeyProperty<E>
}

/** Returns an empty vertex property to be associated with an empty [ImmutableGraph]. */
internal fun <T> emptyVertexProperty(graph: ImmutableGraph, type: PropertyType<T>): MutableVertexProperty<T> {
    require(graph.vertices.isEmpty())

    return object : MutableVertexProperty<T> {
        override val graph: Graph get() = graph
        override val type: PropertyType<T> get() = type

        override fun get(vertex: Vertex): T = throw IllegalArgumentException()
        override fun set(vertex: Vertex, value: T) = throw IllegalArgumentException()
    }
}

/** Returns an empty vertex key property to be associated with an empty [ImmutableGraph]. */
internal fun <T> emptyVertexKeyProperty(graph: ImmutableGraph, type: PropertyType<T>): MutableVertexKeyProperty<T> {
    require(graph.vertices.isEmpty())

    return object : MutableVertexKeyProperty<T> {
        override val graph: Graph get() = graph
        override val type: PropertyType<T> get() = type

        override fun get(vertex: Vertex): T = throw IllegalArgumentException()
        override fun set(vertex: Vertex, value: T) = throw IllegalArgumentException()
        override fun hasVertex(key: T): Boolean = false
        override fun getVertex(key: T): Vertex = throw NoSuchElementException()
    }
}

