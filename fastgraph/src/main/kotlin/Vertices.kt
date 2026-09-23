/**
 * Methods dealing with vertices.
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
 * unstable references. [Graph] instances offer [Graph.createVertexReference] to obtain a stable [io.github.sooniln.fastgraph.references.VertexReference] from
 * an unstable reference. Stable references are guaranteed to never be invalidated, but may be more expensive to
 * maintain than unstable references, and thus should be used sparingly.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
@JvmInline
public value class Vertex(public val id: Int) {
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

/** A read-only collection of vertices. */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface VertexCollection : Collection<Vertex> {
    override fun isEmpty(): Boolean = size == 0

    @JvmName("contains")
    override fun contains(element: Vertex): Boolean {
        for (e in this) {
            if (e == element) return true
        }
        return false
    }

    public fun containsAll(elements: VertexCollection): Boolean {
        for (e in elements) {
            if (!contains(e)) return false
        }
        return true
    }

    override fun containsAll(elements: Collection<Vertex>): Boolean {
        if (elements is VertexCollection) {
            return containsAll(elements)
        }

        for (e in elements) {
            if (!contains(e)) return false
        }
        return true
    }

    override fun iterator(): VertexIterator

    public fun toIntArray(): IntArray {
        val array = IntArray(size)
        var index = 0
        for (element in this) {
            array[index++] = element.id
        }
        return array
    }
}

/** A read-only ordered collection of vertices. */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface VertexSequencedCollection : VertexCollection, RandomAccess {
    @JvmName("get")
    public operator fun get(index: Int): Vertex

    /** Returns the first vertex, or throws [NoSuchElementException] if empty. */
    @JvmName("first")
    public fun first(): Vertex {
        if (isEmpty()) throw NoSuchElementException()
        return get(0)
    }

    /** Returns the last vertex, or throws [NoSuchElementException] if empty. */
    @JvmName("last")
    public fun last(): Vertex {
        if (isEmpty()) throw NoSuchElementException()
        return get(size - 1)
    }

    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = indexOf(element) != -1

    @JvmName("indexOf")
    public fun indexOf(element: Vertex): Int {
        for (index in 0..<size) {
            if (get(index) == element) return index
        }
        return -1
    }

    @JvmName("lastIndexOf")
    public fun lastIndexOf(element: Vertex): Int {
        for (index in size - 1 downTo 0) {
            if (get(index) == element) return index
        }
        return -1
    }

    override fun iterator(): VertexIterator = object : VertexIterator {
        private var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): Vertex {
            if (!hasNext()) throw NoSuchElementException()
            return get(index++)
        }
    }

    override fun toIntArray(): IntArray {
        val array = IntArray(size)
        for (index in 0..<size) {
            array[index] = get(index).id
        }
        return array
    }
}

public val VertexSequencedCollection.lastIndex: Int get() = size - 1

// the following methods shadow the equivalent Iterable<Vertex> methods from the standard library in order to avoid
// Vertex boxing/unboxing, and associated performance penalties. note that clients outside this package must import
// these methods explicitly, otherwise the standard library versions will be used.

/** Returns true if at least one vertex matches the given predicate. */
public inline fun VertexCollection.any(predicate: (Vertex) -> Boolean): Boolean {
    for (vertex in this) {
        if (predicate(vertex)) return true
    }
    return false
}

/** Returns true if all vertices match the given predicate. */
public inline fun VertexCollection.all(predicate: (Vertex) -> Boolean): Boolean {
    for (vertex in this) {
        if (!predicate(vertex)) return false
    }
    return true
}

/** Returns true if no vertices match the given predicate. */
public inline fun VertexCollection.none(predicate: (Vertex) -> Boolean): Boolean {
    for (vertex in this) {
        if (predicate(vertex)) return false
    }
    return true
}

/** Returns the number of vertices matching the given predicate. */
public inline fun VertexCollection.count(predicate: (Vertex) -> Boolean): Int {
    var count = 0
    for (vertex in this) {
        if (predicate(vertex)) ++count
    }
    return count
}

/** Returns the first vertex matching the given predicate. Throws [NoSuchElementException] if no such vertex exists. */
@JvmName("first")
public inline fun VertexCollection.first(predicate: (Vertex) -> Boolean): Vertex {
    for (vertex in this) {
        if (predicate(vertex)) return vertex
    }
    throw NoSuchElementException("No vertex matching the predicate.")
}

/** A read-only set of vertices. */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface VertexSet : VertexCollection, Set<Vertex> {
    override fun isEmpty(): Boolean = super.isEmpty()
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = super.contains(element)
    override fun containsAll(elements: Collection<Vertex>): Boolean = super.containsAll(elements)
}

/** A set of vertices with an iterator that allows for removal. */
public interface MutableVertexSet : VertexSet {
    override fun iterator(): MutableVertexIterator
}

/** A read-only ordered set of vertices. */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface VertexSequencedSet : VertexSequencedCollection, VertexSet {
    override fun isEmpty(): Boolean = super<VertexSequencedCollection>.isEmpty()
    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = super<VertexSequencedCollection>.contains(element)
    override fun containsAll(elements: Collection<Vertex>): Boolean = super<VertexSequencedCollection>.containsAll(elements)
    override fun iterator(): VertexIterator = super.iterator()

    public fun equalsSequenced(other: VertexSequencedSet): Boolean {
        if (size != other.size) return false

        var i = size - 1
        while (i >= 0) {
            if (get(i) != other[i]) return false
            i--
        }
        return true
    }
}

/** An ordered set of vertices with an iterator that allows for removal. */
public interface MutableVertexSequencedSet : VertexSequencedSet, MutableVertexSet {
    override fun iterator(): MutableVertexIterator
}

/**
 * An ordered set of vertices where every vertex is associated with an index in [0, size). The index of a vertex is
 * obtained via [indexOf], and the vertex for an index via [get]. This set MUST iterate vertices in index order. Both
 * [get] and [indexOf] are strongly expected to run in constant time - if they do not this must be extensively
 * documented.
 *
 * If a vertex is removed from the IndexedVertexSet, this implies that the remaining vertices must be re-ordered in
 * order to keep indices in the range [0, size). The most common method of doing so is to assign the last vertex the
 * index of the removed vertex, but this is not guaranteed by this interface, and the actual method is determined by the
 * implementation.
 *
 * See [IdentityIndexedVertexSet] for the stronger guarantee that the index of a vertex is its [Vertex.id].
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface IndexedVertexSet : VertexSequencedSet {
    @JvmName("indexOf")
    abstract override fun indexOf(element: Vertex): Int

    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = indexOf(element) != -1

    @JvmName("lastIndexOf")
    override fun lastIndexOf(element: Vertex): Int = indexOf(element)
}

/** An [IndexedVertexSet] with an iterator that allows for removal. */
public interface MutableIndexedVertexSet : IndexedVertexSet, MutableVertexSequencedSet {
    override fun iterator(): MutableVertexIterator
}

/**
 * An [IndexedVertexSet] where the index of each vertex is its [Vertex.id], i.e. `get(index) == Vertex(index)` and
 * `indexOf(vertex) == vertex.id`.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface IdentityIndexedVertexSet : IndexedVertexSet {
    @JvmName("get")
    override fun get(index: Int): Vertex {
        if (index !in 0..<size) throw IndexOutOfBoundsException()
        return Vertex(index)
    }

    @JvmName("contains")
    override fun contains(element: Vertex): Boolean = element.id in 0..<size

    @JvmName("indexOf")
    override fun indexOf(element: Vertex): Int = if (element.id in 0..<size) element.id else -1
}

/** An [IdentityIndexedVertexSet] with an iterator that allows for removal. */
public interface MutableIdentityIndexedVertexSet : IdentityIndexedVertexSet, MutableIndexedVertexSet {
    override fun iterator(): MutableVertexIterator
}

// KT-33565: suppression and generics can be removed once fixed
@Suppress("FINAL_UPPER_BOUND")
public fun <T : Vertex> vertexSetOf(vararg vertices: T): VertexSet {
    return if (vertices.isEmpty()) {
        emptyVertexSet()
    } else if (vertices.size == 1) {
        SingletonVertexSet(vertices[0])
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

@JvmName("vertexIteratorOf")
public fun vertexIteratorOf(vertex: Vertex): VertexIterator = SingletonVertexIterator(vertex)

private class SingletonVertexIterator(private val vertex: Vertex) : VertexIterator {
    private var done = false

    override fun hasNext(): Boolean = !done
    override fun next(): Vertex {
        if (done) throw NoSuchElementException()
        done = true
        return vertex
    }
}

public fun emptyVertexSet(): IdentityIndexedVertexSet = EmptyVertexSet

private object EmptyVertexSet : IdentityIndexedVertexSet, MutableIndexedVertexSet, AbstractVertexSet() {
    override val size: Int get() = 0
    override fun get(index: Int): Vertex = throw IndexOutOfBoundsException()
    override fun contains(element: Vertex): Boolean = false
    override fun indexOf(element: Vertex): Int = -1
    override fun iterator(): MutableVertexIterator = emptyVertexIterator()
}

/** Provides a skeletal implementation of the [VertexSequencedCollection] interface. */
public abstract class AbstractVertexSequencedCollection : VertexSequencedCollection {
    override fun equals(other: Any?): Boolean {
        if (other !is VertexSequencedCollection) return false
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

/** Provides a skeletal implementation of the [VertexSet] interface. */
public abstract class AbstractVertexSet : VertexSet {
    abstract override fun iterator(): VertexIterator

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

/** Provides a skeletal implementation of the [VertexSequencedSet] interface. */
public abstract class AbstractVertexSequencedSet : VertexSequencedSet, AbstractVertexSet() {
    override fun iterator(): VertexIterator = super.iterator()
}

private class SingletonVertexSet(private val vertex: Vertex) : AbstractVertexSet() {
    override val size: Int get() = 1
    override fun contains(element: Vertex): Boolean = element.id == vertex.id
    override fun iterator(): VertexIterator = vertexIteratorOf(vertex)
    override fun toIntArray(): IntArray = IntArray(1) { vertex.id }
}

internal fun IntIterator.asVertexIterator(): VertexIterator = VertexIteratorWrapper(this)

private class VertexIteratorWrapper(private val it: IntIterator) : VertexIterator {
    override fun hasNext(): Boolean = it.hasNext()
    override fun next(): Vertex = Vertex(it.nextInt())
}

internal fun IntArray.asSequencedVertexSet(): VertexSequencedSet = ArraySequencedVertexSet(this)

private class ArraySequencedVertexSet(private val vertices: IntArray) : VertexSequencedSet, AbstractVertexSet() {
    override val size: Int get() = vertices.size
    override fun get(index: Int): Vertex = Vertex(vertices[index])
    override fun contains(element: Vertex): Boolean = vertices.contains(element.id)
    override fun iterator(): VertexIterator = vertices.iterator().asVertexIterator()
    override fun toIntArray(): IntArray = vertices.copyOf()
}

internal fun IntSet.asVertexSet(): VertexSet = VertexSetWrapper(this)

private class VertexSetWrapper(private val vertices: IntSet) : AbstractVertexSet() {
    override val size: Int get() = vertices.size
    override fun contains(element: Vertex): Boolean = vertices.contains(element.id)
    override fun iterator(): VertexIterator = vertices.iterator().asVertexIterator()
    override fun toIntArray(): IntArray = vertices.copyInto(IntArray(vertices.size))
}
