package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class TraversalTest {

    private lateinit var graph: MutableGraph
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var v3: Vertex = Vertex(-1)
    private var v4: Vertex = Vertex(-1)

    // v0 -> v1 -> v3, v0 -> v2 -> v3, v4 is disconnected
    private fun constructDiamond(directed: Boolean) {
        graph = mutableGraph(directed)
        v0 = graph.addVertex()
        v1 = graph.addVertex()
        v2 = graph.addVertex()
        v3 = graph.addVertex()
        v4 = graph.addVertex()
        graph.addEdge(v0, v1)
        graph.addEdge(v0, v2)
        graph.addEdge(v1, v3)
        graph.addEdge(v2, v3)
    }

    // v0 -> v1, v0 -> v2, v2 -> v1: v1 is reachable both directly and via v2
    private fun constructShortcut(directed: Boolean) {
        graph = mutableGraph(directed)
        v0 = graph.addVertex()
        v1 = graph.addVertex()
        v2 = graph.addVertex()
        graph.addEdge(v0, v1)
        graph.addEdge(v0, v2)
        graph.addEdge(v2, v1)
    }

    // v0 -> v1 -> v2 -> v0
    private fun constructCycle(directed: Boolean) {
        graph = mutableGraph(directed)
        v0 = graph.addVertex()
        v1 = graph.addVertex()
        v2 = graph.addVertex()
        graph.addEdge(v0, v1)
        graph.addEdge(v1, v2)
        graph.addEdge(v2, v0)
    }

    private fun Iterator<Vertex>.toList(): List<Vertex> = asSequence().toList()

    private fun outDegreeSum(vertices: Collection<Vertex>): Int = vertices.sumOf { graph.outDegree(it) }

    // --- Argument validation, common to every traversal -------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("traversals")
    fun requiresNonEmptyInitialVertices(traversal: (Graph, VertexSet) -> Unit, directed: Boolean) {
        constructDiamond(directed)

        assertThrows<IllegalArgumentException> { traversal(graph, emptyVertexSet()) }
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("traversals")
    fun requiresInitialVerticesInGraph(traversal: (Graph, VertexSet) -> Unit, directed: Boolean) {
        constructDiamond(directed)

        assertThrows<IllegalArgumentException> { traversal(graph, vertexSetOf(v0, Vertex(99))) }
    }

    // --- Vertex iterators ---------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("vertexIterators")
    fun vertexIteratorVisitsEachReachableVertexOnce(
        iterator: (Graph, VertexSet) -> VertexIterator,
        directed: Boolean,
    ) {
        constructDiamond(directed)

        // v3 is also reachable from v0 and must not be visited a second time on its own account.
        val result = iterator(graph, vertexSetOf(v0, v3, v4)).toList()

        assertThat(result).containsExactlyInAnyOrder(v0, v1, v2, v3, v4)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun breadthFirstVisitsInLevelOrder(directed: Boolean) {
        constructDiamond(directed)

        val result = graph.breadthFirstVertexIterator(vertexSetOf(v0)).toList()

        assertThat(result).hasSize(4)
        assertThat(result[0]).isEqualTo(v0)
        assertThat(result.subList(1, 3)).containsExactlyInAnyOrder(v1, v2)
        assertThat(result[3]).isEqualTo(v3)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPreOrderDescendsBeforeVisitingSiblings(directed: Boolean) {
        constructDiamond(directed)

        val result = graph.depthFirstPreOrderVertexIterator(vertexSetOf(v0)).toList()

        assertThat(result).hasSize(4)
        assertThat(result[0]).isEqualTo(v0)
        assertThat(result[2]).isEqualTo(v3)
        assertThat(listOf(result[1], result[3])).containsExactlyInAnyOrder(v1, v2)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPostOrderFinishesDescendantsFirst(directed: Boolean) {
        constructDiamond(directed)

        val result = graph.depthFirstPostOrderVertexIterator(vertexSetOf(v0)).toList()

        // Directed: v3 is a leaf reached under the first branch, so it finishes first. Undirected: the search runs
        // straight through v0, v1, v3, v2 (or v0, v2, v3, v1) so the far side finishes first, then v3.
        assertThat(result).hasSize(4)
        assertThat(result[if (directed) 0 else 1]).isEqualTo(v3)
        assertThat(result[3]).isEqualTo(v0)
        assertThat(result).containsExactlyInAnyOrder(v0, v1, v2, v3)
    }

    @Test
    fun depthFirstPostOrderIsReverseTopologicalOrder() {
        constructShortcut(directed = true)

        // v1 must finish before v2 whether v1 is first reached directly from v0 or via v2.
        val result = graph.depthFirstPostOrderVertexIterator(vertexSetOf(v0)).toList()

        assertThat(result).containsExactly(v1, v2, v0)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPostOrderTerminatesOnCycle(directed: Boolean) {
        constructCycle(directed)

        val result = graph.depthFirstPostOrderVertexIterator(vertexSetOf(v0)).toList()

        assertThat(result).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(result.last()).isEqualTo(v0)
    }

    // --- Tree edge iterators ------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("treeEdgeIterators")
    fun treeEdgesFormSpanningForest(iterator: (Graph, VertexSet) -> Iterator<PathStep>, directed: Boolean) {
        constructDiamond(directed)

        val steps = iterator(graph, vertexSetOf(v0, v4)).asSequence().toList()

        // Roots have no tree edge; every other reachable vertex is discovered exactly once.
        assertThat(steps.map { it.vertex }).containsExactlyInAnyOrder(v1, v2, v3)
        // Each tree edge leads from an already discovered vertex to the newly discovered one.
        val discovered = mutableSetOf(v0, v4)
        for (step in steps) {
            assertThat(graph.edgeOpposite(step.edge, step.vertex)).isIn(discovered)
            if (directed) assertThat(graph.edgeTarget(step.edge)).isEqualTo(step.vertex)
            discovered += step.vertex
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun breadthFirstTreeEdgesFollowShortestPaths(directed: Boolean) {
        constructShortcut(directed)

        val steps = graph.breadthFirstTreeEdgeIterator(vertexSetOf(v0)).asSequence().toList()

        val stepToV1 = steps.single { it.vertex == v1 }
        assertThat(graph.edgeOpposite(stepToV1.edge, v1)).isEqualTo(v0)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstTreeEdgesAreInPreOrder(directed: Boolean) {
        constructDiamond(directed)

        val vertices = graph.depthFirstTreeEdgeIterator(vertexSetOf(v0)).asSequence().map { it.vertex }.toList()

        assertThat(vertices).hasSize(3)
        assertThat(vertices[1]).isEqualTo(v3)
        assertThat(listOf(vertices[0], vertices[2])).containsExactlyInAnyOrder(v1, v2)
    }

    // --- Visitors -----------------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitBreadthFirstDiscoversInLevelOrderAndExaminesInDiscoveryOrder(directed: Boolean) {
        constructDiamond(directed)

        val discovered = mutableListOf<Vertex>()
        val examined = mutableListOf<Vertex>()
        val finished = mutableListOf<Vertex>()
        graph.visitBreadthFirst(
            vertexSetOf(v0),
            onVertexDiscovered = { discovered += it },
            onVertexExamined = { examined += it },
            onVertexFinished = { finished += it },
        )

        assertThat(discovered).hasSize(4)
        assertThat(discovered[0]).isEqualTo(v0)
        assertThat(discovered.subList(1, 3)).containsExactlyInAnyOrder(v1, v2)
        assertThat(discovered[3]).isEqualTo(v3)
        assertThat(examined).isEqualTo(discovered)
        assertThat(finished).isEqualTo(examined)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitBreadthFirstExaminesEveryEdgeAndReportsTreeEdges(directed: Boolean) {
        constructDiamond(directed)

        val examinedEdges = mutableListOf<Edge>()
        val treeEdges = mutableListOf<Edge>()
        graph.visitBreadthFirst(
            vertexSetOf(v0),
            onEdgeExamined = { examinedEdges += it },
            onTreeEdge = { treeEdges += it },
        )

        assertThat(examinedEdges).hasSize(outDegreeSum(listOf(v0, v1, v2, v3)))
        assertThat(treeEdges).hasSize(3).doesNotHaveDuplicates()
        assertThat(examinedEdges).containsAll(treeEdges)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitDepthFirstDiscoversInPreOrderAndFinishesInPostOrder(directed: Boolean) {
        constructDiamond(directed)

        val discovered = mutableListOf<Vertex>()
        val finished = mutableListOf<Vertex>()
        graph.visitDepthFirst(
            vertexSetOf(v0),
            onVertexDiscovered = { discovered += it },
            onVertexFinished = { finished += it },
        )

        // Whichever of v1/v2 is taken first leads to v3. Directed: that branch finishes before the other is
        // discovered. Undirected: the other is discovered from v3, so the search is one path and finishes in reverse.
        assertThat(discovered).hasSize(4)
        val first = discovered[1]
        val second = discovered[3]
        assertThat(listOf(first, second)).containsExactlyInAnyOrder(v1, v2)
        assertThat(discovered).containsExactly(v0, first, v3, second)
        if (directed) {
            assertThat(finished).containsExactly(v3, first, second, v0)
        } else {
            assertThat(finished).containsExactly(second, v3, first, v0)
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitDepthFirstStartsEachSearchTree(directed: Boolean) {
        constructDiamond(directed)

        val started = mutableListOf<Vertex>()
        graph.visitDepthFirst(vertexSetOf(v0, v4), onVertexStarted = { started += it })

        assertThat(started).containsExactlyInAnyOrder(v0, v4)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitDepthFirstClassifiesEdges(directed: Boolean) {
        constructDiamond(directed)

        val examinedEdges = mutableListOf<Edge>()
        val treeEdges = mutableListOf<Edge>()
        val backEdges = mutableListOf<Edge>()
        graph.visitDepthFirst(
            vertexSetOf(v0),
            onEdgeExamined = { examinedEdges += it },
            onTreeEdge = { treeEdges += it },
            onBackEdge = { backEdges += it },
        )

        assertThat(examinedEdges).hasSize(outDegreeSum(listOf(v0, v1, v2, v3)))
        assertThat(treeEdges).hasSize(3).doesNotHaveDuplicates()
        // The directed diamond is acyclic. In the undirected diamond every edge is reported as a back edge exactly
        // once: the non-tree edge when first examined from the descendant, and each tree edge when examined from the
        // child back towards its parent.
        assertThat(backEdges).hasSize(if (directed) 0 else 4)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitDepthFirstReportsBackEdgeOnCycle(directed: Boolean) {
        constructCycle(directed)

        val treeEdges = mutableListOf<Edge>()
        val backEdges = mutableListOf<Edge>()
        graph.visitDepthFirst(vertexSetOf(v0), onTreeEdge = { treeEdges += it }, onBackEdge = { backEdges += it })

        assertThat(treeEdges).hasSize(2)
        assertThat(backEdges).hasSize(if (directed) 1 else 3)
        if (directed) assertThat(graph.edgeTarget(backEdges.single())).isEqualTo(v0)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitDepthFirstPreOrderExaminesInPreOrder(directed: Boolean) {
        constructDiamond(directed)

        val examined = mutableListOf<Vertex>()
        val examinedEdges = mutableListOf<Edge>()
        val finished = mutableListOf<Vertex>()
        graph.visitDepthFirstPreOrder(
            vertexSetOf(v0),
            onVertexExamined = { examined += it },
            onEdgeExamined = { examinedEdges += it },
            onVertexFinished = { finished += it },
        )

        assertThat(examined).hasSize(4)
        assertThat(examined[0]).isEqualTo(v0)
        assertThat(examined[2]).isEqualTo(v3)
        assertThat(listOf(examined[1], examined[3])).containsExactlyInAnyOrder(v1, v2)
        assertThat(finished).isEqualTo(examined)
        assertThat(examinedEdges).hasSize(outDegreeSum(examined))
    }

    companion object {
        private fun <T> withDirectedness(cases: List<Pair<String, T>>): List<Arguments> {
            return cases.flatMap { (name, payload) ->
                listOf(true, false).map { directed -> arguments(named(name, payload), directed) }
            }
        }

        @JvmStatic
        fun vertexIterators(): List<Arguments> = withDirectedness<(Graph, VertexSet) -> VertexIterator>(listOf(
            "breadthFirstVertexIterator" to { g, s -> g.breadthFirstVertexIterator(s) },
            "depthFirstPreOrderVertexIterator" to { g, s -> g.depthFirstPreOrderVertexIterator(s) },
            "depthFirstPostOrderVertexIterator" to { g, s -> g.depthFirstPostOrderVertexIterator(s) },
        ))

        @JvmStatic
        fun treeEdgeIterators(): List<Arguments> = withDirectedness<(Graph, VertexSet) -> Iterator<PathStep>>(listOf(
            "breadthFirstTreeEdgeIterator" to { g, s -> g.breadthFirstTreeEdgeIterator(s) },
            "depthFirstTreeEdgeIterator" to { g, s -> g.depthFirstTreeEdgeIterator(s) },
        ))

        @JvmStatic
        fun traversals(): List<Arguments> = withDirectedness<(Graph, VertexSet) -> Unit>(listOf(
            "visitBreadthFirst" to { g, s -> g.visitBreadthFirst(s) },
            "visitDepthFirst" to { g, s -> g.visitDepthFirst(s) },
            "visitDepthFirstPreOrder" to { g, s -> g.visitDepthFirstPreOrder(s, {}, {}, {}) },
            "breadthFirstVertexIterator" to { g, s -> g.breadthFirstVertexIterator(s) },
            "depthFirstPreOrderVertexIterator" to { g, s -> g.depthFirstPreOrderVertexIterator(s) },
            "depthFirstPostOrderVertexIterator" to { g, s -> g.depthFirstPostOrderVertexIterator(s) },
            "breadthFirstTreeEdgeIterator" to { g, s -> g.breadthFirstTreeEdgeIterator(s) },
            "depthFirstTreeEdgeIterator" to { g, s -> g.depthFirstTreeEdgeIterator(s) },
        ))
    }
}
