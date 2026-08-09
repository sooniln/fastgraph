package io.github.sooniln.fastgraph.references

import io.github.sooniln.fastcollect.Int2AnyHashMap
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexReference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

internal class VertexReferenceManager(private val graph: Graph) : VertexChangeListener {

    private val refs = Int2AnyHashMap<VertexWeakReference>()
    private val refQueue = ReferenceQueue<VertexReferenceImpl>()

    private fun cleanup() {
        var removable = refQueue.poll() as VertexWeakReference?
        while (removable != null) {
            refs.remove(removable.id)
            removable = refQueue.poll() as VertexWeakReference?
        }

        if (refs.isEmpty()) {
            graph.unregisterVertexChangeListener(this)
        }
    }

    fun getReference(vertex: Vertex): VertexReference {
        if (refs.isEmpty()) {
            graph.registerVertexChangeListener(this)
        }

        var ref = refs[vertex.id]?.get()
        if (ref == null) {
            ref = VertexReferenceImpl(vertex)
            refs.put(vertex.id, VertexWeakReference(ref, refQueue))
        }

        cleanup()
        return ref
    }

    override fun onVertexAdded(vertex: Vertex) {}

    override fun onVertexRemoved(vertex: Vertex) {
        cleanup()
        refs.remove(vertex.id)?.get()?.invalidate()
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        cleanup()

        val reference: VertexReferenceImpl?
        val weakReference = refs.remove(oldVertex.id).also { reference = it?.get() }
        if (reference != null && weakReference != null) {
            reference.unstable = newVertex
            weakReference.id = newVertex.id
            refs.put(newVertex.id, weakReference)?.get()?.invalidate()
        } else {
            refs.remove(newVertex.id)?.get()?.invalidate()
        }
    }

    override fun trimToSize() {
        refs.trimToSize()
    }

    private class VertexWeakReference(
        ref: VertexReferenceImpl,
        queue: ReferenceQueue<VertexReferenceImpl>
    ) : WeakReference<VertexReferenceImpl>(ref, queue) {
        // store the vertex id so that it can be cleaned up from the map later
        var id: Int = ref.unstable.id
    }

    private class VertexReferenceImpl(vertex: Vertex) : VertexReference {
        private var valid: Boolean = true

        override var unstable: Vertex = vertex
            get() {
                require(valid) { "the vertex referenced has been removed and is no longer valid" }
                return field
            }
            set(value) {
                check(valid)
                field = value
            }

        fun invalidate() {
            valid = false
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other is VertexReferenceImpl) {
                return valid && other.valid && unstable == other.unstable
            }
            return false
        }

        override fun hashCode(): Int = unstable.hashCode()
    }
}
