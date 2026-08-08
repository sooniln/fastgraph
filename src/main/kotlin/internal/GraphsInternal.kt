package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.longs.LongArrayList
import io.github.sooniln.fastcollect.longs.lastIndex
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import kotlin.math.max
import kotlin.math.min

internal fun throwIllegalVertex(vertex: Vertex, cause: Throwable? = null): Nothing {
    throw IllegalArgumentException("$vertex not found in graph", cause)
}

@JvmName("throwGraphIllegalEdge")
context(graph: Graph)
internal fun throwIllegalEdge(edge: Edge, cause: Throwable? = null): Nothing {
    throw IllegalArgumentException(
        "$edge (${graph.edgeSource(edge)} -> ${graph.edgeTarget(edge)}) not found in graph",
        cause
    )
}

internal fun throwIllegalEdge(graph: Graph, edge: Edge, cause: Throwable? = null): Nothing {
    context(graph) { throwIllegalEdge(edge, cause) }
}

internal class ImmutableVertexReference(override val unstable: Vertex) : VertexReference

internal class ImmutableEdgeReference(override val unstable: Edge) : EdgeReference

@JvmInline
internal value class EdgeValue(val longValue: Long) {

    constructor(directed: Boolean, source: Vertex, target: Vertex) : this(
        if (!directed) {
            constructLongValue(
                highBits = min(source.id, target.id),
                lowBits = max(source.id, target.id)
            )
        } else {
            constructLongValue(highBits = source.id, lowBits = target.id)
        }
    )

    val source: Vertex
        inline get() = Vertex(longValue.ushr(32).toInt())

    val target: Vertex
        inline get() = Vertex(longValue.toInt())

    override fun toString(): String = "EdgeValue($source, $target)"
}

@Suppress("OVERRIDE_BY_INLINE", "NOTHING_TO_INLINE")
@JvmInline
internal value class EdgeValueArrayList private constructor(private val arrayList: LongArrayList)  {
    constructor() : this(LongArrayList())

    inline fun ensureCapacity(minimumCapacity: Int) = arrayList.ensureCapacity(minimumCapacity)

    val size: Int inline get() = arrayList.size
    val lastIndex: Int inline get() = arrayList.lastIndex
    val indices: IntRange inline get() = arrayList.indices

    inline fun isEmpty(): Boolean = arrayList.isEmpty()
    inline operator fun get(index: Int): EdgeValue = EdgeValue(arrayList[index])
    inline operator fun set(index: Int, element: EdgeValue): EdgeValue =
        EdgeValue(arrayList.replace(index, element.longValue))
    inline fun add(element: EdgeValue): Int { val index = size; arrayList.add(element.longValue); return index }
    inline fun removeAt(index: Int): EdgeValue = EdgeValue(arrayList.removeAt(index))
    inline fun clear() = arrayList.clear()

    override fun toString(): String = arrayList.joinToString(", ", "[", "]") { EdgeValue(it).toString() }
}

@Suppress("OVERRIDE_BY_INLINE", "NOTHING_TO_INLINE")
@JvmInline
internal value class EdgeValueArray(private val array: LongArray) {

    val size: Int inline get() = array.size
    val lastIndex: Int inline get() = array.lastIndex
    val indices: IntRange inline get() = array.indices

    inline fun isEmpty(): Boolean = array.isEmpty()
    inline operator fun get(index: Int): EdgeValue = EdgeValue(array[index])
    inline operator fun set(index: Int, value: EdgeValue) { array[index] = value.longValue }

    override fun toString(): String = array.joinToString(", ", "[", "]") { EdgeValue(it).toString() }
}

internal inline fun EdgeValueArray(size: Int, init: (Int) -> EdgeValue) : EdgeValueArray {
    return EdgeValueArray(LongArray(size) { init(it).longValue })
}

@JvmInline
internal value class EdgeAdjacency(val longValue: Long) {

    constructor(vertex: Vertex, edgeId: Int) : this(
        constructLongValue(highBits = vertex.id, lowBits = edgeId)
    )

    val vertex: Vertex
        inline get() = Vertex(longValue.ushr(32).toInt())

    val edgeId: Int
        inline get() = longValue.toInt()

    override fun toString(): String = "EdgeAdjacency($vertex, $edgeId)"
}

internal interface EdgeAdjacencyIterator : Iterator<EdgeAdjacency> {
    override fun next(): EdgeAdjacency
}

internal fun interface EdgeAdjacencyConsumer {
    fun accept(value: EdgeAdjacency)
}

internal interface EdgeAdjacencySet {
    val size: Int

    val vertices: VertexSet

    fun isEmpty(): Boolean = size == 0
    fun contains(element: EdgeAdjacency): Boolean
    fun contains(vertex: Vertex): Boolean = vertices.contains(vertex)
    fun iterator(): EdgeAdjacencyIterator
    fun foreach(action: EdgeAdjacencyConsumer) {
        val it = iterator()
        while (it.hasNext()) {
            action.accept(it.next())
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun constructLongValue(highBits: Int, lowBits: Int): Long =
    highBits.toLong().shl(32).or(lowBits.toLong().and(0xFFFFFFFF))
