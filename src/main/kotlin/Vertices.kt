/**
 * Utilities for [Vertex].
 */
@file:JvmMultifileClass @file:JvmName("Vertices")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastcollect.ints.IntSet
import io.github.sooniln.fastcollect.ints.emptyIntIterator
import io.github.sooniln.fastgraph.primitives.collections.GraphIntHashSet

private val VERTEX_HEX_FORMAT = HexFormat {
    number {
        removeLeadingZeros = true
        prefix = "0x"
    }
}

/**
 * A unique opaque vertex identifier. No meaning should be ascribed to the integer value visible here, as it may be
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
@JvmInline
value class Vertex(val intValue: Int) {
    /**
     * See [Graph.createVertexReference].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmSynthetic
    @JvmName("#createReference")
    context(graph: Graph)
    fun createReference(): VertexReference = graph.createVertexReference(this)

    /**
     * See [Graph.outDegree].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmSynthetic
    @get:JvmName("#outDegree")
    context(graph: Graph)
    val outDegree: Int inline get() = graph.outDegree(this)

    /**
     * See [Graph.inDegree].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmSynthetic
    @get:JvmName("#inDegree")
    context(graph: Graph)
    val inDegree: Int inline get() = graph.inDegree(this)

    /**
     * See [Graph.successors].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmSynthetic
    @JvmName("#successors")
    context(graph: Graph)
    fun successors(): VertexSet = graph.successors(this)

    /**
     * See [Graph.predecessors].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmSynthetic
    @JvmName("#predecessors")
    context(graph: Graph)
    fun predecessors(): VertexSet = graph.predecessors(this)

    /**
     * See [Graph.outgoingEdges].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmSynthetic
    @JvmName("#outgoingEdges")
    context(graph: Graph)
    fun outgoingEdges(): EdgeSet = graph.outgoingEdges(this)

    /**
     * See [Graph.incomingEdges].
     */
    @JvmSynthetic
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("#incomingEdges")
    context(graph: Graph)
    fun incomingEdges(): EdgeSet = graph.incomingEdges(this)

    /**
     * Returns the index of this vertex in [IndexedVertexGraph.vertices].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmSynthetic
    @get:JvmName("#index")
    context(graph: IndexedVertexGraph)
    val Vertex.index: Int inline get() = graph.vertices.indexOf(this)

    override fun toString(): String = "Vertex(${intValue.toHexString(VERTEX_HEX_FORMAT)})"
}

internal operator fun Vertex.compareTo(other: Vertex): Int = intValue.compareTo(other.intValue)
internal operator fun Vertex.compareTo(other: Int): Int = intValue.compareTo(other)
internal operator fun Int.compareTo(other: Vertex): Int = this.compareTo(other.intValue)
internal operator fun Vertex.plus(other: Int): Vertex = Vertex(intValue + other)
internal operator fun Vertex.minus(other: Int): Vertex = Vertex(intValue - other)
internal operator fun Vertex.inc(): Vertex = Vertex(intValue + 1)
internal operator fun Vertex.dec(): Vertex = Vertex(intValue - 1)

/**
 * An iterator over vertices. Note that this interface is distinct from [Iterator<Vertex>][Iterator] in order to avoid
 * Vertex boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for
 * those reasons.
 */
interface VertexIterator : Iterator<Vertex> {
    @JvmSynthetic
    override fun next(): Vertex

    @Deprecated("For JVM usage only", level = DeprecationLevel.ERROR)
    fun nextVertex(): Int = next().intValue
}

/**
 * An iterator over vertices that allows for removal.
 */
interface MutableVertexIterator : VertexIterator, MutableIterator<Vertex>

/**
 * An iterable of vertices. Note that this interface is distinct from [Iterable<Vertex>][Iterable] in order to avoid
 * Vertex boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for
 * those reasons.
 */
interface VertexIterable : Iterable<Vertex> {
    override fun iterator(): VertexIterator
}

/**
 * A functional interface for receiving vertices.
 */
fun interface VertexConsumer {
    fun accept(vertex: Vertex)
}

/**
 * A read-only collection of vertices. Note that this interface is distinct from [Collection<Vertex>][Collection] in
 * order to avoid Vertex boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever
 * possible for those reasons. A [VertexCollection] is not re-entrancy safe for writing - attempting to modify the
 * collection while any other method is ongoing leads to undefined behavior.
 */
interface VertexCollection : Collection<Vertex>, VertexIterable {

    override fun isEmpty(): Boolean {
        return size == 0
    }

    /**
     * A method for iteration guaranteed to be as fast or faster than [iterator].
     */
    fun foreach(action: VertexConsumer) {
        val it = iterator()
        while (it.hasNext()) {
            action.accept(it.next())
        }
    }

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean {
        for (e in this) {
            if (e == element) return true
        }
        return false
    }

    fun containsAll(elements: VertexCollection): Boolean {
        for (e in elements) {
            if (!contains(e)) return false
        }
        return true
    }

    override fun containsAll(elements: Collection<Vertex>): Boolean {
        if (elements is VertexCollection) {
            return containsAll(elements)
        }

        for (e in this) {
            if (!contains(e)) return false
        }
        return true
    }

    fun toIntArray(): IntArray {
        val array = IntArray(size)
        var index = 0
        for (element in this) {
            array[index++] = element.intValue
        }
        return array
    }
}

/**
 * A collection of vertices with an iterator that allows for removal.
 */
interface MutableVertexCollection : VertexCollection {
    override fun iterator(): MutableVertexIterator
}

/**
 * A read-only set of vertices. Note that this interface is distinct from [Set<Vertex>][Set] in order to avoid Vertex
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
interface VertexSet : VertexCollection, Set<Vertex> {
    override fun isEmpty(): Boolean = super.isEmpty()

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = super.contains(element)

    override fun containsAll(elements: Collection<Vertex>): Boolean = super.containsAll(elements)
}

/**
 * A set of vertices with an iterator that allows for removal.
 */
interface MutableVertexSet : VertexSet, MutableVertexCollection

/**
 * A read-only set of vertices where each vertex is associated with an index from `0` to `size() - 1`. This makes
 * vertices accessible by index, and an index can be retrieved for each vertex (via `indexOf(vertex)`). The `indexOf()`
 * call is guaranteed to take amortized constant time or better. This collection MUST iterate vertices in index order.
 */
interface IndexedVertexSet : VertexSet {

    @Suppress("ReplaceManualRangeWithIndicesCalls")
    val indices: IntRange get() = 0..<size

    val lastIndex: Int get() = size - 1

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean

    override fun containsAll(elements: Collection<Vertex>): Boolean = super.containsAll(elements)

    operator fun get(index: Int): Vertex

    @Deprecated("For JVM usage only", level = DeprecationLevel.ERROR)
    fun getVertex(index: Int): Int = get(index).intValue

    /**
     * Returns the index of the given vertex in this collection, or -1 if the vertex is not in this set.
     */
    fun indexOf(element: Vertex): Int

    override fun iterator(): VertexIterator = VertexIteratorImpl(this)

    private class VertexIteratorImpl(private val vertices: IndexedVertexSet) : VertexIterator {
        private var index = 0

        override fun hasNext(): Boolean = index < vertices.size
        override fun next(): Vertex = vertices[index++]
    }

    override fun foreach(action: VertexConsumer) {
        var index = 0
        while (index < size) {
            action.accept(get(index))
            index++
        }
    }
}

/**
 * A set of vertices where each vertex is associated with an index from `0` to `size() - 1`. This makes vertices
 * accessible by index, and an index can be retrieved for each vertex (via `indexOf(vertex)`). The `indexOf()` call is
 * guaranteed to take amortized constant time or better. This collection MUST iterate vertices in index order.
 */
interface MutableIndexedVertexSet : IndexedVertexSet, MutableVertexSet {
    override fun iterator(): MutableVertexIterator
}

/**
 * Returns a new read-only set of the given vertices.
 */
// KT-33565: suppression and generics can be removed once fixed
@Suppress("FINAL_UPPER_BOUND")
fun <T : Vertex> vertexSetOf(vararg vertices: T): VertexSet {
    return if (vertices.isEmpty()) {
        emptyVertexSet()
    } else if (vertices.size == 1) {
        SingletonVertexSet(vertices[0])
    } else {
        val set = GraphIntHashSet(vertices.size)
        for (vertex in vertices) {
            set.add(vertex.intValue)
        }
        VertexSetWrapper(set)
    }
}

fun emptyVertexIterator() = EmptyVertexIterator

private val EmptyVertexIterator = emptyIntIterator().asVertexIterator()

/**
 * Returns a read-only empty set/list of vertices.
 */
fun emptyVertexSet(): IndexedVertexSet = EmptyVertexSetList

private object EmptyVertexSetList : IndexedVertexSet {
    override val size: Int get() = 0

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = false

    override fun containsAll(elements: Collection<Vertex>): Boolean = elements.isEmpty()
    override fun iterator(): VertexIterator = emptyVertexIterator()
    override fun foreach(action: VertexConsumer) {}

    override fun get(index: Int): Vertex = throw IndexOutOfBoundsException()
    override fun indexOf(element: Vertex): Int = -1
}

/**
 * Provides a skeletal implementation of the read-only [VertexCollection] interface.
 */
abstract class AbstractVertexCollection : VertexCollection, AbstractCollection<Vertex>() {
    override fun isEmpty(): Boolean = super<VertexCollection>.isEmpty()

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = super<VertexCollection>.contains(element)
    override fun containsAll(elements: Collection<Vertex>): Boolean = super<VertexCollection>.containsAll(elements)
}

/**
 * Provides a skeletal implementation of the read-only [VertexSet] interface.
 */
abstract class AbstractVertexSet : VertexSet, AbstractSet<Vertex>() {
    override fun isEmpty(): Boolean = super<VertexSet>.isEmpty()

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = super<VertexSet>.contains(element)
    override fun containsAll(elements: Collection<Vertex>): Boolean = super<VertexSet>.containsAll(elements)
}

/**
 * Provides a skeletal implementation of the read-only [IndexedVertexSet] interface.
 */
abstract class AbstractIndexedVertexSet : IndexedVertexSet, AbstractSet<Vertex>(), VertexSet {
    override fun isEmpty(): Boolean = super<IndexedVertexSet>.isEmpty()

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = super<VertexSet>.contains(element)
    override fun containsAll(elements: Collection<Vertex>): Boolean = super<IndexedVertexSet>.containsAll(elements)

    override fun iterator(): VertexIterator = super.iterator()

    override fun foreach(action: VertexConsumer) {
        var index = 0
        while (index < size) {
            action.accept(get(index++))
        }
    }
}

private class SingletonVertexSet(private val vertex: Vertex) : AbstractIndexedVertexSet() {
    override val size: Int
        get() = 1

    override fun get(index: Int): Vertex {
        if (index == 0) return vertex else throw IndexOutOfBoundsException()
    }

    override fun indexOf(element: Vertex): Int {
        return if (element == vertex) 0 else -1
    }

    override fun foreach(action: VertexConsumer) {
        action.accept(vertex)
    }
}

internal fun IntIterator.asVertexIterator(): VertexIterator = VertexIteratorWrapper(this)

private class VertexIteratorWrapper(private val it: IntIterator) : VertexIterator {
    override fun hasNext(): Boolean = it.hasNext()
    override fun next(): Vertex = Vertex(it.nextInt())
}

internal fun IntSet.asVertexSet(): VertexSet = VertexSetWrapper(this)

private class VertexSetWrapper(private val vertices: IntSet) : AbstractVertexSet() {
    override val size: Int get() = vertices.size

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = vertices.contains(element.intValue)
    override fun iterator(): VertexIterator = vertices.iterator().asVertexIterator()
    override fun foreach(action: VertexConsumer) = vertices.foreach { vertex -> action.accept(Vertex(vertex)) }

    override fun toIntArray(): IntArray = vertices.toIntArray()
}
