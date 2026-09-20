package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.lang.ref.WeakReference

class ListenerTest {

    private class RecordingVertexListener : VertexChangeListener {
        val added = mutableListOf<Vertex>()
        val removed = mutableListOf<Vertex>()
        val reassigned = mutableListOf<Pair<Vertex, Vertex>>()

        override fun onVertexAdded(vertex: Vertex) {
            added.add(vertex)
        }

        override fun onVertexRemoved(vertex: Vertex) {
            removed.add(vertex)
        }

        override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
            reassigned.add(oldVertex to newVertex)
        }
    }

    private class RecordingEdgeListener : EdgeChangeListener {
        val added = mutableListOf<Edge>()
        val removed = mutableListOf<Edge>()
        val reassigned = mutableListOf<Pair<Edge, Edge>>()

        override fun onEdgeAdded(edge: Edge) {
            added.add(edge)
        }

        override fun onEdgeRemoved(edge: Edge) {
            removed.add(edge)
        }

        override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
            reassigned.add(oldEdge to newEdge)
        }
    }

    companion object {
        @JvmStatic
        fun kinds(): List<Arguments> = MutableGraphContractTest.kinds()
    }

    // --- vertex listeners ---------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun vertexAddedCallback(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val listener = RecordingVertexListener()
        graph.registerVertexChangeListener(listener)

        val v0 = graph.addVertex()
        val v1 = graph.addVertex(2, 2)

        assertThat(listener.added).containsExactly(v0, v1)
        assertThat(listener.removed).isEmpty()
        assertThat(listener.reassigned).isEmpty()
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun vertexRemovedCallbackForLastVertex(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        graph.addVertex()
        val v1 = graph.addVertex()
        val listener = RecordingVertexListener()
        graph.registerVertexChangeListener(listener)

        graph.removeVertex(v1)

        assertThat(listener.added).isEmpty()
        assertThat(listener.removed).containsExactly(v1)
        assertThat(listener.reassigned).isEmpty()
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun vertexReassignedCallbackWhenRemovingNonLastVertex(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val listener = RecordingVertexListener()
        graph.registerVertexChangeListener(listener)

        graph.removeVertex(v0)

        assertThat(listener.removed).isEmpty()
        assertThat(listener.reassigned).hasSize(1)
        val (oldVertex, newVertex) = listener.reassigned.single()
        assertThat(oldVertex).isEqualTo(v2)
        assertThat(newVertex).isNotEqualTo(v2)
        assertThat(graph.vertices).containsExactlyInAnyOrder(newVertex, v1)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun vertexListenerSeesAllIncidentEdgesGoneBeforeRemoval(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        graph.addEdge(v0, v1)
        graph.addEdge(v1, v1)
        if (kind.multiEdge) graph.addEdge(v1, v0)
        val seen = mutableListOf<String>()
        graph.registerVertexChangeListener(object : VertexChangeListener {
            override fun onVertexAdded(vertex: Vertex) {}
            override fun onVertexRemoved(vertex: Vertex) {
                assertThat(graph.vertices.contains(vertex)).isTrue
                assertThat(graph.outDegree(vertex)).isEqualTo(0)
                assertThat(graph.inDegree(vertex)).isEqualTo(0)
                seen += "removed"
            }
            override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) = throw AssertionError()
        })

        graph.removeVertex(v1)

        assertThat(seen).containsExactly("removed")
        assertThat(graph.edges).isEmpty()
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun duplicateVertexListenerRegistrationThrows(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val listener = RecordingVertexListener()
        graph.registerVertexChangeListener(listener)

        assertThrows<IllegalArgumentException> { graph.registerVertexChangeListener(listener) }

        // the failed registration does not double-notify or unregister
        graph.addVertex()
        assertThat(listener.added).hasSize(1)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun unregisterVertexListenerStopsNotifications(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val listener = RecordingVertexListener()
        graph.registerVertexChangeListener(listener)
        graph.unregisterVertexChangeListener(listener)

        graph.addVertex()

        assertThat(listener.added).isEmpty()

        // and the listener can be registered again afterwards
        graph.registerVertexChangeListener(listener)
        graph.addVertex()
        assertThat(listener.added).hasSize(1)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun unregisteringUnregisteredVertexListenerIsNoop(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val listener = RecordingVertexListener()
        val other = RecordingVertexListener()
        graph.registerVertexChangeListener(other)

        graph.unregisterVertexChangeListener(listener)

        graph.addVertex()
        assertThat(other.added).hasSize(1)
    }

    // --- edge listeners -----------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun edgeAddedAndRemovedCallback(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val listener = RecordingEdgeListener()
        graph.registerEdgeChangeListener(listener)

        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)

        assertThat(listener.added).containsExactly(e0, e1)

        // e1 is the last edge, so its removal never re-assigns another edge
        graph.removeEdge(e1)

        assertThat(listener.removed).containsExactly(e1)
        assertThat(listener.reassigned).isEmpty()
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun edgeListenerSeesEdgePresentDuringRemoval(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val seen = mutableListOf<String>()
        graph.registerEdgeChangeListener(object : EdgeChangeListener {
            override fun onEdgeAdded(edge: Edge) {
                assertThat(graph.edges.contains(edge)).isTrue
                seen += "added"
            }
            override fun onEdgeRemoved(edge: Edge) {
                assertThat(graph.edges.contains(edge)).isTrue
                assertThat(graph.edgeSource(edge) == v0 || graph.edgeTarget(edge) == v0).isTrue
                seen += "removed"
            }
            override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) = throw AssertionError()
        })

        graph.removeEdge(e0)
        graph.addEdge(v1, v0)

        assertThat(seen).containsExactly("removed", "added")
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun edgeRemovedCallbackWhenIncidentVertexRemoved(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v1)
        val listener = RecordingEdgeListener()
        graph.registerEdgeChangeListener(listener)

        graph.removeVertex(v1)

        assertThat(listener.removed).containsExactlyInAnyOrder(e0, e1)
        assertThat(listener.reassigned).isEmpty()
        assertThat(graph.edges).isEmpty()
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun edgeCallbacksWhenLastVertexSwapped(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v1, v2)
        val ref = graph.createEdgeReference(e0)
        val listener = RecordingEdgeListener()
        graph.registerEdgeChangeListener(listener)

        graph.removeVertex(v0)

        assertThat(listener.removed).isEmpty()
        assertThat(graph.edges).containsExactly(ref.unstable)
        if (kind.indexEdges || kind.multiEdge) {
            // edge ids are indices, so moving a vertex does not change them
            assertThat(listener.reassigned).isEmpty()
            assertThat(ref.unstable).isEqualTo(e0)
        } else {
            // canonical edge ids encode their endpoints, so the edge of the moved vertex is renamed
            assertThat(listener.reassigned).hasSize(1)
            val (oldEdge, newEdge) = listener.reassigned.single()
            assertThat(oldEdge).isEqualTo(e0)
            assertThat(newEdge).isEqualTo(ref.unstable)
            assertThat(newEdge).isNotEqualTo(e0)
        }
        assertThat(setOf(graph.edgeSource(ref.unstable), graph.edgeTarget(ref.unstable)))
            .containsExactlyInAnyOrderElementsOf(graph.vertices)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun duplicateEdgeListenerRegistrationThrows(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val listener = RecordingEdgeListener()
        graph.registerEdgeChangeListener(listener)

        assertThrows<IllegalArgumentException> { graph.registerEdgeChangeListener(listener) }

        // the failed registration does not double-notify or unregister
        val v0 = graph.addVertex()
        graph.addEdge(v0, v0)
        assertThat(listener.added).hasSize(1)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun unregisterEdgeListenerStopsNotifications(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val listener = RecordingEdgeListener()
        graph.registerEdgeChangeListener(listener)
        graph.unregisterEdgeChangeListener(listener)

        graph.addEdge(v0, v1)

        assertThat(listener.added).isEmpty()

        // and the listener can be registered again afterwards
        graph.registerEdgeChangeListener(listener)
        graph.addEdge(v1, v1)
        assertThat(listener.added).hasSize(1)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun unregisteringUnregisteredEdgeListenerIsNoop(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val v0 = graph.addVertex()
        val listener = RecordingEdgeListener()
        val other = RecordingEdgeListener()
        graph.registerEdgeChangeListener(other)

        graph.unregisterEdgeChangeListener(listener)

        graph.addEdge(v0, v0)
        assertThat(other.added).hasSize(1)
    }

    // --- shared behaviour ---------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun listenersAreIndependentPerGraph(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val first = kind.create(directed)
        val second = kind.create(directed)
        val vertexListener = RecordingVertexListener()
        val edgeListener = RecordingEdgeListener()
        first.registerVertexChangeListener(vertexListener)
        second.registerVertexChangeListener(vertexListener)
        first.registerEdgeChangeListener(edgeListener)
        second.registerEdgeChangeListener(edgeListener)

        val a = first.addVertex()
        val b = second.addVertex()
        first.addEdge(a, a)
        second.unregisterVertexChangeListener(vertexListener)
        second.unregisterEdgeChangeListener(edgeListener)
        second.addVertex()
        second.addEdge(b, b)
        val c = first.addVertex()

        assertThat(vertexListener.added).containsExactly(a, b, c)
        assertThat(edgeListener.added).hasSize(1)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun listenerExceptionsPropagate(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val v0 = graph.addVertex()
        graph.registerVertexChangeListener(object : VertexChangeListener {
            override fun onVertexAdded(vertex: Vertex) = throw IllegalStateException("vertex")
            override fun onVertexRemoved(vertex: Vertex) {}
            override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {}
        })
        graph.registerEdgeChangeListener(object : EdgeChangeListener {
            override fun onEdgeAdded(edge: Edge) = throw IllegalStateException("edge")
            override fun onEdgeRemoved(edge: Edge) {}
            override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {}
        })

        // listeners run after the mutation, so the mutation itself has already happened
        assertThat(assertThrows<IllegalStateException> { graph.addVertex() }).hasMessage("vertex")
        assertThat(graph.vertices).hasSize(2)
        assertThat(assertThrows<IllegalStateException> { graph.addEdge(v0, v0) }).hasMessage("edge")
        assertThat(graph.edges).hasSize(1)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun listenersAreWeaklyReferenced(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val v0 = graph.addVertex()
        val added = mutableListOf<Any>()
        var vertexListener: VertexChangeListener? = object : VertexChangeListener {
            override fun onVertexAdded(vertex: Vertex) { added.add(vertex) }
            override fun onVertexRemoved(vertex: Vertex) {}
            override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {}
        }
        var edgeListener: EdgeChangeListener? = object : EdgeChangeListener {
            override fun onEdgeAdded(edge: Edge) { added.add(edge) }
            override fun onEdgeRemoved(edge: Edge) {}
            override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {}
        }
        val weakVertexListener = WeakReference(vertexListener)
        val weakEdgeListener = WeakReference(edgeListener)
        graph.registerVertexChangeListener(vertexListener!!)
        graph.registerEdgeChangeListener(edgeListener!!)
        graph.addVertex()
        graph.addEdge(v0, v0)
        assertThat(added).hasSize(2)

        // once nothing else holds the listeners, the graph must let them be collected
        vertexListener = null
        edgeListener = null
        var attempts = 0
        while ((weakVertexListener.get() != null || weakEdgeListener.get() != null) && attempts++ < 50) {
            System.gc()
            Thread.sleep(10)
        }
        assertThat(weakVertexListener.get()).isNull()
        assertThat(weakEdgeListener.get()).isNull()

        graph.addVertex()
        graph.addEdge(v0, graph.vertices.last())
        assertThat(added).hasSize(2)
    }
}
