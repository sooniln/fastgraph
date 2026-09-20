package io.github.sooniln.fastgraph.listeners

import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

internal class VertexChangeListenerManager {
    private val listeners = CopyOnWriteArrayList<WeakReference<VertexChangeListener>>()

    fun register(listener: VertexChangeListener) {
        val ref = WeakReference(listener)

        synchronized(listeners) {
            var index = 0
            var size = listeners.size
            while (index < size) {
                val l = listeners[index].get()
                if (l == null) {
                    val old = listeners.removeAt(--size)
                    if (index != size) {
                        listeners[index] = old
                    }
                } else if (l === listener) {
                    throw IllegalArgumentException("listener already registered: $listener")
                } else {
                    ++index
                }
            }

            listeners.add(ref)
        }
    }

    fun unregister(listener: VertexChangeListener) {
        synchronized(listeners) {
            var index = 0
            var size = listeners.size
            while (index < listeners.size) {
                val l = listeners[index].get()
                if (l == null || l === listener) {
                    val old = listeners.removeAt(--size)
                    if (index != size) {
                        listeners[index] = old
                    }
                } else {
                    ++index
                }
            }
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
