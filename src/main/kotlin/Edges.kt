@file:JvmMultifileClass @file:JvmName("Edges")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.internal.checkElementIndex
import io.github.sooniln.fastgraph.internal.checkPositionIndex
import io.github.sooniln.fastgraph.internal.checkRangeIndexes
import it.unimi.dsi.fastutil.longs.LongArraySet
import it.unimi.dsi.fastutil.longs.LongIterator
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import java.util.Spliterator

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
    @get:JvmName("getUnstable")
    val unstable: Edge
}

/**
 * See [Graph.createEdgeReference].
 */
@JvmSynthetic
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmName("#Edge_reference")
context(graph: Graph)
fun Edge.createReference(): EdgeReference = graph.createEdgeReference(this)

/**
 * Accesses the property value of a edge. Equivalent to accessing the property value through the [EdgeProperty] itself.
 */
context(property: EdgeProperty<T>)
var <T> Edge.property: T
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#Edge_property_get") inline get() = property[this]
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#Edge_property_set") inline set(value) {
        property[this] = value
    }

/**
 * Accesses the property value of a edge. Equivalent to accessing the property value through the [EdgeProperty] itself.
 */
context(property: EdgeProperty<T>)
var <T> EdgeReference.property: T
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#EdgeReference_property_get") inline get() = property[unstable]
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#EdgeReference_property_set") inline set(value) {
        property[unstable] = value
    }

/**
 * See [Graph.edgeSource].
 */
context(graph: Graph)
val Edge.source
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#Edge_source") inline get() = graph.edgeSource(this)

/**
 * See [Graph.edgeSource].
 */
context(graph: Graph)
val EdgeReference.source
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#EdgeReference_source") inline get() = graph.edgeSource(
        unstable
    )

/**
 * See [Graph.edgeTarget].
 */
context(graph: Graph)
val Edge.target
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#Edge_target") inline get() = graph.edgeTarget(this)

/**
 * See [Graph.edgeTarget].
 */
context(graph: Graph)
val EdgeReference.target
    @JvmSynthetic @Suppress("INAPPLICABLE_JVM_NAME") @JvmName("#EdgeReference_target") inline get() = graph.edgeTarget(
        unstable
    )

/**
 * See [Graph.edgeOpposite].
 */
@JvmSynthetic
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmName("#Edge_opposite")
context(graph: Graph)
fun Edge.opposite(other: Vertex) = graph.edgeOpposite(this, other)

/**
 * See [Graph.edgeOpposite].
 */
@JvmSynthetic
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmName("#EdgeReference_opposite")
context(graph: Graph)
fun EdgeReference.opposite(other: Vertex) = graph.edgeOpposite(unstable, other)

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
 * A list iterator over edges. Note that this interface is distinct from [ListIterator<Edge>][ListIterator] in order to
 * avoid Edge boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for
 * those reasons.
 */
interface EdgeListIterator : EdgeIterator, ListIterator<Edge> {
    override fun previous(): Edge

    @Deprecated("For JVM usage only", level = DeprecationLevel.ERROR)
    fun previousEdge(): Long = previous().longValue
}

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
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean

    fun toLongArray(): LongArray
    fun toLongArray(array: LongArray): LongArray
}

/**
 * A read-only set of edges. Note that this interface is distinct from [Set<Edge>][Set] in order to avoid Edge
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
interface EdgeSet : EdgeCollection, Set<Edge>

/**
 * A read-only list of edges. Note that this interface is distinct from [List<Edge>][List] in order to avoid Edge
 * boxing/unboxing, and associated performance penalties. Prefer to use this interface whenever possible for those
 * reasons.
 */
interface EdgeList : EdgeCollection, List<Edge> {
    override fun get(index: Int): Edge

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("indexOf")
    override fun indexOf(element: Edge): Int

    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("lastIndexOf")
    override fun lastIndexOf(element: Edge): Int

    override fun listIterator(): EdgeListIterator
    override fun listIterator(index: Int): EdgeListIterator
    override fun subList(fromIndex: Int, toIndex: Int): EdgeSetList

    @Deprecated("For JVM usage only", level = DeprecationLevel.ERROR)
    fun getEdge(index: Int): Long = get(index).longValue
}

/**
 * A read-only set of edges that can also be accessed by index like a list.
 */
interface EdgeSetList : EdgeSet, EdgeList {
    override fun spliterator(): Spliterator<Edge> = super<EdgeList>.spliterator()
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
        val set = if (edges.size < 100) LongArraySet(edges.size) else LongOpenHashSet(edges.size)
        for (edge in edges) {
            set.add(edge.longValue)
        }
        EdgeSetWrapper(set)
    }
}

/**
 * Returns a read-only empty set/list of edges.
 */
fun emptyEdgeSet(): EdgeSetList = EmptyEdgeSetList

private object EmptyEdgeSetList : EdgeSetList, List<Edge> {
    override val size: Int get() = 0

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = false

    override fun containsAll(elements: Collection<Edge>): Boolean = elements.isEmpty()
    override fun isEmpty(): Boolean = true
    override fun iterator(): EdgeIterator = EmptyEdgeIterator

    override fun get(index: Int): Edge = throw IndexOutOfBoundsException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("indexOf")
    override fun indexOf(element: Edge): Int = -1

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("lastIndexOf")
    override fun lastIndexOf(element: Edge): Int = -1

    override fun listIterator(): EdgeListIterator = EmptyEdgeIterator
    override fun listIterator(index: Int): EdgeListIterator =
        EmptyEdgeIterator.also { if (index != 0) throw IndexOutOfBoundsException() }

    override fun subList(fromIndex: Int, toIndex: Int): EdgeSetList = this

    override fun toLongArray(): LongArray = LongArray(0)
    override fun toLongArray(array: LongArray): LongArray = array
}

private object EmptyEdgeIterator : EdgeListIterator {
    override fun hasPrevious(): Boolean = false
    override fun hasNext(): Boolean = false
    override fun previous(): Edge = throw NoSuchElementException()
    override fun next(): Edge = throw NoSuchElementException()
    override fun nextIndex(): Int = throw NoSuchElementException()
    override fun previousIndex(): Int = throw NoSuchElementException()
}

/**
 * Provides a skeletal implementation of the read-only [EdgeCollection] interface.
 */
abstract class AbstractEdgeCollection : EdgeCollection {
    override fun containsAll(elements: Collection<Edge>): Boolean {
        return if (elements is EdgeCollection) {
            elements.all(this::contains)
        } else {
            elements.all(this::contains)
        }
    }

    override fun isEmpty(): Boolean = size == 0

    override fun toLongArray(): LongArray = toLongArray(LongArray(size))
    override fun toLongArray(array: LongArray): LongArray {
        val size = size
        if (size == 0) return array
        val array = if (array.size < size) LongArray(size) else array

        val it = iterator()
        var i = 0
        while (it.hasNext()) {
            array[i++] = it.next().longValue
        }
        return array
    }

    override fun toString(): String = joinToString(", ", "[", "]") { it.toString() }
}

/**
 * Provides a skeletal implementation of the read-only [EdgeSetList] interface.
 */
abstract class AbstractEdgeSetList : EdgeSetList, AbstractList<Edge>() {
    override fun iterator(): EdgeIterator = EdgeListIteratorImpl(0)

    override fun listIterator(): EdgeListIterator = EdgeListIteratorImpl(0)
    override fun listIterator(index: Int): EdgeListIterator = EdgeListIteratorImpl(index)
    override fun subList(fromIndex: Int, toIndex: Int): EdgeSetList = SubList(this, fromIndex, toIndex)

    override fun toLongArray(): LongArray = LongArray(size) { get(it).longValue }
    override fun toLongArray(array: LongArray): LongArray {
        val size = size
        if (size == 0) return array
        if (array.size < size) return LongArray(size) { get(it).longValue }

        for (i in 0..<size) {
            array[i] = get(i).longValue
        }
        return array
    }

    private class SubList(private val list: EdgeSetList, private val fromIndex: Int, toIndex: Int) :
        AbstractEdgeSetList(), RandomAccess {
        init {
            checkRangeIndexes(fromIndex, toIndex, list.size)
        }

        override val size = toIndex - fromIndex

        override fun get(index: Int): Edge {
            checkElementIndex(index, size)
            return list[fromIndex + index]
        }

        override fun subList(fromIndex: Int, toIndex: Int): EdgeSetList {
            checkRangeIndexes(fromIndex, toIndex, size)
            return SubList(list, this.fromIndex + fromIndex, this.fromIndex + toIndex)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is List<*>) {
            if (size != other.size) {
                return false
            }

            if (other is EdgeSetList) {
                val it = other.iterator()
                for (edge in this) {
                    val otherEdge = it.next()
                    if (edge != otherEdge) {
                        return false
                    }
                }
            } else {
                val it = other.iterator()
                for (edge in this) {
                    val otherElement = it.next()
                    if (edge != otherElement) {
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
        for (edge in this) {
            hashCode = 31 * hashCode + java.lang.Long.hashCode(edge.longValue)
        }
        return hashCode
    }

    private inner class EdgeListIteratorImpl(private var index: Int) : EdgeListIterator {
        init {
            checkPositionIndex(index, size)
        }

        override fun hasNext(): Boolean = index < size
        override fun hasPrevious(): Boolean = index > 0
        override fun nextIndex(): Int = index
        override fun previousIndex(): Int = index - 1

        override fun next(): Edge {
            if (index >= size) throw NoSuchElementException()
            return get(index++)
        }

        override fun previous(): Edge {
            if (index <= 0) throw NoSuchElementException()
            return get(--index)
        }
    }
}

private class SingletonEdgeSet(private val edge: Edge) : AbstractEdgeSetList() {
    override val size: Int
        get() = 1

    override fun get(index: Int): Edge {
        checkElementIndex(index, size)
        return edge
    }

    override fun subList(fromIndex: Int, toIndex: Int): EdgeSetList {
        checkRangeIndexes(fromIndex, toIndex, size)
        return if (fromIndex == toIndex) emptyEdgeSet() else this
    }
}

internal class EdgeIteratorWrapper(private val it: LongIterator) : EdgeIterator {
    override fun hasNext(): Boolean = it.hasNext()
    override fun next(): Edge {
        return Edge(it.nextLong())
    }
}

internal class EdgeSetWrapper(internal val edges: LongSet) : EdgeSet, AbstractEdgeCollection() {
    override val size: Int get() = edges.size

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("contains")
    override fun contains(element: Edge): Boolean = edges.contains(element.longValue)

    override fun iterator(): EdgeIterator = EdgeIteratorWrapper(edges.iterator())

    override fun toLongArray(): LongArray = edges.toLongArray()

    override fun toLongArray(array: LongArray): LongArray = edges.toArray(array)
}
