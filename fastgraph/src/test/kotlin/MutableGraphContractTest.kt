package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Behaviour shared by every [MutableGraph] returned by [mutableGraph], checked against every option combination.
 */
class MutableGraphContractTest {

    enum class GraphKind(val multiEdge: Boolean, val indexEdges: Boolean) {
        GRAPH(multiEdge = false, indexEdges = false),
        INDEXED_GRAPH(multiEdge = false, indexEdges = true),
        NETWORK(multiEdge = true, indexEdges = false),
        INDEXED_NETWORK(multiEdge = true, indexEdges = true);

        fun create(directed: Boolean): MutableGraph = mutableGraph(directed, multiEdge, indexEdges)
    }

    private class RecordingVertexListener : VertexChangeListener {
        val events = mutableListOf<String>()
        override fun onVertexAdded(vertex: Vertex) { events += "added" }
        override fun onVertexRemoved(vertex: Vertex) { events += "removed" }
        override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) { events += "reassigned" }
        override fun ensureVertexCapacity(vertexCapacity: Int) { events += "capacity $vertexCapacity" }
        override fun trimToSize() { events += "trim" }
    }

    private class RecordingEdgeListener : EdgeChangeListener {
        val events = mutableListOf<String>()
        override fun onEdgeAdded(edge: Edge) { events += "added" }
        override fun onEdgeRemoved(edge: Edge) { events += "removed" }
        override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) { events += "reassigned" }
        override fun ensureEdgeCapacity(edgeCapacity: Int) { events += "capacity $edgeCapacity" }
        override fun trimToSize() { events += "trim" }
    }

    companion object {
        @JvmStatic
        fun kinds(): List<Arguments> = GraphKind.entries.flatMap { kind ->
            listOf(true, false).map { directed -> Arguments.of(kind, directed) }
        }

        @JvmStatic
        fun singleEdgeKinds(): List<Arguments> = kinds().filter { !(it.get()[0] as GraphKind).multiEdge }

        @JvmStatic
        fun multiEdgeKinds(): List<Arguments> = kinds().filter { (it.get()[0] as GraphKind).multiEdge }
    }

    // --- documented marker interfaces --------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun markerInterfaces(kind: GraphKind, directed: Boolean) {
        val graph = kind.create(directed)

        assertThat(graph.directed).isEqualTo(directed)
        assertThat(graph.multiEdge).isEqualTo(kind.multiEdge)
        assertThat(graph).isInstanceOf(IndexedVertexGraph::class.java)
        if (kind.indexEdges) {
            assertThat(graph).isInstanceOf(IndexedEdgeGraph::class.java)
        }
        if (kind == GraphKind.GRAPH) {
            assertThat(graph).isNotInstanceOf(IndexedEdgeGraph::class.java)
        }
    }

    // --- adding edges -------------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("singleEdgeKinds")
    fun duplicateEdgeIsRejected(kind: GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val ab = graph.addEdge(a, b)
        val aa = graph.addEdge(a, a)
        val listener = RecordingEdgeListener()
        graph.registerEdgeChangeListener(listener)

        assertThrows<IllegalArgumentException> { graph.addEdge(a, b) }
        assertThrows<IllegalArgumentException> { graph.addEdge(a, a) }
        if (directed) {
            // the reverse direction is a different edge
            val ba = graph.addEdge(b, a)
            assertThat(graph.edges).containsExactlyInAnyOrder(ab, aa, ba)
            assertThat(listener.events).containsExactly("added")
        } else {
            assertThrows<IllegalArgumentException> { graph.addEdge(b, a) }
            assertThat(graph.edges).containsExactlyInAnyOrder(ab, aa)
            assertThat(listener.events).isEmpty()
        }

        // a rejected add leaves the topology untouched
        assertThat(graph.edges(a, b)).containsExactlyInAnyOrder(ab)
        assertThat(graph.edges(a, a)).containsExactlyInAnyOrder(aa)
        assertThat(graph.outDegree(a)).isEqualTo(2)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("multiEdgeKinds")
    fun parallelEdgesAreDistinct(kind: GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val e0 = graph.addEdge(a, b)
        val e1 = graph.addEdge(a, b)
        val e2 = graph.addEdge(b, a)
        val l0 = graph.addEdge(a, a)
        val l1 = graph.addEdge(a, a)

        assertThat(e0).isNotEqualTo(e1)
        assertThat(l0).isNotEqualTo(l1)
        assertThat(graph.edges).containsExactlyInAnyOrder(e0, e1, e2, l0, l1)
        assertThat(graph.edges(a, a)).containsExactlyInAnyOrder(l0, l1)
        if (directed) {
            assertThat(graph.edges(a, b)).containsExactlyInAnyOrder(e0, e1)
            assertThat(graph.edges(b, a)).containsExactlyInAnyOrder(e2)
            assertThat(graph.outDegree(a)).isEqualTo(4)
            assertThat(graph.inDegree(a)).isEqualTo(3)
        } else {
            assertThat(graph.edges(a, b)).containsExactlyInAnyOrder(e0, e1, e2)
            assertThat(graph.edges(b, a)).containsExactlyInAnyOrder(e0, e1, e2)
            assertThat(graph.outDegree(a)).isEqualTo(5)
            assertThat(graph.inDegree(a)).isEqualTo(5)
        }
        assertThat(graph.successors(a)).containsExactlyInAnyOrder(a, b)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun addEdgeRejectsForeignVertices(kind: GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val a = graph.addVertex()

        assertThrows<IllegalArgumentException> { graph.addEdge(a, Vertex(1)) }
        assertThrows<IllegalArgumentException> { graph.addEdge(Vertex(1), a) }
        assertThrows<IllegalArgumentException> { graph.addEdge(a, Vertex(-1)) }
        assertThat(graph.edges).isEmpty()
    }

    // --- removing -----------------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun removeVertexRejectsForeignVertex(kind: GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val a = graph.addVertex()
        val b = graph.addVertex()
        graph.addEdge(a, b)

        assertThrows<IllegalArgumentException> { graph.removeVertex(Vertex(-1)) }
        assertThrows<IllegalArgumentException> { graph.removeVertex(Vertex(2)) }
        assertThrows<IllegalArgumentException> { graph.removeVertex(Vertex(99)) }
        assertThat(graph.vertices).containsExactlyInAnyOrder(a, b)
        assertThat(graph.edges).hasSize(1)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun removeEdgeRejectsForeignEdge(kind: GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val c = graph.addVertex()
        val ab = graph.addEdge(a, b)

        assertThrows<IllegalArgumentException> { graph.removeEdge(Edge(-1)) }
        assertThrows<IllegalArgumentException> { graph.removeEdge(Edge(99)) }
        if (!kind.multiEdge && !kind.indexEdges) {
            // an edge between vertices that exist, but which was never added
            val never = CanonicalEdge.from(directed, b, c).toEdge()
            assertThat(graph.edges.contains(never)).isFalse
            assertThrows<IllegalArgumentException> { graph.removeEdge(never) }
        }
        assertThat(graph.edges).containsExactlyInAnyOrder(ab)

        graph.removeEdge(ab)
        assertThrows<IllegalArgumentException> { graph.removeEdge(ab) }
        assertThat(graph.edges).isEmpty()
        assertThat(graph.vertices).containsExactlyInAnyOrder(a, b, c)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun isEmptyTransitions(kind: GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        assertThat(graph.isEmpty()).isTrue
        assertThat(graph.vertices.isEmpty()).isTrue
        assertThat(graph.edges.isEmpty()).isTrue

        val a = graph.addVertex()
        assertThat(graph.isEmpty()).isFalse
        assertThat(graph.edges.isEmpty()).isTrue

        val aa = graph.addEdge(a, a)
        assertThat(graph.edges.isEmpty()).isFalse

        graph.removeEdge(aa)
        assertThat(graph.isEmpty()).isFalse
        assertThat(graph.edges.isEmpty()).isTrue

        graph.removeVertex(a)
        assertThat(graph.isEmpty()).isTrue
    }

    // --- live views ---------------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun vertexAndEdgeSetsAreLive(kind: GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val vertices = graph.vertices
        val edges = graph.edges

        val a = graph.addVertex()
        val b = graph.addVertex()
        assertThat(vertices).containsExactlyInAnyOrder(a, b)
        assertThat(edges).isEmpty()

        val ab = graph.addEdge(a, b)
        assertThat(edges).containsExactlyInAnyOrder(ab)
        assertThat(edges.contains(ab)).isTrue

        graph.removeEdge(ab)
        assertThat(edges).isEmpty()
        assertThat(edges.contains(ab)).isFalse

        graph.removeVertex(b)
        assertThat(vertices).containsExactlyInAnyOrder(a)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun adjacencyViewsAreLive(kind: GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val successors = graph.successors(a)
        val predecessors = graph.predecessors(b)
        val outgoing = graph.outgoingEdges(a)
        val incoming = graph.incomingEdges(b)
        val between = graph.edges(a, b)

        assertThat(successors).isEmpty()
        assertThat(predecessors).isEmpty()
        assertThat(outgoing).isEmpty()
        assertThat(incoming).isEmpty()
        assertThat(between).isEmpty()

        val ab = graph.addEdge(a, b)
        assertThat(successors).containsExactlyInAnyOrder(b)
        assertThat(successors.contains(b)).isTrue
        assertThat(predecessors).containsExactlyInAnyOrder(a)
        assertThat(outgoing).containsExactlyInAnyOrder(ab)
        assertThat(outgoing.contains(ab)).isTrue
        assertThat(incoming).containsExactlyInAnyOrder(ab)
        assertThat(between).containsExactlyInAnyOrder(ab)
        assertThat(between.contains(ab)).isTrue

        graph.removeEdge(ab)
        assertThat(successors).isEmpty()
        assertThat(successors.contains(b)).isFalse
        assertThat(predecessors).isEmpty()
        assertThat(outgoing).isEmpty()
        assertThat(outgoing.contains(ab)).isFalse
        assertThat(incoming).isEmpty()
        assertThat(between).isEmpty()
    }

    // --- iterator contracts -------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun vertexIteratorContract(kind: GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val a = graph.addVertex()

        val fresh = graph.vertices.iterator()
        assertThrows<IllegalStateException> { fresh.remove() }

        val iterator = graph.vertices.iterator()
        assertThat(iterator.hasNext()).isTrue
        assertThat(iterator.hasNext()).isTrue
        assertThat(iterator.next()).isEqualTo(a)
        iterator.remove()
        assertThat(graph.vertices).isEmpty()
        assertThrows<IllegalStateException> { iterator.remove() }
        assertThat(iterator.hasNext()).isFalse
        assertThrows<NoSuchElementException> { iterator.next() }
        assertThrows<NoSuchElementException> { graph.vertices.iterator().next() }

        val exhausted = graph.vertices.iterator()
        assertThat(exhausted.hasNext()).isFalse
        assertThat(exhausted.hasNext()).isFalse
        assertThrows<NoSuchElementException> { exhausted.next() }
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun edgeIteratorContract(kind: GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val ab = graph.addEdge(a, b)

        val fresh = graph.edges.iterator()
        assertThrows<IllegalStateException> { fresh.remove() }

        val iterator = graph.edges.iterator()
        assertThat(iterator.hasNext()).isTrue
        assertThat(iterator.hasNext()).isTrue
        assertThat(iterator.next()).isEqualTo(ab)
        // remove() directly after next(): calling hasNext() in between is not supported by every implementation
        iterator.remove()
        assertThat(graph.edges).isEmpty()
        assertThat(graph.vertices).containsExactlyInAnyOrder(a, b)
        assertThat(iterator.hasNext()).isFalse
        assertThrows<NoSuchElementException> { iterator.next() }
        assertThrows<NoSuchElementException> { graph.edges.iterator().next() }

        val exhausted = graph.edges.iterator()
        assertThat(exhausted.hasNext()).isFalse
        assertThat(exhausted.hasNext()).isFalse
        assertThrows<NoSuchElementException> { exhausted.next() }
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun edgesAreIteratedOnce(kind: GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val ab = graph.addEdge(a, b)
        val aa = graph.addEdge(a, a)

        // an edge must not be reported from both of its endpoints
        assertThat(graph.edges.iterator().asSequence().toList()).containsExactlyInAnyOrder(ab, aa)
        assertThat(graph.edges.toLongArray()).containsExactlyInAnyOrder(ab.id, aa.id)
        assertThat(graph.edges.size).isEqualTo(2)
    }

    // --- capacity hints -----------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun capacityHintsAndTrimDoNotChangeTopology(kind: GraphKind, directed: Boolean) {
        val graph = kind.create(directed)
        val a = graph.addVertex(4, 4)
        val b = graph.addVertex(0, 0)
        val ab = graph.addEdge(a, b)
        val names = graph.createVertexProperty<String>()
        val weights = graph.createEdgeProperty<Int>(0)
        names[a] = "a"
        weights[ab] = 7
        val vertexListener = RecordingVertexListener()
        val edgeListener = RecordingEdgeListener()
        graph.registerVertexChangeListener(vertexListener)
        graph.registerEdgeChangeListener(edgeListener)

        graph.ensureVertexCapacity(100)
        graph.ensureEdgeCapacity(100)
        graph.trimToSize()

        assertThat(graph.vertices).containsExactlyInAnyOrder(a, b)
        assertThat(graph.edges).containsExactlyInAnyOrder(ab)
        assertThat(names[a]).isEqualTo("a")
        assertThat(names[b]).isNull()
        assertThat(weights[ab]).isEqualTo(7)
        // the hints are forwarded to listeners so that properties can pre-allocate/trim in step with the graph
        assertThat(vertexListener.events).containsExactly("capacity 100", "trim")
        assertThat(edgeListener.events).containsExactly("capacity 100", "trim")
    }
}
