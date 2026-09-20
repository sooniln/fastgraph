/**
 * Methods dealing with edges.
 */
@file:JvmName("Edges")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastcollect.*
import kotlin.math.max
import kotlin.math.min

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
     * See [edgeTarget].
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

    /**
     * See [Graph.createEdgeReference].
     */
    @JvmSynthetic
    context(graph: Graph)
    public fun reference(): EdgeReference = graph.createEdgeReference(this)

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
    override fun toString(): String = "Edge($id)"
}

/**
 * A unique identity-indexed edge identifier. Every edge in a graph is assigned a consecutive integer [id] in [0,
 * graph.edges.size), which is also its index in `graph.edges`. An [Edge] may only be converted to an
 * [IdentityIndexedEdge] if it belongs to a [Graph] that implements [IdentityIndexedEdgeGraph].
 *
 * This class is primarily intended for internal usage while implementing a graph, but may find other uses occasionally.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmInline
public value class IdentityIndexedEdge(public val id: Int) {

    @JvmName("toEdge")
    public fun toEdge(): Edge = Edge(id.toLong())

    /**
     * See [Graph.edgeSource].
     */
    @get:JvmSynthetic
    context(graph: IdentityIndexedEdgeGraph)
    public val source: Vertex inline get() = graph.edgeSource(this)

    /**
     * See [Graph.edgeTarget].
     */
    @get:JvmSynthetic
    context(graph: IdentityIndexedEdgeGraph)
    public val target: Vertex inline get() = graph.edgeTarget(this)

    /**
     * See [edgeSource].
     */
    @JvmSynthetic
    context(graph: IdentityIndexedEdgeGraph)
    public fun source(target: Vertex): Vertex = graph.edgeSource(this, target)

    /**
     * See [edgeTarget].
     */
    @JvmSynthetic
    context(graph: IdentityIndexedEdgeGraph)
    public fun target(source: Vertex): Vertex = graph.edgeTarget(this, source)

    /**
     * See [Graph.edgeOpposite].
     */
    @JvmSynthetic
    context(graph: IdentityIndexedEdgeGraph)
    public fun opposite(other: Vertex): Vertex = graph.edgeOpposite(this, other)

    /**
     * See [Graph.createEdgeReference].
     */
    @JvmSynthetic
    context(graph: IdentityIndexedEdgeGraph)
    public fun reference(): EdgeReference = graph.createEdgeReference(this)

    @JvmSynthetic
    context(graph: IdentityIndexedEdgeGraph)
    public operator fun component1(): Vertex = source

    @JvmSynthetic
    context(graph: IdentityIndexedEdgeGraph)
    public operator fun component2(): Vertex = target

    @JvmName("toString")
    override fun toString(): String = "Edge($id)"

    public companion object {
        @JvmSynthetic
        public fun from(edge: Edge) : IdentityIndexedEdge = IdentityIndexedEdge(edge.id.toInt())
    }
}

/**
 * A unique canonical edge identifier. The source and target vertex of the edge are encoded directly into the edge [id]
 * itself, so that it is possible to retrieve them without referencing the owning graph. A CanonicalEdge cannot be used
 * with multi-edge graphs, since the edge is defined only by its source and target vertex. An [Edge] may only be
 * converted to a [CanonicalEdge] if it belongs to a [Graph] that implements [CanonicalEdgeGraph].
 *
 * This class is primarily intended for internal usage while implementing a graph, but may find other uses occasionally.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmInline
public value class CanonicalEdge(public val id: Long) {

    private constructor(highBits: Int, lowBits: Int) : this(
        highBits.toLong().shl(32).or(lowBits.toLong().and(0xFFFFFFFF))
    )

    @JvmName("toEdge")
    public fun toEdge(): Edge = Edge(id)

    @get:JvmName("source")
    public val source: Vertex inline get() = Vertex(id.ushr(32).toInt())

    @get:JvmName("target")
    public val target: Vertex inline get() = Vertex(id.toInt())

    /**
     * See [edgeSource].
     */
    @JvmSynthetic
    context(graph: CanonicalEdgeGraph)
    public fun source(target: Vertex): Vertex = graph.edgeSource(Edge(id), target)

    /**
     * See [edgeTarget].
     */
    @JvmSynthetic
    context(graph: CanonicalEdgeGraph)
    public fun target(source: Vertex): Vertex = graph.edgeTarget(Edge(id), source)

    /**
     * See [Graph.edgeOpposite].
     */
    @JvmSynthetic
    public fun opposite(other: Vertex): Vertex {
        val source = source
        val target = target
        if (other == target) {
            return source
        } else {
            if (other != source) {
                throw IllegalArgumentException("vertex $other is not in edge $source -> $target")
            }

            return target
        }
    }

    /**
     * See [Graph.createEdgeReference].
     */
    @JvmSynthetic
    context(graph: CanonicalEdgeGraph)
    public fun reference(): EdgeReference = graph.createEdgeReference(Edge(id))

    @JvmSynthetic
    public operator fun component1(): Vertex = source

    @JvmSynthetic
    public operator fun component2(): Vertex = target

    @JvmName("toString")
    override fun toString(): String =
        "Edge(${source.id} -> ${target.id})"

    public companion object {
        @JvmSynthetic
        public fun from(edge: Edge) : CanonicalEdge = CanonicalEdge(edge.id)

        @JvmSynthetic
        public fun from(directed: Boolean, source: Vertex, target: Vertex) : CanonicalEdge {
            return if (!directed) {
                CanonicalEdge(highBits = min(source.id, target.id), lowBits = max(source.id, target.id))
            } else {
                CanonicalEdge(highBits = source.id, lowBits = target.id)
            }
        }

        internal fun fromSorted(directed: Boolean, source: Vertex, target: Vertex): CanonicalEdge {
            assert(directed || source <= target)
            return CanonicalEdge(highBits = source.id, lowBits = target.id)
        }
    }
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

/** A read-only collection of edges. */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface EdgeCollection : Collection<Edge> {
    override fun isEmpty(): Boolean = size == 0

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

        for (e in elements) {
            if (!contains(e)) return false
        }
        return true
    }

    override fun iterator(): EdgeIterator

    public fun toLongArray(): LongArray {
        val array = LongArray(size)
        var index = 0
        for (element in this) {
            array[index++] = element.id
        }
        return array
    }
}

/** A read-only ordered collection of edges. */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface EdgeSequencedCollection : EdgeCollection, RandomAccess {
    @JvmName("get")
    public operator fun get(index: Int): Edge

    @JvmName("first")
    public fun first(): Edge {
        if (isEmpty()) throw NoSuchElementException()
        return get(0)
    }

    @JvmName("last")
    public fun last(): Edge {
        if (isEmpty()) throw NoSuchElementException()
        return get(size - 1)
    }

    @JvmName("contains")
    override fun contains(element: Edge): Boolean = indexOf(element) != -1

    @JvmName("indexOf")
    public fun indexOf(element: Edge): Int {
        for (index in 0..<size) {
            if (get(index) == element) return index
        }
        return -1
    }

    @JvmName("lastIndexOf")
    public fun lastIndexOf(element: Edge): Int {
        for (index in size - 1 downTo 0) {
            if (get(index) == element) return index
        }
        return -1
    }

    override fun iterator(): EdgeIterator = object : EdgeIterator {
        private var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): Edge {
            if (!hasNext()) throw NoSuchElementException()
            return get(index++)
        }
    }

    override fun toLongArray(): LongArray {
        val array = LongArray(size)
        for (index in 0..<size) {
            array[index] = get(index).id
        }
        return array
    }
}

/** A read-only set of edges. */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface EdgeSet : EdgeCollection, Set<Edge> {
    override fun isEmpty(): Boolean = super.isEmpty()
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = super.contains(element)
    override fun containsAll(elements: Collection<Edge>): Boolean = super.containsAll(elements)
}

/** A set of edges with an iterator that allows for removal. */
public interface MutableEdgeSet : EdgeSet {
    override fun iterator(): MutableEdgeIterator
}

/** A read-only ordered set of edges. */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface EdgeSequencedSet : EdgeSequencedCollection, EdgeSet {
    override fun isEmpty(): Boolean = super<EdgeSequencedCollection>.isEmpty()
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = super<EdgeSequencedCollection>.contains(element)
    override fun containsAll(elements: Collection<Edge>): Boolean = super<EdgeSequencedCollection>.containsAll(elements)
    override fun iterator(): EdgeIterator = super.iterator()

    public fun equalsSequenced(other: EdgeSequencedSet): Boolean {
        if (size != other.size) return false

        var i = size - 1
        while (i >= 0) {
            if (get(i) != other[i]) return false
            i--
        }
        return true
    }
}

/** An ordered set of edges with an iterator that allows for removal. */
public interface MutableEdgeSequencedSet : EdgeSequencedSet, MutableEdgeSet {
    override fun iterator(): MutableEdgeIterator
}

/**
 * An ordered set of edges where every edge is associated with an index in [0, size), such that [get] and [indexOf]
 * are both constant time. The index of an edge is obtained via [indexOf], and the edge for an index via [get].
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface IndexedEdgeSet : EdgeSequencedSet {
    @JvmName("indexOf")
    abstract override fun indexOf(element: Edge): Int

    @JvmName("contains")
    override fun contains(element: Edge): Boolean = indexOf(element) != -1

    @JvmName("lastIndexOf")
    override fun lastIndexOf(element: Edge): Int = indexOf(element)
}

/** An [IndexedEdgeSet] with an iterator that allows for removal. */
public interface MutableIndexedEdgeSet : IndexedEdgeSet, MutableEdgeSequencedSet {
    override fun iterator(): MutableEdgeIterator
}

/**
 * An [IndexedEdgeSet] where the index of each edge is its [Edge.id], i.e. `get(index) == Edge(index)` and
 * `indexOf(edge) == edge.id`.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface IdentityIndexedEdgeSet : IndexedEdgeSet {
    @JvmName("get")
    override fun get(index: Int): Edge {
        if (index !in 0..<size) throw IndexOutOfBoundsException()
        return Edge(index.toLong())
    }

    @JvmName("contains")
    override fun contains(element: Edge): Boolean = element.id in 0..<size

    @JvmName("indexOf")
    override fun indexOf(element: Edge): Int = if (element.id in 0..<size) element.id.toInt() else -1
}

/** An [IdentityIndexedEdgeSet] with an iterator that allows for removal. */
public interface MutableIdentityIndexedEdgeSet : IdentityIndexedEdgeSet, MutableIndexedEdgeSet {
    override fun iterator(): MutableEdgeIterator
}

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

public fun emptyEdgeIterator(): MutableEdgeIterator = EmptyEdgeIterator

private object EmptyEdgeIterator : MutableEdgeIterator {
    override fun hasNext(): Boolean = false
    override fun next(): Edge = throw NoSuchElementException()
    override fun remove() = throw IllegalStateException()
}

public fun emptyEdgeSet(): IdentityIndexedEdgeSet = EmptyEdgeSet

private object EmptyEdgeSet : IdentityIndexedEdgeSet, MutableIndexedEdgeSet, AbstractEdgeSet() {
    override val size: Int get() = 0
    override fun get(index: Int): Edge = throw IndexOutOfBoundsException()
    override fun contains(element: Edge): Boolean = false
    override fun indexOf(element: Edge): Int = -1
    override fun iterator(): MutableEdgeIterator = emptyEdgeIterator()
}

/** Provides a skeletal implementation of the [EdgeSequencedCollection] interface. */
public abstract class AbstractEdgeSequencedCollection : EdgeSequencedCollection {
    override fun equals(other: Any?): Boolean {
        if (other !is EdgeSequencedCollection) return false
        if (size != other.size) return false

        var i = size - 1
        while (i >= 0) {
            if (get(i) != other[i]) return false
            i--
        }
        return true
    }

    override fun hashCode(): Int {
        var hashCode = 1
        var i = size - 1
        while (i >= 0) {
            hashCode = 31 * hashCode + get(i--).hashCode()
        }
        return hashCode
    }

    override fun toString(): String = joinToString(prefix = "[", postfix = "]", separator = ", ")
}

/** Provides a skeletal implementation of the [EdgeSet] interface. */
public abstract class AbstractEdgeSet : EdgeSet {
    abstract override fun iterator(): EdgeIterator

    override fun equals(other: Any?): Boolean {
        if (other !is Set<*>) return false
        if (size != other.size) return false
        return (this as Set<*>).containsAll(other)
    }

    override fun hashCode(): Int {
        var hashCode = 0
        for (element in this) {
            hashCode += element.hashCode()
        }
        return hashCode
    }

    override fun toString(): String = joinToString(prefix = "{", postfix = "}", separator = ", ")
}

/** Provides a skeletal implementation of the [EdgeSequencedSet] interface. */
public abstract class AbstractEdgeSequencedSet : EdgeSequencedSet, AbstractEdgeSet() {
    override fun iterator(): EdgeIterator = super.iterator()
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
    override fun next(): Edge = Edge(it.nextLong())
}

internal fun LongArray.asSequencedEdgeSet(): EdgeSequencedSet = ArrayEdgeSequencedSet(this)

private class ArrayEdgeSequencedSet(private val edges: LongArray) : EdgeSequencedSet, AbstractEdgeSet() {
    override val size: Int get() = edges.size
    override fun get(index: Int): Edge = Edge(edges[index])
    override fun contains(element: Edge): Boolean = edges.contains(element.id)
    override fun iterator(): EdgeIterator = edges.iterator().asEdgeIterator()
    override fun toLongArray(): LongArray = edges.copyOf()
}

internal fun LongSet.asEdgeSet(): EdgeSet = EdgeSetWrapper(this)

private class EdgeSetWrapper(private val edges: LongSet) : AbstractEdgeSet() {
    override val size: Int get() = edges.size
    override fun contains(element: Edge): Boolean = edges.contains(element.id)
    override fun iterator(): EdgeIterator = edges.iterator().asEdgeIterator()
    override fun toLongArray(): LongArray = edges.copyInto(LongArray(edges.size))
}
