/**
 * Methods dealing with edges.
 */
@file:JvmName("Edges")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastcollect.*
import java.util.Spliterator

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
@Suppress("INAPPLICABLE_JVM_NAME")
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
     * See [edgeSource].
     */
    @JvmSynthetic
    context(graph: Graph)
    public fun source(target: Vertex): Vertex = graph.edgeSource(this, target)

    /**
     * See [edgeSource].
     */
    @JvmSynthetic
    context(graph: Graph)
    public fun target(source: Vertex): Vertex = graph.edgeTarget(this, source)

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

    @JvmName("toString")
    override fun toString(): String =
        "Edge(${highBits.toHexString(EDGE_HEX_FORMAT)}, ${lowBits.toHexString(EDGE_HEX_FORMAT)})"
}

/**
 * A functional interface for consuming edges.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public fun interface EdgeConsumer {
    @JvmName("accept")
    public fun accept(edge: Edge)
}

/**
 * A functional interface for deciding on an edge.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public fun interface EdgePredicate {
    @JvmName("test")
    public fun test(edge: Edge): Boolean
}

/**
 * A functional interface representing an edge function.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public fun interface EdgeFunction<T> {
    @JvmName("apply")
    public fun apply(edge: Edge): T
}

/**
 * An iterator over edges. Note that this interface is distinct from [Iterator<Edge>][Iterator] in order to avoid Edge
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface EdgeIterator : Iterator<Edge> {
    @JvmName("next")
    override fun next(): Edge
}

/**
 * An iterator over edges that allows for edge removal.
 */
public interface MutableEdgeIterator : EdgeIterator, MutableIterator<Edge>


/**
 * A read-only collection of edges. Note that this interface is distinct from [Set<Edge>][Set] in order to avoid Edge
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface EdgeCollection : Collection<Edge> {
    override fun isEmpty(): Boolean = size == 0

    override fun iterator(): EdgeIterator

    @JvmName("contains")
    override fun contains(element: Edge): Boolean {
        for (e in this) {
            if (e == element) return true
        }
        return false
    }

    public fun containsAll(elements: EdgeCollection): Boolean {
        for (e in this) {
            if (!contains(e)) return false
        }
        return true
    }

    override fun containsAll(elements: Collection<Edge>): Boolean {
        if (elements is EdgeSet) {
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

@Suppress("INAPPLICABLE_JVM_NAME")
public interface EdgeList : EdgeCollection, List<Edge> {
    override fun isEmpty(): Boolean = super.isEmpty()
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = super.contains(element)
    override fun containsAll(elements: Collection<Edge>): Boolean = super.containsAll(elements)

    override fun iterator(): EdgeIterator = object : EdgeIterator {
        private var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): Edge {
            if (!hasNext()) throw NoSuchElementException()
            return get(index++)
        }
    }

    @JvmName("indexOf")
    override fun indexOf(element: Edge): Int
    @JvmName("lastIndexOf")
    override fun lastIndexOf(element: Edge): Int
}

/**
 * A read-only set of edges. Note that this interface is distinct from [Set<Edge>][Set] in order to avoid Edge
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface EdgeSet : EdgeCollection, Set<Edge> {
    override fun isEmpty(): Boolean = super.isEmpty()
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = super.contains(element)
    override fun containsAll(elements: Collection<Edge>): Boolean = super.containsAll(elements)
}

/**
 * A set of edges with an iterator that allows for removal.
 */
public interface MutableEdgeSet : EdgeSet {
    override fun iterator(): MutableEdgeIterator
}

/**
 * A set of edges where the [Edge.id] of each edge is in [0, size).
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface IndexedEdgeSet : EdgeSet, EdgeList {
    override fun isEmpty(): Boolean = super<EdgeSet>.isEmpty()

    @JvmName("contains")
    override fun contains(element: Edge): Boolean = element.id in 0..<size
    override fun containsAll(elements: Collection<Edge>): Boolean = super<EdgeSet>.containsAll(elements)

    @JvmName("get")
    override fun get(index: Int): Edge {
        if (index !in 0..<size) throw IndexOutOfBoundsException()
        return Edge(index.toLong())
    }

    @JvmName("indexOf")
    override fun indexOf(element: Edge): Int {
        return if (element.id in 0..<size) element.lowBits else -1
    }

    @JvmName("lastIndexOf")
    override fun lastIndexOf(element: Edge): Int = indexOf(element)

    override fun iterator(): EdgeIterator = object : EdgeIterator {
        private var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): Edge {
            if (!hasNext()) throw NoSuchElementException()
            return get(index++)
        }
    }

    override fun spliterator(): Spliterator<Edge> = super<EdgeList>.spliterator()
}

public val IndexedEdgeSet.lastIndex: Int @JvmSynthetic get() = size - 1

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
        SingletonEdgeSet(edges[0].id)
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
public fun emptyEdgeIterator(): MutableEdgeIterator = EmptyEdgeIterator

private object EmptyEdgeIterator : MutableEdgeIterator {
    override fun hasNext(): Boolean = false
    override fun next(): Edge = throw NoSuchElementException()
    override fun remove() = throw IllegalStateException()
}

/**
 * Returns a read-only empty set/list of edges.
 */
public fun emptyEdgeSet(): IndexedEdgeSet = EmptyEdgeSet

private object EmptyEdgeSet : MutableIndexedEdgeSet, AbstractIndexedEdgeSet() {
    override val size: Int get() = 0
    override fun iterator(): MutableEdgeIterator = emptyEdgeIterator()

    override fun contains(element: Edge): Boolean = false
    override fun containsAll(elements: EdgeCollection): Boolean = elements.isEmpty()
    override fun containsAll(elements: Collection<Edge>): Boolean = elements.isEmpty()

    override fun get(index: Int): Edge = throw IndexOutOfBoundsException()
    override fun indexOf(element: Edge): Int = -1
}

/**
 * Provides a skeletal implementation of the read-only [EdgeList] interface.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public abstract class AbstractEdgeList : EdgeList, AbstractList<Edge>() {
    override fun iterator(): EdgeIterator = super<EdgeList>.iterator()
    override fun isEmpty(): Boolean = super<EdgeList>.isEmpty()
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = super<EdgeList>.contains(element)
    override fun containsAll(elements: Collection<Edge>): Boolean = super<EdgeList>.containsAll(elements)
}

/**
 * Provides a skeletal implementation of the read-only [EdgeSet] interface.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public abstract class AbstractEdgeSet : EdgeSet, AbstractSet<Edge>() {
    override fun isEmpty(): Boolean = super<EdgeSet>.isEmpty()
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = super<EdgeSet>.contains(element)
    override fun containsAll(elements: Collection<Edge>): Boolean = super<EdgeSet>.containsAll(elements)
}

/**
 * Provides a skeletal implementation of the read-only [IndexedEdgeSet] interface.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public abstract class AbstractIndexedEdgeSet : IndexedEdgeSet, AbstractEdgeList() {
    override fun iterator(): EdgeIterator = super<IndexedEdgeSet>.iterator()
    override fun isEmpty(): Boolean = super<IndexedEdgeSet>.isEmpty()

    @JvmName("get")
    override fun get(index: Int): Edge = super.get(index)

    @JvmName("contains")
    override fun contains(element: Edge): Boolean = super<IndexedEdgeSet>.contains(element)
    override fun containsAll(elements: Collection<Edge>): Boolean = super<IndexedEdgeSet>.containsAll(elements)

    @JvmName("indexOf")
    override fun indexOf(element: Edge): Int = super<IndexedEdgeSet>.indexOf(element)
    @JvmName("lastIndexOf")
    override fun lastIndexOf(element: Edge): Int = super<IndexedEdgeSet>.lastIndexOf(element)
}

internal abstract class AbstractMutableIndexedEdgeSet(private val graph: MutableGraph) : MutableIndexedEdgeSet, AbstractIndexedEdgeSet() {
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

private class SingletonEdgeSet(private val edgeId: Long) : AbstractEdgeSet() {
    override val size: Int get() = 1
    override fun contains(element: Edge): Boolean = element.id == edgeId
    override fun iterator(): EdgeIterator = longIteratorOf(edgeId).asEdgeIterator()
    override fun toLongArray(): LongArray = LongArray(1) { edgeId }
}

internal fun LongIterator.asEdgeIterator(): EdgeIterator = EdgeIteratorWrapper(this)

private class EdgeIteratorWrapper(private val it: LongIterator) : EdgeIterator {
    override fun hasNext(): Boolean = it.hasNext()
    override fun next(): Edge {
        return Edge(it.nextLong())
    }
}

internal fun LongArray.asEdgeList(): EdgeList = EdgeListWrapper(this)

private class EdgeListWrapper(private val edges: LongArray) : AbstractEdgeList() {
    override val size: Int get() = edges.size
    override fun iterator(): EdgeIterator = edges.iterator().asEdgeIterator()
    override fun contains(element: Edge): Boolean = edges.contains(element.id)
    override fun get(index: Int): Edge = Edge(edges[index])
    override fun toLongArray(): LongArray = edges.copyOf()
}

internal fun LongSet.asEdgeSet(): EdgeSet = EdgeSetWrapper(this)

private class EdgeSetWrapper(private val edges: LongSet) : AbstractEdgeSet() {
    override val size: Int get() = edges.size
    override fun contains(element: Edge): Boolean = edges.contains(element.id)
    override fun iterator(): EdgeIterator = EdgeIteratorWrapper(edges.iterator())
    override fun toLongArray(): LongArray = edges.copyInto(LongArray(edges.size))
}
