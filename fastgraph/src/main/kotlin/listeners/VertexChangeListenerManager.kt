package io.github.sooniln.fastgraph.listeners

import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate

internal class VertexChangeListenerManager {
    private val listeners = CopyOnWriteArrayList<WeakReference<VertexChangeListener>>()

    fun register(listener: VertexChangeListener) {
        val ref = WeakReference(listener)

        synchronized(listeners) {
            listeners.removeIf(Predicate {
                val l = it.get()
                if (l === listener) {
                    throw IllegalArgumentException("listener already registered: $listener")
                }
                return@Predicate l == null
            })
            listeners.add(ref)
        }
    }

    fun unregister(listener: VertexChangeListener) {
        synchronized(listeners) {
            listeners.removeIf(Predicate {
                val l = it.get()
                return@Predicate l == null || l === listener
            })
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
