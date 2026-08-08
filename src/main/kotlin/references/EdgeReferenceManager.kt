package io.github.sooniln.fastgraph.references

import io.github.sooniln.fastcollect.longs.Long2AnyHashMap
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.Graph
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

internal class EdgeReferenceManager(private val graph: Graph) : EdgeChangeListener {

    private val refs = Long2AnyHashMap<EdgeWeakReference>()
    private val refQueue = ReferenceQueue<EdgeReferenceImpl>()

    private fun cleanup() {
        var removable = refQueue.poll() as EdgeWeakReference?
        while (removable != null) {
            refs.remove(removable.id)
            removable = refQueue.poll() as EdgeWeakReference?
        }

        if (refs.isEmpty()) {
            graph.unregisterEdgeChangeListener(this)
        }
    }

    fun getReference(edge: Edge): EdgeReference {
        if (refs.isEmpty()) {
            graph.registerEdgeChangeListener(this)
        }

        var ref = refs[edge.id]?.get()
        if (ref == null) {
            ref = EdgeReferenceImpl(edge)
            refs.put(edge.id, EdgeWeakReference(ref, refQueue))
        }

        cleanup()
        return ref
    }

    override fun onEdgeAdded(edge: Edge) {}

    override fun onEdgeRemoved(edge: Edge) {
        cleanup()
        refs.remove(edge.id)?.get()?.invalidate()
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        cleanup()

        val reference: EdgeReferenceImpl?
        val weakReference = refs.remove(oldEdge.id).also { reference = it?.get() }
        if (reference != null && weakReference != null) {
            reference.unstable = newEdge
            weakReference.id = newEdge.id
            refs.put(newEdge.id, weakReference)?.get()?.invalidate()
        } else {
            refs.remove(newEdge.id)?.get()?.invalidate()
        }
    }

    override fun trimToSize() {
        refs.trimToSize()
    }

    private class EdgeWeakReference(
        ref: EdgeReferenceImpl,
        queue: ReferenceQueue<EdgeReferenceImpl>
    ) : WeakReference<EdgeReferenceImpl>(ref, queue) {
        // store the edge id so that it can be cleaned up from the map later
        var id: Long = ref.unstable.id
    }

    private class EdgeReferenceImpl(edge: Edge) : EdgeReference {
        private var valid: Boolean = true

        @Suppress("INAPPLICABLE_JVM_NAME")
        @get:JvmName("unstable")
        override var unstable: Edge = edge
            get() {
                require(valid) { "the edge referenced has been removed and is no longer valid" }
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
            if (other is EdgeReferenceImpl) {
                return valid && other.valid && unstable == other.unstable
            }
            return false
        }

        override fun hashCode(): Int = unstable.hashCode()
    }
}
