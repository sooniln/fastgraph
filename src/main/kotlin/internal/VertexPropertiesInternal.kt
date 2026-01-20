package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIndexedVertexGraph
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.VertexProperty
import it.unimi.dsi.fastutil.booleans.BooleanArrays
import it.unimi.dsi.fastutil.ints.IntArrays
import it.unimi.dsi.fastutil.longs.LongArrays

@Suppress("UNCHECKED_CAST")
internal fun <T> emptyVertexProperty(graph: Graph): VertexProperty<T> = EmptyVertexProperty(graph) as VertexProperty<T>

private class EmptyVertexProperty(override val graph: Graph) : VertexProperty<Nothing> {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): Nothing = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: Nothing) = throw IllegalArgumentException()
}

@Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal fun <T : S?, S, G> immutableArrayVertexProperty(
    graph: G,
    clazz: Class<S>,
    initializer: VertexInitializer<T>
): VertexProperty<T> where G : ImmutableGraph, G : VertexIndexedVertexGraph {
    return when (clazz) {
        java.lang.Boolean::class.java -> ImmutableArrayBooleanVertexProperty(
            graph,
            initializer as VertexInitializer<Boolean>
        ) as VertexProperty<T>

        Integer::class.java -> ImmutableArray4ByteVertexProperty(
            graph,
            initializer as VertexInitializer<Int>,
            { it },
            { it }) as VertexProperty<T>

        java.lang.Float::class.java -> ImmutableArray4ByteVertexProperty(
            graph,
            initializer as VertexInitializer<Float>,
            { java.lang.Float.floatToRawIntBits(it) },
            { java.lang.Float.intBitsToFloat(it) }) as VertexProperty<T>

        java.lang.Long::class.java -> ImmutableArray8ByteVertexProperty(
            graph,
            initializer as VertexInitializer<Long>,
            { it },
            { it }) as VertexProperty<T>

        java.lang.Double::class.java -> ImmutableArray8ByteVertexProperty(
            graph,
            initializer as VertexInitializer<Double>,
            { java.lang.Double.doubleToRawLongBits(it) },
            { java.lang.Double.longBitsToDouble(it) }) as VertexProperty<T>

        else -> ImmutableArrayVertexProperty(graph, initializer)
    }
}

private class ImmutableArrayBooleanVertexProperty<G>(
    override val graph: G,
    initializer: VertexInitializer<Boolean>
) : VertexProperty<Boolean> where G : ImmutableGraph, G : VertexIndexedVertexGraph {
    private val property: BooleanArray = BooleanArray(graph.vertices.size) { initializer.initialize(Vertex(it)) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): Boolean {
        try {
            return property[vertex.intValue]
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in property", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: Boolean) {
        try {
            property[vertex.intValue] = value
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in property", e)
        }
    }
}

private inline fun <T, G> ImmutableArray4ByteVertexProperty(
    graph: G,
    initializer: VertexInitializer<T>,
    crossinline write: (T) -> Int,
    crossinline read: (Int) -> T
): VertexProperty<T> where G : ImmutableGraph, G : VertexIndexedVertexGraph = object : VertexProperty<T> {

    override val graph: Graph get() = graph
    private val property: IntArray = IntArray(graph.vertices.size) { write(initializer.initialize(Vertex(it))) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): T {
        try {
            return read(property[vertex.intValue])
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in property", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: T) {
        try {
            property[vertex.intValue] = write(value)
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in property", e)
        }
    }
}

private inline fun <T, G> ImmutableArray8ByteVertexProperty(
    graph: G,
    initializer: VertexInitializer<T>,
    crossinline write: (T) -> Long,
    crossinline read: (Long) -> T
): VertexProperty<T> where G : ImmutableGraph, G : VertexIndexedVertexGraph = object : VertexProperty<T> {

    override val graph: Graph get() = graph
    private val property: LongArray = LongArray(graph.vertices.size) { write(initializer.initialize(Vertex(it))) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): T {
        try {
            return read(property[vertex.intValue])
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in property", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: T) {
        try {
            property[vertex.intValue] = write(value)
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in property", e)
        }
    }
}

private class ImmutableArrayVertexProperty<T, G>(
    override val graph: G,
    initializer: VertexInitializer<T>
) : VertexProperty<T> where G : ImmutableGraph, G : VertexIndexedVertexGraph {

    @Suppress("UNCHECKED_CAST")
    private val property: Array<T> = Array<Any?>(graph.vertices.size) { initializer.initialize(Vertex(it)) } as Array<T>

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): T {
        try {
            return property[vertex.intValue]
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in property", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: T) {
        try {
            property[vertex.intValue] = value
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in property", e)
        }
    }
}

internal interface MutableVertexProperty<V> : VertexProperty<V> {
    /**
     * Set `swapVertex` property to `removeVertex` property and remove `removeVertex` property. Vertices may be the
     * same, in which case they can simply be removed.
     */
    fun swapAndRemove(removeVertex: Vertex, swapVertex: Vertex)

    fun ensureCapacity(capacity: Int)
}

@Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal fun <T : S?, S> mutableArrayListVertexProperty(
    graph: VertexIndexedVertexGraph,
    clazz: Class<S>,
    initializer: VertexInitializer<T>
): MutableVertexProperty<T> {
    return when (clazz) {
        java.lang.Boolean::class.java -> MutableArrayListBooleanVertexProperty(
            graph,
            initializer as VertexInitializer<Boolean>,
        ) as MutableVertexProperty<T>

        Integer::class.java -> MutableArrayList4ByteVertexProperty(
            graph,
            initializer as VertexInitializer<Int>,
            { it },
            { it }) as MutableVertexProperty<T>

        java.lang.Float::class.java -> MutableArrayList4ByteVertexProperty(
            graph,
            initializer as VertexInitializer<Float>,
            { java.lang.Float.floatToRawIntBits(it) },
            { java.lang.Float.intBitsToFloat(it) }) as MutableVertexProperty<T>

        java.lang.Long::class.java -> MutableArrayList8ByteVertexProperty(
            graph,
            initializer as VertexInitializer<Long>,
            { it },
            { it }) as MutableVertexProperty<T>

        java.lang.Double::class.java -> MutableArrayList8ByteVertexProperty(
            graph,
            initializer as VertexInitializer<Double>,
            { java.lang.Double.doubleToRawLongBits(it) },
            { java.lang.Double.longBitsToDouble(it) }) as MutableVertexProperty<T>

        else -> MutableArrayListVertexProperty(graph, initializer)
    }
}

private class MutableArrayListBooleanVertexProperty(
    override val graph: VertexIndexedVertexGraph,
    private val initializer: VertexInitializer<Boolean>
) : MutableVertexProperty<Boolean> {

    // we don't use BooleanArrayList because it doesn't properly implement ensureCapacity as we'd expect (it expands to
    // the exact size given, rather than allowing for larger expansions to save future effort)
    private var property = BooleanArray(0)
    private var propertySize = 0

    private fun expand(size: Int) {
        if (size > propertySize) {
            require(size <= graph.vertices.size)
            property = BooleanArrays.grow(property, size)
            for (i in propertySize..<size) {
                property[i] = initializer.initialize(Vertex(i))
            }
            propertySize = size
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): Boolean {
        val index = vertex.intValue
        try {
            expand(index + 1)
            return property[index]
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in graph", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: Boolean) {
        val index = vertex.intValue
        try {
            expand(index + 1)
            property[index] = value
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in graph", e)
        }
    }

    override fun swapAndRemove(removeVertex: Vertex, swapVertex: Vertex) {
        val oldIndex = removeVertex.intValue
        require(oldIndex in 0..<graph.vertices.size) { "$removeVertex not found in graph" }
        val newIndex = swapVertex.intValue
        require(newIndex in 0..<graph.vertices.size) { "$swapVertex not found in graph" }

        assert(oldIndex == graph.vertices.lastIndex)
        if (oldIndex != newIndex && newIndex < propertySize) {
            if (oldIndex >= propertySize) {
                property[newIndex] = initializer.initialize(removeVertex)
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

private inline fun <T> MutableArrayList4ByteVertexProperty(
    graph: VertexIndexedVertexGraph,
    initializer: VertexInitializer<T>,
    crossinline write: (T) -> Int,
    crossinline read: (Int) -> T
): MutableVertexProperty<T> = object : MutableVertexProperty<T> {

    // we don't use LongArrayList because it doesn't properly implement ensureCapacity as we'd expect (it expands to the
    // exact size given, rather than allowing for larger expansions to save future effort)
    override val graph: Graph get() = graph
    private var property = IntArray(0)
    private var propertySize = 0

    private fun expand(size: Int) {
        if (size > propertySize) {
            require(size <= graph.vertices.size)
            property = IntArrays.grow(property, size)
            for (i in propertySize..<size) {
                property[i] = write(initializer.initialize(Vertex(i)))
            }
            propertySize = size
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): T {
        val index = vertex.intValue
        try {
            expand(index + 1)
            return read(property[index])
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in graph", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: T) {
        val index = vertex.intValue
        try {
            expand(index + 1)
            property[index] = write(value)
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in graph", e)
        }
    }

    override fun swapAndRemove(removeVertex: Vertex, swapVertex: Vertex) {
        val oldIndex = removeVertex.intValue
        require(oldIndex in 0..<graph.vertices.size) { "$removeVertex not found in graph" }
        val newIndex = swapVertex.intValue
        require(newIndex in 0..<graph.vertices.size) { "$swapVertex not found in graph" }

        assert(oldIndex == graph.vertices.lastIndex)
        if (oldIndex != newIndex && newIndex < propertySize) {
            if (oldIndex >= propertySize) {
                property[newIndex] = write(initializer.initialize(removeVertex))
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

private inline fun <T> MutableArrayList8ByteVertexProperty(
    graph: VertexIndexedVertexGraph,
    initializer: VertexInitializer<T>,
    crossinline write: (T) -> Long,
    crossinline read: (Long) -> T
): MutableVertexProperty<T> = object : MutableVertexProperty<T> {

    // we don't use LongArrayList because it doesn't properly implement ensureCapacity as we'd expect (it expands to the
    // exact size given, rather than allowing for larger expansions to save future effort)
    override val graph: Graph get() = graph
    private var property = LongArray(0)
    private var propertySize = 0

    private fun expand(size: Int) {
        if (size > propertySize) {
            require(size <= graph.vertices.size)
            property = LongArrays.grow(property, size)
            for (i in propertySize..<size) {
                property[i] = write(initializer.initialize(Vertex(i)))
            }
            propertySize = size
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): T {
        val index = vertex.intValue
        try {
            expand(index + 1)
            return read(property[index])
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in graph", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: T) {
        val index = vertex.intValue
        try {
            expand(index + 1)
            property[index] = write(value)
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in graph", e)
        }
    }

    override fun swapAndRemove(removeVertex: Vertex, swapVertex: Vertex) {
        val oldIndex = removeVertex.intValue
        require(oldIndex in 0..<graph.vertices.size) { "$removeVertex not found in graph" }
        val newIndex = swapVertex.intValue
        require(newIndex in 0..<graph.vertices.size) { "$swapVertex not found in graph" }

        assert(oldIndex == graph.vertices.lastIndex)
        if (oldIndex != newIndex && newIndex < propertySize) {
            if (oldIndex >= propertySize) {
                property[newIndex] = write(initializer.initialize(removeVertex))
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

private class MutableArrayListVertexProperty<T>(
    override val graph: VertexIndexedVertexGraph,
    private val initializer: VertexInitializer<T>
) : MutableVertexProperty<T> {

    private val property = ArrayList<T>(graph.vertices.size)

    private fun expand(size: Int) {
        if (size > property.size) {
            require(size <= graph.vertices.size)
            property.ensureCapacity(graph.vertices.size)
            for (i in property.size..<size) {
                property.add(initializer.initialize(Vertex(i)))
            }
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): T {
        val index = vertex.intValue
        try {
            expand(index + 1)
            return property[index]
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in graph", e)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: T) {
        val index = vertex.intValue
        try {
            expand(index + 1)
            property[index] = value
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw IllegalArgumentException("$vertex not found in graph", e)
        }
    }

    override fun swapAndRemove(removeVertex: Vertex, swapVertex: Vertex) {
        val oldIndex = removeVertex.intValue
        require(oldIndex in 0..<graph.vertices.size) { "$removeVertex not found in graph" }
        val newIndex = swapVertex.intValue
        require(newIndex in 0..<graph.vertices.size) { "$swapVertex not found in graph" }

        assert(oldIndex == graph.vertices.lastIndex)
        if (removeVertex != swapVertex && swapVertex.intValue < property.size) {
            if (removeVertex.intValue > property.lastIndex) {
                property[swapVertex.intValue] = initializer.initialize(removeVertex)
            } else {
                property[swapVertex.intValue] = property[removeVertex.intValue]
            }
        }
        if (removeVertex.intValue == property.lastIndex) {
            property.removeAt(removeVertex.intValue)
        }
    }

    override fun ensureCapacity(capacity: Int) {
        property.ensureCapacity(capacity)
    }
}
