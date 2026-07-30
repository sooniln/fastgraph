package io.github.sooniln.fastgraph.references

import io.github.sooniln.fastcollect.longs.Long2AnyHashMap
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeReference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

internal class EdgeReferenceManager {
    private val refs = Long2AnyHashMap<EdgeWeakReference>()
    private val refQueue = ReferenceQueue<EdgeReferenceImpl>()

    private fun cleanup() {
        var removable = refQueue.poll() as EdgeWeakReference?
        while (removable != null) {
            refs.remove(removable.longValue)
            removable = refQueue.poll() as EdgeWeakReference?
        }
    }

    /** Returns a stable reference to the given edge. */
    fun getReference(edge: Edge): EdgeReference {
        var ref = refs[edge.longValue]?.get()
        if (ref == null) {
            ref = EdgeReferenceImpl(edge)
            refs.put(edge.longValue, EdgeWeakReference(ref, refQueue))
        }

        cleanup()
        return ref
    }

    /** Invoked before the edge is removed from the graph. */
    fun onEdgeRemoved(edge: Edge) {
        cleanup()
        refs.remove(edge.longValue)?.get()?.invalidate()
    }

    /**
     * Invoked before an edge ID is re-assigned. May not be invoked with [oldEdge] == [newEdge]. The effect of this
     * method should be the same as if: (1) any references pointing at the new edge are invalidated (2) any references
     * pointing at the old edge are updated to point at the new edge (3) no references should point at the old edge
     * after completion.
     */
    fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        check(oldEdge != newEdge)

        cleanup()

        val reference: EdgeReferenceImpl?
        val weakReference = refs.remove(oldEdge.longValue).also { reference = it?.get() }

        if (reference != null && weakReference != null) {
            reference.unstable = newEdge
            weakReference.longValue = newEdge.longValue
            refs.put(newEdge.longValue, weakReference)?.get()?.invalidate()
        } else {
            refs.remove(newEdge.longValue)?.get()?.invalidate()
        }
    }

    private class EdgeWeakReference(
        ref: EdgeReferenceImpl,
        queue: ReferenceQueue<EdgeReferenceImpl>
    ) : WeakReference<EdgeReferenceImpl>(ref, queue) {
        // store the edge id so that it can be cleaned up from the map later
        var longValue: Long = ref.unstable.longValue
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
