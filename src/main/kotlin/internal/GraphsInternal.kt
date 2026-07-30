package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.ints.Int2IntMap
import io.github.sooniln.fastcollect.longs.Long2LongMap
import io.github.sooniln.fastcollect.longs.LongArrayList
import io.github.sooniln.fastcollect.longs.MutableLongIterator
import io.github.sooniln.fastcollect.longs.lastIndex
import io.github.sooniln.fastgraph.AbstractIndexedEdgeSet
import io.github.sooniln.fastgraph.AbstractIndexedVertexSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeConsumer
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.GraphCopy
import io.github.sooniln.fastgraph.GraphMapping
import io.github.sooniln.fastgraph.IndexedEdgeSet
import io.github.sooniln.fastgraph.IndexedVertexSet
import io.github.sooniln.fastgraph.MutableEdgeIterator
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.MutableIndexedEdgeSet
import io.github.sooniln.fastgraph.MutableIndexedVertexSet
import io.github.sooniln.fastgraph.MutableVertexIterator
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.PropertyGraph
import io.github.sooniln.fastgraph.PropertyGraphCopy
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexConsumer
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexSet
import kotlin.math.max
import kotlin.math.min

internal fun throwIllegalVertex(vertex: Vertex, cause: Throwable? = null): Nothing =
    throw IllegalArgumentException("$vertex not found in graph", cause)

context(graph: Graph)
internal fun throwIllegalEdge(edge: Edge, cause: Throwable? = null): Nothing = throw IllegalArgumentException(
    "$edge (${graph.edgeSource(edge)} -> ${graph.edgeTarget(edge)}) not found in graph",
    cause
)

internal fun <G : Graph> GraphCopy(
    originalGraph: Graph,
    graph: G,
    vertexMap: Int2IntMap?,
    edgeMap: Long2LongMap?
): GraphCopy<G> {
    return object : GraphCopy<G>, GraphIsomorphism(vertexMap, edgeMap) {
        override val originalGraph: Graph get() = originalGraph
        override val graph: G get() = graph
    }
}

internal fun <G : Graph, V, E> PropertyGraphCopy(
    originalPropertyGraph: PropertyGraph<*, V, E>,
    graph: GraphCopy<G>,
    vertexProperty: MutableVertexProperty<V>,
    edgeProperty: MutableEdgeProperty<E>,
): PropertyGraphCopy<G, V, E> {
    return object : PropertyGraphCopy<G, V, E>, GraphCopy<G> by graph {
        override val originalPropertyGraph: PropertyGraph<*, V, E> get() = originalPropertyGraph
        override val vertexProperty: MutableVertexProperty<V> get() = vertexProperty
        override val edgeProperty: MutableEdgeProperty<E> get() = edgeProperty
    }
}

internal fun <G : Graph, V, E> PropertyGraphCopy(
    originalPropertyGraph: PropertyGraph<*, V, E>,
    propertyGraph: PropertyGraph<G, V, E>,
    vertexMap: Int2IntMap?,
    edgeMap: Long2LongMap?,
): PropertyGraphCopy<G, V, E> {
    return object : PropertyGraphCopy<G, V, E>, PropertyGraph<G, V, E> by propertyGraph,
        GraphIsomorphism(vertexMap, edgeMap) {
        override val originalPropertyGraph: PropertyGraph<*, V, E> get() = originalPropertyGraph
        override val originalGraph: Graph get() = originalPropertyGraph.graph
    }
}

private open class GraphIsomorphism(private val vertexMap: Int2IntMap?, private val edgeMap: Long2LongMap?) :
    GraphMapping {
    final override fun getCorrespondingVertex(vertex: Vertex): Vertex {
        return if (vertexMap == null) {
            vertex
        } else {
            require(vertexMap.containsKey(vertex.intValue))
            Vertex(vertexMap[vertex.intValue])
        }
    }

    final override fun getCorrespondingEdge(edge: Edge): Edge {
        return if (edgeMap == null) {
            edge
        } else {
            require(edgeMap.containsKey(edge.longValue))
            Edge(edgeMap[edge.longValue])
        }
    }
}

@JvmInline
internal value class EdgeValue(val longValue: Long) {

    constructor(directed: Boolean, source: Vertex, target: Vertex) : this(
        if (!directed) {
            constructLongValue(
                highBits = min(source.intValue, target.intValue),
                lowBits = max(source.intValue, target.intValue)
            )
        } else {
            constructLongValue(highBits = source.intValue, lowBits = target.intValue)
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
    inline fun contains(element: EdgeValue): Boolean = arrayList.contains(element.longValue)
    inline operator fun get(index: Int): EdgeValue = EdgeValue(arrayList[index])
    inline operator fun set(index: Int, element: EdgeValue): EdgeValue =
        EdgeValue(arrayList.replace(index, element.longValue))
    inline fun add(element: EdgeValue): Boolean = arrayList.add(element.longValue)
    inline fun removeAt(index: Int): EdgeValue = EdgeValue(arrayList.removeAt(index))
    inline fun clear() = arrayList.clear()
    inline fun iterator(): EdgeValueIterator = EdgeValueIterator(arrayList.iterator())
    override fun toString(): String = iterator().asSequence().joinToString(", ", "[", "]")
}

@Suppress("OVERRIDE_BY_INLINE", "NOTHING_TO_INLINE")
@JvmInline
internal value class EdgeValueIterator(private val it: MutableLongIterator) : MutableIterator<EdgeValue> {
    override inline fun hasNext(): Boolean = it.hasNext()
    override inline fun next(): EdgeValue = EdgeValue(it.nextLong())
    override inline fun remove() = it.remove()
}

@Suppress("OVERRIDE_BY_INLINE", "NOTHING_TO_INLINE")
@JvmInline
internal value class EdgeValueArray(private val array: LongArray) : List<EdgeValue> {
    override val size: Int inline get() = array.size
    override inline fun isEmpty(): Boolean = array.isEmpty()
    override inline fun contains(element: EdgeValue): Boolean = throw UnsupportedOperationException()
    override inline fun containsAll(elements: Collection<EdgeValue>): Boolean = throw UnsupportedOperationException()
    override inline fun get(index: Int): EdgeValue = EdgeValue(array[index])
    override inline fun indexOf(element: EdgeValue): Int = throw UnsupportedOperationException()
    override inline fun lastIndexOf(element: EdgeValue): Int = throw UnsupportedOperationException()

    override inline fun iterator(): Iterator<EdgeValue> = throw UnsupportedOperationException()
    override inline fun listIterator(): ListIterator<EdgeValue> = throw UnsupportedOperationException()
    override inline fun listIterator(index: Int): ListIterator<EdgeValue> = throw UnsupportedOperationException()
    override inline fun subList(fromIndex: Int, toIndex: Int): MutableList<EdgeValue> =
        throw UnsupportedOperationException()

    override fun toString(): String = joinToString(", ", "[", "]") { it.toString() }
}

@JvmInline
internal value class EdgeAdjacency(val longValue: Long) {

    constructor(vertex: Vertex, edge: Edge) : this(
        constructLongValue(highBits = vertex.intValue, lowBits = edge.lowBits)
    )

    constructor(vertex: Vertex, edgeId: Int) : this(
        constructLongValue(highBits = vertex.intValue, lowBits = edgeId)
    )

    constructor(vertexAdjacency: EdgeAdjacency, edgeId: Int) : this(
        vertexAdjacency.longValue.or(edgeId.toLong().and(0xFFFFFFFF))
    )

    val vertex: Vertex
        inline get() = Vertex(longValue.ushr(32).toInt())

    val edgeId: Int
        inline get() = longValue.toInt()

    val edge: Edge
        inline get() = Edge(edgeId.toLong())

    override fun toString(): String = "EdgeAdjacency($vertex, $edgeId)"
}

internal interface EdgeAdjacencyIterator : Iterator<EdgeAdjacency> {
    override fun next(): EdgeAdjacency
}

internal fun EdgeAdjacencyIterator.toEdgeIterator() : EdgeIterator = object : EdgeIterator {
    override fun hasNext(): Boolean = this@toEdgeIterator.hasNext()
    override fun next(): Edge = this@toEdgeIterator.next().edge
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

internal abstract class AbstractVertexIndexedVertexSet : IndexedVertexSet, AbstractIndexedVertexSet() {

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean {
        require(element.intValue in indices)
        return true
    }

    override fun get(index: Int): Vertex {
        if (index !in indices) throw IndexOutOfBoundsException()
        return Vertex(index)
    }

    override fun indexOf(element: Vertex): Int {
        require(element.intValue in indices)
        return element.intValue
    }

    override fun iterator(): VertexIterator = object : VertexIterator {
        private var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): Vertex = Vertex(index++)
    }

    override fun foreach(action: VertexConsumer) {
        var index = 0
        while (index < size) {
            action.accept(Vertex(index++))
        }
    }
}

internal abstract class AbstractMutableVertexIndexedVertexSet(private val graph: MutableGraph) :
    AbstractVertexIndexedVertexSet(), MutableIndexedVertexSet {

    override fun iterator(): MutableVertexIterator = object : MutableVertexIterator {
        private var index = 0
        private var previous = -1

        override fun hasNext(): Boolean = index < size
        override fun next(): Vertex {
            previous = index++
            return Vertex(previous)
        }
        override fun remove() {
            if (previous == -1) throw IllegalStateException()
            graph.removeVertex(Vertex(previous))
            index = previous
            previous = -1
        }
    }
}

internal abstract class AbstractEdgeIndexedEdgeSet : IndexedEdgeSet, AbstractIndexedEdgeSet() {

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean {
        require(element.lowBits in indices)
        return true
    }

    override fun get(index: Int): Edge {
        if (index !in indices) throw IndexOutOfBoundsException()
        return Edge(index.toLong())
    }

    override fun indexOf(element: Edge): Int {
        require(element.lowBits in indices)
        return element.lowBits
    }

    override fun iterator(): EdgeIterator = object : EdgeIterator {
        private var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): Edge = Edge(index++.toLong())
    }

    override fun foreach(action: EdgeConsumer) {
        var index = 0
        while (index < size) {
            action.accept(Edge(index++.toLong()))
        }
    }
}

internal abstract class AbstractMutableEdgeIndexedEdgeSet(private val graph: MutableGraph) : MutableIndexedEdgeSet,
    AbstractEdgeIndexedEdgeSet() {

    override fun iterator(): MutableEdgeIterator = object : MutableEdgeIterator {
        private var index = 0
        private var previous = -1

        override fun hasNext(): Boolean = index < graph.edges.size
        override fun next(): Edge {
            previous = index++
            return Edge(previous.toLong())
        }
        override fun remove() {
            if (previous == -1) throw IllegalStateException()
            graph.removeEdge(Edge(previous.toLong()))
            index = previous
            previous = -1
        }
    }
}
