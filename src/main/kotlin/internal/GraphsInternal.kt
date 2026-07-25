package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.ints.Int2IntMap
import io.github.sooniln.fastcollect.longs.Long2AnyHashMap
import io.github.sooniln.fastcollect.longs.Long2LongMap
import io.github.sooniln.fastcollect.longs.LongArrayList
import io.github.sooniln.fastcollect.longs.LongListIterator
import io.github.sooniln.fastcollect.longs.MutableLongIterator
import io.github.sooniln.fastcollect.longs.lastIndex
import io.github.sooniln.fastgraph.AbstractIndexedEdgeSet
import io.github.sooniln.fastgraph.AbstractIndexedVertexSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeConsumer
import io.github.sooniln.fastgraph.EdgeIndexedEdgeGraph
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.GraphCopy
import io.github.sooniln.fastgraph.GraphMapping
import io.github.sooniln.fastgraph.MutableEdgeIterator
import io.github.sooniln.fastgraph.MutableIndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableIndexedEdgeSet
import io.github.sooniln.fastgraph.MutableIndexedVertexGraph
import io.github.sooniln.fastgraph.MutableIndexedVertexSet
import io.github.sooniln.fastgraph.MutableVertexIterator
import io.github.sooniln.fastgraph.PropertyGraph
import io.github.sooniln.fastgraph.PropertyGraphCopy
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIndexedVertexGraph
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.primitives.collections.GraphInt2AnyHashMap
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

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
    vertexProperty: VertexProperty<V>,
    edgeProperty: EdgeProperty<E>,
): PropertyGraphCopy<G, V, E> {
    return object : PropertyGraphCopy<G, V, E>, GraphCopy<G> by graph {
        override val originalPropertyGraph: PropertyGraph<*, V, E> get() = originalPropertyGraph
        override val vertexProperty: VertexProperty<V> get() = vertexProperty
        override val edgeProperty: EdgeProperty<E> get() = edgeProperty
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

internal class VertexReferenceImpl(vertex: Vertex) : VertexReference {
    private var valid: Boolean = true

    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("unstable")
    override var unstable: Vertex = vertex
        get() {
            require(valid) { "the vertex referenced has been removed and is no longer valid" }
            return field
        }
        set(value) {
            check(valid)
            field = value
        }

    fun invalidate() {
        valid = false
    }

    override fun equals(other: Any?): Boolean {
        return other is VertexReferenceImpl && valid && other.valid && unstable == other.unstable
    }

    override fun hashCode(): Int = unstable.hashCode()
}

internal class EdgeReferenceImpl(edge: Edge) : EdgeReference {
    private var valid: Boolean = true

    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("unstable")
    override var unstable: Edge = edge
        get() {
            require(valid) { "the edge referenced has been removed and is no longer valid" }
            return field
        }
        set(value) {
            check(valid)
            field = value
        }

    fun invalidate() {
        valid = false
    }

    override fun equals(other: Any?): Boolean {
        return other is EdgeReferenceImpl && valid && other.valid && unstable == other.unstable
    }

    override fun hashCode(): Int = unstable.hashCode()
}

internal class VertexReferenceHolder {
    private val refs = GraphInt2AnyHashMap<VertexWeakReference>()
    private val refQueue = ReferenceQueue<VertexReferenceImpl>()

    private fun cleanup() {
        var removable = refQueue.poll() as VertexWeakReference?
        while (removable != null) {
            refs.remove(removable.intValue)
            removable = refQueue.poll() as VertexWeakReference?
        }
    }

    fun ref(vertex: Vertex): VertexReference {
        var ref = refs.get(vertex.intValue)?.get()
        if (ref == null) {
            ref = VertexReferenceImpl(vertex)
            refs.put(vertex.intValue, VertexWeakReference(ref, refQueue))
        }

        cleanup()
        return ref
    }

    fun swapAndRemove(removeVertex: Vertex, swapVertex: Vertex) {
        cleanup()

        val weakRef = refs.remove(removeVertex.intValue)
        if (weakRef != null) {
            val ref = weakRef.get()
            if (ref != null) {
                if (removeVertex != swapVertex) {
                    ref.unstable = swapVertex
                    weakRef.intValue = swapVertex.intValue
                    refs.put(swapVertex.intValue, weakRef)?.get()?.invalidate()
                } else {
                    ref.invalidate()
                }
            }
        }
    }

    private class VertexWeakReference(ref: VertexReferenceImpl, queue: ReferenceQueue<VertexReferenceImpl>) :
        WeakReference<VertexReferenceImpl>(ref, queue) {
        var intValue: Int = ref.unstable.intValue
    }
}

internal class IntEdgeReferenceHolder {
    private val refs = GraphInt2AnyHashMap<EdgeWeakReference>()
    private val refQueue = ReferenceQueue<EdgeReferenceImpl>()

    private fun cleanup() {
        var removable = refQueue.poll() as EdgeWeakReference?
        while (removable != null) {
            refs.remove(removable.intValue)
            removable = refQueue.poll() as EdgeWeakReference?
        }
    }

    fun ref(edge: Edge): EdgeReference {
        val edgeId = edge.lowBits
        var ref = refs.get(edgeId)?.get()
        if (ref == null) {
            ref = EdgeReferenceImpl(edge)
            refs.put(edgeId, EdgeWeakReference(ref, refQueue))
        }

        cleanup()
        return ref
    }

    fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) {
        cleanup()

        val removeEdgeIntValue = removeEdge.lowBits
        val swapEdgeIntValue = swapEdge.lowBits

        val weakRef = refs.remove(removeEdgeIntValue)
        if (weakRef != null) {
            val ref = weakRef.get()
            if (ref != null) {
                if (removeEdgeIntValue != swapEdgeIntValue) {
                    ref.unstable = swapEdge
                    weakRef.intValue = swapEdgeIntValue
                    refs.put(swapEdgeIntValue, weakRef)?.get()?.invalidate()
                } else {
                    ref.invalidate()
                }
            }
        }
    }

    private class EdgeWeakReference(ref: EdgeReferenceImpl, queue: ReferenceQueue<EdgeReferenceImpl>) :
        WeakReference<EdgeReferenceImpl>(ref, queue) {
        var intValue: Int = ref.unstable.lowBits
    }
}

internal class LongEdgeReferenceHolder {
    private val refs = Long2AnyHashMap<EdgeWeakReference>()
    private val refQueue = ReferenceQueue<EdgeReferenceImpl>()

    private fun cleanup() {
        var removable = refQueue.poll() as EdgeWeakReference?
        while (removable != null) {
            refs.remove(removable.longValue)
            removable = refQueue.poll() as EdgeWeakReference?
        }
    }

    fun ref(edge: Edge): EdgeReference {
        var ref = refs.get(edge.longValue)?.get()
        if (ref == null) {
            ref = EdgeReferenceImpl(edge)
            refs.put(edge.longValue, EdgeWeakReference(ref, refQueue))
        } else {
            check(ref.unstable == edge)
        }

        cleanup()
        return ref
    }

    fun swapAndRemove(removeEdge: Edge, swapEdge: Edge = removeEdge) {
        cleanup()

        val weakRef = refs.remove(removeEdge.longValue)
        if (weakRef != null) {
            val ref = weakRef.get()
            if (ref != null) {
                if (removeEdge != swapEdge) {
                    ref.unstable = swapEdge
                    weakRef.longValue = swapEdge.longValue
                    refs.put(swapEdge.longValue, weakRef)?.get()?.invalidate()
                } else {
                    ref.invalidate()
                }
            }
        }
    }

    private class EdgeWeakReference(ref: EdgeReferenceImpl, queue: ReferenceQueue<EdgeReferenceImpl>) :
        WeakReference<EdgeReferenceImpl>(ref, queue) {
        var longValue: Long = ref.unstable.longValue
    }
}

internal class VertexPropertiesHolder {
    private val properties = ArrayList<WeakReference<MutableVertexProperty<*>>>()

    fun addProperty(property: MutableVertexProperty<*>) {
        val ref = WeakReference(property)
        for (i in properties.indices) {
            val property = properties[i].get()
            if (property == null) {
                properties[i] = ref
                return
            }
        }

        properties.add(ref)
    }

    /**
     * Set `swapVertex` property to `removeVertex` property and remove `removeVertex` property. Vertices may be the
     * same, in which case they can simply be removed.
     */
    fun swapAndRemove(removeVertex: Vertex, swapVertex: Vertex) = forEach { it.swapAndRemove(removeVertex, swapVertex) }

    fun ensureCapacity(capacity: Int) = forEach { it.ensureCapacity(capacity) }

    private fun forEach(propertyAction: (MutableVertexProperty<*>) -> Unit) {
        var i = 0
        while (i < properties.size) {
            val property = properties[i].get()
            if (property == null) {
                properties[i] = properties[properties.lastIndex]
                properties.removeAt(properties.lastIndex)
            } else {
                propertyAction(property)
                ++i
            }
        }
    }
}

internal class EdgePropertiesHolder {
    private val properties = ArrayList<WeakReference<MutableEdgeProperty<*>>>()

    fun addProperty(property: MutableEdgeProperty<*>) {
        val ref = WeakReference(property)
        for (i in properties.indices) {
            val property = properties[i].get()
            if (property == null) {
                properties[i] = ref
                return
            }
        }

        properties.add(ref)
    }

    /**
     * Set `swapEdge` property to `removeEdge` property and remove `removeEdge` property. Edges may be the same, in
     * which case they can simply be removed.
     */
    fun swapAndRemove(removeEdge: Edge, swapEdge: Edge = removeEdge) = forEach { it.swapAndRemove(removeEdge, swapEdge) }

    fun ensureCapacity(capacity: Int) = forEach { it.ensureCapacity(capacity) }

    private inline fun forEach(propertyAction: (MutableEdgeProperty<*>) -> Unit) {
        var i = 0
        while (i < properties.size) {
            val property = properties[i].get()
            if (property == null) {
                properties[i] = properties[properties.lastIndex]
                properties.removeAt(i)
            } else {
                propertyAction.invoke(property)
                ++i
            }
        }
    }
}

internal class MutableVertexIndexedVertexSet<G>(private val graph: G) : MutableIndexedVertexSet, AbstractIndexedVertexSet() where G : VertexIndexedVertexGraph, G: MutableIndexedVertexGraph {

    override val size: Int
        get() = graph.vertexCount

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
        val index = element.intValue
        require(index in indices)
        return index
    }

    override fun iterator(): MutableVertexIterator = object : MutableVertexIterator {
        private var index = 0
        private var previous = -1

        override fun hasNext(): Boolean = index < graph.edges.size
        override fun next(): Vertex = Vertex(index++)
        override fun remove() {
            if (previous == -1) throw IllegalStateException()
            graph.removeVertex(Vertex(previous))
            index = previous
            previous = -1
        }
    }
}

internal class MutableEdgeIndexedEdgeSet<G>(private val graph: G) : MutableIndexedEdgeSet, AbstractIndexedEdgeSet() where G : EdgeIndexedEdgeGraph, G: MutableIndexedEdgeGraph  {

    override val size: Int
        get() = graph.edgeCount

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
        val index = element.lowBits
        require(index in indices)
        return index
    }

    override fun iterator(): MutableEdgeIterator = object : MutableEdgeIterator {
        private var index = 0
        private var previous = -1

        override fun hasNext(): Boolean = index < graph.edges.size
        override fun next(): Edge = Edge(index++.toLong())
        override fun remove() {
            if (previous == -1) throw IllegalStateException()
            graph.removeEdge(Edge(previous.toLong()))
            index = previous
            previous = -1
        }
    }
}
