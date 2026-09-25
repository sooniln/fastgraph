package io.github.sooniln.fastgraph.jgrapht

import io.github.sooniln.fastgraph.properties.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.edgeIdProperty
import io.github.sooniln.fastgraph.mutableGraph
import io.github.sooniln.fastgraph.vertexIdProperty
import org.assertj.core.api.Assertions.assertThat
import org.jgrapht.Graph
import org.jgrapht.Graphs
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.function.Supplier

class JGraphTTest {

    /** A fastgraph graph with string vertex keys and edge keys of the form "a-b". */
    private class Fixture(directed: Boolean, multiEdge: Boolean = false) {
        val graph: MutableGraph = mutableGraph(directed, multiEdge)
        val vertexKeys: MutableVertexKeyProperty<String> = graph.createVertexKeyProperty()
        val edgeKeys: MutableEdgeKeyProperty<String> = graph.createEdgeKeyProperty()
        val weights: MutableEdgeProperty<Double> = graph.createEdgeProperty(1.0)

        fun addVertex(key: String) {
            vertexKeys[graph.addVertex()] = key
        }

        fun addEdge(source: String, target: String, key: String = "$source-$target", weight: Double = 1.0) {
            val edge = graph.addEdge(vertexKeys.getVertex(source), vertexKeys.getVertex(target))
            edgeKeys[edge] = key
            weights[edge] = weight
        }

        fun readOnly(weighted: Boolean = false): Graph<String, String> =
            graph.asJGraphT(vertexKeys, edgeKeys, if (weighted) weights else null)

        fun mutable(
            weighted: Boolean = false,
            vertexSupplier: Supplier<String>? = null,
            edgeSupplier: Supplier<String>? = null,
        ): Graph<String, String> =
            graph.asMutableJGraphT(vertexKeys, edgeKeys, if (weighted) weights else null, vertexSupplier, edgeSupplier)
    }

    /** a -> b -> c, a -> c, d isolated */
    private fun triangle(directed: Boolean): Fixture = Fixture(directed).apply {
        addVertex("a"); addVertex("b"); addVertex("c"); addVertex("d")
        addEdge("a", "b", weight = 1.0)
        addEdge("b", "c", weight = 1.0)
        addEdge("a", "c", weight = 5.0)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun type(directed: Boolean) {
        val fixture = triangle(directed)

        val readOnly = fixture.readOnly().type
        assertThat(readOnly.isDirected).isEqualTo(directed)
        assertThat(readOnly.isUndirected).isEqualTo(!directed)
        assertThat(readOnly.isAllowingMultipleEdges).isFalse()
        assertThat(readOnly.isAllowingSelfLoops).isTrue()
        assertThat(readOnly.isWeighted).isFalse()
        assertThat(readOnly.isModifiable).isFalse()

        val mutable = fixture.mutable(weighted = true).type
        assertThat(mutable.isWeighted).isTrue()
        assertThat(mutable.isModifiable).isTrue()

        assertThat(Fixture(directed, multiEdge = true).readOnly().type.isAllowingMultipleEdges).isTrue()
    }

    @Test
    fun rejectsPropertiesFromOtherGraphs() {
        val fixture = triangle(true)
        val other = triangle(true)
        assertThrows<IllegalArgumentException> { fixture.graph.asJGraphT(other.vertexKeys, fixture.edgeKeys) }
        assertThrows<IllegalArgumentException> { fixture.graph.asJGraphT(fixture.vertexKeys, other.edgeKeys) }
        assertThrows<IllegalArgumentException> {
            fixture.graph.asJGraphT(fixture.vertexKeys, fixture.edgeKeys, other.weights)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun vertexAndEdgeSets(directed: Boolean) {
        val fixture = triangle(directed)
        val jgrapht = fixture.readOnly()

        assertThat(jgrapht.vertexSet()).containsExactlyInAnyOrder("a", "b", "c", "d")
        assertThat(jgrapht.edgeSet()).containsExactlyInAnyOrder("a-b", "b-c", "a-c")
        assertThat(jgrapht.vertexSet().contains("z")).isFalse()
        assertThat(jgrapht.edgeSet().contains("z")).isFalse()
        assertThat(jgrapht.containsVertex("a")).isTrue()
        assertThat(jgrapht.containsVertex("z")).isFalse()
        assertThat(jgrapht.containsEdge("a-b")).isTrue()
        assertThat(jgrapht.containsEdge("z")).isFalse()

        // live view
        fixture.addVertex("e")
        fixture.addEdge("d", "e")
        assertThat(jgrapht.vertexSet()).hasSize(5).contains("e")
        assertThat(jgrapht.edgeSet()).hasSize(4).contains("d-e")
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun edgeLookup(directed: Boolean) {
        val jgrapht = triangle(directed).readOnly()

        assertThat(jgrapht.getEdge("a", "b")).isEqualTo("a-b")
        assertThat(jgrapht.getEdge("b", "a")).isEqualTo(if (directed) null else "a-b")
        assertThat(jgrapht.getEdge("a", "d")).isNull()
        assertThat(jgrapht.getEdge("a", "z")).isNull()
        assertThat(jgrapht.getAllEdges("a", "b")).containsExactly("a-b")
        assertThat(jgrapht.getAllEdges("a", "d")).isEmpty()
        assertThat(jgrapht.getAllEdges("z", "a")).isNull()
        assertThat(jgrapht.containsEdge("a", "b")).isTrue()
        assertThat(jgrapht.containsEdge("a", "d")).isFalse()

        val source = jgrapht.getEdgeSource("a-b")
        val target = jgrapht.getEdgeTarget("a-b")
        if (directed) {
            assertThat(source).isEqualTo("a")
            assertThat(target).isEqualTo("b")
        } else {
            assertThat(setOf(source, target)).containsExactlyInAnyOrder("a", "b")
        }
        assertThrows<IllegalArgumentException> { jgrapht.getEdgeSource("z") }
    }

    @Test
    fun multiEdges() {
        val fixture = Fixture(directed = true, multiEdge = true)
        fixture.addVertex("a"); fixture.addVertex("b")
        fixture.addEdge("a", "b", "e1")
        fixture.addEdge("a", "b", "e2")
        val jgrapht = fixture.mutable()

        assertThat(jgrapht.getAllEdges("a", "b")).containsExactlyInAnyOrder("e1", "e2")
        assertThat(jgrapht.getEdge("a", "b")).isIn("e1", "e2")
        assertThat(jgrapht.addEdge("a", "b", "e3")).isTrue()
        assertThat(jgrapht.getAllEdges("a", "b")).hasSize(3)

        // AbstractGraph.removeAllEdges(V, V) iterates getAllEdges while removing
        assertThat(jgrapht.removeAllEdges("a", "b")).containsExactlyInAnyOrder("e1", "e2", "e3")
        assertThat(jgrapht.edgeSet()).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun degreesAndIncidentEdges(directed: Boolean) {
        val fixture = triangle(directed)
        fixture.addEdge("b", "b", "loop")
        val jgrapht = fixture.readOnly()

        if (directed) {
            assertThat(jgrapht.outDegreeOf("a")).isEqualTo(2)
            assertThat(jgrapht.inDegreeOf("a")).isEqualTo(0)
            assertThat(jgrapht.degreeOf("a")).isEqualTo(2)
            assertThat(jgrapht.outgoingEdgesOf("a")).containsExactlyInAnyOrder("a-b", "a-c")
            assertThat(jgrapht.incomingEdgesOf("a")).isEmpty()
            assertThat(jgrapht.edgesOf("a")).containsExactlyInAnyOrder("a-b", "a-c")

            assertThat(jgrapht.outDegreeOf("b")).isEqualTo(2)
            assertThat(jgrapht.inDegreeOf("b")).isEqualTo(2)
            assertThat(jgrapht.degreeOf("b")).isEqualTo(4)
            assertThat(jgrapht.edgesOf("b")).containsExactlyInAnyOrder("a-b", "b-c", "loop")
        } else {
            assertThat(jgrapht.outDegreeOf("a")).isEqualTo(2)
            assertThat(jgrapht.inDegreeOf("a")).isEqualTo(2)
            assertThat(jgrapht.degreeOf("a")).isEqualTo(2)
            assertThat(jgrapht.edgesOf("a")).containsExactlyInAnyOrder("a-b", "a-c")

            // self-loop counts twice towards degree
            assertThat(jgrapht.degreeOf("b")).isEqualTo(4)
            assertThat(jgrapht.outDegreeOf("b")).isEqualTo(4)
            assertThat(jgrapht.inDegreeOf("b")).isEqualTo(4)
            assertThat(jgrapht.edgesOf("b")).containsExactlyInAnyOrder("a-b", "b-c", "loop")
        }

        assertThat(jgrapht.degreeOf("d")).isEqualTo(0)
        assertThrows<IllegalArgumentException> { jgrapht.degreeOf("z") }
        assertThrows<IllegalArgumentException> { jgrapht.outgoingEdgesOf("z") }
    }

    @Test
    fun weights() {
        val fixture = triangle(true)

        val unweighted = fixture.readOnly()
        assertThat(unweighted.getEdgeWeight("a-c")).isEqualTo(Graph.DEFAULT_EDGE_WEIGHT)
        assertThrows<UnsupportedOperationException> { unweighted.setEdgeWeight("a-c", 2.0) }

        val weighted = fixture.readOnly(weighted = true)
        assertThat(weighted.getEdgeWeight("a-c")).isEqualTo(5.0)
        assertThrows<UnsupportedOperationException> { weighted.setEdgeWeight("a-c", 2.0) }

        val mutableUnweighted = fixture.mutable()
        assertThrows<UnsupportedOperationException> { mutableUnweighted.setEdgeWeight("a-c", 2.0) }

        val mutableWeighted = fixture.mutable(weighted = true)
        mutableWeighted.setEdgeWeight("a-c", 2.0)
        assertThat(fixture.weights[fixture.edgeKeys.getEdge("a-c")]).isEqualTo(2.0)
        assertThat(weighted.getEdgeWeight("a-c")).isEqualTo(2.0)
        assertThrows<IllegalArgumentException> { mutableWeighted.setEdgeWeight("z", 2.0) }
    }

    @Test
    fun readOnlyViewRejectsModification() {
        val jgrapht = triangle(true).readOnly()

        assertThat(jgrapht.vertexSupplier).isNull()
        assertThat(jgrapht.edgeSupplier).isNull()
        assertThrows<UnsupportedOperationException> { jgrapht.addVertex() }
        assertThrows<UnsupportedOperationException> { jgrapht.addVertex("z") }
        assertThrows<UnsupportedOperationException> { jgrapht.addEdge("a", "d") }
        assertThrows<UnsupportedOperationException> { jgrapht.addEdge("a", "d", "a-d") }
        assertThrows<UnsupportedOperationException> { jgrapht.removeVertex("a") }
        assertThrows<UnsupportedOperationException> { jgrapht.removeEdge("a-b") }
        assertThrows<UnsupportedOperationException> { jgrapht.removeEdge("a", "b") }
        assertThrows<UnsupportedOperationException> { jgrapht.removeAllEdges(listOf("a-b")) }
        assertThrows<UnsupportedOperationException> { jgrapht.removeAllVertices(listOf("a")) }
    }

    @Test
    fun readOnlyViewWithIdProperties() {
        val graph = mutableGraph(directed = true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val e = graph.addEdge(v0, v1)

        val jgrapht = graph.asJGraphT(graph.vertexIdProperty, graph.edgeIdProperty)
        assertThat(jgrapht.vertexSet()).containsExactlyInAnyOrder(v0.id, v1.id)
        assertThat(jgrapht.edgeSet()).containsExactly(e.id)
        assertThat(jgrapht.getEdgeSource(e.id)).isEqualTo(v0.id)
        assertThat(jgrapht.getEdgeTarget(e.id)).isEqualTo(v1.id)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun addVertexAndEdge(directed: Boolean) {
        val fixture = triangle(directed)
        val jgrapht = fixture.mutable()

        assertThat(jgrapht.addVertex("e")).isTrue()
        assertThat(jgrapht.addVertex("e")).isFalse()
        assertThat(fixture.vertexKeys.hasVertex("e")).isTrue()
        assertThat(fixture.graph.vertices).hasSize(5)

        assertThat(jgrapht.addEdge("d", "e", "d-e")).isTrue()
        assertThat(jgrapht.addEdge("d", "e", "d-e")).isFalse()
        assertThat(jgrapht.addEdge("e", "d", "e-d")).isEqualTo(directed) // duplicate in undirected simple graph
        assertThat(jgrapht.addEdge("d", "e", "other")).isFalse() // duplicate in simple graph
        assertThat(fixture.graph.edges).hasSize(if (directed) 5 else 4)
        assertThat(jgrapht.getEdge("d", "e")).isEqualTo("d-e")
        assertThrows<IllegalArgumentException> { jgrapht.addEdge("d", "z", "d-z") }

        // no suppliers
        assertThrows<UnsupportedOperationException> { jgrapht.addVertex() }
        assertThrows<UnsupportedOperationException> { jgrapht.addEdge("a", "d") }
    }

    @Test
    fun suppliers() {
        val fixture = triangle(true)
        var nextVertex = 0
        var nextEdge = 0
        val jgrapht = fixture.mutable(vertexSupplier = { "v${nextVertex++}" }, edgeSupplier = { "e${nextEdge++}" })

        assertThat(jgrapht.addVertex()).isEqualTo("v0")
        assertThat(jgrapht.addVertex()).isEqualTo("v1")
        assertThat(jgrapht.vertexSet()).contains("v0", "v1")

        assertThat(jgrapht.addEdge("v0", "v1")).isEqualTo("e0")
        assertThat(jgrapht.addEdge("v0", "v1")).isNull() // duplicate in simple graph
        assertThat(jgrapht.addEdge("v1", "v0")).isEqualTo("e1")
        assertThat(jgrapht.edgeSet()).contains("e0", "e1")
        assertThrows<IllegalArgumentException> { jgrapht.addEdge("v0", "z") }

        // supplier returning existing keys fails before mutation
        nextVertex = 0
        nextEdge = 0
        assertThrows<IllegalArgumentException> { jgrapht.addVertex() }
        assertThrows<IllegalArgumentException> { jgrapht.addEdge("a", "d") }
        assertThat(fixture.graph.vertices).hasSize(6)
        assertThat(fixture.graph.edges).hasSize(5)
        assertThat(jgrapht.getEdge("a", "d")).isNull()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun removeVertexAndEdge(directed: Boolean) {
        val fixture = triangle(directed)
        val jgrapht = fixture.mutable()

        assertThat(jgrapht.removeEdge("a-c")).isTrue()
        assertThat(jgrapht.removeEdge("a-c")).isFalse()
        assertThat(jgrapht.edgeSet()).containsExactlyInAnyOrder("a-b", "b-c")

        assertThat(jgrapht.removeEdge("a", "d")).isNull()
        assertThat(jgrapht.removeEdge("a", "b")).isEqualTo("a-b")
        assertThat(jgrapht.edgeSet()).containsExactly("b-c")

        // removing vertex 'a' (the first vertex) reassigns ids; keys must remain valid
        assertThat(jgrapht.removeVertex("a")).isTrue()
        assertThat(jgrapht.removeVertex("a")).isFalse()
        assertThat(jgrapht.vertexSet()).containsExactlyInAnyOrder("b", "c", "d")
        assertThat(jgrapht.edgeSet()).containsExactly("b-c")
        assertThat(jgrapht.getEdge("b", "c")).isEqualTo("b-c")
        assertThat(jgrapht.outgoingEdgesOf("b")).containsExactly("b-c")
        assertThat(Graphs.neighborSetOf(jgrapht, "c")).containsExactly("b")

        // removing a vertex removes its edges
        assertThat(jgrapht.removeVertex("b")).isTrue()
        assertThat(jgrapht.edgeSet()).isEmpty()
        assertThat(jgrapht.vertexSet()).containsExactlyInAnyOrder("c", "d")

        assertThat(jgrapht.removeAllVertices(listOf("c", "d"))).isTrue()
        assertThat(jgrapht.vertexSet()).isEmpty()
        assertThat(fixture.graph.isEmpty()).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun edgesOfTheMovedVertexStayValidAfterVertexRemoval(directed: Boolean) {
        val fixture = triangle(directed)
        fixture.addEdge("d", "c", weight = 2.0)
        fixture.addEdge("d", "d", weight = 3.0)
        val jgrapht = fixture.mutable(weighted = true)
        val readOnly = fixture.readOnly(weighted = true)

        // removing 'a' moves 'd' (the last vertex) into its place; in a canonical edge graph that renames every edge
        // of 'd', and both views must keep handing out valid edges for them
        assertThat(jgrapht.removeVertex("a")).isTrue()

        for (view in listOf(jgrapht, readOnly)) {
            assertThat(view.vertexSet()).containsExactlyInAnyOrder("b", "c", "d")
            assertThat(view.edgeSet()).containsExactlyInAnyOrder("b-c", "d-c", "d-d")
            assertThat(view.containsEdge("a-b")).isFalse()
            assertThat(view.containsEdge("d-c")).isTrue()
            assertThat(view.getEdge("d", "c")).isEqualTo("d-c")
            assertThat(view.getEdge("d", "d")).isEqualTo("d-d")
            assertThat(setOf(view.getEdgeSource("d-c"), view.getEdgeTarget("d-c"))).containsExactlyInAnyOrder("d", "c")
            assertThat(view.getEdgeSource("d-d")).isEqualTo("d")
            assertThat(view.getEdgeTarget("d-d")).isEqualTo("d")
            assertThat(view.getEdgeWeight("d-c")).isEqualTo(2.0)
            assertThat(view.getEdgeWeight("d-d")).isEqualTo(3.0)
            assertThat(view.edgesOf("d")).containsExactlyInAnyOrder("d-c", "d-d")
            assertThat(view.edgesOf("c")).containsExactlyInAnyOrder("b-c", "d-c")
            assertThat(Graphs.neighborSetOf(view, "d")).containsExactlyInAnyOrder("c", "d")
        }

        // and the underlying fastgraph agrees
        val d = fixture.vertexKeys.getVertex("d")
        val c = fixture.vertexKeys.getVertex("c")
        assertThat(fixture.edgeKeys.getEdge("d-c")).isEqualTo(fixture.graph.edge(d, c))
        assertThat(fixture.edgeKeys.getEdge("d-d")).isEqualTo(fixture.graph.edge(d, d))
        assertThat(fixture.weights[fixture.graph.edge(d, c)]).isEqualTo(2.0)

        // the same rename through the mutable view of the underlying graph is visible in the JGraphT views
        fixture.graph.removeVertex(fixture.vertexKeys.getVertex("b"))
        for (view in listOf(jgrapht, readOnly)) {
            assertThat(view.vertexSet()).containsExactlyInAnyOrder("c", "d")
            assertThat(view.edgeSet()).containsExactlyInAnyOrder("d-c", "d-d")
            assertThat(view.getEdge("d", "c")).isEqualTo("d-c")
            assertThat(view.getEdgeWeight("d-d")).isEqualTo(3.0)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun algorithms(directed: Boolean) {
        val fixture = triangle(directed)
        val jgrapht = fixture.readOnly(weighted = true)

        val path = DijkstraShortestPath(jgrapht).getPath("a", "c")
        assertThat(path.weight).isEqualTo(2.0)
        assertThat(path.vertexList).containsExactly("a", "b", "c")
        assertThat(path.edgeList).containsExactly("a-b", "b-c")
        assertThat(DijkstraShortestPath(jgrapht).getPath("a", "d")).isNull()

        val inspector = ConnectivityInspector(jgrapht)
        assertThat(inspector.isConnected).isFalse()
        assertThat(inspector.connectedSets()).containsExactlyInAnyOrder(setOf("a", "b", "c"), setOf("d"))
    }

    @Test
    fun mutatingAlgorithm() {
        val fixture = Fixture(directed = true)
        val jgrapht = fixture.mutable(edgeSupplier = Supplier { "e" })

        assertThat(Graphs.addEdgeWithVertices(jgrapht, "a", "b")).isEqualTo("e")
        assertThat(jgrapht.vertexSet()).containsExactlyInAnyOrder("a", "b")
        assertThat(jgrapht.edgeSet()).containsExactly("e")

        val source = triangle(true)
        assertThat(Graphs.addGraph(jgrapht, source.readOnly())).isTrue()
        assertThat(jgrapht.vertexSet()).containsExactlyInAnyOrder("a", "b", "c", "d")
        assertThat(jgrapht.edgeSet()).containsExactlyInAnyOrder("e", "b-c", "a-c")
        assertThat(fixture.graph.edges).hasSize(3)
    }
}
