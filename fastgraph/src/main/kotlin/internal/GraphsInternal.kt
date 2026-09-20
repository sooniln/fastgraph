package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.*
import io.github.sooniln.fastgraph.AbstractEdgeSequencedSet
import io.github.sooniln.fastgraph.AbstractVertexSequencedSet
import io.github.sooniln.fastgraph.CanonicalEdge
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IdentityIndexedEdge
import io.github.sooniln.fastgraph.IdentityIndexedEdgeSet
import io.github.sooniln.fastgraph.MutableEdgeIterator
import io.github.sooniln.fastgraph.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.MutableIdentityIndexedVertexSet
import io.github.sooniln.fastgraph.MutableIndexedEdgeSet
import io.github.sooniln.fastgraph.MutableVertexIterator
import io.github.sooniln.fastgraph.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.reparent
import kotlin.math.max
import kotlin.math.min

internal fun throwIllegalVertex(vertex: Vertex, cause: Throwable? = null): Nothing {
    throw IllegalArgumentException("$vertex not found in graph", cause)
}

@JvmName("throwGraphIllegalEdge")
context(graph: Graph)
internal fun throwIllegalEdge(edge: Edge, cause: Throwable? = null): Nothing {
    // the endpoints can only be resolved for an edge the graph knows about (e.g. one that belongs to a filtered view's
    // parent) - resolving an unknown edge would fail or recurse back into this method
    if (!graph.edges.contains(edge)) throw IllegalArgumentException("$edge not found in graph", cause)
    throw IllegalArgumentException(
        "$edge (${graph.edgeSource(edge).id} -> ${graph.edgeTarget(edge).id}) not found in graph",
        cause
    )
}

internal fun throwIllegalEdge(graph: Graph, edge: Edge, cause: Throwable? = null): Nothing {
    context(graph) { throwIllegalEdge(edge, cause) }
}

internal fun throwIllegalEdge(graph: Graph, edge: IdentityIndexedEdge, cause: Throwable? = null): Nothing {
    context(graph) { throwIllegalEdge(edge.toEdge(), cause) }
}

internal fun throwIllegalEdge(edge: CanonicalEdge, cause: Throwable? = null): Nothing {
    throw IllegalArgumentException("$edge not found in graph", cause)
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

    inline fun isEmpty(): Boolean = arrayList.isEmpty()
    inline operator fun get(index: Int): EdgeValue = EdgeValue(arrayList[index])
    inline operator fun set(index: Int, element: EdgeValue): EdgeValue =
        EdgeValue(arrayList.replace(index, element.longValue))
    inline fun add(element: EdgeValue): Int { val index = size; arrayList.add(element.longValue); return index }
    inline fun removeAt(index: Int): EdgeValue = EdgeValue(arrayList.removeAt(index))
    inline fun clear() = arrayList.clear()

    override fun toString(): String = Iterable { arrayList.iterator() }.joinToString(", ", "[", "]") { EdgeValue(it).toString() }
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

internal interface EdgeAdjacencySet {
    val size: Int
    val vertices: VertexSet

    fun isEmpty(): Boolean = size == 0
    fun contains(element: EdgeAdjacency): Boolean
    fun contains(vertex: Vertex): Boolean = vertices.contains(vertex)
    fun edgeIterator(): EdgeIterator
}

internal class TransposedGraph(val graph: Graph) : Graph by graph {
    override fun outDegree(vertex: Vertex): Int = graph.inDegree(vertex)
    override fun inDegree(vertex: Vertex): Int = graph.outDegree(vertex)
    override fun successors(vertex: Vertex): VertexSet = graph.predecessors(vertex)
    override fun successor(vertex: Vertex): Vertex = graph.predecessor(vertex)
    override fun predecessors(vertex: Vertex): VertexSet = graph.successors(vertex)
    override fun predecessor(vertex: Vertex): Vertex = graph.successor(vertex)
    override fun outgoingEdges(vertex: Vertex): EdgeSet = graph.incomingEdges(vertex)
    override fun outgoingEdge(vertex: Vertex): Edge = graph.incomingEdge(vertex)
    override fun incomingEdges(vertex: Vertex): EdgeSet = graph.outgoingEdges(vertex)
    override fun incomingEdge(vertex: Vertex): Edge = graph.outgoingEdge(vertex)
    override fun edgeSource(edge: Edge): Vertex = graph.edgeTarget(edge)
    override fun edgeTarget(edge: Edge): Vertex = graph.edgeSource(edge)
    override fun hasEdge(source: Vertex, target: Vertex): Boolean = graph.hasEdge(target, source)
    override fun edge(source: Vertex, target: Vertex): Edge = graph.edge(target, source)
    override fun edges(source: Vertex, target: Vertex): EdgeSet = graph.edges(target, source)

    override fun <T> createVertexProperty(
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> = graph.createVertexProperty(type, defaultValueFunction).reparent(this)

    override fun <T> createEdgeProperty(
        type: PropertyType<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> = graph.createEdgeProperty(type, defaultValueFunction).reparent(this)

    override fun <T> createVertexKeyProperty(type: PropertyType<T>): MutableVertexKeyProperty<T> =
        graph.createVertexKeyProperty(type).reparent(this)

    override fun <T> createEdgeKeyProperty(type: PropertyType<T>): MutableEdgeKeyProperty<T> =
        graph.createEdgeKeyProperty(type).reparent(this)
}

@Suppress("NOTHING_TO_INLINE")
private inline fun constructLongValue(highBits: Int, lowBits: Int): Long =
    highBits.toLong().shl(32).or(lowBits.toLong().and(0xFFFFFFFF))

internal abstract class AbstractMutableIdentityIndexedVertexSet(private val graph: MutableGraph) : MutableIdentityIndexedVertexSet, AbstractVertexSequencedSet() {
    override fun iterator(): MutableVertexIterator = object : MutableVertexIterator {
        private var index = 0
        private var previous = -1

        override fun hasNext(): Boolean = index < size
        override fun next(): Vertex {
            if (!hasNext()) throw NoSuchElementException()
            previous = index++
            return Vertex(previous)
        }

        override fun remove() {
            check(previous != -1)
            graph.removeVertex(Vertex(previous))
            index = previous
            previous = -1
        }
    }
}

internal abstract class AbstractMutableIdentityIndexedEdgeSet(private val graph: MutableGraph) : IdentityIndexedEdgeSet, MutableIndexedEdgeSet, AbstractEdgeSequencedSet() {
    override fun iterator(): MutableEdgeIterator = object : MutableEdgeIterator {
        private var index = 0
        private var previous = -1

        override fun hasNext(): Boolean = index < size
        override fun next(): Edge {
            if (index >= size) throw NoSuchElementException()
            previous = index++
            return get(previous)
        }

        override fun remove() {
            check (previous != -1)
            graph.removeEdge(get(previous))
            index = previous
            previous = -1
        }
    }
}
