package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class PropertyTest {

    enum class GraphType {
        MUTABLE_GRAPH {
            override fun loadGraph(): Graph = buildGraph(true, builder = build())
        },
        MUTABLE_NETWORK {
            override fun loadGraph(): Graph = buildGraph(true, multiEdge = true, builder = build())
        },
        IMMUTABLE_GRAPH {
            override fun loadGraph(): Graph = buildImmutableGraph(true, builder = build())
        },
        IMMUTABLE_NETWORK {
            override fun loadGraph(): Graph = buildImmutableGraph(true, multiEdge = true, builder = build())
        };

        abstract fun loadGraph(): Graph

        protected fun build(): GraphBuilder.() -> Unit = {
            val v0 = addVertex()
            val v1 = addVertex()
            val v2 = addVertex()
            addEdge(v0, v1)
            addEdge(v1, v2)
            addEdge(v2, v0)
            addEdge(v0, v0)
        }
    }

    class PropertyCase<T>(
        val type: StaticType<T>,
        val defaultValue: T,
        val valueAt: (Int) -> T,
    ) {
        override fun toString(): String = type.toString()
    }

    companion object {
        @JvmStatic
        fun propertyCases(): List<PropertyCase<*>> = listOf(
            PropertyCase(staticTypeOf(), Unit) { },
            PropertyCase(staticTypeOf(), true) { index -> index % 2 == 0 },
            PropertyCase(staticTypeOf(), 1.toByte()) { index -> (2 shl index).toByte() },
            PropertyCase(staticTypeOf(), 1.toShort()) { index -> (2 shl index).toShort() },
            PropertyCase(staticTypeOf(), 1) { index -> 2 shl index },
            PropertyCase(staticTypeOf(), 1L) { index -> 2L shl index },
            PropertyCase(staticTypeOf(), 1f) { index -> (2 shl index).toFloat() },
            PropertyCase(staticTypeOf(), 1.0) { index -> (2 shl index).toDouble() },
            PropertyCase(staticTypeOf(), "hello") { index -> "test$index" },
        )

        @JvmStatic
        fun graphTypeAndPropertyCases(): Stream<Arguments> =
            GraphType.entries.stream().flatMap { graphType ->
                propertyCases().stream().map { case -> Arguments.of(graphType, case) }
            }
    }

    @Suppress("UNCHECKED_CAST")
    @ParameterizedTest(name = "graphType={0}, type={1}")
    @MethodSource("graphTypeAndPropertyCases")
    fun vertexProperty(graphType: GraphType, case: PropertyCase<*>) {
        val typedCase = case as PropertyCase<Any?>
        val graph = graphType.loadGraph()
        val property = graph.createVertexProperty(typedCase.type) { typedCase.defaultValue }

        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(typedCase.defaultValue)
        }

        var index = 0
        for (vertex in graph.vertices) {
            property[vertex] = typedCase.valueAt(index)
            index++
        }

        index = 0
        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(typedCase.valueAt(index))
            index++
        }
    }

    @Suppress("UNCHECKED_CAST")
    @ParameterizedTest(name = "graphType={0}, type={1}")
    @MethodSource("graphTypeAndPropertyCases")
    fun edgeProperty(graphType: GraphType, case: PropertyCase<*>) {
        val typedCase = case as PropertyCase<Any?>
        val graph = graphType.loadGraph()
        val property = graph.createEdgeProperty(typedCase.type) { typedCase.defaultValue }

        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(typedCase.defaultValue)
        }

        var index = 0
        for (edge in graph.edges) {
            property[edge] = typedCase.valueAt(index)
            index++
        }

        index = 0
        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(typedCase.valueAt(index))
            index++
        }
    }

    @Test
    fun vertexPropertyValueFollowsReassignedVertexOnRemoval() {
        var v0 = Vertex(-1)
        var v1 = Vertex(-1)
        var v2 = Vertex(-1)
        val graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            v2 = addVertex()
        }
        val property = graph.createVertexProperty<Int>(0)
        property[v0] = 10
        property[v1] = 11
        property[v2] = 12

        // v0 is removed, so v2 (the last vertex) is reassigned to v0's freed index
        graph.removeVertex(v0)

        assertThat(property[v1]).isEqualTo(11)
        assertThat(property[v0]).isEqualTo(12)
    }

    @Test
    fun edgePropertyValueFollowsReassignedEdgeOnRemoval() {
        var v0 = Vertex(-1)
        var v1 = Vertex(-1)
        var e0 = Edge(-1)
        var e1 = Edge(-1)
        var e2 = Edge(-1)
        val graph = buildGraph(true, multiEdge = true, indexEdges = true) {
            v0 = addVertex()
            v1 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v0, v1)
            e2 = addEdge(v0, v1)
        }
        val property = graph.createEdgeProperty<Int>(0)
        property[e0] = 10
        property[e1] = 11
        property[e2] = 12

        var reassignedTo: Edge? = null
        graph.registerEdgeChangeListener(object : EdgeChangeListener {
            override fun onEdgeAdded(edge: Edge) {}
            override fun onEdgeRemoved(edge: Edge) {}
            override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
                reassignedTo = newEdge
            }
        })

        // e0 is removed, so the last edge (e2) is reassigned to e0's freed index
        graph.removeEdge(e0)

        assertThat(property[e1]).isEqualTo(11)
        assertThat(reassignedTo).isNotNull
        assertThat(property[reassignedTo!!]).isEqualTo(12)
    }

    @Test
    fun vertexPropertyMapTransformsReadsOnly() {
        val graph = buildGraph(true) { addVertex(); addVertex() }
        val ints = graph.createVertexProperty<Int>(0)
        var index = 0
        for (vertex in graph.vertices) {
            ints[vertex] = index++
        }

        val strings = ints.map { value -> value.toString() }

        for (vertex in graph.vertices) {
            assertThat(strings[vertex]).isEqualTo(ints[vertex].toString())
        }
    }

    @Test
    fun vertexPropertyMapWithReverseTransformSupportsWrites() {
        val graph = buildGraph(true) { addVertex() }
        val ints = graph.createVertexProperty<Int>(0)
        val v0 = graph.vertices.first()

        val strings = ints.map({ value -> value.toString() }, { value -> value.toInt() })

        strings[v0] = "42"

        assertThat(ints[v0]).isEqualTo(42)
        assertThat(strings[v0]).isEqualTo("42")
    }

    @Test
    fun edgePropertyMapTransformsReadsOnly() {
        var v0 = Vertex(-1)
        var v1 = Vertex(-1)
        val graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            addEdge(v0, v1)
        }
        val ints = graph.createEdgeProperty<Int>(0)
        for (edge in graph.edges) {
            ints[edge] = 5
        }

        val strings = ints.map { value -> value.toString() }

        for (edge in graph.edges) {
            assertThat(strings[edge]).isEqualTo(ints[edge].toString())
        }
    }

    @Test
    fun edgePropertyMapWithReverseTransformSupportsWrites() {
        var v0 = Vertex(-1)
        var v1 = Vertex(-1)
        val graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            addEdge(v0, v1)
        }
        val ints = graph.createEdgeProperty<Int>(0)
        val e0 = graph.edges.first()

        val strings = ints.map({ value -> value.toString() }, { value -> value.toInt() })

        strings[e0] = "42"

        assertThat(ints[e0]).isEqualTo(42)
        assertThat(strings[e0]).isEqualTo("42")
    }

    @Test
    fun vertexPropertyCopyIntoCopiesAllValues() {
        val graph = buildGraph(true) { addVertex(); addVertex() }
        val source = graph.createVertexProperty<Int>(0)
        val target = graph.createVertexProperty<Int>(-1)
        var index = 0
        for (vertex in graph.vertices) {
            source[vertex] = index++
        }

        source.copyInto(target)

        for (vertex in graph.vertices) {
            assertThat(target[vertex]).isEqualTo(source[vertex])
        }
    }

    @Test
    fun edgePropertyCopyIntoCopiesAllValues() {
        var v0 = Vertex(-1)
        var v1 = Vertex(-1)
        val graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            addEdge(v0, v1)
        }
        val source = graph.createEdgeProperty<Int>(0)
        val target = graph.createEdgeProperty<Int>(-1)
        for (edge in graph.edges) {
            source[edge] = 7
        }

        source.copyInto(target)

        for (edge in graph.edges) {
            assertThat(target[edge]).isEqualTo(source[edge])
        }
    }

    @Test
    fun vertexPropertySafeCastSucceedsForMatchingType() {
        val graph = buildGraph(true) { addVertex() }
        val property: VertexProperty<*> = graph.createVertexProperty<Int>(0)

        val cast = property.safeCast<Int>()

        assertThat(cast[graph.vertices.first()]).isEqualTo(0)
    }

    @Test
    fun vertexPropertySafeCastThrowsForMismatchedType() {
        val graph = buildGraph(true) { addVertex() }
        val property: VertexProperty<*> = graph.createVertexProperty<Int>(0)

        assertThrows<TypeCastException> { property.safeCast<String>() }
    }

    @Test
    fun edgePropertySafeCastSucceedsForMatchingType() {
        var v0 = Vertex(-1)
        var v1 = Vertex(-1)
        val graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            addEdge(v0, v1)
        }
        val property: EdgeProperty<*> = graph.createEdgeProperty<Int>(0)

        val cast = property.safeCast<Int>()

        assertThat(cast[graph.edges.first()]).isEqualTo(0)
    }

    @Test
    fun edgePropertySafeCastThrowsForMismatchedType() {
        var v0 = Vertex(-1)
        var v1 = Vertex(-1)
        val graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            addEdge(v0, v1)
        }
        val property: EdgeProperty<*> = graph.createEdgeProperty<Int>(0)

        assertThrows<TypeCastException> { property.safeCast<String>() }
    }

    @Test
    fun unitVertexPropertyAlwaysReturnsUnit() {
        val graph = buildGraph(true) { addVertex(); addVertex() }
        val property = unitVertexProperty(graph)

        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(Unit)
        }
        property[graph.vertices.first()] = Unit
        assertThat(property[graph.vertices.first()]).isEqualTo(Unit)
    }

    @Test
    fun unitEdgePropertyAlwaysReturnsUnit() {
        var v0 = Vertex(-1)
        var v1 = Vertex(-1)
        val graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            addEdge(v0, v1)
        }
        val property = unitEdgeProperty(graph)

        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(Unit)
        }
        property[graph.edges.first()] = Unit
        assertThat(property[graph.edges.first()]).isEqualTo(Unit)
    }
}
