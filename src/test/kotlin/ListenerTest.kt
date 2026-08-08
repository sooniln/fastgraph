package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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

    @Test
    fun vertexAddedCallback() {
        val graph = Graphs.mutableGraph(true)
        val listener = RecordingVertexListener()
        graph.registerVertexChangeListener(listener)

        val v0 = graph.addVertex()
        val v1 = graph.addVertex()

        assertThat(listener.added).containsExactly(v0, v1)
        assertThat(listener.removed).isEmpty()
        assertThat(listener.reassigned).isEmpty()
    }

    @Test
    fun vertexRemovedCallbackForLastVertex() {
        val graph = Graphs.mutableGraph(true)
        graph.addVertex()
        val v1 = graph.addVertex()
        val listener = RecordingVertexListener()
        graph.registerVertexChangeListener(listener)

        graph.removeVertex(v1)

        assertThat(listener.removed).containsExactly(v1)
        assertThat(listener.reassigned).isEmpty()
    }

    @Test
    fun vertexReassignedCallbackWhenRemovingNonLastVertex() {
        val graph = Graphs.mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val listener = RecordingVertexListener()
        graph.registerVertexChangeListener(listener)

        graph.removeVertex(v0)

        assertThat(listener.removed).isEmpty()
        assertThat(listener.reassigned).containsExactly(v2 to v0)
        assertThat(graph.vertices).containsExactlyInAnyOrder(v0, v1)
    }

    @Test
    fun duplicateVertexListenerRegistrationThrows() {
        val graph = Graphs.mutableGraph(true)
        val listener = RecordingVertexListener()
        graph.registerVertexChangeListener(listener)

        assertThrows<IllegalArgumentException> { graph.registerVertexChangeListener(listener) }
    }

    @Test
    fun unregisterVertexListenerStopsNotifications() {
        val graph = Graphs.mutableGraph(true)
        val listener = RecordingVertexListener()
        graph.registerVertexChangeListener(listener)
        graph.unregisterVertexChangeListener(listener)

        graph.addVertex()

        assertThat(listener.added).isEmpty()
    }

    @Test
    fun unregisteringUnregisteredVertexListenerIsNoop() {
        val graph = Graphs.mutableGraph(true)
        val listener = RecordingVertexListener()

        graph.unregisterVertexChangeListener(listener)
    }

    @Test
    fun edgeAddedAndRemovedCallback() {
        val graph = Graphs.mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val listener = RecordingEdgeListener()
        graph.registerEdgeChangeListener(listener)

        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)

        assertThat(listener.added).containsExactly(e0, e1)

        graph.removeEdge(e0)

        assertThat(listener.removed).containsExactly(e0)
    }

    @Test
    fun edgeRemovedCallbackWhenIncidentVertexRemoved() {
        val graph = Graphs.mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val listener = RecordingEdgeListener()
        graph.registerEdgeChangeListener(listener)

        graph.removeVertex(v1)

        assertThat(listener.removed).containsExactly(e0)
        assertThat(listener.reassigned).isEmpty()
    }

    @Test
    fun edgeReassignedCallbackWhenLastVertexSwapped() {
        val graph = Graphs.mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v1, v2)
        val listener = RecordingEdgeListener()
        graph.registerEdgeChangeListener(listener)

        graph.removeVertex(v0)

        assertThat(listener.removed).isEmpty()
        assertThat(listener.reassigned).hasSize(1)
        val (oldEdge, newEdge) = listener.reassigned[0]
        assertThat(oldEdge).isEqualTo(e0)
        assertThat(graph.edgeSource(newEdge)).isEqualTo(v1)
        assertThat(graph.edgeTarget(newEdge)).isEqualTo(v0)
    }

    @Test
    fun duplicateEdgeListenerRegistrationThrows() {
        val graph = Graphs.mutableGraph(true)
        val listener = RecordingEdgeListener()
        graph.registerEdgeChangeListener(listener)

        assertThrows<IllegalArgumentException> { graph.registerEdgeChangeListener(listener) }
    }

    @Test
    fun unregisterEdgeListenerStopsNotifications() {
        val graph = Graphs.mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val listener = RecordingEdgeListener()
        graph.registerEdgeChangeListener(listener)
        graph.unregisterEdgeChangeListener(listener)

        graph.addEdge(v0, v1)

        assertThat(listener.added).isEmpty()
    }
}
