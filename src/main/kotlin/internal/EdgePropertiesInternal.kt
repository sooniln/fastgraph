package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.EdgeSetList
import io.github.sooniln.fastgraph.Graph
import it.unimi.dsi.fastutil.booleans.BooleanArrays
import it.unimi.dsi.fastutil.ints.IntArrays
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrays
import kotlin.math.max

@Suppress("UNCHECKED_CAST")
internal fun <T> emptyEdgeProperty(): EdgeProperty<T> = EmptyEdgeProperty as EdgeProperty<T>

private object EmptyEdgeProperty : EdgeProperty<Nothing> {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): Nothing = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: Nothing) = throw IllegalArgumentException()
}

@Suppress("UNCHECKED_CAST")
internal fun <T> nothingEdgeProperty(): EdgeProperty<T> = NothingEdgeProperty as EdgeProperty<T>

private object NothingEdgeProperty : EdgeProperty<Nothing> {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): Nothing = throw IllegalStateException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: Nothing) = throw IllegalStateException()
}

@Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal fun <T : S?, S> immutableArrayMapEdgeProperty(
    edges: EdgeSet,
    clazz: Class<S>,
    initializer: (Edge) -> T
): EdgeProperty<T> {
    return when (clazz) {
        java.lang.Boolean::class.java -> ImmutableArrayMapBooleanEdgeProperty(
            edges,
            initializer as (Edge) -> Boolean
        ) as EdgeProperty<T>

        Integer::class.java -> ImmutableArrayMap4ByteEdgeProperty(
            edges,
            initializer as (Edge) -> Int,
            { it },
            { it }) as EdgeProperty<T>

        java.lang.Float::class.java -> ImmutableArrayMap4ByteEdgeProperty(
            edges,
            initializer as (Edge) -> Float,
            { java.lang.Float.floatToRawIntBits(it) },
            { java.lang.Float.intBitsToFloat(it) }) as EdgeProperty<T>

        java.lang.Long::class.java -> ImmutableArrayMap8ByteEdgeProperty(
            edges,
            initializer as (Edge) -> Long,
            { it },
            { it }) as EdgeProperty<T>

        java.lang.Double::class.java -> ImmutableArrayMap8ByteEdgeProperty(
            edges,
            initializer as (Edge) -> Double,
            { java.lang.Double.doubleToRawLongBits(it) },
            { java.lang.Double.longBitsToDouble(it) }) as EdgeProperty<T>

        else -> ArrayMapEdgeProperty(edges, initializer)
    }
}

private class ImmutableArrayMapBooleanEdgeProperty(
    edges: EdgeSet,
    initializer: (Edge) -> Boolean
) : EdgeProperty<Boolean> {

    private val edgeKeys: LongArray = edges.toLongArray().apply { sort() }

    @Suppress("UNCHECKED_CAST")
    private val values: BooleanArray = BooleanArray(edges.size) { initializer(Edge(edgeKeys[it])) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): Boolean {
        val i = edgeKeys.binarySearch(edge.longValue)
        require(i >= 0) { "$edge not found in property" }
        return values[i]
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: Boolean) {
        val i = edgeKeys.binarySearch(edge.longValue)
        require(i >= 0) { "$edge not found in property" }
        values[i] = value
    }
}

private inline fun <T> ImmutableArrayMap4ByteEdgeProperty(
    edges: EdgeSet,
    crossinline initializer: (Edge) -> T,
    crossinline write: (T) -> Int,
    crossinline read: (Int) -> T
): EdgeProperty<T> = object : EdgeProperty<T> {

    private val edgeKeys: LongArray = edges.toLongArray().apply { sort() }

    @Suppress("UNCHECKED_CAST")
    private val values: IntArray = IntArray(edges.size) { write(initializer(Edge(edgeKeys[it]))) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val i = edgeKeys.binarySearch(edge.longValue)
        require(i >= 0) { "$edge not found in property" }
        return read(values[i])
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        val i = edgeKeys.binarySearch(edge.longValue)
        require(i >= 0) { "$edge not found in property" }
        values[i] = write(value)
    }
}

private inline fun <T> ImmutableArrayMap8ByteEdgeProperty(
    edges: EdgeSet,
    crossinline initializer: (Edge) -> T,
    crossinline write: (T) -> Long,
    crossinline read: (Long) -> T
): EdgeProperty<T> = object : EdgeProperty<T> {

    private val edgeKeys: LongArray = edges.toLongArray().apply { sort() }

    @Suppress("UNCHECKED_CAST")
    private val values: LongArray = LongArray(edges.size) { write(initializer(Edge(edgeKeys[it]))) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val i = edgeKeys.binarySearch(edge.longValue)
        require(i >= 0) { "$edge not found in property" }
        return read(values[i])
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        val i = edgeKeys.binarySearch(edge.longValue)
        require(i >= 0) { "$edge not found in property" }
        values[i] = write(value)
    }
}

private class ArrayMapEdgeProperty<T>(edges: EdgeSet, initializer: (Edge) -> T) : EdgeProperty<T> {

    private val edgeKeys: LongArray = edges.toLongArray().apply { sort() }

    @Suppress("UNCHECKED_CAST")
    private val values: Array<T> = Array<Any?>(edges.size) { initializer(Edge(edgeKeys[it])) } as Array<T>

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val i = edgeKeys.binarySearch(edge.longValue)
        require(i >= 0) { "$edge not found in property" }
        return values[i]
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        val i = edgeKeys.binarySearch(edge.longValue)
        require(i >= 0) { "$edge not found in property" }
        values[i] = value
    }
}

@Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal fun <T : S?, S> immutableMapEdgeProperty(
    edges: EdgeSet,
    clazz: Class<S>,
    initializer: (Edge) -> T
): EdgeProperty<T> {
    return when (clazz) {
        java.lang.Boolean::class.java -> ImmutableMap4ByteEdgeProperty(
            edges,
            initializer as (Edge) -> Boolean,
            { if (it) 1 else 0 },
            { it != 0 }) as EdgeProperty<T>

        Integer::class.java -> ImmutableMap4ByteEdgeProperty(
            edges,
            initializer as (Edge) -> Int,
            { it },
            { it }) as EdgeProperty<T>

        java.lang.Float::class.java -> ImmutableMap4ByteEdgeProperty(
            edges,
            initializer as (Edge) -> Float,
            { java.lang.Float.floatToRawIntBits(it) },
            { java.lang.Float.intBitsToFloat(it) }) as EdgeProperty<T>

        java.lang.Long::class.java -> ImmutableMap8ByteEdgeProperty(
            edges,
            initializer as (Edge) -> Long,
            { it },
            { it }) as EdgeProperty<T>

        java.lang.Double::class.java -> ImmutableMap8ByteEdgeProperty(
            edges,
            initializer as (Edge) -> Double,
            { java.lang.Double.doubleToRawLongBits(it) },
            { java.lang.Double.longBitsToDouble(it) }) as EdgeProperty<T>

        else -> ImmutableMapEdgeProperty(edges, initializer)
    }
}

private inline fun <T> ImmutableMap4ByteEdgeProperty(
    edges: EdgeSet,
    crossinline initializer: (Edge) -> T,
    crossinline write: (T) -> Int,
    crossinline read: (Int) -> T
): EdgeProperty<T> = object : EdgeProperty<T> {

    private val property = Long2IntOpenHashMap(edges.size)

    init {
        for (edge in edges) {
            property.put(edge.longValue, write(initializer(edge)))
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        require(property.containsKey(edge.longValue)) { "$edge not found in property" }
        return read(property.get(edge.longValue))
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        require(property.containsKey(edge.longValue)) { "$edge not found in property" }
        property.put(edge.longValue, write(value))
    }
}

private inline fun <T> ImmutableMap8ByteEdgeProperty(
    edges: EdgeSet,
    crossinline initializer: (Edge) -> T,
    crossinline write: (T) -> Long,
    crossinline read: (Long) -> T
): EdgeProperty<T> = object : EdgeProperty<T> {

    private val property = Long2LongOpenHashMap(edges.size)

    init {
        for (edge in edges) {
            property.put(edge.longValue, write(initializer(edge)))
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        require(property.containsKey(edge.longValue)) { "$edge not found in property" }
        return read(property.get(edge.longValue))
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        require(property.containsKey(edge.longValue)) { "$edge not found in property" }
        property.put(edge.longValue, write(value))
    }
}

private class ImmutableMapEdgeProperty<T>(edges: EdgeSet, initializer: (Edge) -> T) : EdgeProperty<T> {

    private val property = Long2ObjectOpenHashMap<T>(edges.size)

    init {
        for (edge in edges) {
            property.put(edge.longValue, initializer(edge))
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        require(property.containsKey(edge.longValue)) { "$edge not found in property" }
        return property.get(edge.longValue)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        require(property.containsKey(edge.longValue)) { "$edge not found in property" }
        property.put(edge.longValue, value)
    }
}

@Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal fun <T : S?, S> immutableArrayEdgeProperty(
    edges: EdgeSetList,
    clazz: Class<S>,
    initializer: (Int) -> T
): EdgeProperty<T> {
    return when (clazz) {
        java.lang.Boolean::class.java -> ImmutableArrayBooleanEdgeProperty(
            edges,
            initializer as (Int) -> Boolean
        ) as EdgeProperty<T>

        Integer::class.java -> ImmutableArray4ByteEdgeProperty(
            edges,
            initializer as (Int) -> Int,
            { it },
            { it }) as EdgeProperty<T>

        java.lang.Float::class.java -> ImmutableArray4ByteEdgeProperty(
            edges,
            initializer as (Int) -> Float,
            { java.lang.Float.floatToRawIntBits(it) },
            { java.lang.Float.intBitsToFloat(it) }) as EdgeProperty<T>

        java.lang.Long::class.java -> ImmutableArray8ByteEdgeProperty(
            edges,
            initializer as (Int) -> Long,
            { it },
            { it }) as EdgeProperty<T>

        java.lang.Double::class.java -> ImmutableArray8ByteEdgeProperty(
            edges,
            initializer as (Int) -> Double,
            { java.lang.Double.doubleToRawLongBits(it) },
            { java.lang.Double.longBitsToDouble(it) }) as EdgeProperty<T>

        else -> ImmutableArrayEdgeProperty(edges, initializer)
    }
}

private class ImmutableArrayBooleanEdgeProperty(private val edges: EdgeSetList, initializer: (Int) -> Boolean) :
    EdgeProperty<Boolean> {
    private val property: BooleanArray = BooleanArray(edges.size) { initializer(it) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): Boolean {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in property" }
        return property[index]
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: Boolean) {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in property" }
        property[index] = value
    }
}

private inline fun <T> ImmutableArray4ByteEdgeProperty(
    e: EdgeSetList,
    crossinline initializer: (Int) -> T,
    crossinline write: (T) -> Int,
    crossinline read: (Int) -> T
): EdgeProperty<T> = object : EdgeProperty<T> {

    private val edges = e
    private val property: IntArray = IntArray(edges.size) { write(initializer(it)) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in property" }
        return read(property[index])
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in property" }
        property[index] = write(value)
    }
}

private inline fun <T> ImmutableArray8ByteEdgeProperty(
    e: EdgeSetList,
    crossinline initializer: (Int) -> T,
    crossinline write: (T) -> Long,
    crossinline read: (Long) -> T
): EdgeProperty<T> = object : EdgeProperty<T> {

    private val edges = e
    private val property: LongArray = LongArray(edges.size) { write(initializer(it)) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in property" }
        return read(property[index])
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in property" }
        property[index] = write(value)
    }
}

private class ImmutableArrayEdgeProperty<T>(private val edges: EdgeSetList, initializer: (Int) -> T) : EdgeProperty<T> {

    @Suppress("UNCHECKED_CAST")
    private val property: Array<T> = Array<Any?>(edges.size) { initializer(it) } as Array<T>

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in property" }
        return property[index]
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in property" }
        property[index] = value
    }
}

internal interface MutableEdgeProperty<V> : EdgeProperty<V> {
    /**
     * Set `swapEdge` property to `removeEdge` property and remove `removeEdge` property. Edges may be the same, in
     * which case they can simply be removed.
     */
    fun swapAndRemove(removeEdge: Edge, swapEdge: Edge)

    fun ensureCapacity(capacity: Int)
}

@Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal fun <T : S?, S> mutableMapEdgeProperty(
    graph: Graph,
    clazz: Class<S>,
    initializer: (Edge) -> T
): MutableEdgeProperty<T> {
    return when (clazz) {
        java.lang.Boolean::class.java -> MutableMap4ByteEdgeProperty(
            graph,
            initializer as (Edge) -> Boolean,
            { if (it) 1 else 0 },
            { it != 0 }
        ) as MutableEdgeProperty<T>

        Integer::class.java -> MutableMap4ByteEdgeProperty(
            graph,
            initializer as (Edge) -> Int,
            { it },
            { it }) as MutableEdgeProperty<T>

        java.lang.Float::class.java -> MutableMap4ByteEdgeProperty(
            graph,
            initializer as (Edge) -> Float,
            { java.lang.Float.floatToRawIntBits(it) },
            { java.lang.Float.intBitsToFloat(it) }) as MutableEdgeProperty<T>

        java.lang.Long::class.java -> MutableMap8ByteEdgeProperty(
            graph,
            initializer as (Edge) -> Long,
            { it },
            { it }) as MutableEdgeProperty<T>

        java.lang.Double::class.java -> MutableMap8ByteEdgeProperty(
            graph,
            initializer as (Edge) -> Double,
            { java.lang.Double.doubleToRawLongBits(it) },
            { java.lang.Double.longBitsToDouble(it) }) as MutableEdgeProperty<T>

        else -> MutableMapEdgeProperty(graph, initializer)
    }
}

private inline fun <T> MutableMap4ByteEdgeProperty(
    g: Graph,
    crossinline initializer: (Edge) -> T,
    crossinline write: (T) -> Int,
    crossinline read: (Int) -> T
): MutableEdgeProperty<T> = object : MutableEdgeProperty<T> {

    private val graph = g
    private val property = Long2IntOpenHashMap(graph.edges.size)

    override fun get(edge: Edge): T {
        val value: T
        if (!property.containsKey(edge.longValue)) {
            require(graph.edges.contains(edge)) { "$edge not found in graph" }
            value = initializer(edge)
            property[edge.longValue] = write(value)
        } else {
            value = read(property.get(edge.longValue))
        }
        return value
    }

    override fun set(edge: Edge, value: T) {
        require(graph.edges.contains(edge)) { "$edge ({graph.edgeSource(edge)} -> ${graph.edgeTarget(edge)}) not found in graph" }
        property[edge.longValue] = write(value)
    }

    override fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) {
        if (property.containsKey(removeEdge.longValue)) {
            if (swapEdge != removeEdge) {
                property[swapEdge.longValue] = property.get(removeEdge.longValue)
            }
            property.remove(removeEdge.longValue)
        } else if (property.containsKey(swapEdge.longValue)) {
            property[swapEdge.longValue] = write(initializer(removeEdge))
        }
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)
}

private inline fun <T> MutableMap8ByteEdgeProperty(
    g: Graph,
    crossinline initializer: (Edge) -> T,
    crossinline write: (T) -> Long,
    crossinline read: (Long) -> T
): MutableEdgeProperty<T> = object : MutableEdgeProperty<T> {

    private val graph = g
    private val property = Long2LongOpenHashMap(graph.edges.size)

    override fun get(edge: Edge): T {
        val value: T
        if (!property.containsKey(edge.longValue)) {
            require(graph.edges.contains(edge)) { "$edge (${graph.edgeSource(edge)} -> ${graph.edgeTarget(edge)}) not found in graph" }
            value = initializer(edge)
            property[edge.longValue] = write(value)
        } else {
            value = read(property.get(edge.longValue))
        }
        return value
    }

    override fun set(edge: Edge, value: T) {
        require(graph.edges.contains(edge)) { "$edge ({graph.edgeSource(edge)} -> ${graph.edgeTarget(edge)}) not found in graph" }
        property[edge.longValue] = write(value)
    }

    override fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) {
        if (property.containsKey(removeEdge.longValue)) {
            if (swapEdge != removeEdge) {
                property[swapEdge.longValue] = property.get(removeEdge.longValue)
            }
            property.remove(removeEdge.longValue)
        } else if (property.containsKey(swapEdge.longValue)) {
            property[swapEdge.longValue] = write(initializer(removeEdge))
        }
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)
}

private class MutableMapEdgeProperty<T>(private val graph: Graph, private val initializer: (Edge) -> T) :
    MutableEdgeProperty<T> {

    private val property = Long2ObjectOpenHashMap<T>(graph.edges.size)

    override fun get(edge: Edge): T {
        var t = property[edge.longValue]
        if (t == null && !property.containsKey(edge.longValue)) {
            require(graph.edges.contains(edge)) { "$edge not found in property" }
            t = initializer(edge)
            property[edge.longValue] = t
        }
        return t
    }

    override fun set(edge: Edge, value: T) {
        require(graph.edges.contains(edge)) { "$edge not found in graph" }
        property[edge.longValue] = value
    }

    override fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) {
        if (property.containsKey(removeEdge.longValue)) {
            if (swapEdge != removeEdge) {
                property[swapEdge.longValue] = property.get(removeEdge.longValue)
            }
            property.remove(removeEdge.longValue)
        } else if (property.containsKey(swapEdge.longValue)) {
            property[swapEdge.longValue] = initializer(removeEdge)
        }
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)
}

@Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal fun <T : S?, S> mutableArrayListEdgeProperty(
    edges: EdgeSetList,
    clazz: Class<S>,
    initializer: (Edge) -> T
): MutableEdgeProperty<T> {
    return when (clazz) {
        java.lang.Boolean::class.java -> MutableArrayListBooleanEdgeProperty(
            edges,
            initializer as (Edge) -> Boolean,
        ) as MutableEdgeProperty<T>

        Integer::class.java -> MutableArrayList4ByteEdgeProperty(
            edges,
            initializer as (Edge) -> Int,
            { it },
            { it }) as MutableEdgeProperty<T>

        java.lang.Float::class.java -> MutableArrayList4ByteEdgeProperty(
            edges,
            initializer as (Edge) -> Float,
            { java.lang.Float.floatToRawIntBits(it) },
            { java.lang.Float.intBitsToFloat(it) }) as MutableEdgeProperty<T>

        java.lang.Long::class.java -> MutableArrayList8ByteEdgeProperty(
            edges,
            initializer as (Edge) -> Long,
            { it },
            { it }) as MutableEdgeProperty<T>

        java.lang.Double::class.java -> MutableArrayList8ByteEdgeProperty(
            edges,
            initializer as (Edge) -> Double,
            { java.lang.Double.doubleToRawLongBits(it) },
            { java.lang.Double.longBitsToDouble(it) }) as MutableEdgeProperty<T>

        else -> MutableArrayListEdgeProperty(edges, initializer)
    }
}

private class MutableArrayListBooleanEdgeProperty(
    private val edges: EdgeSetList,
    private val initializer: (Edge) -> Boolean
) : MutableEdgeProperty<Boolean> {

    // we don't use BooleanArrayList because it doesn't properly implement ensureCapacity as we'd expect (it expands to
    // the exact size given, rather than allowing for larger expansions to save future effort)
    private var property = BooleanArray(0)
    private var propertySize = 0

    init {
        expand(edges.size)
    }

    private fun expand(size: Int) {
        assert(size <= edges.size)
        if (size > property.size) {
            property = BooleanArrays.grow(property, max(10, size))
            for (i in propertySize..<size) {
                property[i] = initializer(edges[i])
            }
            propertySize = size
        }
    }

    override fun get(edge: Edge): Boolean {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in graph" }
        expand(index + 1)
        return property[index]
    }

    override fun set(edge: Edge, value: Boolean) {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in graph" }
        expand(index + 1)
        property[index] = value
    }

    override fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) {
        val oldIndex = edges.indexOf(removeEdge)
        require(oldIndex >= 0) { "$removeEdge not found in graph" }
        val newIndex = edges.indexOf(swapEdge)
        require(newIndex >= 0) { "$swapEdge not found in graph" }

        assert(oldIndex == edges.lastIndex)
        if (oldIndex != newIndex && newIndex < propertySize) {
            if (oldIndex >= propertySize) {
                property[newIndex] = initializer(removeEdge)
            } else {
                property[newIndex] = property[oldIndex]
            }
        }
        if (oldIndex == propertySize - 1) {
            propertySize--
        }
    }

    override fun ensureCapacity(capacity: Int) {
        property = BooleanArrays.grow(property, capacity)
    }
}

private inline fun <T> MutableArrayList4ByteEdgeProperty(
    e: EdgeSetList,
    crossinline initializer: (Edge) -> T,
    crossinline write: (T) -> Int,
    crossinline read: (Int) -> T
): MutableEdgeProperty<T> = object : MutableEdgeProperty<T> {

    // we don't use IntArrayList because it doesn't properly implement ensureCapacity as we'd expect (it expands to the
    // exact size given, rather than allowing for larger expansions to save future effort)
    private val edges = e
    private var property = IntArray(0)
    private var propertySize = 0

    init {
        expand(edges.size)
    }

    private fun expand(size: Int) {
        assert(size <= edges.size)
        if (size > property.size) {
            property = IntArrays.grow(property, max(10, size))
            for (i in propertySize..<size) {
                property[i] = write(initializer(edges[i]))
            }
            propertySize = size
        }
    }

    override fun get(edge: Edge): T {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in graph" }
        expand(index + 1)
        return read(property[index])
    }

    override fun set(edge: Edge, value: T) {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in graph" }
        expand(index + 1)
        property[index] = write(value)
    }

    override fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) {
        val oldIndex = edges.indexOf(removeEdge)
        require(oldIndex >= 0) { "$removeEdge not found in graph" }
        val newIndex = edges.indexOf(swapEdge)
        require(newIndex >= 0) { "$swapEdge not found in graph" }

        assert(oldIndex == edges.lastIndex)
        if (oldIndex != newIndex && newIndex < propertySize) {
            if (oldIndex >= propertySize) {
                property[newIndex] = write(initializer(removeEdge))
            } else {
                property[newIndex] = property[oldIndex]
            }
        }
        if (oldIndex == propertySize - 1) {
            propertySize--
        }
    }

    override fun ensureCapacity(capacity: Int) {
        property = IntArrays.grow(property, capacity)
    }
}

private inline fun <T> MutableArrayList8ByteEdgeProperty(
    e: EdgeSetList,
    crossinline initializer: (Edge) -> T,
    crossinline write: (T) -> Long,
    crossinline read: (Long) -> T
): MutableEdgeProperty<T> = object : MutableEdgeProperty<T> {

    // we don't use LongArrayList because it doesn't properly implement ensureCapacity as we'd expect (it expands to the
    // exact size given, rather than allowing for larger expansions to save future effort)
    private val edges = e
    private var property = LongArray(0)
    private var propertySize = 0

    init {
        expand(edges.size)
    }

    private fun expand(size: Int) {
        assert(size <= edges.size)
        if (size > property.size) {
            property = LongArrays.grow(property, max(10, size))
            for (i in propertySize..<size) {
                property[i] = write(initializer(edges[i]))
            }
            propertySize = size
        }
    }

    override fun get(edge: Edge): T {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in graph" }
        expand(index + 1)
        return read(property[index])
    }

    override fun set(edge: Edge, value: T) {
        val index = edges.indexOf(edge)
        require(index >= 0) { "$edge not found in graph" }
        expand(index + 1)
        property[index] = write(value)
    }

    override fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) {
        val oldIndex = edges.indexOf(removeEdge)
        require(oldIndex >= 0) { "$removeEdge not found in graph" }
        val newIndex = edges.indexOf(swapEdge)
        require(newIndex >= 0) { "$swapEdge not found in graph" }

        assert(oldIndex == edges.lastIndex)
        if (oldIndex != newIndex && newIndex < propertySize) {
            if (oldIndex >= propertySize) {
                property[newIndex] = write(initializer(removeEdge))
            } else {
                property[newIndex] = property[oldIndex]
            }
        }
        if (oldIndex == propertySize - 1) {
            propertySize--
        }
    }

    override fun ensureCapacity(capacity: Int) {
        property = LongArrays.grow(property, capacity)
    }
}

private class MutableArrayListEdgeProperty<T>(
    private val edges: EdgeSetList,
    private val initializer: (Edge) -> T
) : MutableEdgeProperty<T> {

    private val property = ArrayList<T>(edges.size)

    init {
        expand(edges.size)
    }

    private fun expand(size: Int) {
        assert(size <= edges.size)
        property.ensureCapacity(size)
        for (i in property.size..<size) {
            property.add(initializer(edges[i]))
        }
    }

    override fun get(edge: Edge): T {
        val index = edges.indexOf(edge)
        check(index != -1) { "$edge not found in graph" }
        expand(index + 1)
        return property[index]
    }

    override fun set(edge: Edge, value: T) {
        val index = edges.indexOf(edge)
        require(index != -1) { "$edge not found in graph" }
        expand(index + 1)
        property[index] = value
    }

    override fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) {
        val oldIndex = edges.indexOf(removeEdge)
        require(oldIndex != -1) { "$removeEdge not found in graph" }
        val newIndex = edges.indexOf(swapEdge)
        require(newIndex != -1) { "$swapEdge not found in graph" }

        assert(oldIndex == edges.lastIndex)
        if (oldIndex != newIndex && newIndex < property.size) {
            if (oldIndex > property.lastIndex) {
                property[newIndex] = initializer(removeEdge)
            } else {
                property[newIndex] = property[oldIndex]
            }
        }
        if (oldIndex == property.lastIndex) {
            property.removeAt(oldIndex)
        }
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)
}
