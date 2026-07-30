package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.MutableEdgeProperty
import java.lang.ref.WeakReference

internal class MutableGraphEdgePropertiesManager {
    private val properties = ArrayList<WeakReference<MutableGraphEdgeProperty<*>>>()

    /** Registers the given property with this manager. */
    fun <V> registerProperty(property: MutableGraphEdgeProperty<V>): MutableEdgeProperty<V> {
        val ref = WeakReference<MutableGraphEdgeProperty<*>>(property)

        for (i in properties.indices) {
            if (properties[i].get() == null) {
                properties[i] = ref
                return property
            }
        }

        properties.add(ref)
        return property
    }

    fun onEdgeAdded(edge: Edge) = forEach { property -> property.onEdgeAdded(edge) }

    fun onEdgeRemoved(edge: Edge) = forEach { property -> property.onEdgeRemoved(edge) }

    fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        require(newEdge != oldEdge)
        forEach { property -> property.onEdgeReassigned(oldEdge, newEdge) }
    }

    fun ensureCapacity(capacity: Int) = forEach { it.ensureCapacity(capacity) }

    fun trimToSize() = forEach { it.trimToSize() }

    private inline fun forEach(propertyAction: (MutableGraphEdgeProperty<*>) -> Unit) {
        var i = 0
        while (i < properties.size) {
            val property = properties[i].get()
            if (property == null) {
                properties[i] = properties.removeAt(properties.lastIndex)
            } else {
                propertyAction(property)
                ++i
            }
        }
    }
}
