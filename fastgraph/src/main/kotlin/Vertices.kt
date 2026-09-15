/**
 * Methods dealing with edges.
 */
@file:JvmName("Vertices")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastcollect.*

private val VERTEX_HEX_FORMAT = HexFormat {
    number {
        removeLeadingZeros = true
        prefix = "0x"
    }
}

/**
 * A unique opaque vertex identifier. No meaning should be ascribed to the id value visible here, as it may be
 * interpreted differently by different graph implementations. Some graph implementations may give guarantees on their
 * vertex identifiers which are stronger, and allow some meaning to be ascribed to the identifier value.
 *
 * Note that Vertex by itself does not include any representation of what graph it belongs to. There are no safeguards
 * to prevent a client from accidentally using a vertex from one graph with another unrelated graph. It is the client's
 * responsibility to ensure this does not occur (unless a graph explicitly allows it). Some graph implementations may
 * make a best effort to ensure this does not occur, but this cannot be guaranteed or relied on.
 *
 * This class represents an *unstable* reference to a vertex. An unstable reference means that the reference may be
 * invalidated if a mutation is made to the owning graph. Individual graph implementations should make explicit
 * guarantees on when a vertex identifier is invalidated, but in the absence of stronger guarantees clients must assume
 * that any mutation of the graph topology (i.e. adding a vertex/edge, removing a vertex/edge) invalidates all
 * unstable references. [Graph] instances offer [Graph.createVertexReference] to obtain a stable [VertexReference] from
 * an unstable reference. Stable references are guaranteed to never be invalidated, but may be more expensive to
 * maintain than unstable references, and thus should be used sparingly.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmInline
public value class Vertex(public val id: Int) {
    /**
     * See [Graph.createVertexReference].
     */
    @JvmSynthetic
    context(graph: Graph)
    public fun createReference(): VertexReference = graph.createVertexReference(this)

    /**
     * See [Graph.outDegree].
     */
    @get:JvmSynthetic
    context(graph: Graph)
    public val outDegree: Int inline get() = graph.outDegree(this)

    /**
     * See [Graph.inDegree].
     */
    @get:JvmSynthetic
    context(graph: Graph)
    public val inDegree: Int inline get() = graph.inDegree(this)

    /**
     * See [Graph.successors].
     */
    @JvmSynthetic
    context(graph: Graph)
    public fun successors(): VertexSet = graph.successors(this)

    /**
     * See [Graph.predecessors].
     */
    @JvmSynthetic
    context(graph: Graph)
    public fun predecessors(): VertexSet = graph.predecessors(this)

    /**
     * See [Graph.outgoingEdges].
     */
    @JvmSynthetic
    context(graph: Graph)
    public fun outgoingEdges(): EdgeSet = graph.outgoingEdges(this)

    /**
     * See [Graph.incomingEdges].
     */
    @JvmSynthetic
    context(graph: Graph)
    public fun incomingEdges(): EdgeSet = graph.incomingEdges(this)

    /**
     * See [Graph.edge].
     */
    @JvmSynthetic
    context(graph: Graph)
    public fun edgeTo(other: Vertex): Edge = graph.edge(this, other)

    /**
     * See [Graph.edges].
     */
    @JvmSynthetic
    context(graph: Graph)
    public fun edgesTo(other: Vertex): EdgeSet = graph.edges(this, other)

    @get:JvmSynthetic
    context(graph: ValueGraph<V, *>)
    public val <V> value: V inline get() = graph.vertexProperty[this]

    @JvmName("toString")
    override fun toString(): String = "Vertex(${id.toHexString(VERTEX_HEX_FORMAT)})"
}

internal operator fun Vertex.compareTo(other: Vertex): Int = id.compareTo(other.id)
internal operator fun Vertex.compareTo(other: Int): Int = id.compareTo(other)
internal operator fun Int.compareTo(other: Vertex): Int = this.compareTo(other.id)
internal operator fun Vertex.plus(other: Int): Vertex = Vertex(id + other)
internal operator fun Vertex.minus(other: Int): Vertex = Vertex(id - other)
internal operator fun Vertex.inc(): Vertex = Vertex(id + 1)
internal operator fun Vertex.dec(): Vertex = Vertex(id - 1)

/**
 * A functional interface for receiving vertices.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public fun interface VertexConsumer {
    @JvmName("accept")
    public fun accept(vertex: Vertex)
}

/**
 * A functional interface for deciding on a vertex.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public fun interface VertexPredicate {
    @JvmName("test")
    public fun test(vertex: Vertex): Boolean
}

/**
 * A functional interface representing a vertex function.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public fun interface VertexFunction<T> {
    @JvmName("apply")
    public fun apply(vertex: Vertex): T
}

/**
 * An iterator over vertices. Note that this interface is distinct from [Iterator<Vertex>][Iterator] in order to avoid
 * Vertex boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for
 * those reasons.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface VertexIterator : Iterator<Vertex> {
    @JvmName("next")
    override fun next(): Vertex
}

/**
 * An iterator over vertices that allows for removal.
 */
public interface MutableVertexIterator : VertexIterator, MutableIterator<Vertex>

/**
 * A read-only set of vertices. Note that this interface is distinct from [Set<Vertex>][Set] in order to avoid Vertex
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface VertexSet : Set<Vertex> {
    override fun isEmpty(): Boolean = size == 0

    override fun iterator(): VertexIterator

    @JvmName("contains")
    override fun contains(element: Vertex): Boolean {
        for (e in this) {
            if (e == element) return true
        }
        return false
    }

    public fun containsAll(elements: VertexSet): Boolean {
        for (e in elements) {
            if (!contains(e)) return false
        }
        return true
    }

    override fun containsAll(elements: Collection<Vertex>): Boolean {
        if (elements is VertexSet) {
            return containsAll(elements)
        }

        for (e in elements) {
            if (!contains(e)) return false
        }
        return true
    }

    public fun toIntArray(): IntArray {
        val array = IntArray(size)
        var index = 0
        for (element in this) {
            array[index++] = element.id
        }
        return array
    }
}

/**
 * A set of vertices with an iterator that allows for removal.
 */
public interface MutableVertexSet : VertexSet {
    override fun iterator(): MutableVertexIterator
}

/**
 * A set of vertices where the [Vertex.id] of each vertex is in [0, size).
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface IndexedVertexSet : VertexSet {

    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = element.id in 0..<size

    @JvmName("get")
    public operator fun get(index: Int): Vertex {
        if (index !in 0..<size) throw IndexOutOfBoundsException()
        return Vertex(index)
    }

    @JvmName("indexOf")
    public fun indexOf(element: Vertex): Int {
        return if (element.id in 0..<size) element.id else -1
    }

    override fun iterator(): VertexIterator = object : VertexIterator {
        private var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): Vertex {
            if (!hasNext()) throw NoSuchElementException()
            return get(index++)
        }
    }
}

public val IndexedVertexSet.lastIndex: Int get() = size - 1

public interface MutableIndexedVertexSet : IndexedVertexSet, MutableVertexSet {
    override fun iterator(): MutableVertexIterator
}

/**
 * Returns a new read-only set of the given vertices.
 */
// KT-33565: suppression and generics can be removed once fixed
@Suppress("FINAL_UPPER_BOUND")
public fun <T : Vertex> vertexSetOf(vararg vertices: T): VertexSet {
    return if (vertices.isEmpty()) {
        emptyVertexSet()
    } else if (vertices.size == 1) {
        SingletonVertexSet(vertices[0].id)
    } else {
        IntHashSet(vertices.size).apply {
            for (vertex in vertices) {
                add(vertex.id)
            }
        }.asVertexSet()
    }
}

public fun emptyVertexIterator(): MutableVertexIterator = EmptyVertexIterator

private object EmptyVertexIterator : MutableVertexIterator {
    override fun hasNext(): Boolean = false
    override fun next(): Vertex = throw NoSuchElementException()
    override fun remove() = throw IllegalStateException()
}

/**
 * Returns a read-only empty set/list of vertices.
 */
public fun emptyVertexSet(): IndexedVertexSet = EmptyVertexSet

private object EmptyVertexSet : MutableIndexedVertexSet {
    override val size: Int get() = 0
    override fun iterator(): MutableVertexIterator = emptyVertexIterator()

    override fun contains(element: Vertex): Boolean = false
    override fun containsAll(elements: VertexSet): Boolean = elements.isEmpty()
    override fun containsAll(elements: Collection<Vertex>): Boolean = elements.isEmpty()

    override fun get(index: Int): Vertex = throw IndexOutOfBoundsException()
    override fun indexOf(element: Vertex): Int = -1
}

/**
 * Provides a skeletal implementation of the read-only [VertexSet] interface.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public abstract class AbstractVertexSet : VertexSet, AbstractSet<Vertex>() {
    override fun isEmpty(): Boolean = super<VertexSet>.isEmpty()
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = super<VertexSet>.contains(element)
    override fun containsAll(elements: Collection<Vertex>): Boolean = super<VertexSet>.containsAll(elements)
}

/**
 * Provides a skeletal implementation of the read-only [IndexedVertexSet] interface.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public abstract class AbstractIndexedVertexSet : IndexedVertexSet, AbstractVertexSet() {
    override fun iterator(): VertexIterator = super.iterator()
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = super<IndexedVertexSet>.contains(element)
}

/**
 * Provides a skeletal implementation of the [MutableIndexedVertexSet] interface.
 */
public abstract class AbstractMutableIndexedVertexSet(private val graph: MutableGraph) : MutableIndexedVertexSet, AbstractIndexedVertexSet() {
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

private class SingletonVertexSet(private val vertexId: Int) : VertexSet {
    override val size: Int get() = 1
    override fun contains(element: Vertex): Boolean = element.id == vertexId
    override fun iterator(): VertexIterator = intIteratorOf(vertexId).asVertexIterator()
    override fun toIntArray(): IntArray = IntArray(1) { vertexId }
}

internal fun IntIterator.asVertexIterator(): VertexIterator = VertexIteratorWrapper(this)

private class VertexIteratorWrapper(private val it: IntIterator) : VertexIterator {
    override fun hasNext(): Boolean = it.hasNext()
    override fun next(): Vertex = Vertex(it.nextInt())
}

internal fun IntSet.asVertexSet(): VertexSet = VertexSetWrapper(this)

private class VertexSetWrapper(private val vertices: IntSet) : VertexSet {
    override val size: Int get() = vertices.size
    override fun contains(element: Vertex): Boolean = vertices.contains(element.id)
    override fun iterator(): VertexIterator = vertices.iterator().asVertexIterator()
    override fun toIntArray(): IntArray = vertices.copyInto(IntArray(vertices.size))
}
