package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.paths.buildPathForest
import io.github.sooniln.fastgraph.paths.endVertex
import io.github.sooniln.fastgraph.paths.isClosed
import io.github.sooniln.fastgraph.paths.isOpen
import io.github.sooniln.fastgraph.paths.startVertex
import io.github.sooniln.fastgraph.properties.propertyTypeOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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

    companion object {
        @JvmStatic
        fun sourceKinds(): List<Arguments> = MutableGraphContractTest.kinds().flatMap { arguments ->
            listOf(true, false).map { immutable -> Arguments.of(arguments.get()[0], arguments.get()[1], immutable) }
        }
    }

    // v0 -> v1 -> v2, v0 -> v3
    private fun constructGraph(directed: Boolean = true, indexEdges: Boolean = false, immutable: Boolean = false, multiEdge: Boolean = false) {
        val builder: GraphBuilder.() -> Unit = {
            v0 = addVertex()
            v1 = addVertex()
            v2 = addVertex()
            v3 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v1, v2)
            e2 = addEdge(v0, v3)
        }
        graph = if (immutable) {
            buildImmutableGraph(directed, multiEdge, indexEdges, builder)
        } else {
            buildGraph(directed, multiEdge, indexEdges, builder)
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

        val tree = graph.breadthFirstPathForest(v0)
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
        assertThat(path.isOpen()).isTrue
        assertThat(path.isClosed()).isFalse

        // walking the path yields every edge with the vertex it leads to
        val steps = path.iterator().asSequence().toList()
        assertThat(steps.map { it.edge }).containsExactly(e0, e1)
        assertThat(steps.map { it.target }).containsExactly(v1, v2)
        for ((index, step) in steps.withIndex()) {
            assertThat(graph.edgeOpposite(step.edge, step.target)).isEqualTo(path.vertices[index])
        }
        val stepIterator = path.iterator()
        stepIterator.next()
        stepIterator.next()
        assertThat(stepIterator.hasNext()).isFalse

        // materializing again yields an equal but independent path
        val again = tree.materializePath(v2)
        assertThat(again).isNotSameAs(path)
        assertThat(again.vertices).isEqualTo(path.vertices)
        assertThat(again.edges).isEqualTo(path.edges)
        assertThat(again.vertices.equalsSequenced(path.vertices)).isTrue
        assertThat(again.edges.equalsSequenced(path.edges)).isTrue
    }

    @Test
    fun pathVerticesHaveSetEquality() {
        constructGraph()

        val path = graph.breadthFirstPathForest(v0).materializePath(v2)

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

        val tree = graph.breadthFirstPathForest(v0)
        val path = tree.materializePath(v0)

        assertThat(path.vertices).containsExactly(v0)
        assertThat(path.edges).isEmpty()
        assertThat(path.startVertex).isEqualTo(v0)
        assertThat(path.endVertex).isEqualTo(v0)
        assertThat(path.isClosed()).isTrue()
        assertThat(path.isOpen()).isFalse()
        assertThat(path.iterator().hasNext()).isFalse()
        assertThrows<NoSuchElementException> { path.edges.first() }
        assertThrows<NoSuchElementException> { path.edges.last() }
        assertThat(path.vertices.first()).isEqualTo(v0)
        assertThat(path.vertices.last()).isEqualTo(v0)
        assertThat(tree.pathLengthProperty[v0]).isEqualTo(0)
    }

    @Test
    fun unreachableTarget() {
        constructGraph()

        val tree = graph.breadthFirstPathForest(v3)
        assertThat(tree.vertices).containsExactly(v3)
        assertThat(tree.edges).isEmpty()
        assertThat(tree.isEmpty()).isFalse
        assertThat(tree.vertices.contains(v0)).isFalse
        assertThrows<IllegalArgumentException> { tree.materializePath(v0) }
        assertThrows<IllegalArgumentException> { tree.materializePath(Vertex(99)) }
        assertThrows<IllegalArgumentException> { tree.pathLengthProperty[v0] }
        assertThrows<IllegalArgumentException> { tree.outDegree(v0) }
        assertThrows<IllegalArgumentException> { graph.breadthFirstPathForest(Vertex(99)) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun buildPathForestProducesTheSameTreeAsBreadthFirstSearch(indexEdges: Boolean) {
        constructGraph(indexEdges = indexEdges)

        val tree = graph.buildPathForest {
            addRoot(v0)
            setParentEdge(v1, e0)
            setParentEdge(v3, e2)
            setParentEdge(v2, e1)
        }
        val expected = graph.breadthFirstPathForest(v0)

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
    fun setParentEdgeUpdatesTheDepthOfTheWholeSubtree(directed: Boolean, indexEdges: Boolean) {
        constructShortcutGraph(directed, indexEdges)

        val tree = graph.buildPathForest {
            addRoot(v0)
            setParentEdge(v1, e0)
            setParentEdge(v2, e1)
            setParentEdge(v3, e2)
            // v2 is reached straight from v0 instead, which shortens v3's path as well
            setParentEdge(v2, e3)
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
    fun setParentEdgeCanMoveAChildFromARootToANonRoot(directed: Boolean, indexEdges: Boolean) {
        constructShortcutGraph(directed, indexEdges)

        val tree = graph.buildPathForest {
            addRoot(v0)
            setParentEdge(v2, e3)
            setParentEdge(v1, e0)
            setParentEdge(v3, e2)
            // v2 is reached through v1 instead, which lengthens v3's path as well
            setParentEdge(v2, e1)
        }

        assertThat(tree.pathLengthProperty[v1]).isEqualTo(1)
        assertThat(tree.pathLengthProperty[v2]).isEqualTo(2)
        assertThat(tree.pathLengthProperty[v3]).isEqualTo(3)
        assertThat(tree.successor(v2)).isEqualTo(v1)
        assertThat(tree.materializePath(v3).vertices).containsExactly(v0, v1, v2, v3)
        assertThat(tree.materializePath(v3).edges).containsExactly(e0, e1, e2)
        assertThat(tree.edges).containsExactlyInAnyOrder(e0, e1, e2)
        assertThat(tree.edges.contains(e3)).isFalse()
    }

    @ParameterizedTest
    @CsvSource("true,true", "true,false", "false,true", "false,false")
    fun rewiredTreeExposesTheNewTopology(directed: Boolean, indexEdges: Boolean) {
        constructShortcutGraph(directed, indexEdges)

        val tree = graph.buildPathForest {
            addRoot(v0)
            setParentEdge(v1, e0)
            setParentEdge(v2, e1)
            setParentEdge(v3, e2)
            setParentEdge(v2, e3)
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
        assertThat(tree.edges).containsExactlyInAnyOrder(e0, e3, e2)
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
            graph.buildPathForest {
                addRoot(v0)
                setParentEdge(v1, e0)
                setParentEdge(v2, e1)
                // v1 now reaches the tree through v2, which only reaches it through v1
                setParentEdge(v1, e2)
            }
        }
        assertThat(exception).hasMessageContaining("cycle")
    }

    @Test
    fun setParentEdgeRejectsIllegalArguments() {
        constructGraph()

        // v2 has not been reached yet, so it cannot be a parent
        assertThrows<IllegalArgumentException> {
            graph.buildPathForest { addRoot(v0); setParentEdge(v1, e1) }
        }
        // a root can never be given a parent
        assertThrows<IllegalArgumentException> {
            graph.buildPathForest { addRoot(v0); addRoot(v3); setParentEdge(v3, e2) }
        }
        // e0 leads to v1, not v0, in a directed graph
        assertThrows<IllegalArgumentException> {
            graph.buildPathForest { addRoot(v0); setParentEdge(v1, e0); setParentEdge(v0, e0) }
        }
        // e1 does not touch v3 at all
        assertThrows<IllegalArgumentException> {
            graph.buildPathForest { addRoot(v0); setParentEdge(v1, e0); setParentEdge(v3, e1) }
        }

        constructGraph(directed = false)

        // a root can never be given a parent, even when the undirected edge would otherwise lead back to it
        assertThrows<IllegalArgumentException> {
            graph.buildPathForest {
                addRoot(v0)
                setParentEdge(v1, e0)
                setParentEdge(v0, e0)
            }
        }

        // a vertex cannot be its own parent
        for (directed in listOf(true, false)) {
            var loop = Edge(-1)
            val graph = buildGraph(directed) {
                v0 = addVertex()
                loop = addEdge(v0, v0)
            }
            assertThrows<IllegalArgumentException> {
                graph.buildPathForest { addRoot(v0); setParentEdge(v0, loop) }
            }
        }
    }

    @Test
    fun buildPathForestRequiresRootsInTheGraph() {
        constructGraph()

        assertThrows<IllegalArgumentException> { graph.buildPathForest { addRoot(Vertex(99)) } }
    }

    @ParameterizedTest(name = "indexEdges={0}")
    @ValueSource(booleans = [true, false])
    fun pathForestWithSeveralRootsKeepsEachTreeApart(indexEdges: Boolean) {
        constructGraph(indexEdges = indexEdges)
        val forest = graph.buildPathForest {
            addRoot(v0)
            addRoot(v3)
            setParentEdge(v1, e0)
            setParentEdge(v2, e1)
        }

        assertThat(forest.roots).containsExactlyInAnyOrder(v0, v3)
        assertThat(forest.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
        assertThat(forest.edges).containsExactlyInAnyOrder(e0, e1)
        assertThat(forest.materializePath(v2).vertices.first()).isEqualTo(v0)
        assertThat(forest.pathLengthProperty[v2]).isEqualTo(2)
        assertThat(forest.pathLengthProperty[v3]).isEqualTo(0)
        assertThat(forest.materializePath(v2).vertices).containsExactly(v0, v1, v2)
        assertThat(forest.materializePath(v3).vertices).containsExactly(v3)

        // both roots have no parent
        for (root in listOf(v0, v3)) {
            assertThat(forest.outDegree(root)).isEqualTo(0)
            assertThat(forest.outgoingEdges(root)).isEmpty()
            assertThrows<IllegalStateException> { forest.successor(root) }
        }
        assertThat(forest.inDegree(v3)).isEqualTo(0)
        assertThat(forest.predecessors(v0)).containsExactly(v1)
        assertThat(forest.predecessors(v3)).isEmpty()
        assertThat(forest.hasEdge(v1, v0)).isTrue
        assertThat(forest.hasEdge(v3, v0)).isFalse
    }

    @Test
    fun reparentingMovesASubtreeToAnotherTree() {
        // v0 -> v1 -> v2, v3 -> v1
        graph = buildGraph(directed = true) {
            v0 = addVertex()
            v1 = addVertex()
            v2 = addVertex()
            v3 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v1, v2)
            e2 = addEdge(v3, v1)
        }

        val forest = graph.buildPathForest {
            addRoot(v0)
            addRoot(v3)
            setParentEdge(v1, e0)
            setParentEdge(v2, e1)
            setParentEdge(v1, e2)
        }

        assertThat(forest.materializePath(v1).vertices.first()).isEqualTo(v3)
        assertThat(forest.materializePath(v2).vertices.first()).isEqualTo(v3)
        assertThat(forest.pathLengthProperty[v1]).isEqualTo(1)
        assertThat(forest.pathLengthProperty[v2]).isEqualTo(2)
        assertThat(forest.materializePath(v2).vertices).containsExactly(v3, v1, v2)
        assertThat(forest.predecessors(v0)).isEmpty()
        assertThat(forest.edges).containsExactlyInAnyOrder(e1, e2)
    }

    @ParameterizedTest(name = "indexEdges={0}")
    @ValueSource(booleans = [true, false])
    fun rootsMayBeAddedAfterOtherVertices(indexEdges: Boolean) {
        constructGraph(indexEdges = indexEdges)
        val forest = graph.buildPathForest {
            addRoot(v0)
            setParentEdge(v1, e0)
            addRoot(v3)
            setParentEdge(v2, e1)
        }

        assertThat(forest.roots).containsExactlyInAnyOrder(v0, v3)
        assertThat(forest.roots.contains(v1)).isFalse
        assertThat(forest.roots.contains(Vertex(99))).isFalse
        assertThat(forest.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
        assertThat(forest.edges).containsExactlyInAnyOrder(e0, e1)
        assertThat(forest.materializePath(v2).vertices.first()).isEqualTo(v0)
        assertThat(forest.pathLengthProperty[v2]).isEqualTo(2)
        assertThat(forest.pathLengthProperty[v3]).isEqualTo(0)
        assertThat(forest.materializePath(v3).vertices).containsExactly(v3)
        assertThat(forest.outDegree(v3)).isEqualTo(0)
        assertThat(forest.predecessors(v0)).containsExactly(v1)
        assertThat(forest.predecessors(v3)).isEmpty()

        // the tree edges skip over the root in the middle of the parent arrays
        val edges = forest.edges as EdgeSequencedSet
        assertThat(edges).containsExactly(e0, e1)
        assertThat(edges.size).isEqualTo(2)
        assertThat(edges[0]).isEqualTo(e0)
        assertThat(edges[1]).isEqualTo(e1)
        assertThrows<IndexOutOfBoundsException> { edges[2] }
        assertThat(edges.indexOf(e0)).isEqualTo(0)
        assertThat(edges.indexOf(e1)).isEqualTo(1)
        assertThat(edges.indexOf(e2)).isEqualTo(-1)
        assertThat(edges.contains(e2)).isFalse
        assertThat(edges.contains(Edge(0))).isEqualTo(e0 == Edge(0) || e1 == Edge(0))
        assertThat(forest.edgeSource(e1)).isEqualTo(v2)
        assertThat(forest.edgeTarget(e1)).isEqualTo(v1)
    }

    @Test
    fun reparentingUnderALaterRootMovesTheSubtree() {
        // v0 -> v1 -> v2, v3 -> v1
        graph = buildGraph(directed = true) {
            v0 = addVertex()
            v1 = addVertex()
            v2 = addVertex()
            v3 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v1, v2)
            e2 = addEdge(v3, v1)
        }

        val forest = graph.buildPathForest {
            addRoot(v0)
            setParentEdge(v1, e0)
            setParentEdge(v2, e1)
            addRoot(v3)
            setParentEdge(v1, e2)
        }

        assertThat(forest.roots).containsExactlyInAnyOrder(v0, v3)
        assertThat(forest.materializePath(v1).vertices.first()).isEqualTo(v3)
        assertThat(forest.materializePath(v2).vertices.first()).isEqualTo(v3)
        assertThat(forest.pathLengthProperty[v1]).isEqualTo(1)
        assertThat(forest.pathLengthProperty[v2]).isEqualTo(2)
        assertThat(forest.materializePath(v2).vertices).containsExactly(v3, v1, v2)
        assertThat(forest.predecessors(v0)).isEmpty()
        assertThat(forest.edges).containsExactlyInAnyOrder(e1, e2)
    }

    @Test
    fun addRootRejectsAVertexAlreadyInTheForest() {
        constructGraph()

        assertThrows<IllegalArgumentException> {
            graph.buildPathForest { addRoot(v0); addRoot(v0) }
        }
        assertThrows<IllegalArgumentException> {
            graph.buildPathForest {
                addRoot(v0)
                setParentEdge(v1, e0)
                addRoot(v1)
            }
        }
    }

    @Test
    fun emptyPathForest() {
        constructGraph()

        val forest = graph.buildPathForest {}
        assertThat(forest.isEmpty()).isTrue
        assertThat(forest.vertices).isEmpty()
        assertThat(forest.edges).isEmpty()
        assertThat(forest.roots).isEmpty()
        assertThrows<IllegalArgumentException> { forest.materializePath(v0) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun breadthFirstPathForestIsUnchangedByDirectedness(directed: Boolean) {
        constructGraph(directed = directed)

        val tree = graph.breadthFirstPathForest(v0)

        assertThat(tree.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
        assertThat(tree.edges).containsExactlyInAnyOrder(e0, e1, e2)
        assertThat(tree.pathLengthProperty[v0]).isEqualTo(0)
        assertThat(tree.pathLengthProperty[v1]).isEqualTo(1)
        assertThat(tree.pathLengthProperty[v3]).isEqualTo(1)
        assertThat(tree.pathLengthProperty[v2]).isEqualTo(2)
        assertThat(tree.materializePath(v2).vertices).containsExactly(v0, v1, v2)
        assertThat(tree.materializePath(v2).edges).containsExactly(e0, e1)
        assertThat(tree.materializePath(v3).edges).containsExactly(e2)
    }

    @ParameterizedTest(name = "indexEdges={0}")
    @ValueSource(booleans = [true, false])
    fun pathTreePropertiesAreKeyedByVertex(indexEdges: Boolean) {
        constructGraph(indexEdges = indexEdges)

        // v3 is discovered before v1 and v2 so that discovery order differs from vertex ids
        val tree = graph.buildPathForest {
            addRoot(v0)
            setParentEdge(v3, e2)
            setParentEdge(v1, e0)
            setParentEdge(v2, e1)
        }
        assertThat(tree.vertices).isNotInstanceOf(IdentityIndexedVertexSet::class.java)

        val ints = tree.createVertexProperty { vertex -> vertex.id * 10 }
        val strings = tree.createVertexProperty<String?>()
        for (vertex in tree.vertices) {
            assertThat(ints[vertex]).isEqualTo(vertex.id * 10)
            assertThat(strings[vertex]).isNull()
            strings[vertex] = "v${vertex.id}"
        }
        assertThat(ints.put(v2, 7)).isEqualTo(v2.id * 10)
        assertThat(ints[v2]).isEqualTo(7)
        assertThat(strings[v3]).isEqualTo("v${v3.id}")
        assertThat(strings[v2]).isEqualTo("v${v2.id}")
        assertThrows<IllegalArgumentException> { ints[Vertex(99)] }
        assertThrows<IllegalArgumentException> { strings[Vertex(99)] = "x" }

        val lengths = tree.createEdgeProperty { edge -> edge.id }
        val labels = tree.createEdgeProperty<String?>()
        for (edge in tree.edges) {
            assertThat(lengths[edge]).isEqualTo(edge.id)
            labels[edge] = "e${edge.id}"
        }
        assertThat(labels[e2]).isEqualTo("e${e2.id}")
        assertThat(labels[e1]).isEqualTo("e${e1.id}")
        assertThrows<IllegalArgumentException> { lengths[Edge(99)] }
        assertThrows<IllegalArgumentException> { labels[Edge(99)] = "x" }

        val vertexKeys = tree.createVertexKeyProperty<String>()
        val edgeKeys = tree.createEdgeKeyProperty<Int>()
        vertexKeys[v0] = "k0"
        vertexKeys[v3] = "k1"
        vertexKeys[v1] = "k2"
        vertexKeys[v2] = "k3"
        edgeKeys[e2] = 0
        edgeKeys[e0] = 1
        edgeKeys[e1] = 2
        assertThat(vertexKeys.getVertex("k1")).isEqualTo(v3)
        assertThat(vertexKeys.getVertex("k3")).isEqualTo(v2)
        assertThat(vertexKeys[v0]).isEqualTo("k0")
        assertThat(edgeKeys.getEdge(0)).isEqualTo(e2)
        assertThat(edgeKeys.getEdge(2)).isEqualTo(e1)
        assertThat(edgeKeys[e0]).isEqualTo(1)
        assertThat(vertexKeys.put(v2, "k9")).isEqualTo("k3")
        assertThat(vertexKeys.getVertex("k9")).isEqualTo(v2)
        assertThat(edgeKeys.put(e1, 9)).isEqualTo(2)
        assertThat(edgeKeys.getEdge(9)).isEqualTo(e1)
        assertThrows<IllegalArgumentException> { vertexKeys[v0] = "k1" }
        assertThrows<IllegalArgumentException> { vertexKeys[Vertex(99)] = "k9" }
        assertThrows<IllegalArgumentException> { edgeKeys[e0] = 9 }
        assertThrows<IllegalArgumentException> { edgeKeys[Edge(99)] = 5 }
    }

    @ParameterizedTest(name = "{0}, directed={1}, immutable={2}")
    @MethodSource("sourceKinds")
    fun pathTreeIsBuiltFromEveryGraphImplementation(kind: MutableGraphContractTest.GraphKind, directed: Boolean, immutable: Boolean) {
        constructGraph(directed, kind.indexEdges, immutable, kind.multiEdge)

        val tree = graph.breadthFirstPathForest(v0)

        assertThat(tree.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
        assertThat(tree.edges).containsExactlyInAnyOrder(e0, e1, e2)
        assertThat(tree.materializePath(v2).vertices).containsExactly(v0, v1, v2)
        assertThat(tree.materializePath(v2).edges).containsExactly(e0, e1)
        assertThat(tree.materializePath(v3).edges).containsExactly(e2)
        assertThat(tree.edgeSource(e1)).isEqualTo(v2)
        assertThat(tree.edgeTarget(e1)).isEqualTo(v1)
        assertThat(tree.pathLengthProperty[v2]).isEqualTo(2)

        // a tree does not depend on the graph it came from once built
        if (!immutable) {
            val mutable = graph as MutableGraph
            mutable.removeVertex(v0)
            assertThat(tree.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
            assertThat(tree.materializePath(v2).edges).containsExactly(e0, e1)
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun pathTreeKeepsTheEdgeGivenToSetParentAmongParallelEdges(directed: Boolean) {
        graph = buildGraph(directed, multiEdge = true) {
            v0 = addVertex()
            v1 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v0, v1)
            e2 = addEdge(v0, v0)
        }

        val tree = graph.buildPathForest { addRoot(v0); setParentEdge(v1, e1) }

        assertThat(tree.edges).containsExactly(e1)
        assertThat(tree.edges.contains(e0)).isFalse
        assertThat(tree.edges.contains(e2)).isFalse
        assertThat(tree.outgoingEdge(v1)).isEqualTo(e1)
        assertThat(tree.materializePath(v1).edges).containsExactly(e1)
        assertThrows<IllegalArgumentException> { tree.edgeSource(e0) }
        assertThrows<IllegalArgumentException> { tree.edgeTarget(e2) }

        // a breadth-first tree picks exactly one of the parallel edges and never a self-loop
        val bfs = graph.breadthFirstPathForest(v0)
        assertThat(bfs.edges).hasSize(1)
        assertThat(bfs.edges.first()).isIn(e0, e1)
        assertThat(bfs.materializePath(v1).edges).containsExactly(bfs.edges.first())
    }

    @ParameterizedTest(name = "indexEdges={0}")
    @ValueSource(booleans = [true, false])
    fun pathTreeIsADirectedGraphOfParentPointers(indexEdges: Boolean) {
        constructGraph(indexEdges = indexEdges)
        val tree = graph.buildPathForest {
            addRoot(v0)
            setParentEdge(v1, e0)
            setParentEdge(v3, e2)
            setParentEdge(v2, e1)
        }

        assertThat(tree).isInstanceOf(ImmutableGraph::class.java)
        assertThat(tree.directed).isTrue
        assertThat(tree.multiEdge).isFalse
        assertThat(tree.isEmpty()).isFalse
        assertThat(tree.roots).containsExactly(v0)
        assertThat(tree.toImmutableGraph()).isSameAs(tree)

        // the root has no parent
        assertThat(tree.outDegree(v0)).isEqualTo(0)
        assertThat(tree.successors(v0)).isEmpty()
        assertThat(tree.outgoingEdges(v0)).isEmpty()
        assertThrows<IllegalStateException> { tree.successor(v0) }
        assertThrows<IllegalStateException> { tree.outgoingEdge(v0) }
        assertThat(tree.inDegree(v0)).isEqualTo(2)
        assertThat(tree.predecessors(v0)).containsExactlyInAnyOrder(v1, v3)
        assertThat(tree.incomingEdges(v0)).containsExactlyInAnyOrder(e0, e2)
        assertThrows<IllegalStateException> { tree.predecessor(v0) }
        assertThrows<IllegalStateException> { tree.incomingEdge(v0) }

        // every other vertex points at exactly one parent
        for ((child, parent, edge) in listOf(Triple(v1, v0, e0), Triple(v3, v0, e2), Triple(v2, v1, e1))) {
            assertThat(tree.outDegree(child)).isEqualTo(1)
            assertThat(tree.successor(child)).isEqualTo(parent)
            assertThat(tree.successors(child)).containsExactly(parent)
            assertThat(tree.outgoingEdge(child)).isEqualTo(edge)
            assertThat(tree.outgoingEdges(child)).containsExactly(edge)
            assertThat(tree.edgeSource(edge)).isEqualTo(child)
            assertThat(tree.edgeTarget(edge)).isEqualTo(parent)
            assertThat(tree.edgeOpposite(edge, child)).isEqualTo(parent)
            assertThat(tree.hasEdge(child, parent)).isTrue
            assertThat(tree.hasEdge(parent, child)).isFalse
            assertThat(tree.edge(child, parent)).isEqualTo(edge)
            assertThat(tree.edges(child, parent)).containsExactly(edge)
            assertThat(tree.edges(parent, child)).isEmpty()
            assertThrows<IllegalStateException> { tree.edge(parent, child) }
            assertThat(tree.predecessors(parent).contains(child)).isTrue
            assertThat(tree.incomingEdges(parent).contains(edge)).isTrue
        }

        // leaves have no children
        for (leaf in listOf(v2, v3)) {
            assertThat(tree.inDegree(leaf)).isEqualTo(0)
            assertThat(tree.predecessors(leaf)).isEmpty()
            assertThat(tree.incomingEdges(leaf)).isEmpty()
            assertThrows<IllegalStateException> { tree.predecessor(leaf) }
            assertThrows<IllegalStateException> { tree.incomingEdge(leaf) }
        }
        assertThat(tree.predecessor(v1)).isEqualTo(v2)
        assertThat(tree.incomingEdge(v1)).isEqualTo(e1)
        assertThat(tree.hasEdge(v2, v0)).isFalse
        assertThat(tree.hasEdge(v0, v0)).isFalse

        // stable references and listeners behave as on any immutable graph
        assertThat(tree.createVertexReference(v2).unstable).isEqualTo(v2)
        assertThat(tree.createEdgeReference(e1).unstable).isEqualTo(e1)
        assertThrows<IllegalArgumentException> { tree.createVertexReference(Vertex(99)) }
        assertThrows<IllegalArgumentException> { tree.createEdgeReference(Edge(99)) }
        tree.registerVertexChangeListener(object : VertexChangeListener {
            override fun onVertexAdded(vertex: Vertex) = throw AssertionError()
            override fun onVertexRemoved(vertex: Vertex) = throw AssertionError()
            override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) = throw AssertionError()
        })

        // the length property belongs to the tree
        assertThat(tree.pathLengthProperty.graph).isSameAs(tree)
        assertThat(tree.pathLengthProperty.type).isEqualTo(propertyTypeOf<Int>())

        // graph algorithms run over the tree like over any other graph: every vertex reaches the root
        for (vertex in tree.vertices) {
            assertThat(tree.breadthFirstVertexIterator(vertex).asSequence().last()).isEqualTo(v0)
        }
        // and transposing the tree points the edges from parents to children
        val transposed = tree.asTransposed()
        assertThat(transposed.successors(v0)).containsExactlyInAnyOrder(v1, v3)
        assertThat(transposed.breadthFirstVertexIterator(v0).asSequence().toList()).containsExactlyInAnyOrder(v0, v1, v2, v3)
    }

    @ParameterizedTest(name = "indexEdges={0}")
    @ValueSource(booleans = [true, false])
    fun pathTreeCollectionsAreProperSets(indexEdges: Boolean) {
        constructGraph(indexEdges = indexEdges)
        val tree = graph.buildPathForest {
            addRoot(v0)
            setParentEdge(v3, e2)
            setParentEdge(v1, e0)
            setParentEdge(v2, e1)
        }

        // vertices and edges compare by elements, symmetrically, and hash consistently
        val vertices = vertexSetOf(v2, v0, v1, v3)
        assertThat(tree.vertices == vertices).isTrue
        assertThat(vertices == tree.vertices).isTrue
        assertThat(tree.vertices.hashCode()).isEqualTo(vertices.hashCode())
        assertThat(tree.vertices == graph.vertices).isTrue
        assertThat(graph.vertices == tree.vertices).isTrue

        val edges = edgeSetOf(e1, e0, e2)
        assertThat(tree.edges == edges).isTrue
        assertThat(edges == tree.edges).isTrue
        assertThat(tree.edges.hashCode()).isEqualTo(edges.hashCode())
        assertThat(tree.edges == graph.edges).isTrue
        assertThat(graph.edges == tree.edges).isTrue

        assertThat(tree.vertices.toIntArray()).containsExactlyInAnyOrder(v0.id, v3.id, v1.id, v2.id)
        assertThat(tree.edges.toLongArray()).containsExactlyInAnyOrder(e2.id, e0.id, e1.id)
        assertThat(tree.vertices.containsAll(vertexSetOf(v1, v3))).isTrue
        assertThat(tree.vertices.containsAll(listOf(v1, Vertex(99)))).isFalse
        assertThat(tree.edges.containsAll(edgeSetOf(e0, e1))).isTrue
        assertThat(tree.edges.containsAll(listOf(e0, Edge(99)))).isFalse

        // a single-vertex tree has no edges but is still a valid graph
        val trivial = graph.buildPathForest { addRoot(v3) }
        assertThat(trivial.vertices).containsExactly(v3)
        assertThat(trivial.edges).isEmpty()
        assertThat(trivial.edges == emptyEdgeSet()).isTrue
        assertThat(emptyEdgeSet() == trivial.edges).isTrue
    }

    @Test
    fun pathAccessorsInUndirectedGraphAreConsistentWithEdgeOpposite() {
        constructGraph(directed = false)

        val path = graph.breadthFirstPathForest(v2).materializePath(v3)

        assertThat(path.vertices).containsExactly(v2, v1, v0, v3)
        assertThat(path.edges).containsExactly(e1, e0, e2)
        for (index in 0 until path.edges.size) {
            assertThat(graph.edgeOpposite(path.edges[index], path.vertices[index])).isEqualTo(path.vertices[index + 1])
        }
        assertThat(path.startVertex).isEqualTo(v2)
        assertThat(path.endVertex).isEqualTo(v3)
        assertThat(path.vertices.indexOf(v0)).isEqualTo(2)
        assertThat(path.vertices.lastIndexOf(v0)).isEqualTo(2)
        assertThat(path.vertices.lastIndex).isEqualTo(3)
        assertThat(path.edges.lastIndex).isEqualTo(2)
        assertThat(path.vertices.toIntArray()).containsExactly(v2.id, v1.id, v0.id, v3.id)
        assertThat(path.edges.toLongArray()).containsExactly(e1.id, e0.id, e2.id)
    }
}
