package io.github.sooniln.fastgraph.util

import io.github.sooniln.fastcollect.IntArrayDeque
import io.github.sooniln.fastgraph.AbstractVertexSequencedCollection
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexCollection

@Suppress("INAPPLICABLE_JVM_NAME")
public class VertexArrayDeque(capacity: Int) : AbstractVertexSequencedCollection() {

    public constructor(elements: VertexCollection) : this(elements.size) {
        addAll(elements)
    }

    private val deque = IntArrayDeque(capacity)

    override val size: Int get() = deque.size

    public fun ensureCapacity(capacity: Int): Unit = deque.ensureCapacity(capacity)
    public fun trimToSize(): Unit = deque.trimToSize()

    @JvmName("get")
    override fun get(index: Int): Vertex = Vertex(deque[index])
    @JvmName("set")
    public fun set(index: Int, element: Vertex) { deque[index] = element.id }

    @JvmName("addFirst")
    public fun addFirst(element: Vertex): Unit = deque.addFirst(element.id)
    @JvmName("addLast")
    public fun addLast(element: Vertex): Unit = deque.addLast(element.id)

    @JvmName("add")
    public fun add(index: Int, element: Vertex): Unit = deque.add(index, element.id)

    @JvmName("removeFirst")
    public fun removeFirst(): Vertex = Vertex(deque.removeFirst())
    @JvmName("removeLast")
    public fun removeLast(): Vertex = Vertex(deque.removeLast())
    @JvmName("removeAt")
    public fun removeAt(index: Int): Vertex = Vertex(deque.removeAt(index))

    public fun removeRange(fromIndex: Int, toIndex: Int): Unit = deque.removeRange(fromIndex, toIndex)

    public fun clear(): Unit = deque.clear()

    @JvmName("indexOf")
    public override fun indexOf(element: Vertex): Int = deque.indexOf(element.id)
    @JvmName("lastIndexOf")
    public override fun lastIndexOf(element: Vertex): Int = deque.lastIndexOf(element.id)

    public fun addAll(elements: VertexCollection): Boolean {
        if (elements is VertexArrayDeque) return deque.addAll(elements.deque)
        if (elements.isEmpty()) return false

        deque.ensureCapacity(size + elements.size)
        for (element in elements) {
            deque.addLast(element.id)
        }
        return true
    }

    public fun addAll(elements: Collection<Vertex>): Boolean {
        if (elements.isEmpty()) return false

        deque.ensureCapacity(size + elements.size)
        for (element in elements) {
            deque.addLast(element.id)
        }
        return true
    }

    override fun toIntArray(): IntArray {
        return deque.copyInto(IntArray(size))
    }
}
