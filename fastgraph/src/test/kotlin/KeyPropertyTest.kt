package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class KeyPropertyTest {

    class KeyCase<T>(val type: PropertyType<T>, val keyAt: (Int) -> T) {
        override fun toString(): String = type.toString()
    }

    companion object {
        @JvmStatic
        fun keyCasesWithDirectedness(): List<Arguments> = keyCases().flatMap { case ->
            listOf(true, false).map { directed -> Arguments.of(case, directed) }
        }

        @JvmStatic
        fun keyCasesWithIndexing(): List<Arguments> = keyCases().flatMap { case ->
            listOf(true, false).map { indexEdges -> Arguments.of(case, indexEdges) }
        }

        @JvmStatic
        fun keyCases(): List<KeyCase<*>> = listOf(
            KeyCase(propertyTypeOf()) { index -> 2 shl index },
            KeyCase(propertyTypeOf()) { index -> 2L shl index },
            KeyCase(propertyTypeOf()) { index -> "key$index" },
            // null is a valid key
            KeyCase(propertyTypeOf<String?>()) { index -> if (index == 0) null else "key$index" },
        )
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> vertexKeyPropertyIsUnreadableUntilComplete(case: KeyCase<T>) {
        val graph = buildGraph(true) { addVertex(); addVertex() }
        val property = graph.createVertexKeyProperty(case.type)
        val v0 = graph.vertices.elementAt(0)
        val v1 = graph.vertices.elementAt(1)
        assertThat(property.graph).isSameAs(graph)
        assertThat(property.type).isEqualTo(case.type)

        assertThrows<IllegalStateException> { property[v0] }
        assertThrows<IllegalStateException> { property.put(v0, case.keyAt(0)) }
        assertThrows<IllegalStateException> { property.hasVertex(case.keyAt(0)) }
        assertThrows<IllegalStateException> { property.getVertex(case.keyAt(0)) }
        assertThrows<IllegalStateException> { property.copy() }

        property[v0] = case.keyAt(0)
        assertThrows<IllegalStateException> { property[v0] }
        property[v1] = case.keyAt(1)

        assertThat(property[v0]).isEqualTo(case.keyAt(0))
        assertThat(property[v1]).isEqualTo(case.keyAt(1))
        assertThat(property.hasVertex(case.keyAt(0))).isTrue()
        assertThat(property.hasVertex(case.keyAt(2))).isFalse()
        assertThat(property.getVertex(case.keyAt(1))).isEqualTo(v1)
        assertThrows<NoSuchElementException> { property.getVertex(case.keyAt(2)) }

        val v2 = graph.addVertex()
        assertThrows<IllegalStateException> { property[v0] }
        property[v2] = case.keyAt(2)
        assertThat(property.getVertex(case.keyAt(2))).isEqualTo(v2)
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> vertexKeyPropertyRejectsDuplicateKeys(case: KeyCase<T>) {
        val graph = buildGraph(true) { addVertex(); addVertex() }
        val property = graph.createVertexKeyProperty(case.type)
        val v0 = graph.vertices.elementAt(0)
        val v1 = graph.vertices.elementAt(1)
        property[v0] = case.keyAt(0)
        property[v1] = case.keyAt(1)

        assertThrows<IllegalArgumentException> { property[v1] = case.keyAt(0) }
        assertThat(property[v1]).isEqualTo(case.keyAt(1))

        // re-setting a vertex's own key is a no-op
        property[v0] = case.keyAt(0)
        assertThat(property.put(v0, case.keyAt(0))).isEqualTo(case.keyAt(0))

        // changing a key frees the old one
        assertThat(property.put(v0, case.keyAt(2))).isEqualTo(case.keyAt(0))
        assertThat(property.hasVertex(case.keyAt(0))).isFalse()
        assertThat(property.getVertex(case.keyAt(2))).isEqualTo(v0)
        property[v1] = case.keyAt(0)
        assertThat(property.getVertex(case.keyAt(0))).isEqualTo(v1)
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> vertexKeyPropertyFollowsRemovalAndReassignment(case: KeyCase<T>) {
        val graph = buildGraph(true) { addVertex(); addVertex(); addVertex() }
        val property = graph.createVertexKeyProperty(case.type)
        val v0 = graph.vertices.elementAt(0)
        val v1 = graph.vertices.elementAt(1)
        val v2 = graph.vertices.elementAt(2)
        property[v0] = case.keyAt(0)
        property[v1] = case.keyAt(1)
        property[v2] = case.keyAt(2)
        var reassignedTo: Vertex? = null
        graph.registerVertexChangeListener(object : VertexChangeListener {
            override fun onVertexAdded(vertex: Vertex) {}
            override fun onVertexRemoved(vertex: Vertex) {}
            override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) { reassignedTo = newVertex }
        })

        // v0 is removed, so v2 (the last vertex) is reassigned to v0's freed index
        graph.removeVertex(v0)
        val moved = reassignedTo!!
        assertThat(property[moved]).isEqualTo(case.keyAt(2))
        assertThat(property[v1]).isEqualTo(case.keyAt(1))
        assertThat(property.getVertex(case.keyAt(2))).isEqualTo(moved)
        assertThat(property.hasVertex(case.keyAt(0))).isFalse()

        // removing the last vertex
        reassignedTo = null
        graph.removeVertex(v1)
        assertThat(reassignedTo).isNull()
        assertThat(property.hasVertex(case.keyAt(1))).isFalse()
        assertThat(property.getVertex(case.keyAt(2))).isEqualTo(moved)

        // removing an unkeyed vertex makes the property complete again
        val v3 = graph.addVertex()
        assertThrows<IllegalStateException> { property[moved] }
        graph.removeVertex(v3)
        assertThat(property[moved]).isEqualTo(case.keyAt(2))

        // an unkeyed last vertex reassigned onto a removed keyed vertex frees the removed key
        graph.addVertex()
        graph.removeVertex(moved)
        val unkeyed = reassignedTo!!
        assertThat(graph.vertices).containsExactly(unkeyed)
        assertThrows<IllegalStateException> { property.hasVertex(case.keyAt(2)) }
        property[unkeyed] = case.keyAt(2)
        assertThat(property.getVertex(case.keyAt(2))).isEqualTo(unkeyed)
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> vertexKeyPropertyCopy(case: KeyCase<T>) {
        val graph = buildGraph(true) { addVertex(); addVertex() }
        val property = graph.createVertexKeyProperty(case.type)
        val v0 = graph.vertices.elementAt(0)
        val v1 = graph.vertices.elementAt(1)
        property[v0] = case.keyAt(0)
        property[v1] = case.keyAt(1)

        val copy = property.copy()
        assertThat(copy.graph).isSameAs(graph)
        assertThat(copy.type).isEqualTo(property.type)
        property[v0] = case.keyAt(2)
        assertThat(copy[v0]).isEqualTo(case.keyAt(0))
        assertThat(copy.getVertex(case.keyAt(1))).isEqualTo(v1)
        assertThat(copy.hasVertex(case.keyAt(2))).isFalse()
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> vertexKeyPropertyOnImmutableGraph(case: KeyCase<T>) {
        val graph = buildImmutableGraph(true) { addVertex(); addVertex() }
        val property = graph.createVertexKeyProperty(case.type)
        val v0 = graph.vertices.elementAt(0)
        val v1 = graph.vertices.elementAt(1)
        assertThat(property.graph).isSameAs(graph)
        assertThrows<IllegalStateException> { property[v0] }
        property[v0] = case.keyAt(0)
        property[v1] = case.keyAt(1)
        assertThat(property.getVertex(case.keyAt(1))).isEqualTo(v1)
        assertThat(property[v0]).isEqualTo(case.keyAt(0))
        assertThrows<IllegalArgumentException> { property[v1] = case.keyAt(0) }
        assertThrows<IllegalArgumentException> { property[Vertex(2)] = case.keyAt(2) }
        assertThrows<NoSuchElementException> { property.getVertex(case.keyAt(2)) }
        assertThat(property.copy().getVertex(case.keyAt(0))).isEqualTo(v0)
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> vertexKeyPropertyOnInducedFilteredGraph(case: KeyCase<T>) {
        val graph = buildGraph(true) { addVertex(); addVertex(); addVertex() }
        val v0 = graph.vertices.elementAt(0)
        val v1 = graph.vertices.elementAt(1)
        val v2 = graph.vertices.elementAt(2)
        val subgraph = graph.filter(vertexSetOf(v0, v2), emptyEdgeSet())
        val property = subgraph.createVertexKeyProperty(case.type)
        assertThat(property.graph).isSameAs(subgraph)
        property[v0] = case.keyAt(0)
        assertThrows<IllegalStateException> { property[v0] }
        property[v2] = case.keyAt(2)
        assertThat(property[v2]).isEqualTo(case.keyAt(2))
        assertThat(property.getVertex(case.keyAt(0))).isEqualTo(v0)
        assertThrows<IllegalArgumentException> { property[v2] = case.keyAt(0) }

        // removing a filtered vertex from the parent frees its key and keeps the property complete
        graph.removeVertex(v2)
        assertThat(property.hasVertex(case.keyAt(2))).isFalse()
        assertThat(property.getVertex(case.keyAt(0))).isEqualTo(v0)
    }

    @Test
    fun filteredFilteredGraphDoesNotSupportKeyProperties() {
        val graph = buildGraph(true) { addVertex() }
        val subgraph = graph.filter({ true }, { true })
        assertThrows<UnsupportedOperationException> { subgraph.createVertexKeyProperty<String>() }
        assertThrows<UnsupportedOperationException> { subgraph.createEdgeKeyProperty<String>() }
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> edgeKeyPropertyIsUnreadableUntilComplete(case: KeyCase<T>) {
        var e0 = Edge(-1)
        var e1 = Edge(-1)
        val graph = buildGraph(true, multiEdge = true, indexEdges = true) {
            val v0 = addVertex()
            val v1 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v0, v1)
        }
        val property = graph.createEdgeKeyProperty(case.type)

        assertThrows<IllegalStateException> { property[e0] }
        assertThrows<IllegalStateException> { property.put(e0, case.keyAt(0)) }
        assertThrows<IllegalStateException> { property.hasEdge(case.keyAt(0)) }
        assertThrows<IllegalStateException> { property.getEdge(case.keyAt(0)) }
        assertThrows<IllegalStateException> { property.copy() }

        property[e0] = case.keyAt(0)
        assertThrows<IllegalStateException> { property[e0] }
        property[e1] = case.keyAt(1)

        assertThat(property[e0]).isEqualTo(case.keyAt(0))
        assertThat(property[e1]).isEqualTo(case.keyAt(1))
        assertThat(property.hasEdge(case.keyAt(0))).isTrue()
        assertThat(property.hasEdge(case.keyAt(2))).isFalse()
        assertThat(property.getEdge(case.keyAt(1))).isEqualTo(e1)
        assertThrows<NoSuchElementException> { property.getEdge(case.keyAt(2)) }
        assertThrows<IllegalArgumentException> { property[e1] = case.keyAt(0) }

        val e2 = graph.addEdge(graph.vertices.elementAt(1), graph.vertices.elementAt(0))
        assertThrows<IllegalStateException> { property[e0] }
        property[e2] = case.keyAt(2)
        assertThat(property.getEdge(case.keyAt(2))).isEqualTo(e2)

        val copy = property.copy()
        assertThat(copy.getEdge(case.keyAt(2))).isEqualTo(e2)
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> edgeKeyPropertyFollowsRemovalAndReassignment(case: KeyCase<T>) {
        var e0 = Edge(-1)
        var e1 = Edge(-1)
        var e2 = Edge(-1)
        val graph = buildGraph(true, multiEdge = true, indexEdges = true) {
            val v0 = addVertex()
            val v1 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v0, v1)
            e2 = addEdge(v0, v1)
        }
        val property = graph.createEdgeKeyProperty(case.type)
        property[e0] = case.keyAt(0)
        property[e1] = case.keyAt(1)
        property[e2] = case.keyAt(2)

        var reassignedTo: Edge? = null
        graph.registerEdgeChangeListener(object : EdgeChangeListener {
            override fun onEdgeAdded(edge: Edge) {}
            override fun onEdgeRemoved(edge: Edge) {}
            override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
                assertThat(oldEdge).isEqualTo(e2)
                reassignedTo = newEdge
            }
        })

        graph.removeEdge(e0)
        val moved = reassignedTo!!
        assertThat(property[moved]).isEqualTo(case.keyAt(2))
        assertThat(property[e1]).isEqualTo(case.keyAt(1))
        assertThat(property.getEdge(case.keyAt(2))).isEqualTo(moved)
        assertThat(property.hasEdge(case.keyAt(0))).isFalse()

        graph.removeEdge(e1)
        assertThat(property.hasEdge(case.keyAt(1))).isFalse()
        assertThat(property.getEdge(case.keyAt(2))).isEqualTo(moved)
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("keyCasesWithDirectedness")
    fun <T> edgeKeyPropertyOnCanonicalEdges(case: KeyCase<T>, directed: Boolean) {
        // a graph without multi-edges or edge indices identifies edges by their endpoints; removing an edge never
        // re-assigns another one, but removing a vertex renames the edges of the vertex which takes its place
        var v0 = Vertex(-1)
        var v1 = Vertex(-1)
        var v2 = Vertex(-1)
        var e01 = Edge(-1)
        var e12 = Edge(-1)
        var e22 = Edge(-1)
        val graph = buildGraph(directed) {
            v0 = addVertex()
            v1 = addVertex()
            v2 = addVertex()
            e01 = addEdge(v0, v1)
            e12 = addEdge(v1, v2)
            e22 = addEdge(v2, v2)
        }
        assertThat(graph).isNotInstanceOf(IndexedEdgeGraph::class.java)
        val property = graph.createEdgeKeyProperty(case.type)
        assertThat(property.graph).isSameAs(graph)
        property[e01] = case.keyAt(0)
        assertThrows<IllegalStateException> { property[e01] }
        property[e12] = case.keyAt(1)
        property[e22] = case.keyAt(2)
        assertThat(property.getEdge(case.keyAt(0))).isEqualTo(e01)
        assertThat(property.put(e12, case.keyAt(3))).isEqualTo(case.keyAt(1))
        assertThat(property.hasEdge(case.keyAt(1))).isFalse()
        assertThrows<IllegalArgumentException> { property[e12] = case.keyAt(0) }

        graph.removeEdge(e12)
        assertThat(property.hasEdge(case.keyAt(3))).isFalse()
        assertThat(property.getEdge(case.keyAt(0))).isEqualTo(e01)
        assertThat(property.getEdge(case.keyAt(2))).isEqualTo(e22)

        val e02 = graph.addEdge(v0, v2)
        assertThrows<IllegalStateException> { property[e01] }
        graph.removeEdge(e02)
        assertThat(property[e01]).isEqualTo(case.keyAt(0))

        // removing v0 moves v2 into its place and renames e22; the key must follow the renamed edge
        val e22Ref = graph.createEdgeReference(e22)
        graph.removeVertex(v0)
        assertThat(graph.edges).containsExactly(e22Ref.unstable)
        assertThat(property.hasEdge(case.keyAt(0))).isFalse()
        assertThat(property[e22Ref.unstable]).isEqualTo(case.keyAt(2))
        assertThat(property.getEdge(case.keyAt(2))).isEqualTo(e22Ref.unstable)
        assertThat(property.copy().getEdge(case.keyAt(2))).isEqualTo(e22Ref.unstable)
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> edgeKeyPropertyRejectsDuplicateKeys(case: KeyCase<T>) {
        var e0 = Edge(-1)
        var e1 = Edge(-1)
        val graph = buildGraph(true, indexEdges = true) {
            val v0 = addVertex()
            val v1 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v1, v0)
        }
        val property = graph.createEdgeKeyProperty(case.type)
        property[e0] = case.keyAt(0)
        property[e1] = case.keyAt(1)

        assertThrows<IllegalArgumentException> { property[e1] = case.keyAt(0) }
        assertThat(property[e1]).isEqualTo(case.keyAt(1))

        // re-setting an edge's own key is a no-op
        property[e0] = case.keyAt(0)
        assertThat(property.put(e0, case.keyAt(0))).isEqualTo(case.keyAt(0))

        // changing a key frees the old one
        assertThat(property.put(e0, case.keyAt(2))).isEqualTo(case.keyAt(0))
        assertThat(property.hasEdge(case.keyAt(0))).isFalse()
        assertThat(property.getEdge(case.keyAt(2))).isEqualTo(e0)
        property[e1] = case.keyAt(0)
        assertThat(property.getEdge(case.keyAt(0))).isEqualTo(e1)
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> edgeKeyPropertyCopy(case: KeyCase<T>) {
        var e0 = Edge(-1)
        var e1 = Edge(-1)
        val graph = buildGraph(true, indexEdges = true) {
            val v0 = addVertex()
            val v1 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v1, v0)
        }
        val property = graph.createEdgeKeyProperty(case.type)
        property[e0] = case.keyAt(0)
        property[e1] = case.keyAt(1)

        val copy = property.copy()
        assertThat(copy.graph).isSameAs(graph)
        assertThat(copy.type).isEqualTo(property.type)
        property[e0] = case.keyAt(2)
        assertThat(copy[e0]).isEqualTo(case.keyAt(0))
        assertThat(copy.getEdge(case.keyAt(1))).isEqualTo(e1)
        assertThat(copy.hasEdge(case.keyAt(2))).isFalse()
    }

    @ParameterizedTest(name = "{0}, indexEdges={1}")
    @MethodSource("keyCasesWithIndexing")
    fun <T> edgeKeyPropertyOnImmutableGraph(case: KeyCase<T>, indexEdges: Boolean) {
        var e0 = Edge(-1)
        var e1 = Edge(-1)
        val graph = buildImmutableGraph(true, indexEdges = indexEdges) {
            val v0 = addVertex()
            val v1 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v1, v0)
        }
        val property = graph.createEdgeKeyProperty(case.type)
        assertThat(property.graph).isSameAs(graph)
        assertThrows<IllegalStateException> { property[e0] }
        property[e0] = case.keyAt(0)
        property[e1] = case.keyAt(1)
        assertThat(property.getEdge(case.keyAt(1))).isEqualTo(e1)
        assertThat(property[e0]).isEqualTo(case.keyAt(0))
        assertThrows<IllegalArgumentException> { property[e1] = case.keyAt(0) }
        if (indexEdges) assertThrows<IllegalArgumentException> { property[Edge(99)] = case.keyAt(2) }
        assertThrows<NoSuchElementException> { property.getEdge(case.keyAt(2)) }
        assertThat(property.copy().getEdge(case.keyAt(0))).isEqualTo(e0)
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> edgeKeyPropertyOnInducedFilteredGraph(case: KeyCase<T>) {
        var e0 = Edge(-1)
        var e1 = Edge(-1)
        var e2 = Edge(-1)
        val graph = buildGraph(true) {
            val v0 = addVertex()
            val v1 = addVertex()
            val v2 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v1, v2)
            e2 = addEdge(v2, v0)
        }
        val subgraph = graph.filter(null, edgeSetOf(e0, e2))
        val property = subgraph.createEdgeKeyProperty(case.type)
        assertThat(property.graph).isSameAs(subgraph)
        property[e0] = case.keyAt(0)
        assertThrows<IllegalStateException> { property[e0] }
        property[e2] = case.keyAt(2)
        assertThat(property[e2]).isEqualTo(case.keyAt(2))
        assertThat(property.getEdge(case.keyAt(0))).isEqualTo(e0)
        assertThrows<IllegalArgumentException> { property[e2] = case.keyAt(0) }

        // removing a filtered edge from the parent frees its key and keeps the property complete
        graph.removeEdge(e2)
        assertThat(property.hasEdge(case.keyAt(2))).isFalse()
        assertThat(property.getEdge(case.keyAt(0))).isEqualTo(e0)
    }
}
