package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.filtered.filter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class IndexedGraphTest {

    private lateinit var graph: Graph
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var e0: Edge = Edge(-1)
    private var e1: Edge = Edge(-1)

    private fun constructGraph(immutable: Boolean, indexEdges: Boolean, directed: Boolean = true) {
        graph = if (immutable) {
            buildImmutableGraph(directed, indexEdges = indexEdges) {
                v0 = addVertex()
                v1 = addVertex()
                v2 = addVertex()
                e0 = addEdge(v0, v1)
                e1 = addEdge(v1, v2)
            }
        } else {
            buildGraph(directed, indexEdges = indexEdges) {
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
    fun graphHasIndexedVertexSet(immutable: Boolean) {
        constructGraph(immutable, indexEdges = false)

        assertThat(graph.vertices).isInstanceOf(IndexedVertexSet::class.java)
    }

    @ParameterizedTest(name = "immutable={0}, directed={1}")
    @CsvSource("true,true", "true,false", "false,true", "false,false")
    fun vertexSetGetAndIndexOf(immutable: Boolean, directed: Boolean) {
        constructGraph(immutable, indexEdges = false, directed = directed)

        val vertices = graph.vertices as IndexedVertexSet

        assertThat(vertices[0]).isEqualTo(v0)
        assertThat(vertices[1]).isEqualTo(v1)
        assertThat(vertices[2]).isEqualTo(v2)
        assertThat(vertices.indexOf(v0)).isEqualTo(0)
        assertThat(vertices.indexOf(v1)).isEqualTo(1)
        assertThat(vertices.indexOf(v2)).isEqualTo(2)

        assertThat(vertices.indexOf(Vertex(3))).isEqualTo(-1)
        assertThat(vertices.indexOf(Vertex(-1))).isEqualTo(-1)
        assertThat(vertices.lastIndexOf(v1)).isEqualTo(1)
        assertThat(vertices.lastIndexOf(Vertex(3))).isEqualTo(-1)

        assertThrows<IndexOutOfBoundsException> { vertices[3] }
        assertThrows<IndexOutOfBoundsException> { vertices[-1] }

        // vertices iterate in index order
        assertThat(vertices).containsExactly(v0, v1, v2)
        assertThat(vertices.toIntArray()).containsExactly(v0.id, v1.id, v2.id)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun graphWithoutIndexEdgesHasNoIndexedEdgeSet(immutable: Boolean) {
        constructGraph(immutable, indexEdges = false)

        assertThat(graph.edges).isNotInstanceOf(IndexedEdgeSet::class.java)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun graphWithIndexEdgesHasIndexedEdgeSet(immutable: Boolean) {
        constructGraph(immutable, indexEdges = true)

        assertThat(graph.edges).isInstanceOf(IndexedEdgeSet::class.java)
    }

    @ParameterizedTest(name = "immutable={0}, directed={1}")
    @CsvSource("true,true", "true,false", "false,true", "false,false")
    fun edgeSetGetAndIndexOf(immutable: Boolean, directed: Boolean) {
        constructGraph(immutable, indexEdges = true, directed = directed)

        val edges = graph.edges as IndexedEdgeSet

        assertThat(edges[0]).isEqualTo(e0)
        assertThat(edges[1]).isEqualTo(e1)
        assertThat(edges.indexOf(e0)).isEqualTo(0)
        assertThat(edges.indexOf(e1)).isEqualTo(1)

        assertThat(edges.indexOf(Edge(2))).isEqualTo(-1)
        assertThat(edges.indexOf(Edge(-1))).isEqualTo(-1)
        assertThat(edges.lastIndexOf(e1)).isEqualTo(1)
        assertThat(edges.lastIndexOf(Edge(2))).isEqualTo(-1)

        assertThrows<IndexOutOfBoundsException> { edges[2] }
        assertThrows<IndexOutOfBoundsException> { edges[-1] }

        // edges iterate in index order
        assertThat(edges).containsExactly(e0, e1)
        assertThat(edges.toLongArray()).containsExactly(e0.id, e1.id)
    }

    @ParameterizedTest(name = "indexEdges={0}, directed={1}")
    @CsvSource("true,true", "true,false", "false,true", "false,false")
    fun vertexIndexIsReassignedWhenNonLastVertexRemoved(indexEdges: Boolean, directed: Boolean) {
        constructGraph(immutable = false, indexEdges = indexEdges, directed = directed)
        val mutableGraph = graph as MutableGraph
        val v2Ref = graph.createVertexReference(v2)

        var reassignedTo: Vertex? = null
        mutableGraph.registerVertexChangeListener(object : VertexChangeListener {
            override fun onVertexAdded(vertex: Vertex) {}
            override fun onVertexRemoved(vertex: Vertex) {}
            override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
                assertThat(oldVertex).isEqualTo(v2)
                reassignedTo = newVertex
            }
        })

        // v0 is removed, so v2 (the last vertex) is reassigned to v0's freed index
        mutableGraph.removeVertex(v0)

        val vertices = graph.vertices as IndexedVertexSet
        val moved = reassignedTo!!
        assertThat(vertices).containsExactlyInAnyOrder(moved, v1)
        assertThat(vertices.indexOf(moved)).isEqualTo(0)
        assertThat(vertices.indexOf(v1)).isEqualTo(1)
        assertThat(vertices[0]).isEqualTo(moved)
        assertThat(vertices[1]).isEqualTo(v1)
        assertThat(vertices.indexOf(v2)).isEqualTo(-1)
        assertThat(v2Ref.unstable).isEqualTo(moved)
        assertThat(graph.hasEdge(v1, moved)).isTrue
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun edgeIndexIsReassignedWhenNonLastEdgeRemoved(directed: Boolean) {
        constructGraph(immutable = false, indexEdges = true, directed = directed)
        val mutableGraph = graph as MutableGraph
        val e1Ref = graph.createEdgeReference(e1)

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
        val moved = reassignedTo!!
        assertThat(edges).containsExactly(moved)
        assertThat(edges.indexOf(moved)).isEqualTo(0)
        assertThat(edges[0]).isEqualTo(moved)
        assertThat(edges.indexOf(e1)).isEqualTo(-1)
        assertThat(e1Ref.unstable).isEqualTo(moved)
        assertThat(setOf(graph.edgeSource(moved), graph.edgeTarget(moved))).containsExactlyInAnyOrder(v1, v2)
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

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun edgeListenersAreNotifiedBeforeMutationWithIndexedEdgeSet(directed: Boolean) {
        constructGraph(immutable = false, indexEdges = true, directed = directed)
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

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun edgeListenersAreNotifiedBeforeMutationWithCanonicalEdgeSet(directed: Boolean) {
        constructGraph(immutable = false, indexEdges = false, directed = directed)
        val mutableGraph = graph as MutableGraph
        val events = ArrayList<String>()

        mutableGraph.registerEdgeChangeListener(object : EdgeChangeListener {
            override fun onEdgeAdded(edge: Edge) {
                assertThat(graph.edges.contains(edge)).isTrue
                events.add("added")
            }
            override fun onEdgeRemoved(edge: Edge) {
                // the edge is still present when the listener runs
                assertThat(graph.edges.contains(edge)).isTrue
                events.add("removed")
            }
            override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) = throw AssertionError()
        })

        // canonical edge ids are not indices, so removing an edge never re-assigns another one
        mutableGraph.removeEdge(e0)
        assertThat(events).containsExactly("removed")
        assertThat(graph.edges).containsExactly(e1)

        val e2 = mutableGraph.addEdge(v0, v2)
        assertThat(events).containsExactly("removed", "added")
        assertThat(graph.edges).containsExactlyInAnyOrder(e1, e2)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun lastIndexExtensionProperties(immutable: Boolean) {
        constructGraph(immutable, indexEdges = true)

        val vertices = graph.vertices as IndexedVertexSet
        val edges = graph.edges as IndexedEdgeSet

        assertThat(vertices.lastIndex).isEqualTo(2)
        assertThat(edges.lastIndex).isEqualTo(1)
        assertThat(emptyVertexSet().lastIndex).isEqualTo(-1)
        assertThat(emptyEdgeSet().lastIndex).isEqualTo(-1)
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

    @ParameterizedTest(name = "immutable={0}, directed={1}")
    @CsvSource("true,true", "true,false", "false,true", "false,false")
    fun firstAndLast(immutable: Boolean, directed: Boolean) {
        constructGraph(immutable, indexEdges = true, directed = directed)

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

    // --- set guarantees propagate through wrapping views ----------------------------------------------------------

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun transposedViewKeepsVertexAndEdgeSetGuarantees(immutable: Boolean) {
        constructGraph(immutable, indexEdges = true)
        val transposed = graph.asTransposed()

        assertThat(transposed.vertices).isInstanceOf(IdentityIndexedVertexSet::class.java)
        assertThat(transposed.edges).isInstanceOf(IdentityIndexedEdgeSet::class.java)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun transposedViewHidesCanonicalEdges(immutable: Boolean) {
        // a canonical edge id encodes its endpoints in the untransposed orientation, so the transposed view must not
        // advertise them - otherwise anything decoding the id directly reports the wrong direction
        constructGraph(immutable, indexEdges = false)
        assertThat(graph.edges).isInstanceOf(CanonicalEdgeSet::class.java)

        val transposed = graph.asTransposed()
        assertThat(transposed.edges).isNotInstanceOf(CanonicalEdgeSet::class.java)
        assertThat(transposed.vertices).isInstanceOf(IdentityIndexedVertexSet::class.java)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun filteringNoVerticesKeepsTheVertexSetGuarantee(immutable: Boolean) {
        constructGraph(immutable, indexEdges = true)

        val filtered = graph.filter(inducingEdges = graph.edges)
        assertThat(filtered.vertices).isInstanceOf(IdentityIndexedVertexSet::class.java)
        // the edge set is a subset, so it keeps no index guarantee
        assertThat(filtered.edges).isNotInstanceOf(IndexedEdgeSet::class.java)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun filteringKeepsCanonicalEdges(immutable: Boolean) {
        // filtering never rewrites an edge id, so the endpoints stay decodable
        constructGraph(immutable, indexEdges = false)

        assertThat(graph.filter(inducingEdges = graph.edges).edges).isInstanceOf(CanonicalEdgeSet::class.java)
        assertThat(graph.filter({ true }, { true }).edges).isInstanceOf(CanonicalEdgeSet::class.java)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun inducedSubsetDropsTheVertexIndexGuarantee(immutable: Boolean) {
        constructGraph(immutable, indexEdges = true)
        val subset = graph.filter(vertexSetOf(graph.vertices.first()), graph.edges)

        assertThat(subset.vertices).isNotInstanceOf(IndexedVertexSet::class.java)
    }
}
