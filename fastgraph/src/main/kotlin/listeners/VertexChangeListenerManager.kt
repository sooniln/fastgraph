package io.github.sooniln.fastgraph.listeners

import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import java.lang.ref.WeakReference

internal class VertexChangeListenerManager {
    private val listeners = ArrayList<WeakReference<VertexChangeListener>>()

    /** Returns true if this is the first listener added. */
    fun register(listener: VertexChangeListener): Boolean {
        val ref = WeakReference(listener)

        synchronized(listeners) {
            var index = 0
            var size = listeners.size
            while (index < size) {
                val l = listeners[index].get()
                when (l) {
                    null -> {
                        val old = listeners.removeAt(--size)
                        if (index != size) {
                            listeners[index] = old
                        }
                    }
                    listener -> throw IllegalArgumentException("listener already registered: $listener")
                    else -> ++index
                }
            }

            listeners.add(ref)
            return listeners.size == 1
        }
    }

    /** Returns true if the last listener was removed. */
    fun unregister(listener: VertexChangeListener): Boolean {
        var removed = false
        synchronized(listeners) {
            var index = 0
            var size = listeners.size
            while (index < listeners.size) {
                val l = listeners[index].get()
                if (l == null || l == listener) {
                    val old = listeners.removeAt(--size)
                    removed = true
                    if (index != size) {
                        listeners[index] = old
                    }
                } else {
                    ++index
                }
            }

            return removed && listeners.isEmpty()
        }
    }

    fun notifyVertexAdded(vertex: Vertex) = forEach { it.onVertexAdded(vertex) }

    fun notifyVertexRemoved(vertex: Vertex) = forEach { it.onVertexRemoved(vertex) }

    fun notifyVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        require(newVertex != oldVertex)
        forEach { listener -> listener.onVertexReassigned(oldVertex, newVertex) }
    }

    fun notifyEnsureCapacity(vertexCapacity: Int) = forEach { it.ensureVertexCapacity(vertexCapacity) }

    fun notifyTrimToSize() {
        synchronized(listeners) {
            listeners.trimToSize()
            forEach { it.trimToSize() }
        }
    }

    private inline fun forEach(propertyAction: (VertexChangeListener) -> Unit) {
        synchronized(listeners) {
            var index = 0
            var size = listeners.size
            while (index < size) {
                val listener = listeners[index].get()
                if (listener == null) {
                    val old = listeners.removeAt(--size)
                    if (index != size) {
                        listeners[index] = old
                    }
                } else {
                    propertyAction(listener)
                    ++index
                }
            }
        }
    }
}
