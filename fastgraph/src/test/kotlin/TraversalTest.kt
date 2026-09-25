package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.filtered.filter
import io.github.sooniln.fastgraph.paths.Step
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
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

    // traversals examine each outgoing edge once, whereas outDegree counts undirected self-loops twice
    private fun outDegreeSum(vertices: Collection<Vertex>): Int = vertices.sumOf { graph.outgoingEdgeCount(it) }

    // --- Argument validation, common to every traversal -------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("multiVertexTraversals")
    fun requiresNonEmptyInitialVertices(traversal: (Graph, VertexSet) -> Unit, directed: Boolean) {
        constructDiamond(directed)

        assertThrows<IllegalArgumentException> { traversal(graph, emptyVertexSet()) }
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("multiVertexTraversals")
    fun requiresInitialVerticesInGraph(traversal: (Graph, VertexSet) -> Unit, directed: Boolean) {
        constructDiamond(directed)

        assertThrows<IllegalArgumentException> { traversal(graph, vertexSetOf(v0, Vertex(99))) }
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("singleVertexTraversals")
    fun requiresInitialVertexInGraph(traversal: (Graph, Vertex) -> Unit, directed: Boolean) {
        constructDiamond(directed)

        assertThrows<IllegalArgumentException> { traversal(graph, Vertex(99)) }
    }

    // --- Vertex iterators ---------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("vertexIterators")
    fun vertexIteratorVisitsEachReachableVertexOnce(iterator: (Graph, Vertex) -> VertexIterator, directed: Boolean) {
        constructDiamond(directed)

        // v3 is reachable both through v1 and through v2 and must only be visited once.
        val result = iterator(graph, v0).toList()

        assertThat(result).containsExactlyInAnyOrder(v0, v1, v2, v3)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun breadthFirstVertexIteratorVisitsEachReachableVertexOnceFromSeveralVertices(directed: Boolean) {
        constructDiamond(directed)

        // v3 is also reachable from v0 and must not be visited a second time on its own account.
        val result = graph.breadthFirstVertexIterator(vertexSetOf(v0, v3, v4)).toList()

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

        val result = graph.depthFirstPreOrderVertexIterator(v0).toList()

        assertThat(result).hasSize(4)
        assertThat(result[0]).isEqualTo(v0)
        assertThat(result[2]).isEqualTo(v3)
        assertThat(listOf(result[1], result[3])).containsExactlyInAnyOrder(v1, v2)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPostOrderFinishesDescendantsFirst(directed: Boolean) {
        constructDiamond(directed)

        val result = graph.depthFirstPostOrderVertexIterator(v0).toList()

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
        val result = graph.depthFirstPostOrderVertexIterator(v0).toList()

        assertThat(result).containsExactly(v1, v2, v0)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPostOrderTerminatesOnCycle(directed: Boolean) {
        constructCycle(directed)

        val result = graph.depthFirstPostOrderVertexIterator(v0).toList()

        assertThat(result).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(result.last()).isEqualTo(v0)
    }

    // --- Tree edge iterators ------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("treeEdgeIterators")
    fun treeEdgesFormSpanningTree(iterator: (Graph, Vertex) -> Iterator<Step>, directed: Boolean) {
        constructDiamond(directed)

        val steps = iterator(graph, v0).asSequence().toList()

        // The root has no tree edge; every other reachable vertex is discovered exactly once.
        assertThat(steps.map { it.target }).containsExactlyInAnyOrder(v1, v2, v3)
        // Each tree edge leads from an already discovered vertex to the newly discovered one.
        val discovered = mutableSetOf(v0)
        for (step in steps) {
            assertThat(graph.edgeOpposite(step.edge, step.target)).isIn(discovered)
            if (directed) assertThat(graph.edgeTarget(step.edge)).isEqualTo(step.target)
            discovered += step.target
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun breadthFirstTreeEdgesFollowShortestPaths(directed: Boolean) {
        constructShortcut(directed)

        val steps = graph.breadthFirstTreeEdgeIterator(vertexSetOf(v0)).asSequence().toList()

        val stepToV1 = steps.single { it.target == v1 }
        assertThat(graph.edgeOpposite(stepToV1.edge, v1)).isEqualTo(v0)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstTreeEdgesAreInPreOrder(directed: Boolean) {
        constructDiamond(directed)

        val vertices = graph.depthFirstTreeEdgeIterator(v0).asSequence().map { it.target }.toList()

        assertThat(vertices).hasSize(3)
        assertThat(vertices[1]).isEqualTo(v3)
        assertThat(listOf(vertices[0], vertices[2])).containsExactlyInAnyOrder(v1, v2)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPathForestIsTheDepthFirstSearchTree(directed: Boolean) {
        constructDiamond(directed)

        val treeEdges = mutableListOf<Edge>()
        graph.visitDepthFirst(v0, onTreeEdge = { treeEdges += it })

        val tree = graph.depthFirstPathForest(v0)
        assertThat(tree.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
        assertThat(tree.edges).containsExactlyInAnyOrderElementsOf(treeEdges)
        // tree edges point from child back towards the root, and join the same vertices they do in the graph
        for (edge in tree.edges) {
            val child = tree.edgeSource(edge)
            val parent = tree.edgeTarget(edge)
            assertThat(graph.edgeOpposite(edge, child)).isEqualTo(parent)
            assertThat(tree.pathLengthProperty[child]).isEqualTo(tree.pathLengthProperty[parent] + 1)
        }
        assertThat(tree.materializePath(v0).vertices).containsExactly(v0)
        assertThat(tree.materializePath(v3).vertices).hasSize(3)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun breadthFirstPathForestPlacesEachVertexUnderExactlyOneRoot(directed: Boolean) {
        constructDiamond(directed)

        val result = graph.breadthFirstPathForest(vertexSetOf(v1, v2, v4))

        assertThat(result.roots).containsExactlyInAnyOrder(v1, v2, v4)
        assertThat(result.vertices).containsExactlyInAnyOrderElementsOf(if (directed) listOf(v1, v2, v3, v4) else listOf(v0, v1, v2, v3, v4))
        assertThat(result.edges).hasSize(if (directed) 1 else 2)
        for (root in result.roots) {
            assertThat(result.pathLengthProperty[root]).isEqualTo(0)
            assertThat(result.materializePath(root).vertices).containsExactly(root)
        }
        // v3 (and v0 when undirected) is adjacent to both v1 and v2 and is claimed by exactly one of them
        assertThat(result.materializePath(v3).vertices.first()).isIn(v1, v2)
        assertThat(result.pathLengthProperty[v3]).isEqualTo(1)
        if (!directed) {
            assertThat(result.materializePath(v0).vertices.first()).isIn(v1, v2)
            assertThat(result.pathLengthProperty[v0]).isEqualTo(1)
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun breadthFirstPathForestNeverDiscoversARootThroughAnEdge(directed: Boolean) {
        // v0 -> v1 -> v2 -> v3, rooted at both ends: v3 is reachable from v0 but must stay a root
        construct(directed) {
            v0 = addVertex()
            v1 = addVertex()
            v2 = addVertex()
            v3 = addVertex()
            addEdge(v0, v1)
            addEdge(v1, v2)
            addEdge(v2, v3)
        }

        val bfs = graph.breadthFirstPathForest(vertexSetOf(v0, v3))
        assertThat(bfs.roots).containsExactlyInAnyOrder(v0, v3)
        assertThat(bfs.materializePath(v1).vertices.first()).isEqualTo(v0)
        assertThat(bfs.pathLengthProperty[v3]).isEqualTo(0)
        // breadth-first places v2 under the nearest root, which is v3 when the edge can be walked backwards
        assertThat(bfs.materializePath(v2).vertices.first()).isEqualTo(if (directed) v0 else v3)
        assertThat(bfs.pathLengthProperty[v2]).isEqualTo(if (directed) 2 else 1)
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
            v0,
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
    fun visitDepthFirstStartsTheInitialVertex(directed: Boolean) {
        constructDiamond(directed)

        val events = mutableListOf<String>()
        graph.visitDepthFirst(
            v0,
            onVertexStarted = { events += "started $it" },
            onVertexDiscovered = { events += "discovered $it" },
        )

        assertThat(events.take(2)).containsExactly("started $v0", "discovered $v0")
        assertThat(events.filter { it.startsWith("started") }).hasSize(1)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitDepthFirstClassifiesEdges(directed: Boolean) {
        constructDiamond(directed)

        val examinedEdges = mutableListOf<Edge>()
        val treeEdges = mutableListOf<Edge>()
        val backEdges = mutableListOf<Edge>()
        graph.visitDepthFirst(
            v0,
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
        graph.visitDepthFirst(v0, onTreeEdge = { treeEdges += it }, onBackEdge = { backEdges += it })

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
            v0,
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

    // --- Visitor pruning ----------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitBreadthFirstPrunesAtVertex(directed: Boolean) {
        constructDiamond(directed)

        val colors = graph.createVertexProperty { VertexColors.WHITE }
        val discovered = mutableListOf<Vertex>()
        val examined = mutableListOf<Vertex>()
        val examinedEdges = mutableListOf<Edge>()
        val finished = mutableListOf<Vertex>()
        graph.visitBreadthFirst(
            vertexSetOf(v0),
            onVertexDiscovered = { discovered += it },
            onVertexExamined = { examined += it },
            shouldExpand = { it != v1 && it != v2 },
            onEdgeExamined = { examinedEdges += it },
            onVertexFinished = { finished += it },
            colors = colors,
        )

        // v3 is only reachable through the pruned vertices
        assertThat(discovered).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(examined).isEqualTo(discovered)
        assertThat(finished).isEqualTo(examined)
        assertThat(examinedEdges).hasSize(graph.outDegree(v0))
        assertThat(colors[v1]).isEqualTo(VertexColors.BLACK)
        assertThat(colors[v2]).isEqualTo(VertexColors.BLACK)
        assertThat(colors[v3]).isEqualTo(VertexColors.WHITE)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitDepthFirstPrunesAtVertex(directed: Boolean) {
        constructDiamond(directed)

        val colors = graph.createVertexProperty { VertexColors.WHITE }
        val discovered = mutableListOf<Vertex>()
        val examinedEdges = mutableListOf<Edge>()
        val treeEdges = mutableListOf<Edge>()
        val finished = mutableListOf<Vertex>()
        graph.visitDepthFirst(
            v0,
            onVertexDiscovered = { discovered += it },
            shouldExpand = { it != v1 && it != v2 },
            onEdgeExamined = { examinedEdges += it },
            onTreeEdge = { treeEdges += it },
            onVertexFinished = { finished += it },
            colors = colors,
        )

        // v3 is only reachable through the pruned vertices, which are finished as leaves
        assertThat(discovered).hasSize(3)
        assertThat(discovered[0]).isEqualTo(v0)
        assertThat(discovered.subList(1, 3)).containsExactlyInAnyOrder(v1, v2)
        assertThat(finished).containsExactly(discovered[1], discovered[2], v0)
        assertThat(examinedEdges).hasSize(graph.outDegree(v0))
        assertThat(treeEdges).hasSize(2)
        assertThat(colors[v1]).isEqualTo(VertexColors.BLACK)
        assertThat(colors[v2]).isEqualTo(VertexColors.BLACK)
        assertThat(colors[v3]).isEqualTo(VertexColors.WHITE)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitDepthFirstPrunesAtInitialVertex(directed: Boolean) {
        constructDiamond(directed)

        val colors = graph.createVertexProperty { VertexColors.WHITE }
        val discovered = mutableListOf<Vertex>()
        val examinedEdges = mutableListOf<Edge>()
        val finished = mutableListOf<Vertex>()
        graph.visitDepthFirst(
            v0,
            onVertexDiscovered = { discovered += it },
            shouldExpand = { false },
            onEdgeExamined = { examinedEdges += it },
            onVertexFinished = { finished += it },
            colors = colors,
        )

        assertThat(discovered).containsExactly(v0)
        assertThat(finished).containsExactly(v0)
        assertThat(examinedEdges).isEmpty()
        assertThat(colors[v0]).isEqualTo(VertexColors.BLACK)
        assertThat(colors[v1]).isEqualTo(VertexColors.WHITE)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitDepthFirstPreOrderPrunesAtVertex(directed: Boolean) {
        constructDiamond(directed)

        val colors = graph.createVertexProperty { VertexColors.WHITE }
        val discovered = mutableListOf<Vertex>()
        val examinedEdges = mutableListOf<Edge>()
        val finished = mutableListOf<Vertex>()
        graph.visitDepthFirstPreOrder(
            v0,
            onVertexDiscovered = { discovered += it },
            shouldExpand = { it != v1 && it != v2 },
            onEdgeExamined = { examinedEdges += it },
            onVertexFinished = { finished += it },
            colors,
        )

        // v3 is only reachable through the pruned vertices
        assertThat(discovered).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(discovered[0]).isEqualTo(v0)
        assertThat(finished).isEqualTo(discovered)
        assertThat(examinedEdges).hasSize(graph.outDegree(v0))
        assertThat(colors[v1]).isEqualTo(VertexColors.BLACK)
        assertThat(colors[v2]).isEqualTo(VertexColors.BLACK)
        assertThat(colors[v3]).isEqualTo(VertexColors.WHITE)
    }

    // --- Visitor colors -----------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitorsColorVerticesAsTheyGo(directed: Boolean) {
        constructDiamond(directed)

        val runs: List<(MutableVertexProperty<Byte>, (Vertex) -> Unit, (Vertex) -> Unit) -> Unit> = listOf(
            { colors, discovered, finished -> graph.visitBreadthFirst(
                vertexSetOf(v0),
                onVertexDiscovered = discovered,
                onVertexFinished = finished,
                colors = colors
            ) },
            { colors, discovered, finished -> graph.visitDepthFirst(
                v0,
                onVertexDiscovered = discovered,
                onVertexFinished = finished,
                colors = colors
            ) },
            { colors, discovered, finished -> graph.visitDepthFirstPreOrder(
                v0,
                onVertexDiscovered = discovered,
                onVertexFinished = finished,
                colors = colors
            ) },
        )
        for (run in runs) {
            val colors = graph.createVertexProperty { VertexColors.WHITE }
            val discovered = mutableListOf<Vertex>()
            val finished = mutableListOf<Vertex>()
            run(
                colors,
                { assertThat(colors[it]).isEqualTo(VertexColors.GRAY); discovered += it },
                { assertThat(colors[it]).isEqualTo(VertexColors.BLACK); finished += it },
            )

            assertThat(discovered).containsExactlyInAnyOrder(v0, v1, v2, v3)
            assertThat(finished).containsExactlyInAnyOrderElementsOf(discovered)
            for (vertex in listOf(v0, v1, v2, v3)) assertThat(colors[vertex]).isEqualTo(VertexColors.BLACK)
            assertThat(colors[v4]).isEqualTo(VertexColors.WHITE)
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitDepthFirstContinuesWithSharedColors(directed: Boolean) {
        constructDiamond(directed)
        val colors = graph.createVertexProperty { VertexColors.WHITE }
        val started = mutableListOf<Vertex>()
        val discovered = mutableListOf<Vertex>()
        val treeEdges = mutableListOf<Edge>()

        graph.visitDepthFirst(
            v0,
            onVertexStarted = { started += it },
            onVertexDiscovered = { discovered += it },
            onTreeEdge = { treeEdges += it },
            colors = colors
        )
        assertThat(started).containsExactly(v0)
        assertThat(discovered).containsExactlyInAnyOrder(v0, v1, v2, v3)
        assertThat(treeEdges).hasSize(3)

        // v3 was reached by the first search, so nothing happens
        graph.visitDepthFirst(
            v3,
            onVertexStarted = { started += it },
            onVertexDiscovered = { discovered += it },
            onTreeEdge = { treeEdges += it },
            colors = colors
        )
        assertThat(started).containsExactly(v0)
        assertThat(discovered).hasSize(4)
        assertThat(treeEdges).hasSize(3)

        // v4 was not, so it starts a tree of its own
        graph.visitDepthFirst(
            v4,
            onVertexStarted = { started += it },
            onVertexDiscovered = { discovered += it },
            onTreeEdge = { treeEdges += it },
            colors = colors
        )
        assertThat(started).containsExactly(v0, v4)
        assertThat(discovered).containsExactlyInAnyOrder(v0, v1, v2, v3, v4)
        assertThat(treeEdges).hasSize(3)
        for (vertex in graph.vertices) assertThat(colors[vertex]).isEqualTo(VertexColors.BLACK)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitDepthFirstPreOrderContinuesWithSharedColors(directed: Boolean) {
        constructDiamond(directed)
        val colors = graph.createVertexProperty { VertexColors.WHITE }
        val discovered = mutableListOf<Vertex>()

        graph.visitDepthFirstPreOrder(v0, onVertexDiscovered = { discovered += it }, colors = colors)
        graph.visitDepthFirstPreOrder(v3, onVertexDiscovered = { discovered += it }, colors = colors)
        assertThat(discovered).containsExactlyInAnyOrder(v0, v1, v2, v3)
        graph.visitDepthFirstPreOrder(v4, onVertexDiscovered = { discovered += it }, colors = colors)
        assertThat(discovered).containsExactlyInAnyOrder(v0, v1, v2, v3, v4)
        for (vertex in graph.vertices) assertThat(colors[vertex]).isEqualTo(VertexColors.BLACK)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitBreadthFirstSkipsAlreadyVisitedInitialVertices(directed: Boolean) {
        constructDiamond(directed)
        val colors = graph.createVertexProperty { VertexColors.WHITE }
        val discovered = mutableListOf<Vertex>()
        val examined = mutableListOf<Vertex>()

        graph.visitBreadthFirst(
            vertexSetOf(v0),
            onVertexDiscovered = { discovered += it },
            onVertexExamined = { examined += it },
            colors = colors
        )
        assertThat(discovered).containsExactlyInAnyOrder(v0, v1, v2, v3)

        graph.visitBreadthFirst(
            vertexSetOf(v3, v4),
            onVertexDiscovered = { discovered += it },
            onVertexExamined = { examined += it },
            colors = colors
        )
        assertThat(discovered).containsExactlyInAnyOrder(v0, v1, v2, v3, v4)
        assertThat(examined).containsExactlyInAnyOrder(v0, v1, v2, v3, v4)

        graph.visitBreadthFirst(
            vertexSetOf(v3),
            onVertexDiscovered = { discovered += it },
            onVertexExamined = { examined += it },
            colors = colors
        )
        assertThat(discovered).hasSize(5)
        assertThat(examined).hasSize(5)
    }

    companion object {
        private fun <T> withDirectedness(cases: List<Pair<String, T>>): List<Arguments> {
            return cases.flatMap { (name, payload) ->
                listOf(true, false).map { directed -> arguments(named(name, payload), directed) }
            }
        }

        /** Every vertex iterator, started from a single vertex. */
        @JvmStatic
        fun vertexIterators(): List<Arguments> = withDirectedness<(Graph, Vertex) -> VertexIterator>(listOf(
            "breadthFirstVertexIterator" to { g, v -> g.breadthFirstVertexIterator(v) },
            "depthFirstPreOrderVertexIterator" to { g, v -> g.depthFirstPreOrderVertexIterator(v) },
            "depthFirstPostOrderVertexIterator" to { g, v -> g.depthFirstPostOrderVertexIterator(v) },
        ))

        /** Every tree edge iterator, started from a single vertex. */
        @JvmStatic
        fun treeEdgeIterators(): List<Arguments> = withDirectedness<(Graph, Vertex) -> Iterator<Step>>(listOf(
            "breadthFirstTreeEdgeIterator" to { g, v -> g.breadthFirstTreeEdgeIterator(v) },
            "depthFirstTreeEdgeIterator" to { g, v -> g.depthFirstTreeEdgeIterator(v) },
        ))

        /** Every traversal which accepts a set of initial vertices; the depth-first traversals only take one. */
        @JvmStatic
        fun multiVertexTraversals(): List<Arguments> = withDirectedness<(Graph, VertexSet) -> Unit>(listOf(
            "visitBreadthFirst" to { g, s -> g.visitBreadthFirst(s) },
            "breadthFirstVertexIterator" to { g, s -> g.breadthFirstVertexIterator(s) },
            "breadthFirstTreeEdgeIterator" to { g, s -> g.breadthFirstTreeEdgeIterator(s) },
            "breadthFirstPathForest" to { g, s -> g.breadthFirstPathForest(s) },
        ))

        /** Every traversal, started from a single vertex. */
        @JvmStatic
        fun singleVertexTraversals(): List<Arguments> = withDirectedness<(Graph, Vertex) -> Unit>(listOf(
            "visitBreadthFirst" to { g, v -> g.visitBreadthFirst(vertexSetOf(v)) },
            "visitDepthFirst" to { g, v -> g.visitDepthFirst(v) },
            "visitDepthFirstPreOrder" to { g, v -> g.visitDepthFirstPreOrder(v) },
            "breadthFirstVertexIterator" to { g, v -> g.breadthFirstVertexIterator(v) },
            "depthFirstPreOrderVertexIterator" to { g, v -> g.depthFirstPreOrderVertexIterator(v) },
            "depthFirstPostOrderVertexIterator" to { g, v -> g.depthFirstPostOrderVertexIterator(v) },
            "breadthFirstTreeEdgeIterator" to { g, v -> g.breadthFirstTreeEdgeIterator(v) },
            "depthFirstTreeEdgeIterator" to { g, v -> g.depthFirstTreeEdgeIterator(v) },
            "breadthFirstPathForest" to { g, v -> g.breadthFirstPathForest(v) },
            "depthFirstPathForest" to { g, v -> g.depthFirstPathForest(v) },
        ))
    }

    // --- single-vertex overloads and other graph implementations --------------------------------------------------

    @ParameterizedTest(name = "directed={0}, immutable={1}")
    @CsvSource("true,true", "true,false", "false,true", "false,false")
    fun singleVertexOverloadsMatchTheSetOverloads(directed: Boolean, immutable: Boolean) {
        constructDiamond(directed, immutable)

        assertThat(graph.breadthFirstVertexIterator(v0).toList())
            .isEqualTo(graph.breadthFirstVertexIterator(vertexSetOf(v0)).toList())
        assertThat(graph.breadthFirstTreeEdgeIterator(v0).asSequence().map { it.edge to it.target }.toList())
            .isEqualTo(graph.breadthFirstTreeEdgeIterator(vertexSetOf(v0)).asSequence().map { it.edge to it.target }.toList())

        for (iterator in listOf(
            graph.breadthFirstVertexIterator(v4),
            graph.depthFirstPreOrderVertexIterator(v4),
            graph.depthFirstPostOrderVertexIterator(v4),
        )) {
            assertThat(iterator.toList()).containsExactly(v4)
        }
        assertThat(graph.breadthFirstTreeEdgeIterator(v4).hasNext()).isFalse
        assertThat(graph.depthFirstTreeEdgeIterator(v4).hasNext()).isFalse
        for (forest in listOf(graph.breadthFirstPathForest(v0), graph.depthFirstPathForest(v0))) {
            assertThat(forest.roots).containsExactly(v0)
            assertThat(forest.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
        }
        assertThat(graph.breadthFirstPathForest(v0).edges).isEqualTo(graph.breadthFirstPathForest(vertexSetOf(v0)).edges)
        assertThat(graph.breadthFirstPathForest(v4).vertices).containsExactly(v4)
        assertThat(graph.depthFirstPathForest(v4).edges).isEmpty()

    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("vertexIterators")
    fun vertexIteratorsWorkOnImmutableGraphs(iterator: (Graph, Vertex) -> VertexIterator, directed: Boolean) {
        constructDiamond(directed, immutable = true)

        val result = iterator(graph, v0).toList()

        assertThat(result).containsExactlyInAnyOrder(v0, v1, v2, v3)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("vertexIterators")
    fun vertexIteratorsAreExhaustedExactlyOnce(iterator: (Graph, Vertex) -> VertexIterator, directed: Boolean) {
        constructCycle(directed)

        val it = iterator(graph, v0)
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
    fun treeEdgeIteratorsAreExhaustedExactlyOnce(iterator: (Graph, Vertex) -> Iterator<Step>, directed: Boolean) {
        constructCycle(directed)

        val it = iterator(graph, v0)
        val seen = mutableListOf<Vertex>()
        while (it.hasNext()) {
            assertThat(it.hasNext()).isTrue
            seen += it.next().target
        }

        assertThat(seen).containsExactlyInAnyOrder(v1, v2)
        assertThat(it.hasNext()).isFalse
        assertThrows<NoSuchElementException> { it.next() }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun breadthFirstTreeEdgeIteratorNeverDiscoversInitialVertices(directed: Boolean) {
        constructDiamond(directed)

        // every initial vertex is a root, so v3 is not discovered through an edge even though v0 reaches it - and
        // this does not depend on the (unspecified) order in which the initial vertices are listed
        for (roots in listOf(vertexSetOf(v0, v3), vertexSetOf(v3, v0))) {
            val steps = graph.breadthFirstTreeEdgeIterator(roots).asSequence().toList()
            assertThat(steps.map { it.target }).containsExactlyInAnyOrder(v1, v2)
        }
        for (roots in listOf(vertexSetOf(v1, v2), vertexSetOf(v0, v1, v2, v3))) {
            val steps = graph.breadthFirstTreeEdgeIterator(roots).asSequence().toList()
            assertThat(steps.map { it.target }).containsExactlyInAnyOrderElementsOf(
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
            assertThat(list.map { it.target }).containsExactlyInAnyOrder(v1, v2)
            val toV1 = list.single { it.target == v1 }
            assertThat(toV1.edge).isIn(parallel)
            assertThat(list.map { it.edge }).doesNotContain(loop)
        }

        val treeEdges = mutableListOf<Edge>()
        val examined = mutableListOf<Edge>()
        val backEdges = mutableListOf<Edge>()
        graph.visitBreadthFirst(vertexSetOf(v0), onEdgeExamined = { examined += it }, onTreeEdge = { treeEdges += it })
        assertThat(treeEdges).hasSize(2)
        assertThat(treeEdges).doesNotContain(loop)
        assertThat(treeEdges.filter { it in parallel }).hasSize(1)
        assertThat(examined.filter { it == loop }).hasSize(1)
        assertThat(examined.filter { it in parallel }).hasSize(if (directed) 2 else 4)

        treeEdges.clear()
        graph.visitDepthFirst(v0, onTreeEdge = { treeEdges += it }, onBackEdge = { backEdges += it })
        assertThat(treeEdges).hasSize(2)
        assertThat(treeEdges).doesNotContain(loop)
        assertThat(treeEdges.filter { it in parallel }).hasSize(1)
        // the self-loop is always a back edge, and so is the second of the parallel edges when it is examined while
        // v1 is still on the stack
        assertThat(backEdges).contains(loop)

        for (tree in listOf(graph.breadthFirstPathForest(v0), graph.depthFirstPathForest(v0))) {
            assertThat(tree.edges).hasSize(2)
            assertThat(tree.edges.contains(loop)).isFalse
            assertThat(tree.materializePath(v1).edges.single()).isIn(parallel)
        }
    }

    // --- edge classification ------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun visitDepthFirstReportsForwardAndCrossEdgesOnlyAsExamined(directed: Boolean) {
        constructDiamond(directed)

        val examined = mutableListOf<Edge>()
        val tree = mutableListOf<Edge>()
        val back = mutableListOf<Edge>()
        graph.visitDepthFirst(
            v0,
            onEdgeExamined = { examined += it },
            onTreeEdge = { tree += it },
            onBackEdge = { back += it })

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
        graph.visitDepthFirst(v0)
        graph.visitDepthFirstPreOrder(v0)

        val discovered = mutableListOf<Vertex>()
        graph.visitDepthFirstPreOrder(v0, onVertexDiscovered = { discovered += it })
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
        assertThat(filtered.breadthFirstTreeEdgeIterator(v0).asSequence().map { it.target }.toList()).containsExactly(v1, v3)
        assertThat(filtered.breadthFirstPathForest(v0).materializePath(v3).vertices).containsExactly(v0, v1, v3)
        assertThat(filtered.depthFirstPathForest(v0).materializePath(v3).vertices).containsExactly(v0, v1, v3)
        val predicated = graph.filter({ it != v1 }, { true })
        assertThat(predicated.breadthFirstVertexIterator(v0).toList()).containsExactly(v0, v2, v3)

        if (directed) {
            // transposed: v3 reaches everything but v4
            val transposed = graph.asTransposed()
            assertThat(transposed.breadthFirstVertexIterator(v3).toList()).containsExactlyInAnyOrder(v0, v1, v2, v3)
            assertThat(transposed.breadthFirstVertexIterator(v3).toList().first()).isEqualTo(v3)
            assertThat(transposed.depthFirstPostOrderVertexIterator(v3).toList().last()).isEqualTo(v3)
            assertThat(transposed.breadthFirstVertexIterator(v0).toList()).containsExactly(v0)
            assertThat(transposed.depthFirstTreeEdgeIterator(v3).asSequence().map { it.target }.toList())
                .containsExactlyInAnyOrder(v0, v1, v2)
        }

        // a path tree: every vertex walks back to the root
        val tree = graph.breadthFirstPathForest(v0)
        assertThat(tree.breadthFirstVertexIterator(v3).toList()).hasSize(3)
        assertThat(tree.breadthFirstVertexIterator(v3).toList().last()).isEqualTo(v0)
        assertThat(tree.depthFirstPreOrderVertexIterator(v3).toList().first()).isEqualTo(v3)
        assertThat(tree.asTransposed().breadthFirstVertexIterator(v0).toList()).containsExactlyInAnyOrder(v0, v1, v2, v3)
        val steps = tree.asTransposed().breadthFirstTreeEdgeIterator(v0).asSequence().toList()
        assertThat(steps.map { it.target }).containsExactlyInAnyOrder(v1, v2, v3)
        assertThat(steps.map { it.edge }).containsExactlyInAnyOrderElementsOf(tree.edges)
    }
}
