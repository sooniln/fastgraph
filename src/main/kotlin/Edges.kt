/**
 * Utilities for [Edge].
 */
@file:JvmMultifileClass @file:JvmName("Edges")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastcollect.longs.LongHashSet
import io.github.sooniln.fastcollect.longs.LongSet
import io.github.sooniln.fastcollect.longs.emptyLongIterator
import io.github.sooniln.fastcollect.longs.longIteratorOf

private val EDGE_HEX_FORMAT = HexFormat {
    number {
        removeLeadingZeros = true
        prefix = "0x"
    }
}

/**
 * A unique opaque edge identifier. No meaning should be ascribed to the id value visible here, as it may be
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
public value class Edge(public val id: Long) {

    internal constructor(highBits: Int, lowBits: Int) : this(
        highBits.toLong().shl(32).or(lowBits.toLong().and(0xFFFFFFFF))
    )

    internal val highBits: Int
        inline get() = id.ushr(32).toInt()

    internal val lowBits: Int
        inline get() = id.toInt()

    /**
     * See [Graph.createEdgeReference].
     */
    @JvmSynthetic
    context(graph: Graph)
    public fun reference(): EdgeReference = graph.createEdgeReference(this)

    /**
     * See [Graph.edgeSource].
     */
    @get:JvmSynthetic
    context(graph: Graph)
    public val source: Vertex inline get() = graph.edgeSource(this)

    /**
     * See [Graph.edgeTarget].
     */
    @get:JvmSynthetic
    context(graph: Graph)
    public val target: Vertex inline get() = graph.edgeTarget(this)

    /**
     * See [Graph.edgeOpposite].
     */
    @JvmSynthetic
    context(graph: Graph)
    public fun opposite(other: Vertex): Vertex = graph.edgeOpposite(this, other)

    @get:JvmSynthetic
    context(graph: ValueGraph<*, E>)
    public val <E> value: E inline get() = graph.edgeProperty[this]

    @JvmSynthetic
    context(graph: Graph)
    public operator fun component1(): Vertex = source

    @JvmSynthetic
    context(graph: Graph)
    public operator fun component2(): Vertex = target

    override fun toString(): String =
        "Edge(${highBits.toHexString(EDGE_HEX_FORMAT)}, ${lowBits.toHexString(EDGE_HEX_FORMAT)})"
}

/**
 * An iterator over edges. Note that this interface is distinct from [Iterator<Edge>][Iterator] in order to avoid Edge
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
public interface EdgeIterator : Iterator<Edge> {
    @JvmSynthetic
    override fun next(): Edge

    @Deprecated("For JVM usage only", level = DeprecationLevel.HIDDEN)
    public fun nextEdge(): Long = next().id
}

/**
 * An iterator over edges that allows for edge removal.
 */
public interface MutableEdgeIterator : EdgeIterator, MutableIterator<Edge>

/**
 * An iterable of edges. Note that this interface is distinct from [Iterable<Edge>][Iterable] in order to avoid Edge
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
public interface EdgeIterable : Iterable<Edge> {
    override fun iterator(): EdgeIterator
}

/**
 * A functional interface for consuming edges.
 */
public fun interface EdgeConsumer {
    public fun accept(edge: Edge)
}

/**
 * A function interface for reacting to edges with true/false.
 */
public fun interface EdgePredicate {
    public fun test(edge: Edge): Boolean
}

/**
 * A read-only collection of edges. Note that this interface is distinct from [Collection<Edge>][Collection] in order to
 * avoid Edge boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for
 * those reasons. An [EdgeCollection] is not safe for reentrant writing - attempting to modify the collection while
 * any other method is ongoing leads to undefined behavior.
 */
public interface EdgeCollection : Collection<Edge>, EdgeIterable {

    override fun isEmpty(): Boolean {
        return size == 0
    }

    /**
     * A method for iteration guaranteed to be as fast or faster than [iterator].
     */
    public fun foreach(action: EdgeConsumer) {
        val it = iterator()
        while (it.hasNext()) {
            action.accept(it.next())
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

    public fun containsAll(elements: EdgeCollection): Boolean {
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

    public fun toLongArray(): LongArray {
        val array = LongArray(size)
        var index = 0
        for (element in this) {
            array[index++] = element.id
        }
        return array
    }
}

/**
 * A collection of edges with an iterator that allows for removal.
 */
public interface MutableEdgeCollection : EdgeCollection {
    override fun iterator(): MutableEdgeIterator
}

/**
 * A read-only set of edges. Note that this interface is distinct from [Set<Edge>][Set] in order to avoid Edge
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
public interface EdgeSet : EdgeCollection, Set<Edge> {
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
public interface MutableEdgeSet : EdgeSet, MutableEdgeCollection

/**
 * A read-only set of edges where each edge is associated with an index from `0` to `size() - 1`. This makes edges
 * accessible by index, and an index can be retrieved for each edge (via `indexOf(edge)`). The `indexOf()` call is
 * guaranteed to take amortized constant time or better. This collection MUST iterate edges in index order.
 */
public interface IndexedEdgeSet : EdgeSet {

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean

    override fun containsAll(elements: Collection<Edge>): Boolean = super.containsAll(elements)

    public operator fun get(index: Int): Edge

    @Deprecated("For JVM usage only", level = DeprecationLevel.HIDDEN)
    public fun getEdge(index: Int): Long = get(index).id

    /**
     * Returns the index of the given edge in this collection, or -1 if the edge is not in this set.
     */
    public fun indexOf(element: Edge): Int

    override fun iterator(): EdgeIterator = object : EdgeIterator {
        private var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): Edge {
            require(index < size)
            return get(index++)
        }
    }

    override fun foreach(action: EdgeConsumer) {
        var index = 0
        while (index < size) {
            action.accept(get(index))
            index++
        }
    }
}

/**
 * A set of edges where each edge is associated with an index from `0` to `size() - 1`. This makes edges accessible by
 * index, and an index can be retrieved for each edge (via `indexOf(edge)`). The `indexOf()` call is guaranteed to take
 * amortized constant time or better. This collection MUST iterate edges in index order.
 */
public interface MutableIndexedEdgeSet : IndexedEdgeSet, MutableEdgeSet {
    override fun iterator(): MutableEdgeIterator
}

/**
 * Returns a new read-only set of the given edges.
 */
// KT-33565: suppression and generics can be removed once fixed
@Suppress("FINAL_UPPER_BOUND")
public fun <T : Edge> edgeSetOf(vararg edges: T): EdgeSet {
    return if (edges.isEmpty()) {
        emptyEdgeSet()
    } else if (edges.size == 1) {
        SingletonEdgeSet(edges[0])
    } else {
        LongHashSet(edges.size).apply {
            for (edge in edges) {
                add(edge.id)
            }
        }.asEdgeSet()
    }
}

/**
 * Returns an empty [EdgeIterator].
 */
public fun emptyEdgeIterator(): EdgeIterator = EmptyEdgeIterator

private val EmptyEdgeIterator = emptyLongIterator().asEdgeIterator()

/**
 * Returns a read-only empty set/list of edges.
 */
public fun emptyEdgeSet(): IndexedEdgeSet = EmptyEdgeSet

private object EmptyEdgeSet : IndexedEdgeSet {
    override val size: Int get() = 0

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = false

    override fun containsAll(elements: Collection<Edge>): Boolean = elements.isEmpty()
    override fun iterator(): EdgeIterator = emptyEdgeIterator()
    override fun foreach(action: EdgeConsumer) {}

    override fun get(index: Int): Edge = throw IndexOutOfBoundsException()
    override fun indexOf(element: Edge): Int = -1
}

/**
 * Provides a skeletal implementation of the read-only [EdgeCollection] interface.
 */
public abstract class AbstractEdgeCollection : EdgeCollection, AbstractCollection<Edge>() {
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
public abstract class AbstractEdgeSet : EdgeSet, AbstractSet<Edge>() {
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
public abstract class AbstractIndexedEdgeSet : IndexedEdgeSet, AbstractEdgeSet() {
    override fun iterator(): EdgeIterator = super.iterator()
    override fun containsAll(elements: Collection<Edge>): Boolean = super<IndexedEdgeSet>.containsAll(elements)

    protected abstract fun toEdgeId(edge: Edge): Int
    protected abstract fun toEdge(edgeId: Int): Edge

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean {
        require(toEdgeId(element) in indices)
        return true
    }

    override fun get(index: Int): Edge {
        if (index !in indices) throw IndexOutOfBoundsException()
        return toEdge(index)
    }

    override fun indexOf(element: Edge): Int {
        val index = toEdgeId(element)
        require(index in indices)
        return index
    }
}

internal abstract class AbstractMutableIndexedEdgeSet(private val graph: MutableGraph) : MutableIndexedEdgeSet,
    AbstractIndexedEdgeSet() {

    override fun iterator(): MutableEdgeIterator = object : MutableEdgeIterator {
        private var index = 0
        private var previous = -1

        override fun hasNext(): Boolean = index < size
        override fun next(): Edge {
            require(index < size)
            previous = index++
            return toEdge(previous)
        }

        override fun remove() {
            if (previous == -1) throw IllegalStateException()
            graph.removeEdge(toEdge(previous))
            index = previous
            previous = -1
        }
    }
}

private class SingletonEdgeSet(private val edge: Edge) : AbstractEdgeSet() {
    override val size: Int get() = 1
    override fun contains(element: Edge): Boolean = element == edge
    override fun iterator(): EdgeIterator = longIteratorOf(edge.id).asEdgeIterator()
    override fun foreach(action: EdgeConsumer) = action.accept(edge)
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
    override fun contains(element: Edge): Boolean = edges.contains(element.id)
    override fun iterator(): EdgeIterator = EdgeIteratorWrapper(edges.iterator())
    override fun foreach(action: EdgeConsumer) = edges.foreach { edge -> action.accept(Edge(edge)) }

    override fun toLongArray(): LongArray = edges.toLongArray()
}
