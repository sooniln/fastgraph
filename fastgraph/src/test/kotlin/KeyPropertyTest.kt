package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class KeyPropertyTest {

    class KeyCase<T>(val type: PropertyType<T>, val keyAt: (Int) -> T) {
        override fun toString(): String = type.toString()
    }

    companion object {
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
        val v0 = Vertex(0)
        val v1 = Vertex(1)

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
        val v0 = Vertex(0)
        val v1 = Vertex(1)
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
        val v0 = Vertex(0)
        val v1 = Vertex(1)
        val v2 = Vertex(2)
        property[v0] = case.keyAt(0)
        property[v1] = case.keyAt(1)
        property[v2] = case.keyAt(2)

        // v0 is removed, so v2 (the last vertex) is reassigned to v0's freed index
        graph.removeVertex(v0)
        assertThat(property[v0]).isEqualTo(case.keyAt(2))
        assertThat(property[v1]).isEqualTo(case.keyAt(1))
        assertThat(property.getVertex(case.keyAt(2))).isEqualTo(v0)
        assertThat(property.hasVertex(case.keyAt(0))).isFalse()

        // removing the last vertex
        graph.removeVertex(v1)
        assertThat(property.hasVertex(case.keyAt(1))).isFalse()
        assertThat(property.getVertex(case.keyAt(2))).isEqualTo(v0)

        // removing an unkeyed vertex makes the property complete again
        val v3 = graph.addVertex()
        assertThrows<IllegalStateException> { property[v0] }
        graph.removeVertex(v3)
        assertThat(property[v0]).isEqualTo(case.keyAt(2))

        // an unkeyed last vertex reassigned onto a removed keyed vertex frees the removed key
        val v4 = graph.addVertex()
        graph.removeVertex(v0)
        assertThat(v4).isEqualTo(Vertex(1))
        assertThrows<IllegalStateException> { property.hasVertex(case.keyAt(2)) }
        property[v0] = case.keyAt(2)
        assertThat(property.getVertex(case.keyAt(2))).isEqualTo(v0)
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> vertexKeyPropertyCopy(case: KeyCase<T>) {
        val graph = buildGraph(true) { addVertex(); addVertex() }
        val property = graph.createVertexKeyProperty(case.type)
        property[Vertex(0)] = case.keyAt(0)
        property[Vertex(1)] = case.keyAt(1)

        val copy = property.copy()
        property[Vertex(0)] = case.keyAt(2)
        assertThat(copy[Vertex(0)]).isEqualTo(case.keyAt(0))
        assertThat(copy.getVertex(case.keyAt(1))).isEqualTo(Vertex(1))
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> vertexKeyPropertyOnImmutableGraph(case: KeyCase<T>) {
        val graph = buildImmutableGraph(true) { addVertex(); addVertex() }
        val property = graph.createVertexKeyProperty(case.type)
        assertThrows<IllegalStateException> { property[Vertex(0)] }
        property[Vertex(0)] = case.keyAt(0)
        property[Vertex(1)] = case.keyAt(1)
        assertThat(property.getVertex(case.keyAt(1))).isEqualTo(Vertex(1))
        assertThrows<IllegalArgumentException> { property[Vertex(1)] = case.keyAt(0) }
    }

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> vertexKeyPropertyOnInducedSubgraph(case: KeyCase<T>) {
        val graph = buildGraph(true) { addVertex(); addVertex(); addVertex() }
        val subgraph = graph.subgraph(vertexSetOf(Vertex(0), Vertex(2)), emptyEdgeSet())
        val property = subgraph.createVertexKeyProperty(case.type)
        property[Vertex(0)] = case.keyAt(0)
        assertThrows<IllegalStateException> { property[Vertex(0)] }
        property[Vertex(2)] = case.keyAt(2)
        assertThat(property[Vertex(2)]).isEqualTo(case.keyAt(2))
        assertThat(property.getVertex(case.keyAt(0))).isEqualTo(Vertex(0))
        assertThrows<IllegalArgumentException> { property[Vertex(2)] = case.keyAt(0) }
    }

    @Test
    fun filteredSubgraphDoesNotSupportKeyProperties() {
        val graph = buildGraph(true) { addVertex() }
        val subgraph = graph.subgraph({ true }, { true })
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

        val e2 = graph.addEdge(Vertex(1), Vertex(0))
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

    @ParameterizedTest
    @MethodSource("keyCases")
    fun <T> edgeKeyPropertyOnNonIndexedEdges(case: KeyCase<T>) {
        var e0 = Edge(-1)
        var e1 = Edge(-1)
        val graph = buildGraph(true, multiEdge = true) {
            val v0 = addVertex()
            val v1 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v0, v1)
        }
        val property = graph.createEdgeKeyProperty(case.type)
        property[e0] = case.keyAt(0)
        assertThrows<IllegalStateException> { property[e0] }
        property[e1] = case.keyAt(1)
        assertThat(property.getEdge(case.keyAt(0))).isEqualTo(e0)
        assertThat(property.put(e1, case.keyAt(2))).isEqualTo(case.keyAt(1))
        assertThat(property.hasEdge(case.keyAt(1))).isFalse()

        // e1 is the last edge, so no reassignment takes place
        graph.removeEdge(e1)
        assertThat(property.hasEdge(case.keyAt(2))).isFalse()
        assertThat(property.getEdge(case.keyAt(0))).isEqualTo(e0)

        val e2 = graph.addEdge(Vertex(0), Vertex(1))
        assertThrows<IllegalStateException> { property[e0] }
        graph.removeEdge(e2)
        assertThat(property[e0]).isEqualTo(case.keyAt(0))
    }
}
