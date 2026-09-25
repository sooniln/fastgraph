package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.MutableGraphContractTest.GraphKind
import io.github.sooniln.fastgraph.filtered.filter
import io.github.sooniln.fastgraph.homomorphisms.GraphIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.component1
import io.github.sooniln.fastgraph.homomorphisms.component2
import io.github.sooniln.fastgraph.homomorphisms.component3
import io.github.sooniln.fastgraph.homomorphisms.isomorphism
import io.github.sooniln.fastgraph.homomorphisms.transfer
import io.github.sooniln.fastgraph.homomorphisms.transferInto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class GraphCopyTest {

    enum class Source(val create: (MutableGraph) -> Graph) {
        SELF({ it }),
        VERTEX_FILTERED({ graph -> graph.filter(vertexFilter = { it != Vertex(0) }) }),
        EDGE_FILTERED({ graph -> graph.filter(edgeFilter = { graph.edgeSource(it) != graph.edgeTarget(it) }) }),
        TRANSPOSED({ it.asTransposed() }),
        UNDIRECTED({ it.asUndirected() }),
        IMMUTABLE({ it.toImmutableGraph().target }),
        EMPTY({ graph -> graph.filter(vertexFilter = { false }) }),
    }

    companion object {
        @JvmStatic
        fun kinds(): List<Arguments> = MutableGraphContractTest.kinds()

        @JvmStatic
        fun copyCases(): List<Arguments> = GraphKind.entries.flatMap { kind ->
            listOf(true, false).flatMap { directed ->
                Source.entries.flatMap { source ->
                    listOf(true, false).flatMap { immutable ->
                        listOf(true, false).flatMap { forceMultiEdge ->
                            listOf(true, false).map { indexEdges ->
                                Arguments.of(kind, directed, source, immutable, forceMultiEdge, indexEdges)
                            }
                        }
                    }
                }
            }
        }

        private fun createGraph(kind: GraphKind, directed: Boolean): MutableGraph {
            val graph = kind.create(directed)
            val a = graph.addVertex()
            val b = graph.addVertex()
            val c = graph.addVertex()
            val d = graph.addVertex()
            graph.addVertex() // isolated
            graph.addEdge(a, b)
            graph.addEdge(b, c)
            graph.addEdge(c, a)
            graph.addEdge(a, a)
            graph.addEdge(c, d)
            if (kind.multiEdge) {
                graph.addEdge(a, b)
                graph.addEdge(a, a)
                graph.addEdge(c, d)
            }
            return graph
        }

        private fun assertIsCopy(copy: GraphIsomorphism<*, *, *, *>) {
            val source = copy.source
            val target = copy.target
            val vertexMap = copy.vertexMap
            val edgeMap = copy.edgeMap

            assertThat(target).isNotSameAs(source)
            assertThat(target.directed).isEqualTo(source.directed)

            // the maps are bijections
            assertThat(source.vertices.map { vertexMap[it] }).containsExactlyInAnyOrderElementsOf(target.vertices)
            assertThat(source.edges.map { edgeMap[it] }).containsExactlyInAnyOrderElementsOf(target.edges)
            for (vertex in source.vertices) {
                assertThat(vertexMap.hasVertex(vertexMap[vertex])).isTrue
                assertThat(vertexMap.getVertex(vertexMap[vertex])).isEqualTo(vertex)
            }
            for (edge in source.edges) {
                assertThat(edgeMap.hasEdge(edgeMap[edge])).isTrue
                assertThat(edgeMap.getEdge(edgeMap[edge])).isEqualTo(edge)
            }

            // the maps respect the topology
            for (edge in source.edges) {
                val edgeCopy = edgeMap[edge]
                if (source.directed) {
                    assertThat(target.edgeSource(edgeCopy)).isEqualTo(vertexMap[source.edgeSource(edge)])
                    assertThat(target.edgeTarget(edgeCopy)).isEqualTo(vertexMap[source.edgeTarget(edge)])
                } else {
                    assertThat(setOf(target.edgeSource(edgeCopy), target.edgeTarget(edgeCopy)))
                        .isEqualTo(setOf(vertexMap[source.edgeSource(edge)], vertexMap[source.edgeTarget(edge)]))
                }
            }
            for (vertex in source.vertices) {
                val vertexCopy = vertexMap[vertex]
                assertThat(target.outDegree(vertexCopy)).isEqualTo(source.outDegree(vertex))
                assertThat(target.inDegree(vertexCopy)).isEqualTo(source.inDegree(vertex))
                assertThat(target.successors(vertexCopy)).containsExactlyInAnyOrderElementsOf(source.successors(vertex).map { vertexMap[it] })
                assertThat(target.predecessors(vertexCopy)).containsExactlyInAnyOrderElementsOf(source.predecessors(vertex).map { vertexMap[it] })
                assertThat(target.outgoingEdges(vertexCopy)).containsExactlyInAnyOrderElementsOf(source.outgoingEdges(vertex).map { edgeMap[it] })
                assertThat(target.incomingEdges(vertexCopy)).containsExactlyInAnyOrderElementsOf(source.incomingEdges(vertex).map { edgeMap[it] })
            }
        }
    }

    @ParameterizedTest(name = "{0}, directed={1}, {2}, immutable={3}, forceMultiEdge={4}, indexEdges={5}")
    @MethodSource("copyCases")
    fun copyIsIsomorphicToSource(
        kind: GraphKind,
        directed: Boolean,
        sourceKind: Source,
        immutable: Boolean,
        forceMultiEdge: Boolean,
        indexEdges: Boolean,
    ) {
        val source = sourceKind.create(createGraph(kind, directed))

        val copy: GraphCopy<Graph> = if (immutable) {
            source.toImmutableGraph(forceMultiEdge, indexEdges)
        } else {
            source.toMutableGraph(forceMultiEdge, indexEdges)
        }

        assertThat(copy.source).isSameAs(source)
        if (immutable) {
            assertThat(copy.target).isInstanceOf(ImmutableGraph::class.java)
        } else {
            assertThat(copy.target).isInstanceOf(MutableGraph::class.java)
        }
        if (source.multiEdge || forceMultiEdge) assertThat(copy.target.multiEdge).isTrue
        if (indexEdges) assertThat(copy.target.edges).isInstanceOf(IdentityIndexedEdgeSet::class.java)

        if (copy.target === source) {
            // an immutable graph which already satisfies the options is its own copy
            assertThat(source).isInstanceOf(ImmutableGraph::class.java)
        } else {
            assertIsCopy(copy)
        }

        // identity indexed sources always keep their vertex ids
        if (source.vertices is IdentityIndexedVertexSet) {
            for (vertex in source.vertices) {
                assertThat(copy.vertexMap[vertex]).isEqualTo(vertex)
            }
        }
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun mutableCopyIsIndependentOfSource(kind: GraphKind, directed: Boolean) {
        val source = createGraph(kind, directed)
        val vertices = source.vertices.size
        val edges = source.edges.size

        val copy = source.toMutableGraph().target
        copy.addEdge(copy.addVertex(), copy.vertices.first())
        copy.removeVertex(copy.vertices.first())

        assertThat(source.vertices).hasSize(vertices)
        assertThat(source.edges).hasSize(edges)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun copyDestructuresIntoTargetAndMaps(kind: GraphKind, directed: Boolean) {
        val source = createGraph(kind, directed).filter(vertexFilter = { it != Vertex(0) })

        val (target, vertexMap, edgeMap) = source.toMutableGraph()

        assertThat(target.vertices).hasSize(source.vertices.size)
        for (vertex in source.vertices) {
            assertThat(vertexMap.getVertex(vertexMap[vertex])).isEqualTo(vertex)
        }
        for (edge in source.edges) {
            assertThat(edgeMap.getEdge(edgeMap[edge])).isEqualTo(edge)
        }
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun transferCopiesPropertiesOfViews(kind: GraphKind, directed: Boolean) {
        val graph = createGraph(kind, directed)
        val names = graph.createVertexProperty("")
        val keys = graph.createVertexKeyProperty<String>()
        for (vertex in graph.vertices) {
            names[vertex] = "name${vertex.id}"
            keys[vertex] = "key${vertex.id}"
        }
        val weights = graph.createEdgeProperty(0L)
        for (edge in graph.edges) {
            weights[edge] = edge.id
        }
        // properties of the parent graph accept vertices/edges of the view
        val view = graph.filter(vertexFilter = { it != Vertex(0) })

        val immutableCopy = view.toImmutableGraph()
        val immutableNames = immutableCopy.transfer(names)
        val immutableWeights = immutableCopy.transfer(weights)
        for (vertex in view.vertices) {
            assertThat(immutableNames[immutableCopy.vertexMap[vertex]]).isEqualTo(names[vertex])
        }
        for (edge in view.edges) {
            assertThat(immutableWeights[immutableCopy.edgeMap[edge]]).isEqualTo(weights[edge])
        }

        val mutableCopy = view.toMutableGraph()
        val mutableNames = mutableCopy.target.createVertexProperty("")
        val mutableKeys = mutableCopy.target.createVertexKeyProperty<String>()
        val mutableWeights = mutableCopy.target.createEdgeProperty(-1L)
        mutableCopy.transferInto(names, mutableNames)
        mutableCopy.transferInto(keys, mutableKeys)
        mutableCopy.transferInto(weights, mutableWeights)
        for (vertex in view.vertices) {
            assertThat(mutableNames[mutableCopy.vertexMap[vertex]]).isEqualTo(names[vertex])
            assertThat(mutableKeys.getVertex(keys[vertex])).isEqualTo(mutableCopy.vertexMap[vertex])
        }
        for (edge in view.edges) {
            assertThat(mutableWeights[mutableCopy.edgeMap[edge]]).isEqualTo(weights[edge])
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun derivedEdgeIsomorphismReverseLookup(directed: Boolean) {
        val graph = mutableGraph(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        graph.addEdge(v0, v1)
        val edge = graph.addEdge(v1, v2)
        // the view keeps its parent's vertex ids, whereas the copy renumbers them
        val view = graph.filter(vertexFilter = { it != v0 })
        val copy = view.toImmutableGraph()

        val derived = isomorphism(copy.vertexHomomorphism)
        val edgeCopy = copy.target.edges.single()

        assertThat(derived.edgeMap[edge]).isEqualTo(edgeCopy)
        assertThat(derived.edgeMap.hasEdge(edgeCopy)).isTrue
        assertThat(derived.edgeMap.getEdge(edgeCopy)).isEqualTo(edge)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun mutableValueGraphCopiesKeysAndValues(directed: Boolean) {
        val valueGraph = buildValueGraph<String, Int>(directed, { 0 }) {
            addEdge(addVertex("a"), addVertex("b"), 42)
        }

        val copy = valueGraph.toMutableValueGraph({ -1 })

        assertThat(copy.vertices.map { copy.vertexKeys[it] }).containsExactlyInAnyOrder("a", "b")
        assertThat(copy.edgeValues[copy.edge(copy.getVertex("a"), copy.getVertex("b"))]).isEqualTo(42)

        // edges added to the copy use the given default, and do not affect the source
        copy.addVertex("c")
        assertThat(copy.edgeValues[copy.addEdge("a", "c")]).isEqualTo(-1)
        assertThat(valueGraph.vertices).hasSize(2)
        assertThat(valueGraph.edges).hasSize(1)
    }
}
