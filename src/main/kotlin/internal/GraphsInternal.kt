package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.GraphCopy
import io.github.sooniln.fastgraph.GraphMapping
import io.github.sooniln.fastgraph.PropertyGraph
import io.github.sooniln.fastgraph.PropertyGraphCopy
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntIterator
import it.unimi.dsi.fastutil.longs.Long2LongMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongListIterator
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

internal fun <G : Graph> GraphCopy(graph: G, vertexMap: Int2IntMap?, edgeMap: Long2LongMap?): GraphCopy<G> {
    return object : GraphCopy<G>, GraphIsomorphism(vertexMap, edgeMap) {
        override val graph: G get() = graph
    }
}

internal fun <G : Graph, V, E> PropertyGraphCopy(
    graph: GraphCopy<G>,
    vertexProperty: VertexProperty<V>,
    edgeProperty: EdgeProperty<E>,
): PropertyGraphCopy<G, V, E> {
    return object : PropertyGraphCopy<G, V, E>, GraphCopy<G> by graph {
        override val vertexProperty: VertexProperty<V> get() = vertexProperty
        override val edgeProperty: EdgeProperty<E> get() = edgeProperty
    }
}

internal fun <G : Graph, V, E> PropertyGraphCopy(
    propertyGraph: PropertyGraph<G, V, E>,
    vertexMap: Int2IntMap?,
    edgeMap: Long2LongMap?,
): PropertyGraphCopy<G, V, E> {
    return object : PropertyGraphCopy<G, V, E>, PropertyGraph<G, V, E> by propertyGraph,
        GraphIsomorphism(vertexMap, edgeMap) {}
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
internal value class EdgeValueArrayList private constructor(private val arrayList: LongArrayList) :
    MutableList<EdgeValue> {
    constructor() : this(LongArrayList())

    inline fun ensureCapacity(minimumCapacity: Int) = arrayList.ensureCapacity(minimumCapacity)
    inline fun trimToSize() = arrayList.trim()

    override val size: Int inline get() = arrayList.size
    override inline fun isEmpty(): Boolean = arrayList.isEmpty
    override inline fun contains(element: EdgeValue): Boolean = arrayList.contains(element.longValue)
    override inline fun containsAll(elements: Collection<EdgeValue>): Boolean = throw UnsupportedOperationException()
    override inline fun get(index: Int): EdgeValue = EdgeValue(arrayList.getLong(index))
    override inline fun indexOf(element: EdgeValue): Int = arrayList.indexOf(element.longValue)
    override inline fun lastIndexOf(element: EdgeValue): Int = arrayList.lastIndexOf(element.longValue)

    override inline fun iterator(): EdgeValueListIterator = EdgeValueListIterator(arrayList.iterator())
    override inline fun listIterator(): EdgeValueListIterator = EdgeValueListIterator(arrayList.listIterator())
    override inline fun listIterator(index: Int): EdgeValueListIterator =
        EdgeValueListIterator(arrayList.listIterator(index))

    override inline fun subList(fromIndex: Int, toIndex: Int): MutableList<EdgeValue> =
        throw UnsupportedOperationException()

    override inline fun add(element: EdgeValue): Boolean = arrayList.add(element.longValue)
    override inline fun remove(element: EdgeValue): Boolean = arrayList.rem(element.longValue)
    override inline fun clear() = arrayList.clear()
    override inline fun set(index: Int, element: EdgeValue): EdgeValue =
        EdgeValue(arrayList.set(index, element.longValue))

    override inline fun add(index: Int, element: EdgeValue) = arrayList.add(index, element.longValue)
    override inline fun removeAt(index: Int): EdgeValue = EdgeValue(arrayList.removeLong(index))

    override inline fun addAll(elements: Collection<EdgeValue>): Boolean = throw UnsupportedOperationException()
    override inline fun addAll(index: Int, elements: Collection<EdgeValue>): Boolean =
        throw UnsupportedOperationException()

    override inline fun removeAll(elements: Collection<EdgeValue>): Boolean = throw UnsupportedOperationException()
    override inline fun retainAll(elements: Collection<EdgeValue>): Boolean = throw UnsupportedOperationException()

    override fun toString(): String = joinToString(", ", "[", "]") { it.toString() }
}

@Suppress("OVERRIDE_BY_INLINE", "NOTHING_TO_INLINE")
@JvmInline
internal value class EdgeValueListIterator(private val it: LongListIterator) : MutableListIterator<EdgeValue> {
    override inline fun hasNext(): Boolean = it.hasNext()
    override inline fun next(): EdgeValue = EdgeValue(it.nextLong())
    override inline fun hasPrevious(): Boolean = it.hasPrevious()
    override inline fun previous(): EdgeValue = EdgeValue(it.previousLong())
    override inline fun nextIndex(): Int = it.nextIndex()
    override inline fun previousIndex(): Int = it.previousIndex()

    override inline fun remove() = throw UnsupportedOperationException()
    override inline fun set(element: EdgeValue) = throw UnsupportedOperationException()
    override inline fun add(element: EdgeValue) = throw UnsupportedOperationException()
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

    override fun toString(): String = "EdgeAdjacency($vertex, $edgeId)"
}

internal interface EdgeAdjacencyIterator : Iterator<EdgeAdjacency> {
    override fun next(): EdgeAdjacency
}

internal interface EdgeAdjacencySet : Set<EdgeAdjacency> {
    override fun contains(element: EdgeAdjacency): Boolean
    fun contains(vertex: Vertex): Boolean
    fun vertices(): VertexSet
    override fun iterator(): EdgeAdjacencyIterator
    fun edgeIdIterator(): IntIterator
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
        if (other is VertexReferenceImpl) {
            return valid && other.valid && unstable == other.unstable
        }

        return false
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
        if (other is EdgeReferenceImpl) {
            return valid && other.valid && unstable == other.unstable
        }

        return false
    }

    override fun hashCode(): Int = unstable.hashCode()
}

internal class VertexReferenceHolder {
    private val refs = Int2ObjectOpenHashMap<VertexWeakReference>()
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
    private val refs = Int2ObjectOpenHashMap<EdgeWeakReference>()
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
    private val refs = Long2ObjectOpenHashMap<EdgeWeakReference>()
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

    fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) {
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
    fun swapAndRemove(removeEdge: Edge, swapEdge: Edge) = forEach { it.swapAndRemove(removeEdge, swapEdge) }

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
