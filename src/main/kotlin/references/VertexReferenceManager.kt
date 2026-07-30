package io.github.sooniln.fastgraph.references

import io.github.sooniln.fastcollect.ints.Int2AnyHashMap
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexReference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

internal class VertexReferenceManager {
    private val refs = Int2AnyHashMap<VertexWeakReference>()
    private val refQueue = ReferenceQueue<VertexReferenceImpl>()

    private fun cleanup() {
        var removable = refQueue.poll() as VertexWeakReference?
        while (removable != null) {
            refs.remove(removable.intValue)
            removable = refQueue.poll() as VertexWeakReference?
        }
    }

    /** Returns a stable reference to the given vertex. */
    fun getReference(vertex: Vertex): VertexReference {
        var ref = refs[vertex.intValue]?.get()
        if (ref == null) {
            ref = VertexReferenceImpl(vertex)
            refs.put(vertex.intValue, VertexWeakReference(ref, refQueue))
        }

        cleanup()
        return ref
    }

    /** Invoked before the vertex is removed from the graph. */
    fun onVertexRemoved(vertex: Vertex) {
        cleanup()
        refs.remove(vertex.intValue)?.get()?.invalidate()
    }

    /**
     * Invoked before a vertex ID is re-assigned. May not be invoked with [oldVertex] == [newVertex]. The effect of
     * this method should be the same as if: (1) any references pointing at the new vertex are invalidated (2) any
     * references pointing at the old vertex are updated to point at the new vertex (3) no references should point at
     * the old vertex after completion.
     */
    fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        check(oldVertex != newVertex)

        cleanup()

        val reference: VertexReferenceImpl?
        val weakReference = refs.remove(oldVertex.intValue).also { reference = it?.get() }

        if (weakReference != null && reference != null) {
            reference.unstable = newVertex
            weakReference.intValue = newVertex.intValue
            refs.put(newVertex.intValue, weakReference)?.get()?.invalidate()
        } else {
            refs.remove(newVertex.intValue)?.get()?.invalidate()
        }
    }

    private class VertexWeakReference(
        ref: VertexReferenceImpl,
        queue: ReferenceQueue<VertexReferenceImpl>
    ) : WeakReference<VertexReferenceImpl>(ref, queue) {
        // store the vertex id so that it can be cleaned up from the map later
        var intValue: Int = ref.unstable.intValue
    }

    private class VertexReferenceImpl(vertex: Vertex) : VertexReference {
        private var valid: Boolean = true

        @Suppress("INAPPLICABLE_JVM_NAME")
        @get:JvmName("unstable")
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
