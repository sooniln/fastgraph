package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class PathTest {

    private lateinit var graph: Graph
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var v3: Vertex = Vertex(-1)
    private var e0: Edge = Edge(-1)
    private var e1: Edge = Edge(-1)
    private var e2: Edge = Edge(-1)
    private var e3: Edge = Edge(-1)

    // v0 -> v1 -> v2, v0 -> v3
    private fun constructGraph(directed: Boolean = true, indexEdges: Boolean = false) {
        graph = buildGraph(directed, indexEdges = indexEdges) {
            v0 = addVertex()
            v1 = addVertex()
            v2 = addVertex()
            v3 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v1, v2)
            e2 = addEdge(v0, v3)
        }
    }

    // v0 -> v1 -> v2 -> v3, with a shortcut from v0 straight to v2
    private fun constructShortcutGraph(directed: Boolean = true, indexEdges: Boolean = false) {
        graph = buildGraph(directed, indexEdges = indexEdges) {
            v0 = addVertex()
            v1 = addVertex()
            v2 = addVertex()
            v3 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v1, v2)
            e2 = addEdge(v2, v3)
            e3 = addEdge(v0, v2)
        }
    }

    @Test
    fun pathVerticesAndEdgesAreOrdered() {
        constructGraph()

        val tree = graph.breadthFirstPathTree(v0)
        val path = tree.materializePath(v2)

        assertThat(path.vertices).containsExactly(v0, v1, v2)
        assertThat(path.edges).containsExactly(e0, e1)
        assertThat(path.vertices[1]).isEqualTo(v1)
        assertThat(path.edges[1]).isEqualTo(e1)
        assertThat(path.vertices.indexOf(v2)).isEqualTo(2)
        assertThat(path.edges.indexOf(e1)).isEqualTo(1)
        assertThat(path.startVertex).isEqualTo(v0)
        assertThat(path.endVertex).isEqualTo(v2)
        assertThat(path.vertices.reversed()).containsExactly(v2, v1, v0)
        assertThat(tree.pathLengthProperty[v2]).isEqualTo(2)
    }

    @Test
    fun pathVerticesHaveSetEquality() {
        constructGraph()

        val path = graph.breadthFirstPathTree(v0).materializePath(v2)

        val vertices = vertexSetOf(v2, v0, v1)
        assertThat(path.vertices).isEqualTo(vertices)
        assertThat(vertices).isEqualTo(path.vertices)
        assertThat(path.vertices.hashCode()).isEqualTo(vertices.hashCode())

        val edges = edgeSetOf(e1, e0)
        assertThat(path.edges).isEqualTo(edges)
        assertThat(edges).isEqualTo(path.edges)
        assertThat(path.edges.hashCode()).isEqualTo(edges.hashCode())
    }

    @Test
    fun trivialPath() {
        constructGraph()

        val tree = graph.breadthFirstPathTree(v0)
        val path = tree.materializePath(v0)

        assertThat(path.vertices).containsExactly(v0)
        assertThat(path.edges).isEmpty()
        assertThat(path.startVertex).isEqualTo(v0)
        assertThat(path.endVertex).isEqualTo(v0)
        assertThat(path.isClosed()).isTrue()
        assertThrows<NoSuchElementException> { path.edges.first() }
        assertThat(tree.pathLengthProperty[v0]).isEqualTo(0)
    }

    @Test
    fun unreachableTarget() {
        constructGraph()

        val tree = graph.breadthFirstPathTree(v3)
        assertThat(tree.vertices).containsExactly(v3)
        assertThrows<IllegalArgumentException> { tree.materializePath(v0) }
        assertThrows<IllegalArgumentException> { tree.pathLengthProperty[v0] }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun buildPathTreeProducesTheSameTreeAsBreadthFirstSearch(indexEdges: Boolean) {
        constructGraph(indexEdges = indexEdges)

        val tree = graph.buildPathTree(v0) {
            setParent(v0, e0, v1)
            setParent(v0, e2, v3)
            setParent(v1, e1, v2)
        }
        val expected = graph.breadthFirstPathTree(v0)

        assertThat(tree.vertices).containsExactlyElementsOf(expected.vertices)
        assertThat(tree.edges).containsExactlyElementsOf(expected.edges)
        for (vertex in expected.vertices) {
            assertThat(tree.pathLengthProperty[vertex]).isEqualTo(expected.pathLengthProperty[vertex])
            assertThat(tree.materializePath(vertex).vertices)
                .containsExactlyElementsOf(expected.materializePath(vertex).vertices)
            assertThat(tree.materializePath(vertex).edges)
                .containsExactlyElementsOf(expected.materializePath(vertex).edges)
        }
    }

    @ParameterizedTest
    @CsvSource("true,true", "true,false", "false,true", "false,false")
    fun setParentUpdatesTheDepthOfTheWholeSubtree(directed: Boolean, indexEdges: Boolean) {
        constructShortcutGraph(directed, indexEdges)

        val tree = graph.buildPathTree(v0) {
            setParent(v0, e0, v1)
            setParent(v1, e1, v2)
            setParent(v2, e2, v3)
            // v2 is reached straight from v0 instead, which shortens v3's path as well
            setParent(v0, e3, v2)
        }

        assertThat(tree.pathLengthProperty[v1]).isEqualTo(1)
        assertThat(tree.pathLengthProperty[v2]).isEqualTo(1)
        assertThat(tree.pathLengthProperty[v3]).isEqualTo(2)
        assertThat(tree.materializePath(v2).vertices).containsExactly(v0, v2)
        assertThat(tree.materializePath(v2).edges).containsExactly(e3)
        assertThat(tree.materializePath(v3).vertices).containsExactly(v0, v2, v3)
        assertThat(tree.materializePath(v3).edges).containsExactly(e3, e2)
    }

    @ParameterizedTest
    @CsvSource("true,true", "true,false", "false,true", "false,false")
    fun rewiredTreeExposesTheNewTopology(directed: Boolean, indexEdges: Boolean) {
        constructShortcutGraph(directed, indexEdges)

        val tree = graph.buildPathTree(v0) {
            setParent(v0, e0, v1)
            setParent(v1, e1, v2)
            setParent(v2, e2, v3)
            setParent(v0, e3, v2)
        }

        // the edges of a path tree point from a vertex back to its parent
        assertThat(tree.successor(v2)).isEqualTo(v0)
        assertThat(tree.outgoingEdge(v2)).isEqualTo(e3)
        assertThat(tree.edgeSource(e3)).isEqualTo(v2)
        assertThat(tree.edgeTarget(e3)).isEqualTo(v0)
        assertThat(tree.predecessors(v0)).containsExactlyInAnyOrder(v1, v2)
        assertThat(tree.predecessors(v2)).containsExactly(v3)
        assertThat(tree.inDegree(v0)).isEqualTo(2)
        assertThat(tree.outDegree(v0)).isEqualTo(0)
        assertThat(tree.outDegree(v2)).isEqualTo(1)
        assertThat(tree.hasEdge(v2, v0)).isTrue()
        assertThat(tree.hasEdge(v2, v1)).isFalse()
        // e1 was replaced as v2's tree edge and is no longer part of the tree
        assertThat(tree.edges).containsExactly(e0, e3, e2)
        assertThat(tree.edges.contains(e1)).isFalse()
    }

    @Test
    fun cyclicParentsAreRejected() {
        // v0 -> v1 -> v2 -> v1
        graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            v2 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v1, v2)
            e2 = addEdge(v2, v1)
        }

        val exception = assertThrows<IllegalStateException> {
            graph.buildPathTree(v0) {
                setParent(v0, e0, v1)
                setParent(v1, e1, v2)
                // v1 now reaches the tree through v2, which only reaches it through v1
                setParent(v2, e2, v1)
            }
        }
        assertThat(exception).hasMessageContaining("cycle")
    }

    @Test
    fun setParentRejectsIllegalArguments() {
        constructGraph()

        // v2 has not been reached yet, so it cannot be a parent
        assertThrows<IllegalArgumentException> {
            graph.buildPathTree(v0) { setParent(v2, e1, v1) }
        }
        // the source is always the root of the tree
        assertThrows<IllegalArgumentException> {
            graph.buildPathTree(v0) {
                setParent(v0, e0, v1)
                setParent(v1, e0, v0)
            }
        }
        // a vertex cannot be its own parent
        assertThrows<IllegalArgumentException> {
            graph.buildPathTree(v0) { setParent(v0, e0, v0) }
        }
    }

    @Test
    fun buildPathTreeRequiresASourceInTheGraph() {
        constructGraph()

        assertThrows<IllegalArgumentException> { graph.buildPathTree(Vertex(99)) {} }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun breadthFirstPathTreeIsUnchangedByDirectedness(directed: Boolean) {
        constructGraph(directed = directed)

        val tree = graph.breadthFirstPathTree(v0)

        assertThat(tree.vertices).containsExactly(v0, v1, v3, v2)
        assertThat(tree.pathLengthProperty[v2]).isEqualTo(2)
        assertThat(tree.materializePath(v2).vertices).containsExactly(v0, v1, v2)
        assertThat(tree.materializePath(v2).edges).containsExactly(e0, e1)
        assertThat(tree.materializePath(v3).edges).containsExactly(e2)
    }

    @ParameterizedTest(name = "indexEdges={0}")
    @ValueSource(booleans = [true, false])
    fun pathTreeIsIndexedByDiscoveryOrder(indexEdges: Boolean) {
        constructGraph(indexEdges = indexEdges)

        // v3 is discovered before v1 and v2 so that discovery order differs from vertex ids
        val tree = graph.buildPathTree(v0) {
            setParent(v0, e2, v3)
            setParent(v0, e0, v1)
            setParent(v1, e1, v2)
        }

        assertThat(tree).isNotInstanceOf(IdentityIndexedVertexGraph::class.java)
        assertThat(tree.vertices).containsExactly(v0, v3, v1, v2)
        assertThat(tree.vertices.indexOf(v0)).isEqualTo(0)
        assertThat(tree.vertices.indexOf(v3)).isEqualTo(1)
        assertThat(tree.vertices.indexOf(v2)).isEqualTo(3)
        assertThat(tree.vertices.indexOf(Vertex(99))).isEqualTo(-1)
        for ((index, vertex) in tree.vertices.withIndex()) {
            assertThat(tree.vertices[index]).isEqualTo(vertex)
            assertThat(tree.vertices.indexOf(vertex)).isEqualTo(index)
        }

        assertThat(tree.edges).containsExactly(e2, e0, e1)
        assertThat(tree.edges.indexOf(e2)).isEqualTo(0)
        assertThat(tree.edges.indexOf(e1)).isEqualTo(2)
        assertThat(tree.edges.indexOf(Edge(99))).isEqualTo(-1)
        for ((index, edge) in tree.edges.withIndex()) {
            assertThat(tree.edges[index]).isEqualTo(edge)
            assertThat(tree.edges.indexOf(edge)).isEqualTo(index)
        }
    }

    @ParameterizedTest(name = "indexEdges={0}")
    @ValueSource(booleans = [true, false])
    fun pathTreePropertiesAreIndexedByDiscoveryOrder(indexEdges: Boolean) {
        constructGraph(indexEdges = indexEdges)
        val tree = graph.buildPathTree(v0) {
            setParent(v0, e2, v3)
            setParent(v0, e0, v1)
            setParent(v1, e1, v2)
        }

        val ints = tree.createVertexProperty { vertex -> tree.vertices.indexOf(vertex) * 10 }
        val strings = tree.createVertexProperty<String?>()
        for ((index, vertex) in tree.vertices.withIndex()) {
            assertThat(ints[vertex]).isEqualTo(index * 10)
            assertThat(strings[vertex]).isNull()
            strings[vertex] = "v$index"
        }
        assertThat(ints.put(v2, 7)).isEqualTo(30)
        assertThat(ints[v2]).isEqualTo(7)
        assertThat(strings[v3]).isEqualTo("v1")
        assertThat(strings[v2]).isEqualTo("v3")
        assertThrows<IllegalArgumentException> { ints[Vertex(99)] }
        assertThrows<IllegalArgumentException> { strings[Vertex(99)] = "x" }

        val lengths = tree.createEdgeProperty { edge -> tree.edges.indexOf(edge).toLong() }
        val labels = tree.createEdgeProperty<String?>()
        for ((index, edge) in tree.edges.withIndex()) {
            assertThat(lengths[edge]).isEqualTo(index.toLong())
            labels[edge] = "e$index"
        }
        assertThat(labels[e2]).isEqualTo("e0")
        assertThat(labels[e1]).isEqualTo("e2")
        assertThrows<IllegalArgumentException> { lengths[Edge(99)] }
        assertThrows<IllegalArgumentException> { labels[Edge(99)] = "x" }

        val vertexKeys = tree.createVertexKeyProperty<String>()
        val edgeKeys = tree.createEdgeKeyProperty<Int>()
        for ((index, vertex) in tree.vertices.withIndex()) vertexKeys[vertex] = "k$index"
        for ((index, edge) in tree.edges.withIndex()) edgeKeys[edge] = index
        assertThat(vertexKeys.getVertex("k1")).isEqualTo(v3)
        assertThat(vertexKeys.getVertex("k3")).isEqualTo(v2)
        assertThat(vertexKeys[v0]).isEqualTo("k0")
        assertThat(edgeKeys.getEdge(0)).isEqualTo(e2)
        assertThat(edgeKeys.getEdge(2)).isEqualTo(e1)
        assertThrows<IllegalArgumentException> { vertexKeys[v0] = "k1" }
        assertThrows<IllegalArgumentException> { vertexKeys[Vertex(99)] = "k9" }
    }
}
