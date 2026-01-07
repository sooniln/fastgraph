package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.VertexSetList
import it.unimi.dsi.fastutil.booleans.BooleanArrays
import it.unimi.dsi.fastutil.ints.IntArrays
import it.unimi.dsi.fastutil.longs.LongArrays
import kotlin.math.max

@Suppress("UNCHECKED_CAST")
internal fun <T> emptyVertexProperty(): VertexProperty<T> = EmptyVertexProperty as VertexProperty<T>

private object EmptyVertexProperty : VertexProperty<Nothing> {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): Nothing = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: Nothing) = throw IllegalArgumentException()
}

@Suppress("UNCHECKED_CAST")
internal fun <T> nothingVertexProperty(): VertexProperty<T> = NothingVertexProperty as VertexProperty<T>

private object NothingVertexProperty : VertexProperty<Nothing> {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): Nothing = throw IllegalStateException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: Nothing) = throw IllegalStateException()
}

@Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal fun <T : S?, S> immutableArrayVertexProperty(
    vertices: VertexSetList,
    clazz: Class<S>,
    initializer: (Vertex) -> T
): VertexProperty<T> {
    return when (clazz) {
        java.lang.Boolean::class.java -> ImmutableArrayBooleanVertexProperty(
            vertices,
            initializer as (Vertex) -> Boolean
        ) as VertexProperty<T>

        Integer::class.java -> ImmutableArray4ByteVertexProperty(
            vertices,
            initializer as (Vertex) -> Int,
            { it },
            { it }) as VertexProperty<T>

        java.lang.Float::class.java -> ImmutableArray4ByteVertexProperty(
            vertices,
            initializer as (Vertex) -> Float,
            { java.lang.Float.floatToRawIntBits(it) },
            { java.lang.Float.intBitsToFloat(it) }) as VertexProperty<T>

        java.lang.Long::class.java -> ImmutableArray8ByteVertexProperty(
            vertices,
            initializer as (Vertex) -> Long,
            { it },
            { it }) as VertexProperty<T>

        java.lang.Double::class.java -> ImmutableArray8ByteVertexProperty(
            vertices,
            initializer as (Vertex) -> Double,
            { java.lang.Double.doubleToRawLongBits(it) },
            { java.lang.Double.longBitsToDouble(it) }) as VertexProperty<T>

        else -> ImmutableArrayVertexProperty(vertices, initializer)
    }
}

private class ImmutableArrayBooleanVertexProperty(vertices: VertexSetList, initializer: (Vertex) -> Boolean) :
    VertexProperty<Boolean> {
    private val property: BooleanArray = BooleanArray(vertices.size) { initializer(Vertex(it)) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): Boolean {
        if (vertex.intValue !in 0..<property.size) throw IllegalArgumentException("$vertex not found in property")
        return property[vertex.intValue]
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: Boolean) {
        if (vertex.intValue !in 0..<property.size) throw IllegalArgumentException("$vertex not found in property")
        property[vertex.intValue] = value
    }
}

private inline fun <T> ImmutableArray4ByteVertexProperty(
    vertices: VertexSetList,
    crossinline initializer: (Vertex) -> T,
    crossinline write: (T) -> Int,
    crossinline read: (Int) -> T
): VertexProperty<T> = object : VertexProperty<T> {

    private val property: IntArray = IntArray(vertices.size) { write(initializer(Vertex(it))) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): T {
        if (vertex.intValue !in 0..<property.size) throw IllegalArgumentException("$vertex not found in property")
        return read(property[vertex.intValue])
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: T) {
        if (vertex.intValue !in 0..<property.size) throw IllegalArgumentException("$vertex not found in property")
        property[vertex.intValue] = write(value)
    }
}

private inline fun <T> ImmutableArray8ByteVertexProperty(
    vertices: VertexSetList,
    crossinline initializer: (Vertex) -> T,
    crossinline write: (T) -> Long,
    crossinline read: (Long) -> T
): VertexProperty<T> = object : VertexProperty<T> {

    private val property: LongArray = LongArray(vertices.size) { write(initializer(Vertex(it))) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): T {
        if (vertex.intValue !in 0..<property.size) throw IllegalArgumentException("$vertex not found in property")
        return read(property[vertex.intValue])
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: T) {
        if (vertex.intValue !in 0..<property.size) throw IllegalArgumentException("$vertex not found in property")
        property[vertex.intValue] = write(value)
    }
}

private class ImmutableArrayVertexProperty<T>(vertices: VertexSetList, initializer: (Vertex) -> T) : VertexProperty<T> {

    @Suppress("UNCHECKED_CAST")
    private val property: Array<T> = Array<Any?>(vertices.size) { initializer(Vertex(it)) } as Array<T>

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    override fun get(vertex: Vertex): T {
        if (vertex.intValue !in 0..<property.size) throw IllegalArgumentException("$vertex not found in property")
        return property[vertex.intValue]
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    override fun set(vertex: Vertex, value: T) {
        if (vertex.intValue !in 0..<property.size) throw IllegalArgumentException("$vertex not found in property")
        property[vertex.intValue] = value
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
    vertices: VertexSetList,
    clazz: Class<S>,
    initializer: (Vertex) -> T
): MutableVertexProperty<T> {
    return when (clazz) {
        java.lang.Boolean::class.java -> MutableArrayListBooleanVertexProperty(
            vertices,
            initializer as (Vertex) -> Boolean,
        ) as MutableVertexProperty<T>

        Integer::class.java -> MutableArrayList4ByteVertexProperty(
            vertices,
            initializer as (Vertex) -> Int,
            { it },
            { it }) as MutableVertexProperty<T>

        java.lang.Float::class.java -> MutableArrayList4ByteVertexProperty(
            vertices,
            initializer as (Vertex) -> Float,
            { java.lang.Float.floatToRawIntBits(it) },
            { java.lang.Float.intBitsToFloat(it) }) as MutableVertexProperty<T>

        java.lang.Long::class.java -> MutableArrayList8ByteVertexProperty(
            vertices,
            initializer as (Vertex) -> Long,
            { it },
            { it }) as MutableVertexProperty<T>

        java.lang.Double::class.java -> MutableArrayList8ByteVertexProperty(
            vertices,
            initializer as (Vertex) -> Double,
            { java.lang.Double.doubleToRawLongBits(it) },
            { java.lang.Double.longBitsToDouble(it) }) as MutableVertexProperty<T>

        else -> MutableArrayListVertexProperty(vertices, initializer)
    }
}

private class MutableArrayListBooleanVertexProperty(
    private val vertices: VertexSetList,
    private val initializer: (Vertex) -> Boolean
) : MutableVertexProperty<Boolean> {

    // we don't use BooleanArrayList because it doesn't properly implement ensureCapacity as we'd expect (it expands to
    // the exact size given, rather than allowing for larger expansions to save future effort)
    private var property = BooleanArray(0)
    private var propertySize = 0

    init {
        expand(vertices.size)
    }

    private fun expand(size: Int) {
        assert(size <= vertices.size)
        if (size > property.size) {
            property = BooleanArrays.grow(property, max(10, size))
            for (i in propertySize..<size) {
                property[i] = initializer(vertices[i])
            }
            propertySize = size
        }
    }

    override fun get(vertex: Vertex): Boolean {
        val index = vertices.indexOf(vertex)
        require(index >= 0) { "$vertex not found in graph" }
        expand(index + 1)
        return property[index]
    }

    override fun set(vertex: Vertex, value: Boolean) {
        val index = vertices.indexOf(vertex)
        require(index >= 0) { "$vertex not found in graph" }
        expand(index + 1)
        property[index] = value
    }

    override fun swapAndRemove(removeVertex: Vertex, swapVertex: Vertex) {
        val oldIndex = vertices.indexOf(removeVertex)
        require(oldIndex >= 0) { "$removeVertex not found in graph" }
        val newIndex = vertices.indexOf(swapVertex)
        require(newIndex >= 0) { "$swapVertex not found in graph" }

        assert(oldIndex == vertices.lastIndex)
        if (oldIndex != newIndex && newIndex < propertySize) {
            if (oldIndex >= propertySize) {
                property[newIndex] = initializer(removeVertex)
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
    v: VertexSetList,
    crossinline initializer: (Vertex) -> T,
    crossinline write: (T) -> Int,
    crossinline read: (Int) -> T
): MutableVertexProperty<T> = object : MutableVertexProperty<T> {

    // we don't use LongArrayList because it doesn't properly implement ensureCapacity as we'd expect (it expands to the
    // exact size given, rather than allowing for larger expansions to save future effort)
    private val vertices = v
    private var property = IntArray(0)
    private var propertySize = 0

    init {
        expand(vertices.size)
    }

    private fun expand(size: Int) {
        assert(size <= vertices.size)
        if (size > property.size) {
            property = IntArrays.grow(property, max(10, size))
            for (i in propertySize..<size) {
                property[i] = write(initializer(vertices[i]))
            }
            propertySize = size
        }
    }

    override fun get(vertex: Vertex): T {
        val index = vertices.indexOf(vertex)
        require(index >= 0) { "$vertex not found in graph" }
        expand(index + 1)
        return read(property[index])
    }

    override fun set(vertex: Vertex, value: T) {
        val index = vertices.indexOf(vertex)
        require(index >= 0) { "$vertex not found in graph" }
        expand(index + 1)
        property[index] = write(value)
    }

    override fun swapAndRemove(removeVertex: Vertex, swapVertex: Vertex) {
        val oldIndex = vertices.indexOf(removeVertex)
        require(oldIndex >= 0) { "$removeVertex not found in graph" }
        val newIndex = vertices.indexOf(swapVertex)
        require(newIndex >= 0) { "$swapVertex not found in graph" }

        assert(oldIndex == vertices.lastIndex)
        if (oldIndex != newIndex && newIndex < propertySize) {
            if (oldIndex >= propertySize) {
                property[newIndex] = write(initializer(removeVertex))
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
    v: VertexSetList,
    crossinline initializer: (Vertex) -> T,
    crossinline write: (T) -> Long,
    crossinline read: (Long) -> T
): MutableVertexProperty<T> = object : MutableVertexProperty<T> {

    // we don't use LongArrayList because it doesn't properly implement ensureCapacity as we'd expect (it expands to the
    // exact size given, rather than allowing for larger expansions to save future effort)
    private val vertices = v
    private var property = LongArray(0)
    private var propertySize = 0

    init {
        expand(vertices.size)
    }

    private fun expand(size: Int) {
        assert(size <= vertices.size)
        if (size > property.size) {
            property = LongArrays.grow(property, max(10, size))
            for (i in propertySize..<size) {
                property[i] = write(initializer(vertices[i]))
            }
            propertySize = size
        }
    }

    override fun get(vertex: Vertex): T {
        val index = vertices.indexOf(vertex)
        require(index >= 0) { "$vertex not found in graph" }
        expand(index + 1)
        return read(property[index])
    }

    override fun set(vertex: Vertex, value: T) {
        val index = vertices.indexOf(vertex)
        require(index >= 0) { "$vertex not found in graph" }
        expand(index + 1)
        property[index] = write(value)
    }

    override fun swapAndRemove(removeVertex: Vertex, swapVertex: Vertex) {
        val oldIndex = vertices.indexOf(removeVertex)
        require(oldIndex >= 0) { "$removeVertex not found in graph" }
        val newIndex = vertices.indexOf(swapVertex)
        require(newIndex >= 0) { "$swapVertex not found in graph" }

        assert(oldIndex == vertices.lastIndex)
        if (oldIndex != newIndex && newIndex < propertySize) {
            if (oldIndex >= propertySize) {
                property[newIndex] = write(initializer(removeVertex))
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
    private val vertices: VertexSetList,
    private val initializer: (Vertex) -> T
) : MutableVertexProperty<T> {

    private val property = ArrayList<T>(vertices.size)

    init {
        expand(vertices.size)
    }

    private fun expand(size: Int) {
        assert(size <= vertices.size)
        property.ensureCapacity(size)
        for (i in property.size..<size) {
            property.add(initializer(vertices[i]))
        }
    }

    override fun get(vertex: Vertex): T {
        val index = vertices.indexOf(vertex)
        require(vertex.intValue >= 0) { "$vertex not found in graph" }
        expand(index + 1)
        return property[index]
    }

    override fun set(vertex: Vertex, value: T) {
        val index = vertices.indexOf(vertex)
        require(vertex.intValue >= 0) { "$vertex not found in graph" }
        expand(index + 1)
        property[index] = value
    }

    override fun swapAndRemove(removeVertex: Vertex, swapVertex: Vertex) {
        val oldIndex = vertices.indexOf(removeVertex)
        require(oldIndex >= 0) { "$removeVertex not found in graph" }
        val newIndex = vertices.indexOf(swapVertex)
        require(newIndex >= 0) { "$swapVertex not found in graph" }

        assert(oldIndex == vertices.lastIndex)
        if (removeVertex != swapVertex && swapVertex.intValue < property.size) {
            if (removeVertex.intValue > property.lastIndex) {
                property[swapVertex.intValue] = initializer(removeVertex)
            } else {
                property[swapVertex.intValue] = property[removeVertex.intValue]
            }
        }
        if (removeVertex.intValue == property.lastIndex) {
            property.removeAt(removeVertex.intValue)
        }
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)
}
