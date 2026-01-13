/**
 * Utilities for [Vertex].
 */
@file:JvmMultifileClass @file:JvmName("Vertices")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.internal.checkElementIndex
import io.github.sooniln.fastgraph.internal.checkPositionIndex
import io.github.sooniln.fastgraph.internal.checkRangeIndexes
import it.unimi.dsi.fastutil.ints.IntArraySet
import it.unimi.dsi.fastutil.ints.IntIterator
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import java.util.Spliterator

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
    override fun toString(): String = "Vertex(${intValue.toHexString(VERTEX_HEX_FORMAT)})"
}

/**
 * A *stable* reference to a vertex. This reference is guaranteed to never be invalidated when mutations are made to the
 * graph topology. A stable reference can be obtained through [Graph.createVertexReference]. [VertexReference] is
 * generally a less efficient representation than [Vertex], in terms of both memory and CPU. Prefer [Vertex] unless
 * reference stability across mutations is a requirement.
 */
interface VertexReference {

    /**
     * An unstable [Vertex] reference corresponding to this stable reference.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("getUnstable")
    val unstable: Vertex
}

/**
 * See [Graph.createVertexReference].
 */
@JvmSynthetic
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmName("#Vertex_reference")
context(graph: Graph)
fun Vertex.createReference(): VertexReference = graph.createVertexReference(this)

/**
 * Accesses the property value of a vertex. Equivalent to accessing the property value through the [VertexProperty]
 * itself.
 */
context(property: VertexProperty<T>)
var <T> Vertex.property: T
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#Vertex_property_get") inline get() = property[this]
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#Vertex_property_set") inline set(value) {
        property[this] = value
    }

/**
 * Accesses the property value of a vertex. Equivalent to accessing the property value through the [VertexProperty]
 * itself.
 */
context(property: VertexProperty<T>)
var <T> VertexReference.property: T
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#VertexReference_property_get") inline get() = property[unstable]
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#VertexReference_property_set") inline set(value) {
        property[unstable] = value
    }

/**
 * See [Graph.outDegree].
 */
context(graph: Graph)
val Vertex.outDegree: Int
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#Vertex_outDegree") inline get() = graph.outDegree(this)

/**
 * See [Graph.outDegree].
 */
context(graph: Graph)
val VertexReference.outDegree: Int
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#VertexReference_outDegree") inline get() = graph.outDegree(
        unstable
    )

/**
 * See [Graph.inDegree].
 */
context(graph: Graph)
val Vertex.inDegree
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#Vertex_inDegree") inline get() = graph.inDegree(this)

/**
 * See [Graph.inDegree].
 */
context(graph: Graph)
val VertexReference.inDegree
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#VertexReference_inDegree") inline get() = graph.inDegree(
        unstable
    )

/**
 * See [Graph.successors].
 */
@JvmSynthetic
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmName("#Vertex_successors")
context(graph: Graph)
fun Vertex.successors() = graph.successors(this)

/**
 * See [Graph.successors].
 */
@JvmSynthetic
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmName("#VertexReference_successors")
context(graph: Graph)
fun VertexReference.successors() = graph.successors(unstable)

/**
 * See [Graph.predecessors].
 */
@JvmSynthetic
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmName("#Vertex_predecessors")
context(graph: Graph)
fun Vertex.predecessors() = graph.predecessors(this)

/**
 * See [Graph.predecessors].
 */
@JvmSynthetic
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmName("#VertexReference_predecessors")
context(graph: Graph)
fun VertexReference.predecessors() = graph.predecessors(unstable)

/**
 * See [Graph.outgoingEdges].
 */
@JvmSynthetic
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmName("#Vertex_outgoingEdges")
context(graph: Graph)
fun Vertex.outgoingEdges() = graph.outgoingEdges(this)

/**
 * See [Graph.outgoingEdges].
 */
@JvmSynthetic
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmName("#VertexReference_outgoingEdges")
context(graph: Graph)
fun VertexReference.outgoingEdges() = graph.outgoingEdges(unstable)

/**
 * See [Graph.incomingEdges].
 */
@JvmSynthetic
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmName("#Vertex_incomingEdges")
context(graph: Graph)
fun Vertex.incomingEdges() = graph.incomingEdges(this)

/**
 * See [Graph.incomingEdges].
 */
@JvmSynthetic
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmName("#VertexReference_incomingEdges")
context(graph: Graph)
fun VertexReference.incomingEdges() = graph.incomingEdges(unstable)

/**
 * Returns the index of this vertex in [IndexedVertexGraph.vertices].
 */
context(graph: IndexedEdgeGraph)
val Vertex.index: Int
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#Vertex_index")
    inline get() = graph.vertices.indexOf(this)

/**
 * Returns the index of this vertex in [IndexedVertexGraph.vertices].
 */
context(graph: IndexedEdgeGraph)
val VertexReference.index: Int
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#VertexReference_index")
    inline get() = graph.vertices.indexOf(unstable)

/**
 * An iterator over vertices. Note that this interface is distinct from [Iterator<Vertex>][Iterator] in order to avoid
 * Vertex boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for
 * those reasons.
 */
interface VertexIterator : Iterator<Vertex> {
    override fun next(): Vertex

    @Deprecated("For JVM usage only", level = DeprecationLevel.ERROR)
    fun nextVertex(): Int = next().intValue
}

/**
 * An iterator over vertices that allows for removal.
 */
interface MutableVertexIterator : VertexIterator, MutableIterator<Vertex>

/**
 * A list iterator over vertices. Note that this interface is distinct from [ListIterator<Vertex>][ListIterator] in
 * order to avoid Vertex boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever
 * possible for those reasons.
 */
interface VertexListIterator : VertexIterator, ListIterator<Vertex> {
    override fun previous(): Vertex

    @Deprecated("For JVM usage only", level = DeprecationLevel.ERROR)
    fun previousVertex(): Int = previous().intValue
}

/**
 * A list iterator over vertices that allows for removal.
 */
interface MutableVertexListIterator : VertexListIterator, MutableVertexIterator, MutableListIterator<Vertex>

/**
 * An iterable of vertices. Note that this interface is distinct from [Iterable<Vertex>][Iterable] in order to avoid
 * Vertex boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for
 * those reasons.
 */
interface VertexIterable : Iterable<Vertex> {
    override fun iterator(): VertexIterator
}

/**
 * A read-only collection of vertices. Note that this interface is distinct from [Collection<Vertex>][Collection] in
 * order to avoid Vertex boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever
 * possible for those reasons.
 */
interface VertexCollection : Collection<Vertex>, VertexIterable {
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean
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
interface VertexSet : VertexCollection, Set<Vertex>

/**
 * A set of vertices with an iterator that allows for removal.
 */
interface MutableVertexSet : VertexSet, MutableVertexCollection

/**
 * A read-only list of vertices. Note that this interface is distinct from [List<Vertex>][List] in order to avoid Vertex
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
interface VertexList : VertexCollection, List<Vertex> {
    override fun get(index: Int): Vertex

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("indexOf")
    override fun indexOf(element: Vertex): Int

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("lastIndexOf")
    override fun lastIndexOf(element: Vertex): Int

    override fun listIterator(): VertexListIterator
    override fun listIterator(index: Int): VertexListIterator
    override fun subList(fromIndex: Int, toIndex: Int): VertexSetList

    @Deprecated("For JVM usage only", level = DeprecationLevel.ERROR)
    fun getVertex(index: Int): Int = get(index).intValue
}

/**
 * A list of vertices with an iterator that allows for removal.
 */
interface MutableVertexList : VertexList, MutableVertexCollection {
    override fun listIterator(): MutableVertexListIterator
    override fun listIterator(index: Int): MutableVertexListIterator
}

/**
 * A read-only set of vertices that can also be accessed by index like a list.
 */
interface VertexSetList : VertexSet, VertexList {
    override fun spliterator(): Spliterator<Vertex> = super<VertexList>.spliterator()
}

/**
 * A set of vertices that can also be accessed by index like a list, with an iterator that allows for removal.
 */
interface MutableVertexSetList : VertexSetList, MutableVertexSet, MutableVertexList

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
        val set = if (vertices.size < 100) IntArraySet(vertices.size) else IntOpenHashSet(vertices.size)
        for (vertex in vertices) {
            set.add(vertex.intValue)
        }
        VertexSetWrapper(set)
    }
}

/**
 * Returns a read-only empty set/list of vertices.
 */
fun emptyVertexSet(): VertexSetList = EmptyVertexSetList

private object EmptyVertexSetList : VertexSetList, List<Vertex> {
    override val size: Int get() = 0

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = false

    override fun containsAll(elements: Collection<Vertex>): Boolean = elements.isEmpty()
    override fun isEmpty(): Boolean = true
    override fun iterator(): VertexIterator = EmptyVertexIterator

    override fun get(index: Int): Vertex = throw IndexOutOfBoundsException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("indexOf")
    override fun indexOf(element: Vertex): Int = -1

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("lastIndexOf")
    override fun lastIndexOf(element: Vertex): Int = -1

    override fun listIterator(): VertexListIterator = EmptyVertexIterator
    override fun listIterator(index: Int): VertexListIterator =
        EmptyVertexIterator.also { if (index != 0) throw IndexOutOfBoundsException() }

    override fun subList(fromIndex: Int, toIndex: Int): VertexSetList = this
}

private object EmptyVertexIterator : VertexListIterator {
    override fun hasPrevious(): Boolean = false
    override fun hasNext(): Boolean = false
    override fun previous(): Vertex = throw NoSuchElementException()
    override fun next(): Vertex = throw NoSuchElementException()
    override fun nextIndex(): Int = throw NoSuchElementException()
    override fun previousIndex(): Int = throw NoSuchElementException()
}

/**
 * Provides a skeletal implementation of the read-only [VertexCollection] interface.
 */
abstract class AbstractVertexCollection : VertexCollection {
    override fun containsAll(elements: Collection<Vertex>): Boolean {
        return if (elements is VertexCollection) {
            elements.all(this::contains)
        } else {
            elements.all(this::contains)
        }
    }

    override fun isEmpty(): Boolean = size == 0

    override fun toString(): String = joinToString(", ", "[", "]") { it.intValue.toString() }
}

/**
 * Provides a skeletal implementation of the read-only [VertexSetList] interface.
 */
abstract class AbstractVertexSetList : VertexSetList, AbstractList<Vertex>() {
    override fun iterator(): VertexIterator = AbstractVertexListIterator(0)

    override fun listIterator(): VertexListIterator = AbstractVertexListIterator(0)
    override fun listIterator(index: Int): VertexListIterator = AbstractVertexListIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): VertexSetList = SubList(this, fromIndex, toIndex)

    private class SubList(private val list: VertexSetList, private val fromIndex: Int, toIndex: Int) :
        AbstractVertexSetList(), RandomAccess {
        init {
            checkRangeIndexes(fromIndex, toIndex, list.size)
        }

        override val size = toIndex - fromIndex

        override fun get(index: Int): Vertex {
            checkElementIndex(index, size)
            return list[fromIndex + index]
        }

        override fun subList(fromIndex: Int, toIndex: Int): VertexSetList {
            checkRangeIndexes(fromIndex, toIndex, size)
            return SubList(list, this.fromIndex + fromIndex, this.fromIndex + toIndex)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is List<*>) {
            if (size != other.size) {
                return false
            }

            if (other is VertexSetList) {
                val it = other.iterator()
                for (vertex in this) {
                    val otherVertex = it.next()
                    if (vertex != otherVertex) {
                        return false
                    }
                }
            } else {
                val it = other.iterator()
                for (vertex in this) {
                    val otherElement = it.next()
                    if (vertex != otherElement) {
                        return false
                    }
                }
            }
            return true
        }

        return false
    }

    override fun hashCode(): Int {
        var hashCode = 1
        for (vertex in this) {
            hashCode = 31 * hashCode + Integer.hashCode(vertex.intValue)
        }
        return hashCode
    }

    protected open inner class AbstractVertexListIterator(protected var index: Int) : MutableVertexListIterator {
        protected var lastIndex = -1

        init {
            checkPositionIndex(index, size)
        }

        final override fun hasNext(): Boolean = index < size
        final override fun hasPrevious(): Boolean = index > 0
        final override fun nextIndex(): Int = index
        final override fun previousIndex(): Int = index - 1

        final override fun next(): Vertex {
            if (index >= size) throw NoSuchElementException()
            lastIndex = index++
            return get(lastIndex)
        }

        final override fun previous(): Vertex {
            if (index <= 0) throw NoSuchElementException()
            lastIndex = --index
            return get(lastIndex)
        }

        final override fun set(element: Vertex): Unit = throw UnsupportedOperationException()
        final override fun add(element: Vertex): Unit = throw UnsupportedOperationException()

        final override fun remove() {
            check(lastIndex != -1)
            remove(lastIndex)
            index = lastIndex
            lastIndex = -1
        }

        protected open fun remove(index: Int): Unit = throw UnsupportedOperationException()
    }
}

private class SingletonVertexSet(private val vertex: Vertex) : AbstractVertexSetList() {
    override val size: Int
        get() = 1

    override fun get(index: Int): Vertex {
        checkElementIndex(index, size)
        return vertex
    }

    override fun subList(fromIndex: Int, toIndex: Int): VertexSetList {
        checkRangeIndexes(fromIndex, toIndex, size)
        return if (fromIndex == toIndex) emptyVertexSet() else this
    }
}

internal class VertexIteratorWrapper(private val it: IntIterator) : VertexIterator {
    override fun hasNext(): Boolean = it.hasNext()
    override fun next(): Vertex = Vertex(it.nextInt())
}

internal open class VertexSetWrapper(internal val vertices: IntSet) : VertexSet, AbstractVertexCollection() {
    override val size: Int get() = vertices.size

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = vertices.contains(element.intValue)
    override fun iterator(): VertexIterator = VertexIteratorWrapper(vertices.iterator())
}
