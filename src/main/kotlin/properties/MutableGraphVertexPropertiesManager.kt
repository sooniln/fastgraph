package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.Vertex
import java.lang.ref.WeakReference

internal class MutableGraphVertexPropertiesManager {
    private val properties = ArrayList<WeakReference<MutableGraphVertexProperty<*>>>()

    /** Registers the given property with this manager. */
    fun <V> registerProperty(property: MutableGraphVertexProperty<V>): MutableVertexProperty<V> {
        val ref = WeakReference<MutableGraphVertexProperty<*>>(property)

        for (i in properties.indices) {
            if (properties[i].get() == null) {
                properties[i] = ref
                return property
            }
        }

        properties.add(ref)
        return property
    }

    fun onVertexAdded(vertex: Vertex) = forEach { property -> property.onVertexAdded(vertex) }

    fun onVertexRemoved(vertex: Vertex) = forEach { property -> property.onVertexRemoved(vertex) }

    fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        require(newVertex != oldVertex)
        forEach { property -> property.onVertexReassigned(oldVertex, newVertex) }
    }

    fun ensureCapacity(capacity: Int) = forEach { it.ensureCapacity(capacity) }

    fun trimToSize() = forEach { it.trimToSize() }

    private inline fun forEach(propertyAction: (MutableGraphVertexProperty<*>) -> Unit) {
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
