package io.github.sooniln.fastgraph.listeners

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import java.lang.ref.WeakReference

internal class EdgeChangeListenerManager {
    private val listeners = ArrayList<WeakReference<EdgeChangeListener>>()

    fun register(listener: EdgeChangeListener) {
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
        }
    }

    fun unregister(listener: EdgeChangeListener) {
        synchronized(listeners) {
            var index = 0
            var size = listeners.size
            while (index < listeners.size) {
                val l = listeners[index].get()
                if (l == null || l == listener) {
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

    fun notifyEdgeAdded(edge: Edge) = forEach { it.onEdgeAdded(edge) }

    fun notifyEdgeRemoved(edge: Edge) = forEach { it.onEdgeRemoved(edge) }

    fun notifyEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        require(newEdge != oldEdge)
        forEach { it.onEdgeReassigned(oldEdge, newEdge) }
    }

    fun notifyEnsureCapacity(edgeCapacity: Int) = forEach { it.ensureEdgeCapacity(edgeCapacity) }

    fun notifyTrimToSize() {
        synchronized(listeners) {
            listeners.trimToSize()
            forEach { it.trimToSize() }
        }
    }

    private inline fun forEach(propertyAction: (EdgeChangeListener) -> Unit) {
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
