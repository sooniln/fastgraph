package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class TraversalTest {

    private lateinit var graph: Graph
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var v3: Vertex = Vertex(-1)
    private var v4: Vertex = Vertex(-1)

    private fun construct(directed: Boolean, immutable: Boolean = false, multiEdge: Boolean = false, builder: GraphBuilder.() -> Unit) {
        graph = if (immutable) buildImmutableGraph(directed, multiEdge, builder = builder) else buildGraph(directed, multiEdge, builder = builder)
    }

    // v0 -> v1 -> v3, v0 -> v2 -> v3, v4 is disconnected
    private fun constructDiamond(directed: Boolean, immutable: Boolean = false) = construct(directed, immutable) {
        v0 = addVertex()
        v1 = addVertex()
        v2 = addVertex()
        v3 = addVertex()
        v4 = addVertex()
        addEdge(v0, v1)
        addEdge(v0, v2)
        addEdge(v1, v3)
        addEdge(v2, v3)
    }

    // v0 -> v1, v0 -> v2, v2 -> v1: v1 is reachable both directly and via v2
    private fun constructShortcut(directed: Boolean, immutable: Boolean = false) = construct(directed, immutable) {
        v0 = addVertex()
        v1 = addVertex()
        v2 = addVertex()
        addEdge(v0, v1)
        addEdge(v0, v2)
        addEdge(v2, v1)
    }

    // v0 -> v1 -> v2 -> v0
    private fun constructCycle(directed: Boolean, immutable: Boolean = false) = construct(directed, immutable) {
        v0 = addVertex()
        v1 = addVertex()
        v2 = addVertex()
        addEdge(v0, v1)
        addEdge(v1, v2)
        addEdge(v2, v0)
    }

    // v0 => v1 (two parallel edges), v0 -> v0 (self-loop), v1 -> v2
    private fun constructMulti(directed: Boolean, immutable: Boolean = false) = construct(directed, immutable, multiEdge = true) {
        v0 = addVertex()
        v1 = addVertex()
        v2 = addVertex()
        addEdge(v0, v1)
        addEdge(v0, v1)
        addEdge(v0, v0)
        addEdge(v1, v2)
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
    fun treeEdgesFormSpanningForest(iterator: (Graph, VertexSet) -> Iterator<Step>, directed: Boolean) {
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
            onVertexDiscovered = { examined += it },
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
        fun treeEdgeIterators(): List<Arguments> = withDirectedness<(Graph, VertexSet) -> Iterator<Step>>(listOf(
            "breadthFirstTreeEdgeIterator" to { g, s -> g.breadthFirstTreeEdgeIterator(s) },
            "depthFirstTreeEdgeIterator" to { g, s -> g.depthFirstTreeEdgeIterator(s) },
        ))

        @JvmStatic
        fun traversals(): List<Arguments> = withDirectedness<(Graph, VertexSet) -> Unit>(listOf(
            "visitBreadthFirst" to { g, s -> g.visitBreadthFirst(s) },
            "visitDepthFirst" to { g, s -> g.visitDepthFirst(s) },
            "visitDepthFirstPreOrder" to { g, s -> g.visitDepthFirstPreOrder(s) },
            "breadthFirstVertexIterator" to { g, s -> g.breadthFirstVertexIterator(s) },
            "depthFirstPreOrderVertexIterator" to { g, s -> g.depthFirstPreOrderVertexIterator(s) },
            "depthFirstPostOrderVertexIterator" to { g, s -> g.depthFirstPostOrderVertexIterator(s) },
            "breadthFirstTreeEdgeIterator" to { g, s -> g.breadthFirstTreeEdgeIterator(s) },
            "depthFirstTreeEdgeIterator" to { g, s -> g.depthFirstTreeEdgeIterator(s) },
        ))
    }

    // --- single-vertex overloads and other graph implementations --------------------------------------------------

    @ParameterizedTest(name = "directed={0}, immutable={1}")
    @CsvSource("true,true", "true,false", "false,true", "false,false")
    fun singleVertexOverloadsMatchTheSetOverloads(directed: Boolean, immutable: Boolean) {
        constructDiamond(directed, immutable)

        assertThat(graph.breadthFirstVertexIterator(v0).toList())
            .isEqualTo(graph.breadthFirstVertexIterator(vertexSetOf(v0)).toList())
        assertThat(graph.depthFirstPreOrderVertexIterator(v0).toList())
            .isEqualTo(graph.depthFirstPreOrderVertexIterator(vertexSetOf(v0)).toList())
        assertThat(graph.depthFirstPostOrderVertexIterator(v0).toList())
            .isEqualTo(graph.depthFirstPostOrderVertexIterator(vertexSetOf(v0)).toList())
        assertThat(graph.breadthFirstTreeEdgeIterator(v0).asSequence().map { it.edge to it.vertex }.toList())
            .isEqualTo(graph.breadthFirstTreeEdgeIterator(vertexSetOf(v0)).asSequence().map { it.edge to it.vertex }.toList())
        assertThat(graph.depthFirstTreeEdgeIterator(v0).asSequence().map { it.edge to it.vertex }.toList())
            .isEqualTo(graph.depthFirstTreeEdgeIterator(vertexSetOf(v0)).asSequence().map { it.edge to it.vertex }.toList())

        for (iterator in listOf(
            graph.breadthFirstVertexIterator(v4),
            graph.depthFirstPreOrderVertexIterator(v4),
            graph.depthFirstPostOrderVertexIterator(v4),
        )) {
            assertThat(iterator.toList()).containsExactly(v4)
        }
        assertThat(graph.breadthFirstTreeEdgeIterator(v4).hasNext()).isFalse
        assertThat(graph.depthFirstTreeEdgeIterator(v4).hasNext()).isFalse

        assertThrows<IllegalArgumentException> { graph.breadthFirstVertexIterator(Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.depthFirstPreOrderVertexIterator(Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.depthFirstPostOrderVertexIterator(Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.breadthFirstTreeEdgeIterator(Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.depthFirstTreeEdgeIterator(Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.breadthFirstPathTree(Vertex(99)) }
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("vertexIterators")
    fun vertexIteratorsWorkOnImmutableGraphs(iterator: (Graph, VertexSet) -> VertexIterator, directed: Boolean) {
        constructDiamond(directed, immutable = true)

        val result = iterator(graph, vertexSetOf(v0, v3, v4)).toList()

        assertThat(result).containsExactlyInAnyOrder(v0, v1, v2, v3, v4)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("vertexIterators")
    fun vertexIteratorsAreExhaustedExactlyOnce(iterator: (Graph, VertexSet) -> VertexIterator, directed: Boolean) {
        constructCycle(directed)

        val it = iterator(graph, vertexSetOf(v0))
        val seen = mutableListOf<Vertex>()
        while (it.hasNext()) {
            // hasNext does not advance
            assertThat(it.hasNext()).isTrue
            seen += it.next()
        }

        assertThat(seen).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(it.hasNext()).isFalse
        assertThrows<NoSuchElementException> { it.next() }
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("treeEdgeIterators")
    fun treeEdgeIteratorsAreExhaustedExactlyOnce(iterator: (Graph, VertexSet) -> Iterator<Step>, directed: Boolean) {
        constructCycle(directed)

        val it = iterator(graph, vertexSetOf(v0))
        val seen = mutableListOf<Vertex>()
        while (it.hasNext()) {
            assertThat(it.hasNext()).isTrue
            seen += it.next().vertex
        }

        assertThat(seen).containsExactlyInAnyOrder(v1, v2)
        assertThat(it.hasNext()).isFalse
        assertThrows<NoSuchElementException> { it.next() }
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("treeEdgeIterators")
    fun treeEdgeIteratorsNeverDiscoverInitialVertices(iterator: (Graph, VertexSet) -> Iterator<Step>, directed: Boolean) {
        constructDiamond(directed)

        // every initial vertex is a root, so v3 is not discovered through an edge even though v0 reaches it - and
        // this does not depend on the (unspecified) order in which the initial vertices are listed
        for (roots in listOf(vertexSetOf(v0, v3), vertexSetOf(v3, v0))) {
            val steps = iterator(graph, roots).asSequence().toList()
            assertThat(steps.map { it.vertex }).containsExactlyInAnyOrder(v1, v2)
        }
        for (roots in listOf(vertexSetOf(v1, v2), vertexSetOf(v0, v1, v2, v3))) {
            val steps = iterator(graph, roots).asSequence().toList()
            assertThat(steps.map { it.vertex }).containsExactlyInAnyOrderElementsOf(
                if (directed) listOf(v3) - roots.toList() else listOf(v0, v3) - roots.toList()
            )
        }
    }

    // --- networks: parallel edges and self-loops ----------------------------------------------------------------------

    @ParameterizedTest(name = "directed={0}, immutable={1}")
    @CsvSource("true,true", "true,false", "false,true", "false,false")
    fun parallelEdgesAndSelfLoopsAreNeverTreeEdges(directed: Boolean, immutable: Boolean) {
        constructMulti(directed, immutable)
        val loop = graph.edge(v0, v0)
        val parallel = graph.edges(v0, v1)
        assertThat(parallel).hasSize(2)

        for (iterator in listOf(graph.breadthFirstVertexIterator(v0), graph.depthFirstPreOrderVertexIterator(v0), graph.depthFirstPostOrderVertexIterator(v0))) {
            assertThat(iterator.toList()).containsExactlyInAnyOrder(v0, v1, v2)
        }

        for (steps in listOf(graph.breadthFirstTreeEdgeIterator(v0), graph.depthFirstTreeEdgeIterator(v0))) {
            val list = steps.asSequence().toList()
            assertThat(list.map { it.vertex }).containsExactlyInAnyOrder(v1, v2)
            val toV1 = list.single { it.vertex == v1 }
            assertThat(toV1.edge).isIn(parallel)
            assertThat(list.map { it.edge }).doesNotContain(loop)
        }

        val treeEdges = mutableListOf<Edge>()
        val examined = mutableListOf<Edge>()
        val backEdges = mutableListOf<Edge>()
        graph.visitBreadthFirst(vertexSetOf(v0), onTreeEdge = { treeEdges += it }, onEdgeExamined = { examined += it })
        assertThat(treeEdges).hasSize(2)
        assertThat(treeEdges).doesNotContain(loop)
        assertThat(treeEdges.filter { it in parallel }).hasSize(1)
        assertThat(examined.filter { it == loop }).hasSize(1)
        assertThat(examined.filter { it in parallel }).hasSize(if (directed) 2 else 4)

        treeEdges.clear()
        graph.visitDepthFirst(vertexSetOf(v0), onTreeEdge = { treeEdges += it }, onBackEdge = { backEdges += it })
        assertThat(treeEdges).hasSize(2)
        assertThat(treeEdges).doesNotContain(loop)
        assertThat(treeEdges.filter { it in parallel }).hasSize(1)
        // the self-loop is always a back edge, and so is the second of the parallel edges when it is examined while
        // v1 is still on the stack
        assertThat(backEdges).contains(loop)

        val tree = graph.breadthFirstPathTree(v0)
        assertThat(tree.edges).hasSize(2)
        assertThat(tree.edges.contains(loop)).isFalse
        assertThat(tree.materializePath(v1).edges.single()).isIn(parallel)
    }

    // --- edge classification ------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitDepthFirstReportsForwardAndCrossEdgesOnlyAsExamined(directed: Boolean) {
        constructDiamond(directed)

        val examined = mutableListOf<Edge>()
        val tree = mutableListOf<Edge>()
        val back = mutableListOf<Edge>()
        graph.visitDepthFirst(vertexSetOf(v0), onEdgeExamined = { examined += it }, onTreeEdge = { tree += it }, onBackEdge = { back += it })

        // exactly one examination is neither a tree edge nor a back edge: the edge examined towards an already
        // finished vertex (directed: the second edge into v3; undirected: the non-tree edge, re-examined from the
        // other side once the first side has finished)
        assertThat(examined.size - tree.size - back.size).isEqualTo(1)
        if (directed) {
            val crossing = examined.single { it !in tree && it !in back }
            assertThat(graph.edgeTarget(crossing)).isEqualTo(v3)
            assertThat(examined).doesNotHaveDuplicates()
        } else {
            // every undirected edge is examined from both endpoints
            assertThat(examined).hasSize(2 * graph.edges.size)
            for (edge in graph.edges) assertThat(examined.count { it == edge }).isEqualTo(2)
            val nonTree = graph.edges.single { it !in tree }
            assertThat(back).contains(nonTree)
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitBreadthFirstExaminesUndirectedEdgesFromBothEndpoints(directed: Boolean) {
        constructDiamond(directed)

        val examined = mutableListOf<Edge>()
        graph.visitBreadthFirst(vertexSetOf(v0), onEdgeExamined = { examined += it })

        for (edge in graph.edges) {
            assertThat(examined.count { it == edge }).isEqualTo(if (directed) 1 else 2)
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitorsAcceptDefaultCallbacks(directed: Boolean) {
        constructDiamond(directed)

        graph.visitBreadthFirst(vertexSetOf(v0))
        graph.visitDepthFirst(vertexSetOf(v0))
        graph.visitDepthFirstPreOrder(vertexSetOf(v0))

        val discovered = mutableListOf<Vertex>()
        graph.visitDepthFirstPreOrder(vertexSetOf(v0), onVertexDiscovered = { discovered += it })
        assertThat(discovered).containsExactlyInAnyOrder(v0, v1, v2, v3)
    }

    // --- other graph views --------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun traversalsRunOverViews(directed: Boolean) {
        constructDiamond(directed)

        // filtered: drop v2, so v3 is only reachable through v1
        val filtered = graph.filter(vertexSetOf(v0, v1, v3, v4), graph.edges)
        assertThat(filtered.breadthFirstVertexIterator(v0).toList()).containsExactly(v0, v1, v3)
        assertThat(filtered.depthFirstPostOrderVertexIterator(v0).toList()).containsExactly(v3, v1, v0)
        assertThat(filtered.breadthFirstTreeEdgeIterator(v0).asSequence().map { it.vertex }.toList()).containsExactly(v1, v3)
        assertThat(filtered.breadthFirstPathTree(v0).materializePath(v3).vertices).containsExactly(v0, v1, v3)
        val predicated = graph.filter({ it != v1 }, { true })
        assertThat(predicated.breadthFirstVertexIterator(v0).toList()).containsExactly(v0, v2, v3)

        if (directed) {
            // transposed: v3 reaches everything but v4
            val transposed = graph.transpose()
            assertThat(transposed.breadthFirstVertexIterator(v3).toList()).containsExactlyInAnyOrder(v0, v1, v2, v3)
            assertThat(transposed.breadthFirstVertexIterator(v3).toList().first()).isEqualTo(v3)
            assertThat(transposed.depthFirstPostOrderVertexIterator(v3).toList().last()).isEqualTo(v3)
            assertThat(transposed.breadthFirstVertexIterator(v0).toList()).containsExactly(v0)
            assertThat(transposed.depthFirstTreeEdgeIterator(v3).asSequence().map { it.vertex }.toList())
                .containsExactlyInAnyOrder(v0, v1, v2)
        }

        // a path tree: every vertex walks back to the root
        val tree = graph.breadthFirstPathTree(v0)
        assertThat(tree.breadthFirstVertexIterator(v3).toList()).hasSize(3)
        assertThat(tree.breadthFirstVertexIterator(v3).toList().last()).isEqualTo(v0)
        assertThat(tree.depthFirstPreOrderVertexIterator(v3).toList().first()).isEqualTo(v3)
        assertThat(tree.transpose().breadthFirstVertexIterator(v0).toList()).containsExactlyInAnyOrder(v0, v1, v2, v3)
        val steps = tree.transpose().breadthFirstTreeEdgeIterator(v0).asSequence().toList()
        assertThat(steps.map { it.vertex }).containsExactlyInAnyOrder(v1, v2, v3)
        assertThat(steps.map { it.edge }).containsExactlyInAnyOrderElementsOf(tree.edges)
    }
}
