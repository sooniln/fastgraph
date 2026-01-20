package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeIndexedEdgeGraph
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import it.unimi.dsi.fastutil.booleans.BooleanArrays
import it.unimi.dsi.fastutil.ints.IntArrays
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrays

@Suppress("UNCHECKED_CAST")
internal fun <T> emptyEdgeProperty(graph: Graph): EdgeProperty<T> = EmptyEdgeProperty(graph) as EdgeProperty<T>

private class EmptyEdgeProperty(override val graph: Graph) : EdgeProperty<Nothing> {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): Nothing = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: Nothing) = throw IllegalArgumentException()
}

@Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal fun <T : S?, S> immutableMapEdgeProperty(
    graph: ImmutableGraph,
    clazz: Class<S>,
    initializer: EdgeInitializer<T>
): EdgeProperty<T> {
    return when (clazz) {
        java.lang.Boolean::class.java -> ImmutableMap4ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Boolean>,
            { if (it) 1 else 0 },
            { it != 0 }) as EdgeProperty<T>

        Integer::class.java -> ImmutableMap4ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Int>,
            { it },
            { it }) as EdgeProperty<T>

        java.lang.Float::class.java -> ImmutableMap4ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Float>,
            { java.lang.Float.floatToRawIntBits(it) },
            { java.lang.Float.intBitsToFloat(it) }) as EdgeProperty<T>

        java.lang.Long::class.java -> ImmutableMap8ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Long>,
            { it },
            { it }) as EdgeProperty<T>

        java.lang.Double::class.java -> ImmutableMap8ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Double>,
            { java.lang.Double.doubleToRawLongBits(it) },
            { java.lang.Double.longBitsToDouble(it) }) as EdgeProperty<T>

        else -> ImmutableMapEdgeProperty(graph, initializer)
    }
}

private inline fun <T> ImmutableMap4ByteEdgeProperty(
    graph: ImmutableGraph,
    initializer: EdgeInitializer<T>,
    crossinline write: (T) -> Int,
    crossinline read: (Int) -> T
): EdgeProperty<T> = object : EdgeProperty<T> {

    override val graph: Graph get() = graph
    private val property = Long2IntOpenHashMap(graph.edges.size)

    init {
        for (edge in graph.edges) {
            property.put(edge.longValue, write(initializer.initialize(edge)))
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
    graph: ImmutableGraph,
    initializer: EdgeInitializer<T>,
    crossinline write: (T) -> Long,
    crossinline read: (Long) -> T
): EdgeProperty<T> = object : EdgeProperty<T> {

    override val graph: Graph get() = graph
    private val property = Long2LongOpenHashMap(graph.edges.size)

    init {
        for (edge in graph.edges) {
            property.put(edge.longValue, write(initializer.initialize(edge)))
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

private class ImmutableMapEdgeProperty<T>(
    override val graph: ImmutableGraph,
    initializer: EdgeInitializer<T>
) : EdgeProperty<T> {

    private val property = Long2ObjectOpenHashMap<T>(graph.edges.size)

    init {
        for (edge in graph.edges) {
            property.put(edge.longValue, initializer.initialize(edge))
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
internal fun <T : S?, S, G> immutableArrayEdgeProperty(
    graph: G,
    clazz: Class<S>,
    initializer: EdgeInitializer<T>
): EdgeProperty<T> where G : ImmutableGraph, G : EdgeIndexedEdgeGraph {
    return when (clazz) {
        java.lang.Boolean::class.java -> ImmutableArrayBooleanEdgeProperty(
            graph,
            initializer as EdgeInitializer<Boolean>
        ) as EdgeProperty<T>

        Integer::class.java -> ImmutableArray4ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Int>,
            { it },
            { it }) as EdgeProperty<T>

        java.lang.Float::class.java -> ImmutableArray4ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Float>,
            { java.lang.Float.floatToRawIntBits(it) },
            { java.lang.Float.intBitsToFloat(it) }) as EdgeProperty<T>

        java.lang.Long::class.java -> ImmutableArray8ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Long>,
            { it },
            { it }) as EdgeProperty<T>

        java.lang.Double::class.java -> ImmutableArray8ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Double>,
            { java.lang.Double.doubleToRawLongBits(it) },
            { java.lang.Double.longBitsToDouble(it) }) as EdgeProperty<T>

        else -> ImmutableArrayEdgeProperty(graph, initializer)
    }
}

private class ImmutableArrayBooleanEdgeProperty<G>(
    override val graph: G,
    initializer: EdgeInitializer<Boolean>
) : EdgeProperty<Boolean> where G : ImmutableGraph, G : EdgeIndexedEdgeGraph {

    private val property: BooleanArray = BooleanArray(graph.edges.size) { initializer.initialize(graph.edges[it]) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): Boolean {
        val index = edge.longValue.toInt()
        try {
            return property[index]
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in property", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: Boolean) {
        val index = edge.longValue.toInt()
        try {
            property[index] = value
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in property", e)
        }
    }
}

private inline fun <T, G> ImmutableArray4ByteEdgeProperty(
    graph: G,
    initializer: EdgeInitializer<T>,
    crossinline write: (T) -> Int,
    crossinline read: (Int) -> T
): EdgeProperty<T> where G : ImmutableGraph, G : EdgeIndexedEdgeGraph = object : EdgeProperty<T> {

    override val graph: Graph = graph
    private val property: IntArray = IntArray(graph.edges.size) { write(initializer.initialize(graph.edges[it])) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val index = edge.longValue.toInt()
        try {
            return read(property[index])
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in property", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        val index = edge.longValue.toInt()
        try {
            property[index] = write(value)
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in property", e)
        }
    }
}

private inline fun <T, G> ImmutableArray8ByteEdgeProperty(
    graph: G,
    initializer: EdgeInitializer<T>,
    crossinline write: (T) -> Long,
    crossinline read: (Long) -> T
): EdgeProperty<T> where G : ImmutableGraph, G : EdgeIndexedEdgeGraph = object : EdgeProperty<T> {

    override val graph: Graph = graph
    private val property: LongArray = LongArray(graph.edges.size) { write(initializer.initialize(graph.edges[it])) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val index = edge.longValue.toInt()
        try {
            return read(property[index])
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in property", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        val index = edge.longValue.toInt()
        try {
            property[index] = write(value)
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in property", e)
        }
    }
}

private class ImmutableArrayEdgeProperty<T, G>(
    override val graph: G,
    initializer: EdgeInitializer<T>
) : EdgeProperty<T> where G : ImmutableGraph, G : EdgeIndexedEdgeGraph {

    @Suppress("UNCHECKED_CAST")
    private val property: Array<T> =
        Array<Any?>(graph.edges.size) { initializer.initialize(graph.edges[it]) } as Array<T>

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val index = edge.longValue.toInt()
        try {
            return property[index]
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in property", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        val index = edge.longValue.toInt()
        try {
            property[index] = value
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in property", e)
        }
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
    initializer: EdgeInitializer<T>
): MutableEdgeProperty<T> {
    return when (clazz) {
        java.lang.Boolean::class.java -> MutableMap4ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Boolean>,
            { if (it) 1 else 0 },
            { it != 0 }
        ) as MutableEdgeProperty<T>

        Integer::class.java -> MutableMap4ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Int>,
            { it },
            { it }) as MutableEdgeProperty<T>

        java.lang.Float::class.java -> MutableMap4ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Float>,
            { java.lang.Float.floatToRawIntBits(it) },
            { java.lang.Float.intBitsToFloat(it) }) as MutableEdgeProperty<T>

        java.lang.Long::class.java -> MutableMap8ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Long>,
            { it },
            { it }) as MutableEdgeProperty<T>

        java.lang.Double::class.java -> MutableMap8ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Double>,
            { java.lang.Double.doubleToRawLongBits(it) },
            { java.lang.Double.longBitsToDouble(it) }) as MutableEdgeProperty<T>

        else -> MutableMapEdgeProperty(graph, initializer)
    }
}

private inline fun <T> MutableMap4ByteEdgeProperty(
    g: Graph,
    initializer: EdgeInitializer<T>,
    crossinline write: (T) -> Int,
    crossinline read: (Int) -> T
): MutableEdgeProperty<T> = object : MutableEdgeProperty<T> {

    override val graph = g
    private val property = Long2IntOpenHashMap(graph.edges.size)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val value: T
        if (!property.containsKey(edge.longValue)) {
            require(graph.edges.contains(edge)) { "$edge not found in graph" }
            value = initializer.initialize(edge)
            property[edge.longValue] = write(value)
        } else {
            value = read(property.get(edge.longValue))
        }
        return value
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
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
            property[swapEdge.longValue] = write(initializer.initialize(removeEdge))
        }
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)
}

private inline fun <T> MutableMap8ByteEdgeProperty(
    g: Graph,
    initializer: EdgeInitializer<T>,
    crossinline write: (T) -> Long,
    crossinline read: (Long) -> T
): MutableEdgeProperty<T> = object : MutableEdgeProperty<T> {

    override val graph = g
    private val property = Long2LongOpenHashMap(graph.edges.size)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val value: T
        if (!property.containsKey(edge.longValue)) {
            require(graph.edges.contains(edge)) { "$edge (${graph.edgeSource(edge)} -> ${graph.edgeTarget(edge)}) not found in graph" }
            value = initializer.initialize(edge)
            property[edge.longValue] = write(value)
        } else {
            value = read(property.get(edge.longValue))
        }
        return value
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
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
            property[swapEdge.longValue] = write(initializer.initialize(removeEdge))
        }
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)
}

private class MutableMapEdgeProperty<T>(override val graph: Graph, private val initializer: EdgeInitializer<T>) :
    MutableEdgeProperty<T> {

    private val property = Long2ObjectOpenHashMap<T>(graph.edges.size)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        var t = property[edge.longValue]
        if (t == null && !property.containsKey(edge.longValue)) {
            require(graph.edges.contains(edge)) { "$edge not found in property" }
            t = initializer.initialize(edge)
            property[edge.longValue] = t
        }
        return t
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
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
            property[swapEdge.longValue] = initializer.initialize(removeEdge)
        }
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)
}

@Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal fun <T : S?, S> mutableArrayListEdgeProperty(
    graph: EdgeIndexedEdgeGraph,
    clazz: Class<S>,
    initializer: EdgeInitializer<T>
): MutableEdgeProperty<T> {
    return when (clazz) {
        java.lang.Boolean::class.java -> MutableArrayListBooleanEdgeProperty(
            graph,
            initializer as EdgeInitializer<Boolean>,
        ) as MutableEdgeProperty<T>

        Integer::class.java -> MutableArrayList4ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Int>,
            { it },
            { it }) as MutableEdgeProperty<T>

        java.lang.Float::class.java -> MutableArrayList4ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Float>,
            { java.lang.Float.floatToRawIntBits(it) },
            { java.lang.Float.intBitsToFloat(it) }) as MutableEdgeProperty<T>

        java.lang.Long::class.java -> MutableArrayList8ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Long>,
            { it },
            { it }) as MutableEdgeProperty<T>

        java.lang.Double::class.java -> MutableArrayList8ByteEdgeProperty(
            graph,
            initializer as EdgeInitializer<Double>,
            { java.lang.Double.doubleToRawLongBits(it) },
            { java.lang.Double.longBitsToDouble(it) }) as MutableEdgeProperty<T>

        else -> MutableArrayListEdgeProperty(graph, initializer)
    }
}

private class MutableArrayListBooleanEdgeProperty(
    override val graph: EdgeIndexedEdgeGraph,
    private val initializer: EdgeInitializer<Boolean>
) : MutableEdgeProperty<Boolean> {

    // we don't use BooleanArrayList because it doesn't properly implement ensureCapacity as we'd expect (it expands to
    // the exact size given, rather than allowing for larger expansions to save future effort)
    private var property = BooleanArray(0)
    private var propertySize = 0

    private fun expand(size: Int) {
        if (size > propertySize) {
            require(size <= graph.edges.size)
            property = BooleanArrays.grow(property, size)
            for (i in propertySize..<size) {
                property[i] = initializer.initialize(graph.edges[i])
            }
            propertySize = size
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): Boolean {
        val index = edge.longValue.toInt()
        try {
            expand(index + 1)
            return property[index]
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in graph", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: Boolean) {
        val index = edge.longValue.toInt()
        try {
            expand(index + 1)
            property[index] = value
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in graph", e)
        }
    }

    override fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) {
        val oldIndex = removeEdge.longValue.toInt()
        require(oldIndex in 0..<graph.edges.size) { "$removeEdge not found in graph" }
        val newIndex = swapEdge.longValue.toInt()
        require(newIndex in 0..<graph.edges.size) { "$swapEdge not found in graph" }

        assert(oldIndex == graph.edges.lastIndex)
        if (oldIndex != newIndex && newIndex < propertySize) {
            if (oldIndex >= propertySize) {
                property[newIndex] = initializer.initialize(removeEdge)
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
    graph: EdgeIndexedEdgeGraph,
    initializer: EdgeInitializer<T>,
    crossinline write: (T) -> Int,
    crossinline read: (Int) -> T
): MutableEdgeProperty<T> = object : MutableEdgeProperty<T> {

    // we don't use IntArrayList because it doesn't properly implement ensureCapacity as we'd expect (it expands to the
    // exact size given, rather than allowing for larger expansions to save future effort)
    override val graph: Graph get() = graph
    private var property = IntArray(0)
    private var propertySize = 0

    private fun expand(size: Int) {
        if (size > propertySize) {
            require(size <= graph.edges.size)
            property = IntArrays.grow(property, size)
            for (i in propertySize..<size) {
                property[i] = write(initializer.initialize(graph.edges[i]))
            }
            propertySize = size
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val index = edge.longValue.toInt()
        try {
            expand(index + 1)
            return read(property[index])
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in graph", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        val index = edge.longValue.toInt()
        try {
            expand(index + 1)
            property[index] = write(value)
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in graph", e)
        }
    }

    override fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) {
        val oldIndex = removeEdge.longValue.toInt()
        require(oldIndex in 0..<graph.edges.size) { "$removeEdge not found in graph" }
        val newIndex = swapEdge.longValue.toInt()
        require(newIndex in 0..<graph.edges.size) { "$swapEdge not found in graph" }

        assert(oldIndex == graph.edges.lastIndex)
        if (oldIndex != newIndex && newIndex < propertySize) {
            if (oldIndex >= propertySize) {
                property[newIndex] = write(initializer.initialize(removeEdge))
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
    graph: EdgeIndexedEdgeGraph,
    initializer: EdgeInitializer<T>,
    crossinline write: (T) -> Long,
    crossinline read: (Long) -> T
): MutableEdgeProperty<T> = object : MutableEdgeProperty<T> {

    // we don't use LongArrayList because it doesn't properly implement ensureCapacity as we'd expect (it expands to the
    // exact size given, rather than allowing for larger expansions to save future effort)
    override val graph: Graph get() = graph
    private var property = LongArray(0)
    private var propertySize = 0

    private fun expand(size: Int) {
        if (size > propertySize) {
            require(size <= graph.edges.size)
            property = LongArrays.grow(property, size)
            for (i in propertySize..<size) {
                property[i] = write(initializer.initialize(graph.edges[i]))
            }
            propertySize = size
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val index = edge.longValue.toInt()
        try {
            expand(index + 1)
            return read(property[index])
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in graph", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        val index = edge.longValue.toInt()
        try {
            expand(index + 1)
            property[index] = write(value)
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in graph", e)
        }
    }

    override fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) {
        val oldIndex = removeEdge.longValue.toInt()
        require(oldIndex in 0..<graph.edges.size) { "$removeEdge not found in graph" }
        val newIndex = swapEdge.longValue.toInt()
        require(newIndex in 0..<graph.edges.size) { "$swapEdge not found in graph" }

        assert(oldIndex == graph.edges.lastIndex)
        if (oldIndex != newIndex && newIndex < propertySize) {
            if (oldIndex >= propertySize) {
                property[newIndex] = write(initializer.initialize(removeEdge))
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
    override val graph: EdgeIndexedEdgeGraph,
    private val initializer: EdgeInitializer<T>
) : MutableEdgeProperty<T> {

    private val property = ArrayList<T>(graph.edges.size)

    private fun expand(size: Int) {
        if (size > property.size) {
            require(size <= graph.edges.size)
            property.ensureCapacity(graph.edges.size)
            for (i in property.size..<size) {
                property.add(initializer.initialize(graph.edges[i]))
            }
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(edge: Edge): T {
        val index = edge.longValue.toInt()
        try {
            expand(index + 1)
            return property[index]
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in graph", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(edge: Edge, value: T) {
        val index = edge.longValue.toInt()
        try {
            expand(index + 1)
            property[index] = value
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$edge not found in graph", e)
        }
    }

    override fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) {
        val oldIndex = removeEdge.longValue.toInt()
        require(oldIndex in 0..<graph.edges.size) { "$removeEdge not found in graph" }
        val newIndex = swapEdge.longValue.toInt()
        require(newIndex in 0..<graph.edges.size) { "$swapEdge not found in graph" }

        assert(oldIndex == graph.edges.lastIndex)
        if (oldIndex != newIndex && newIndex < property.size) {
            if (oldIndex > property.lastIndex) {
                property[newIndex] = initializer.initialize(removeEdge)
            } else {
                property[newIndex] = property[oldIndex]
            }
        }
        if (oldIndex == property.lastIndex) {
            property.removeAt(oldIndex)
        }
    }

    override fun ensureCapacity(capacity: Int) {
        property.ensureCapacity(capacity)
    }
}
