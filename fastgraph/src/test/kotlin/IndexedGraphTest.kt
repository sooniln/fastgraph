package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class IndexedGraphTest {

    private lateinit var graph: Graph
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var e0: Edge = Edge(-1)
    private var e1: Edge = Edge(-1)

    private fun constructGraph(immutable: Boolean, indexEdges: Boolean) {
        graph = if (immutable) {
            buildImmutableGraph(true, indexEdges = indexEdges) {
                v0 = addVertex()
                v1 = addVertex()
                v2 = addVertex()
                e0 = addEdge(v0, v1)
                e1 = addEdge(v1, v2)
            }
        } else {
            buildGraph(true, indexEdges = indexEdges) {
                v0 = addVertex()
                v1 = addVertex()
                v2 = addVertex()
                e0 = addEdge(v0, v1)
                e1 = addEdge(v1, v2)
            }
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun graphIsIndexedVertexGraph(immutable: Boolean) {
        constructGraph(immutable, indexEdges = false)

        assertThat(graph).isInstanceOf(IndexedVertexGraph::class.java)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun vertexSetGetAndIndexOf(immutable: Boolean) {
        constructGraph(immutable, indexEdges = false)

        val vertices = graph.vertices as IndexedVertexSet

        assertThat(vertices[0]).isEqualTo(v0)
        assertThat(vertices[1]).isEqualTo(v1)
        assertThat(vertices[2]).isEqualTo(v2)
        assertThat(vertices.indexOf(v0)).isEqualTo(0)
        assertThat(vertices.indexOf(v1)).isEqualTo(1)
        assertThat(vertices.indexOf(v2)).isEqualTo(2)

        assertThrows<IndexOutOfBoundsException> { vertices[3] }
        assertThrows<IndexOutOfBoundsException> { vertices[-1] }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun graphWithoutIndexEdgesIsNotIndexedEdgeGraph(immutable: Boolean) {
        constructGraph(immutable, indexEdges = false)

        assertThat(graph).isNotInstanceOf(IndexedEdgeGraph::class.java)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun graphWithIndexEdgesIsIndexedEdgeGraph(immutable: Boolean) {
        constructGraph(immutable, indexEdges = true)

        assertThat(graph).isInstanceOf(IndexedEdgeGraph::class.java)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeSetGetAndIndexOf(immutable: Boolean) {
        constructGraph(immutable, indexEdges = true)

        val edges = graph.edges as IndexedEdgeSet

        assertThat(edges[0]).isEqualTo(e0)
        assertThat(edges[1]).isEqualTo(e1)
        assertThat(edges.indexOf(e0)).isEqualTo(0)
        assertThat(edges.indexOf(e1)).isEqualTo(1)

        assertThrows<IndexOutOfBoundsException> { edges[2] }
        assertThrows<IndexOutOfBoundsException> { edges[-1] }
    }

    @Test
    fun vertexIndexIsReassignedWhenNonLastVertexRemoved() {
        constructGraph(immutable = false, indexEdges = false)
        val mutableGraph = graph as MutableGraph

        // v0 is removed, so v2 (the last vertex) is reassigned to v0's freed index
        mutableGraph.removeVertex(v0)

        val vertices = graph.vertices as IndexedVertexSet
        assertThat(graph.vertices).containsExactlyInAnyOrder(v0, v1)
        assertThat(vertices.indexOf(v0)).isEqualTo(0)
        assertThat(vertices.indexOf(v1)).isEqualTo(1)
        assertThat(vertices[0]).isEqualTo(v0)
        assertThat(vertices[1]).isEqualTo(v1)
    }

    @Test
    fun edgeIndexIsReassignedWhenNonLastEdgeRemoved() {
        constructGraph(immutable = false, indexEdges = true)
        val mutableGraph = graph as MutableGraph

        var reassignedTo: Edge? = null
        mutableGraph.registerEdgeChangeListener(object : EdgeChangeListener {
            override fun onEdgeAdded(edge: Edge) {}
            override fun onEdgeRemoved(edge: Edge) {}
            override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
                reassignedTo = newEdge
            }
        })

        // e0 is removed, so e1 (the last edge) is reassigned to e0's freed index
        mutableGraph.removeEdge(e0)

        val edges = graph.edges as IndexedEdgeSet
        assertThat(reassignedTo).isNotNull
        assertThat(edges.indexOf(reassignedTo!!)).isEqualTo(0)
        assertThat(edges[0]).isEqualTo(reassignedTo)
    }

    @ParameterizedTest(name = "indexEdges={0}")
    @ValueSource(booleans = [true, false])
    fun vertexListenersAreNotifiedBeforeMutation(indexEdges: Boolean) {
        constructGraph(immutable = false, indexEdges = indexEdges)
        val mutableGraph = graph as MutableGraph
        val vertices = graph.vertices as IndexedVertexSet
        val events = ArrayList<String>()

        mutableGraph.registerVertexChangeListener(object : VertexChangeListener {
            override fun onVertexAdded(vertex: Vertex) {}
            override fun onVertexRemoved(vertex: Vertex) {
                // the vertex is still present, at the last index, with no incident edges left
                assertThat(vertices.contains(vertex)).isTrue
                assertThat(vertices.indexOf(vertex)).isEqualTo(vertices.lastIndex)
                assertThat(graph.outDegree(vertex) + graph.inDegree(vertex)).isEqualTo(0)
                events.add("removed")
            }
            override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
                // both vertices are still present; old occupies the last index, new the index being freed
                assertThat(vertices.contains(oldVertex)).isTrue
                assertThat(vertices.contains(newVertex)).isTrue
                assertThat(vertices.indexOf(oldVertex)).isEqualTo(vertices.lastIndex)
                assertThat(vertices.indexOf(newVertex)).isEqualTo(0)
                assertThat(graph.outDegree(newVertex) + graph.inDegree(newVertex)).isEqualTo(0)
                events.add("reassigned")
            }
        })

        mutableGraph.removeVertex(v0)
        assertThat(events).containsExactly("reassigned")
        assertThat(vertices.size).isEqualTo(2)

        mutableGraph.removeVertex(vertices.last())
        assertThat(events).containsExactly("reassigned", "removed")
        assertThat(vertices.size).isEqualTo(1)
    }

    @Test
    fun edgeListenersAreNotifiedBeforeMutationInIndexedEdgeGraph() {
        constructGraph(immutable = false, indexEdges = true)
        val mutableGraph = graph as MutableGraph
        val edges = graph.edges as IndexedEdgeSet
        val events = ArrayList<String>()

        mutableGraph.registerEdgeChangeListener(object : EdgeChangeListener {
            override fun onEdgeAdded(edge: Edge) {}
            override fun onEdgeRemoved(edge: Edge) {
                assertThat(edges.contains(edge)).isTrue
                assertThat(edges.indexOf(edge)).isEqualTo(edges.size - 1)
                events.add("removed")
            }
            override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
                assertThat(edges.contains(oldEdge)).isTrue
                assertThat(edges.contains(newEdge)).isTrue
                assertThat(edges.indexOf(oldEdge)).isEqualTo(edges.size - 1)
                assertThat(edges.indexOf(newEdge)).isEqualTo(0)
                events.add("reassigned")
            }
        })

        mutableGraph.removeEdge(e0)
        assertThat(events).containsExactly("reassigned")
        assertThat(edges.size).isEqualTo(1)

        mutableGraph.removeEdge(edges.first())
        assertThat(events).containsExactly("reassigned", "removed")
        assertThat(edges.size).isEqualTo(0)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun canonicalEdgeReassignmentsAreNotifiedBeforeVertexMutation(directed: Boolean) {
        // last vertex (v2) has a self-loop, an edge from v1 and (if directed) an edge to v0. removing v0 moves v2 into
        // v0's index, which renames all of v2's edges - every rename must be reported while the graph is untouched,
        // and before the vertex re-assignment itself.
        val mutableGraph = mutableGraph(directed)
        val v0 = mutableGraph.addVertex()
        val v1 = mutableGraph.addVertex()
        val v2 = mutableGraph.addVertex()
        val e12 = mutableGraph.addEdge(v1, v2)
        val e22 = mutableGraph.addEdge(v2, v2)
        val e20 = if (directed) mutableGraph.addEdge(v2, v0) else null
        val expectedRenames = mutableSetOf(e12, e22)
        val events = ArrayList<String>()

        mutableGraph.registerEdgeChangeListener(object : EdgeChangeListener {
            override fun onEdgeAdded(edge: Edge) {}
            override fun onEdgeRemoved(edge: Edge) {
                assertThat(mutableGraph.edges.contains(edge)).isTrue
                events.add("removed $edge")
            }
            override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
                assertThat(mutableGraph.edges.contains(oldEdge)).isTrue
                assertThat(mutableGraph.vertices.size).isEqualTo(3)
                assertThat(expectedRenames.remove(oldEdge)).isTrue
                assertThat(mutableGraph.edgeSource(oldEdge) == v2 || mutableGraph.edgeTarget(oldEdge) == v2).isTrue
                events.add("reassigned")
            }
        })
        mutableGraph.registerVertexChangeListener(object : VertexChangeListener {
            override fun onVertexAdded(vertex: Vertex) {}
            override fun onVertexRemoved(vertex: Vertex) = throw AssertionError()
            override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
                assertThat(expectedRenames).isEmpty()
                assertThat(oldVertex).isEqualTo(v2)
                assertThat(newVertex).isEqualTo(v0)
                assertThat(mutableGraph.vertices.size).isEqualTo(3)
                events.add("vertex reassigned")
            }
        })

        mutableGraph.removeVertex(v0)

        val expectedEvents = ArrayList<String>()
        if (e20 != null) expectedEvents.add("removed $e20")
        expectedEvents.add("reassigned")
        expectedEvents.add("reassigned")
        expectedEvents.add("vertex reassigned")
        assertThat(events).isEqualTo(expectedEvents)
        assertThat(mutableGraph.vertices.size).isEqualTo(2)
        assertThat(mutableGraph.edges.size).isEqualTo(2)
        // v2 now lives at v0's old index, and its edges have been renamed accordingly
        assertThat(mutableGraph.hasEdge(v1, v0)).isTrue
        assertThat(mutableGraph.hasEdge(v0, v0)).isTrue
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun indicesAndLastIndexExtensionProperties(immutable: Boolean) {
        constructGraph(immutable, indexEdges = false)

        val vertices = graph.vertices as IndexedVertexSet

        assertThat(vertices.lastIndex).isEqualTo(2)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun setEqualityIsSymmetric(immutable: Boolean) {
        constructGraph(immutable, indexEdges = true)

        val vertices = vertexSetOf(v2, v0, v1)
        assertThat(graph.vertices).isEqualTo(vertices)
        assertThat(vertices).isEqualTo(graph.vertices)
        assertThat(graph.vertices.hashCode()).isEqualTo(vertices.hashCode())

        val edges = edgeSetOf(e1, e0)
        assertThat(graph.edges).isEqualTo(edges)
        assertThat(edges).isEqualTo(graph.edges)
        assertThat(graph.edges.hashCode()).isEqualTo(edges.hashCode())
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun firstAndLast(immutable: Boolean) {
        constructGraph(immutable, indexEdges = true)

        val vertices = graph.vertices as IndexedVertexSet
        assertThat(vertices.first()).isEqualTo(v0)
        assertThat(vertices.last()).isEqualTo(v2)

        val edges = graph.edges as IndexedEdgeSet
        assertThat(edges.first()).isEqualTo(e0)
        assertThat(edges.last()).isEqualTo(e1)

        assertThrows<NoSuchElementException> { emptyVertexSet().first() }
        assertThrows<NoSuchElementException> { emptyVertexSet().last() }
        assertThrows<NoSuchElementException> { emptyEdgeSet().first() }
        assertThrows<NoSuchElementException> { emptyEdgeSet().last() }
    }
}
