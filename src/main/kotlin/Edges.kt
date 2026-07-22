/**
 * Utilities for [Edge].
 */
@file:JvmMultifileClass @file:JvmName("Edges")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastcollect.longs.LongSet
import io.github.sooniln.fastcollect.longs.emptyLongIterator
import io.github.sooniln.fastgraph.primitives.collections.GraphLongHashSet

private val EDGE_HEX_FORMAT = HexFormat {
    number {
        removeLeadingZeros = true
        prefix = "0x"
    }
}

/**
 * A unique opaque edge identifier. No meaning should be ascribed to the long value visible here, as it may be
 * interpreted differently by different graph implementations. Some graph implementations may give guarantees on their
 * edge identifiers which are stronger, and allow some meaning to be ascribed to the identifier value.
 *
 * Note that Edge by itself does not include any representation of what graph it belongs to. There are no safeguards
 * to prevent a client from accidentally using an edge from one graph with another unrelated graph. It is the client's
 * responsibility to ensure this does not occur. Some graph implementations may make a best effort to ensure this does
 * not occur, but this cannot be guaranteed or relied on.
 *
 * This class represents an *unstable* reference to an edge. An unstable reference means that the reference may be
 * invalidated if a mutation is made to the owning graph. Individual graph implementations should make explicit
 * guarantees on when an edge identifier is invalidated, but in the absence of stronger guarantees clients must assume
 * that any mutation of the graph topology (i.e. adding a vertex/edge, removing a vertex/edge) invalidates all
 * unstable references. [Graph] instances offer [Graph.createEdgeReference] to obtain a stable [EdgeReference] from an
 * unstable reference. Stable references are guaranteed to never be invalidated, but may be more expensive to maintain
 * than unstable references, and thus should be used sparingly.
 */
@JvmInline
value class Edge(val longValue: Long) {

    internal constructor(highBits: Int, lowBits: Int) : this(
        highBits.toLong().shl(32).or(lowBits.toLong().and(0xFFFFFFFF))
    )

    internal val highBits: Int
        inline get() = longValue.ushr(32).toInt()

    internal val lowBits: Int
        inline get() = longValue.toInt()

    /**
     * See [Graph.createEdgeReference].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmSynthetic
    @JvmName("#createReference")
    context(graph: Graph)
    fun createReference(): EdgeReference = graph.createEdgeReference(this)

    /**
     * See [Graph.edgeSource].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmSynthetic
    @get:JvmName("#source")
    context(graph: Graph)
    val source inline get() = graph.edgeSource(this)

    /**
     * See [Graph.edgeTarget].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmSynthetic
    @get:JvmName("#target")
    context(graph: Graph)
    val target inline get() = graph.edgeTarget(this)

    /**
     * See [Graph.edgeOpposite].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmSynthetic
    @JvmName("#opposite")
    context(graph: Graph)
    fun opposite(other: Vertex) = graph.edgeOpposite(this, other)

    /**
     * See [EdgeProperty.get] and [EdgeProperty.set].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmSynthetic
    @set:JvmSynthetic
    @get:JvmName("#getProperty")
    @set:JvmName("#setProperty")
    context(property: EdgeProperty<T>)
    var <T> property: T
        inline get() = property[this]
        inline set(value) {
            property[this] = value
        }

    /**
     * Returns the index of this edge in [IndexedEdgeGraph.edges].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmSynthetic
    @get:JvmName("#index")
    context(graph: IndexedEdgeGraph)
    val index: Int inline get() = graph.edges.indexOf(this)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmSynthetic
    @JvmName("#component1")
    context(graph: Graph)
    operator fun component1(): Vertex = source

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmSynthetic
    @JvmName("#component2")
    context(graph: Graph)
    operator fun component2(): Vertex = target

    override fun toString(): String =
        "Edge(${highBits.toHexString(EDGE_HEX_FORMAT)}, ${lowBits.toHexString(EDGE_HEX_FORMAT)})"
}

/**
 * A *stable* reference to an edge. This reference is guaranteed to never be invalidated when mutations are made to the
 * graph topology. A stable reference can be obtained through [Graph.createEdgeReference]. [EdgeReference] is generally
 * a less efficient representation than [Edge], in terms of both memory and CPU. Prefer [Edge] unless reference
 * stability across mutations is a requirement.
 */
interface EdgeReference {

    /**
     * An unstable [Edge] reference corresponding to this stable reference.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("unstable")
    val unstable: Edge
}

/**
 * Accesses the property value of an edge. Equivalent to accessing the property value through the [EdgeProperty] itself.
 */
context(property: EdgeProperty<T>)
var <T> EdgeReference.property: T
    @JvmSynthetic @JvmName("#EdgeReference_property_get") inline get() = property[unstable]
    @JvmSynthetic @JvmName("#EdgeReference_property_set") inline set(value) {
        property[unstable] = value
    }

/**
 * See [Graph.edgeSource].
 */
context(graph: Graph)
val EdgeReference.source
    @JvmSynthetic @JvmName("#EdgeReference_source") inline get() = graph.edgeSource(
        unstable
    )

/**
 * See [Graph.edgeTarget].
 */
context(graph: Graph)
val EdgeReference.target
    @JvmSynthetic @JvmName("#EdgeReference_target") inline get() = graph.edgeTarget(
        unstable
    )

/**
 * See [Graph.edgeOpposite].
 */
@JvmSynthetic
@JvmName("#EdgeReference_opposite")
context(graph: Graph)
fun EdgeReference.opposite(other: Vertex) = graph.edgeOpposite(unstable, other)

/**
 * Returns the index of this edge in [IndexedEdgeGraph.edges].
 */
context(graph: IndexedEdgeGraph)
val EdgeReference.index: Int
    @JvmSynthetic @JvmName("#EdgeReference_index")
    inline get() = graph.edges.indexOf(unstable)

@JvmSynthetic
@JvmName("#EdgeReference_component1")
context(graph: Graph)
operator fun EdgeReference.component1(): Vertex = source

@JvmSynthetic
@JvmName("#EdgeReference_component2")
context(graph: Graph)
operator fun EdgeReference.component2(): Vertex = target

/**
 * An iterator over edges. Note that this interface is distinct from [Iterator<Edge>][Iterator] in order to avoid Edge
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
interface EdgeIterator : Iterator<Edge> {
    override fun next(): Edge

    @Deprecated("For JVM usage only", level = DeprecationLevel.ERROR)
    fun nextEdge(): Long = next().longValue
}

/**
 * An iterator over edges that allows for edge removal.
 */
interface MutableEdgeIterator : EdgeIterator, MutableIterator<Edge>

/**
 * An iterable of edges. Note that this interface is distinct from [Iterable<Edge>][Iterable] in order to avoid Edge
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
interface EdgeIterable : Iterable<Edge> {
    override fun iterator(): EdgeIterator
}

/**
 * A read-only collection of edges. Note that this interface is distinct from [Collection<Edge>][Collection] in order to
 * avoid Edge boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for
 * those reasons.
 */
interface EdgeCollection : Collection<Edge>, EdgeIterable {

    override fun isEmpty(): Boolean {
        return size == 0
    }

    fun fastForEach(action: (Edge) -> Unit) {
        val it = iterator()
        while (it.hasNext()) {
            action(it.next())
        }
    }

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean {
        for (e in this) {
            if (e == element) return true
        }
        return false
    }

    fun containsAll(elements: EdgeCollection): Boolean {
        for (e in elements) {
            if (!contains(e)) return false
        }
        return true
    }

    override fun containsAll(elements: Collection<Edge>): Boolean {
        if (elements is EdgeCollection) {
            return containsAll(elements)
        }

        for (e in this) {
            if (!contains(e)) return false
        }
        return true
    }

    fun toLongArray(): LongArray {
        val array = LongArray(size)
        var index = 0
        for (element in this) {
            array[index++] = element.longValue
        }
        return array
    }
}

/**
 * A collection of edges with an iterator that allows for removal.
 */
interface MutableEdgeCollection : EdgeCollection {
    override fun iterator(): MutableEdgeIterator
}

/**
 * A read-only set of edges. Note that this interface is distinct from [Set<Edge>][Set] in order to avoid Edge
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
interface EdgeSet : EdgeCollection, Set<Edge> {
    override fun isEmpty(): Boolean = super.isEmpty()

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = super.contains(element)

    override fun containsAll(elements: Collection<Edge>): Boolean = super.containsAll(elements)
}

/**
 * A set of edges with an iterator that allows for removal.
 */
interface MutableEdgeSet : EdgeSet, MutableEdgeCollection

/**
 * A read-only set of edges where each edge is associated with an index from `0` to `size() - 1`. This makes edges
 * accessible by index, and an index can be retrieved for each edge (via `indexOf(edge)`). The `indexOf()` call is
 * guaranteed to take amortized constant time or better. This collection MUST iterate edges in index order.
 */
interface IndexedEdgeSet : EdgeSet {

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean

    override fun containsAll(elements: Collection<Edge>): Boolean = super.containsAll(elements)

    fun get(index: Int): Edge

    @Deprecated("For JVM usage only", level = DeprecationLevel.ERROR)
    fun getEdge(index: Int): Long = get(index).longValue

    /**
     * Returns the index of the given edge in this collection, or -1 if the edge is not in this set.
     */
    fun indexOf(element: Edge): Int

    override fun iterator(): EdgeIterator = EdgeIteratorImpl(this)

    private class EdgeIteratorImpl(private val edges: IndexedEdgeSet) : EdgeIterator {
        private var index = 0

        override fun hasNext(): Boolean = index < edges.size
        override fun next(): Edge = edges.get(index++)
    }

    override fun fastForEach(action: (Edge) -> Unit) {
        var index = 0
        while (index < size) {
            action(get(index))
            index++
        }
    }
}

/**
 * A set of edges where each edge is associated with an index from `0` to `size() - 1`. This makes edges accessible by
 * index, and an index can be retrieved for each edge (via `indexOf(edge)`). The `indexOf()` call is guaranteed to take
 * amortized constant time or better. This collection MUST iterate edges in index order.
 */
interface MutableIndexedEdgeSet : IndexedEdgeSet, MutableEdgeSet {
    override fun iterator(): MutableEdgeIterator
}

/**
 * Returns a new read-only set of the given edges.
 */
// KT-33565: suppression and generics can be removed once fixed
@Suppress("FINAL_UPPER_BOUND")
fun <T : Edge> edgeSetOf(vararg edges: T): EdgeSet {
    return if (edges.isEmpty()) {
        emptyEdgeSet()
    } else if (edges.size == 1) {
        SingletonEdgeSet(edges[0])
    } else {
        val set = GraphLongHashSet(edges.size)
        for (edge in edges) {
            set.add(edge.longValue)
        }
        EdgeSetWrapper(set)
    }
}

fun emptyEdgeIterator() = EmptyEdgeIterator

private val EmptyEdgeIterator = emptyLongIterator().asEdgeIterator()

/**
 * Returns a read-only empty set/list of edges.
 */
fun emptyEdgeSet(): IndexedEdgeSet = EmptyEdgeSet

private object EmptyEdgeSet : IndexedEdgeSet {
    override val size: Int get() = 0

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = false

    override fun containsAll(elements: Collection<Edge>): Boolean = elements.isEmpty()
    override fun iterator(): EdgeIterator = emptyEdgeIterator()
    override fun fastForEach(action: (Edge) -> Unit) {}

    override fun get(index: Int): Edge = throw IndexOutOfBoundsException()
    override fun indexOf(element: Edge): Int = -1
}

/**
 * Provides a skeletal implementation of the read-only [EdgeCollection] interface.
 */
abstract class AbstractEdgeCollection : EdgeCollection, AbstractCollection<Edge>() {
    override fun isEmpty(): Boolean = super<EdgeCollection>.isEmpty()

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = super<EdgeCollection>.contains(element)
    override fun containsAll(elements: Collection<Edge>): Boolean = super<EdgeCollection>.containsAll(elements)
}

/**
 * Provides a skeletal implementation of the read-only [EdgeSet] interface.
 */
abstract class AbstractEdgeSet : EdgeSet, AbstractSet<Edge>() {
    override fun isEmpty(): Boolean = super<EdgeSet>.isEmpty()

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = super<EdgeSet>.contains(element)
    override fun containsAll(elements: Collection<Edge>): Boolean = super<EdgeSet>.containsAll(elements)
}

/**
 * Provides a skeletal implementation of the read-only [IndexedEdgeSet] interface.
 */
abstract class AbstractIndexedEdgeSet : IndexedEdgeSet, AbstractSet<Edge>(), EdgeSet {
    override fun isEmpty(): Boolean = super<IndexedEdgeSet>.isEmpty()

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = super<EdgeSet>.contains(element)
    override fun containsAll(elements: Collection<Edge>): Boolean = super<IndexedEdgeSet>.containsAll(elements)

    override fun iterator(): EdgeIterator = super.iterator()

    override fun fastForEach(action: (Edge) -> Unit) {
        var index = 0
        while (index < size) {
            action(get(index++))
        }
    }

    protected abstract inner class AbstractMutableEdgeIterator : MutableEdgeIterator {
        private var index = 0
        private var previous = -1

        override fun hasNext(): Boolean = index < size
        override fun next(): Edge = get(index++)
        override fun remove() {
            if (previous == -1) throw IllegalStateException()
            val index = previous
            previous = -1
            removeAt(index)
        }

        protected abstract fun removeAt(index: Int)
    }
}

private class SingletonEdgeSet(private val edge: Edge) : AbstractIndexedEdgeSet() {
    override val size: Int
        get() = 1

    override fun get(index: Int): Edge {
        if (index == 0) return edge else throw IndexOutOfBoundsException()
    }

    override fun indexOf(element: Edge): Int {
        return if (element == edge) 0 else -1
    }

    override fun fastForEach(action: (Edge) -> Unit) = action(edge)
}

internal fun LongIterator.asEdgeIterator(): EdgeIterator = EdgeIteratorWrapper(this)

private class EdgeIteratorWrapper(private val it: LongIterator) : EdgeIterator {
    override fun hasNext(): Boolean = it.hasNext()
    override fun next(): Edge {
        return Edge(it.nextLong())
    }
}

internal fun LongSet.asEdgeSet(): EdgeSet = EdgeSetWrapper(this)

private class EdgeSetWrapper(private val edges: LongSet) : AbstractEdgeSet() {
    override val size: Int get() = edges.size

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = edges.contains(element.longValue)
    override fun iterator(): EdgeIterator = EdgeIteratorWrapper(edges.iterator())
    override fun fastForEach(action: (Edge) -> Unit) = edges.fastForEach { edge -> action(Edge(edge)) }

    override fun toLongArray(): LongArray = edges.toLongArray()
}
