package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream

class PropertyTest {

    enum class GraphType(val directed: Boolean, val immutable: Boolean, val multiEdge: Boolean, val indexEdges: Boolean) {
        MUTABLE_GRAPH(directed = true, immutable = false, multiEdge = false, indexEdges = false),
        MUTABLE_INDEXED_GRAPH(directed = true, immutable = false, multiEdge = false, indexEdges = true),
        MUTABLE_NETWORK(directed = true, immutable = false, multiEdge = true, indexEdges = false),
        IMMUTABLE_GRAPH(directed = true, immutable = true, multiEdge = false, indexEdges = false),
        IMMUTABLE_INDEXED_GRAPH(directed = true, immutable = true, multiEdge = false, indexEdges = true),
        IMMUTABLE_NETWORK(directed = true, immutable = true, multiEdge = true, indexEdges = false),
        UNDIRECTED_MUTABLE_GRAPH(directed = false, immutable = false, multiEdge = false, indexEdges = false),
        UNDIRECTED_MUTABLE_NETWORK(directed = false, immutable = false, multiEdge = true, indexEdges = false),
        UNDIRECTED_IMMUTABLE_GRAPH(directed = false, immutable = true, multiEdge = false, indexEdges = false),
        UNDIRECTED_IMMUTABLE_NETWORK(directed = false, immutable = true, multiEdge = true, indexEdges = false);

        fun loadGraph(): Graph = if (immutable) {
            buildImmutableGraph(directed, multiEdge, indexEdges, build())
        } else {
            buildGraph(directed, multiEdge, indexEdges, build())
        }

        fun loadMutableGraph(): MutableGraph {
            check(!immutable)
            return buildGraph(directed, multiEdge, indexEdges, build())
        }

        private fun build(): GraphBuilder.() -> Unit = {
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
        val type: PropertyType<T>,
        val defaultValue: T,
        val valueAt: (Int) -> T,
    ) {
        override fun toString(): String = type.toString()
    }

    companion object {
        @JvmStatic
        fun propertyCases(): List<PropertyCase<*>> = listOf(
            PropertyCase(propertyTypeOf(), Unit) { },
            PropertyCase(propertyTypeOf(), true) { index -> index % 2 == 0 },
            PropertyCase(propertyTypeOf(), 1.toByte()) { index -> (2 shl index).toByte() },
            PropertyCase(propertyTypeOf(), 1.toShort()) { index -> (2 shl index).toShort() },
            PropertyCase(propertyTypeOf(), 1) { index -> 2 shl index },
            PropertyCase(propertyTypeOf(), 1L) { index -> 2L shl index },
            PropertyCase(propertyTypeOf(), 1f) { index -> (2 shl index).toFloat() },
            PropertyCase(propertyTypeOf(), 1.0) { index -> (2 shl index).toDouble() },
            PropertyCase(propertyTypeOf(), "hello") { index -> "test$index" },
            // nullable primitives take the generic path
            PropertyCase(propertyTypeOf<Int?>(), null) { index -> if (index == 0) null else index },
            // vertices and edges have their own specialisations
            PropertyCase(propertyTypeOf(), Vertex(-1)) { index -> Vertex(index) },
            PropertyCase(propertyTypeOf(), Edge(-1)) { index -> Edge(index.toLong()) },
        )

        @JvmStatic
        fun graphTypeAndPropertyCases(): Stream<Arguments> =
            GraphType.entries.stream().flatMap { graphType ->
                propertyCases().stream().map { case -> Arguments.of(graphType, case) }
            }

        @JvmStatic
        fun mutableGraphTypeAndPropertyCases(): Stream<Arguments> =
            graphTypeAndPropertyCases().filter { !(it.get()[0] as GraphType).immutable }

        @JvmStatic
        fun graphTypes(): List<GraphType> = GraphType.entries

        @JvmStatic
        fun mutableGraphTypes(): List<GraphType> = GraphType.entries.filter { !it.immutable }
    }

    // --- get/set on every graph implementation ------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    @ParameterizedTest(name = "graphType={0}, type={1}")
    @MethodSource("graphTypeAndPropertyCases")
    fun vertexProperty(graphType: GraphType, case: PropertyCase<*>) {
        val typedCase = case as PropertyCase<Any?>
        val graph = graphType.loadGraph()
        val property = graph.createVertexProperty(typedCase.type) { typedCase.defaultValue }

        assertThat(property.graph).isSameAs(graph)
        assertThat(property.type).isEqualTo(typedCase.type)
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

        // put returns the previous value
        val first = graph.vertices.first()
        assertThat(property.put(first, typedCase.valueAt(5))).isEqualTo(typedCase.valueAt(0))
        assertThat(property[first]).isEqualTo(typedCase.valueAt(5))

        // stable references work through the extension overloads
        val reference = graph.createVertexReference(first)
        assertThat(property[reference]).isEqualTo(typedCase.valueAt(5))
        property[reference] = typedCase.valueAt(6)
        assertThat(property.put(reference, typedCase.valueAt(7))).isEqualTo(typedCase.valueAt(6))
        assertThat(property[first]).isEqualTo(typedCase.valueAt(7))
    }

    @Suppress("UNCHECKED_CAST")
    @ParameterizedTest(name = "graphType={0}, type={1}")
    @MethodSource("graphTypeAndPropertyCases")
    fun edgeProperty(graphType: GraphType, case: PropertyCase<*>) {
        val typedCase = case as PropertyCase<Any?>
        val graph = graphType.loadGraph()
        val property = graph.createEdgeProperty(typedCase.type) { typedCase.defaultValue }

        assertThat(property.graph).isSameAs(graph)
        assertThat(property.type).isEqualTo(typedCase.type)
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

        // put returns the previous value
        val first = graph.edges.first()
        assertThat(property.put(first, typedCase.valueAt(5))).isEqualTo(typedCase.valueAt(0))
        assertThat(property[first]).isEqualTo(typedCase.valueAt(5))

        // stable references work through the extension overloads
        val reference = graph.createEdgeReference(first)
        assertThat(property[reference]).isEqualTo(typedCase.valueAt(5))
        property[reference] = typedCase.valueAt(6)
        assertThat(property.put(reference, typedCase.valueAt(7))).isEqualTo(typedCase.valueAt(6))
        assertThat(property[first]).isEqualTo(typedCase.valueAt(7))
    }

    // --- defaults -----------------------------------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    @ParameterizedTest(name = "graphType={0}, type={1}")
    @MethodSource("mutableGraphTypeAndPropertyCases")
    fun vertexPropertyAppliesDefaultToVerticesAddedLater(graphType: GraphType, case: PropertyCase<*>) {
        val typedCase = case as PropertyCase<Any?>
        val graph = graphType.loadMutableGraph()
        val property = graph.createVertexProperty(typedCase.type) { typedCase.defaultValue }
        for (vertex in graph.vertices) {
            property[vertex] = typedCase.valueAt(1)
        }

        val added = graph.addVertex()

        assertThat(property[added]).isEqualTo(typedCase.defaultValue)
        assertThat(property.put(added, typedCase.valueAt(2))).isEqualTo(typedCase.defaultValue)
        assertThat(property[added]).isEqualTo(typedCase.valueAt(2))
        for (vertex in graph.vertices) {
            if (vertex != added) assertThat(property[vertex]).isEqualTo(typedCase.valueAt(1))
        }

        // removing and re-adding yields a fresh default, not the stale value
        graph.removeVertex(added)
        val readded = graph.addVertex()
        assertThat(property[readded]).isEqualTo(typedCase.defaultValue)
    }

    @Suppress("UNCHECKED_CAST")
    @ParameterizedTest(name = "graphType={0}, type={1}")
    @MethodSource("mutableGraphTypeAndPropertyCases")
    fun edgePropertyAppliesDefaultToEdgesAddedLater(graphType: GraphType, case: PropertyCase<*>) {
        val typedCase = case as PropertyCase<Any?>
        val graph = graphType.loadMutableGraph()
        val property = graph.createEdgeProperty(typedCase.type) { typedCase.defaultValue }
        for (edge in graph.edges) {
            property[edge] = typedCase.valueAt(1)
        }
        val source = graph.vertices.elementAt(1)
        val target = graph.addVertex()

        val added = graph.addEdge(source, target)

        assertThat(property[added]).isEqualTo(typedCase.defaultValue)
        assertThat(property.put(added, typedCase.valueAt(2))).isEqualTo(typedCase.defaultValue)
        assertThat(property[added]).isEqualTo(typedCase.valueAt(2))
        for (edge in graph.edges) {
            if (edge != added) assertThat(property[edge]).isEqualTo(typedCase.valueAt(1))
        }

        // removing and re-adding yields a fresh default, not the stale value
        graph.removeEdge(added)
        val readded = graph.addEdge(source, target)
        assertThat(property[readded]).isEqualTo(typedCase.defaultValue)
    }

    @ParameterizedTest(name = "graphType={0}")
    @MethodSource("mutableGraphTypes")
    fun vertexDefaultFunctionReceivesTheVertex(graphType: GraphType) {
        val graph = graphType.loadMutableGraph()
        val property = graph.createVertexProperty { vertex -> vertex.id * 10 }

        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(vertex.id * 10)
        }
        val added = graph.addVertex()
        assertThat(property[added]).isEqualTo(added.id * 10)
    }

    @ParameterizedTest(name = "graphType={0}")
    @MethodSource("mutableGraphTypes")
    fun edgeDefaultFunctionReceivesTheEdge(graphType: GraphType) {
        val graph = graphType.loadMutableGraph()
        val property = graph.createEdgeProperty { edge -> graph.edgeSource(edge).id + graph.edgeTarget(edge).id }

        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(graph.edgeSource(edge).id + graph.edgeTarget(edge).id)
        }
        val added = graph.addEdge(graph.vertices.elementAt(1), graph.addVertex())
        assertThat(property[added]).isEqualTo(graph.edgeSource(added).id + graph.edgeTarget(added).id)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun immutableGraphEvaluatesDefaultsEagerlyAndMutableGraphsLazily(directed: Boolean) {
        val mutable = buildGraph(directed) {
            val v0 = addVertex()
            val v1 = addVertex()
            addEdge(v0, v1)
        }
        val immutable = mutable.toImmutableGraph()

        var vertexCalls = 0
        var edgeCalls = 0
        immutable.createVertexProperty<Int> { vertexCalls++ }
        immutable.createEdgeProperty<Int> { edgeCalls++ }
        // an immutable graph never references the initializer after construction, so it has been applied to
        // every vertex/edge by now
        assertThat(vertexCalls).isEqualTo(immutable.vertices.size)
        assertThat(edgeCalls).isEqualTo(immutable.edges.size)

        vertexCalls = 0
        edgeCalls = 0
        val vertexProperty = mutable.createVertexProperty<Int> { vertexCalls++ }
        val edgeProperty = mutable.createEdgeProperty<Int> { edgeCalls++ }
        // the mutable graph may keep the initializer around, but only ever invokes it for unset values
        val v0 = mutable.vertices.first()
        vertexProperty[v0] = 100
        edgeProperty[mutable.edges.first()] = 100
        assertThat(vertexProperty[v0]).isEqualTo(100)
        assertThat(edgeProperty[mutable.edges.first()]).isEqualTo(100)
        assertThat(vertexCalls).isLessThanOrEqualTo(mutable.vertices.size)
        assertThat(edgeCalls).isLessThanOrEqualTo(mutable.edges.size)
    }

    // --- values follow re-assignment ----------------------------------------------------------------------------------

    @ParameterizedTest(name = "graphType={0}")
    @MethodSource("mutableGraphTypes")
    fun vertexPropertyValueFollowsReassignedVertexOnRemoval(graphType: GraphType) {
        val graph = graphType.loadMutableGraph()
        val v0 = graph.vertices.elementAt(0)
        val v1 = graph.vertices.elementAt(1)
        val v2 = graph.vertices.elementAt(2)
        val property = graph.createVertexProperty<Int>(0)
        property[v0] = 10
        property[v1] = 11
        property[v2] = 12

        var reassignedTo: Vertex? = null
        graph.registerVertexChangeListener(object : VertexChangeListener {
            override fun onVertexAdded(vertex: Vertex) {}
            override fun onVertexRemoved(vertex: Vertex) {}
            override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
                assertThat(oldVertex).isEqualTo(v2)
                reassignedTo = newVertex
            }
        })

        // v0 is removed, so the last vertex (v2) is reassigned to v0's freed index
        graph.removeVertex(v0)

        assertThat(property[v1]).isEqualTo(11)
        assertThat(property[reassignedTo!!]).isEqualTo(12)
    }

    @ParameterizedTest(name = "graphType={0}")
    @MethodSource("mutableGraphTypes")
    fun edgePropertyValueFollowsReassignedEdgeOnRemoval(graphType: GraphType) {
        val graph = graphType.loadMutableGraph()
        val e0 = graph.edges.first()
        val others = graph.edges.filter { it != e0 }
        val property = graph.createEdgeProperty<Int>(0)
        property[e0] = 10
        val expected = HashMap<EdgeReference, Int>()
        others.forEachIndexed { index, edge ->
            property[edge] = 11 + index
            expected[graph.createEdgeReference(edge)] = 11 + index
        }

        // whether the removal renames another edge is up to the implementation; values must follow either way
        graph.removeEdge(e0)

        assertThat(graph.edges).hasSize(others.size)
        for ((reference, value) in expected) {
            assertThat(property[reference]).isEqualTo(value)
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun edgePropertyValueFollowsCanonicalEdgeRenamedByVertexRemoval(directed: Boolean) {
        // in a canonical edge graph, moving a vertex renames every edge incident to it
        val graph = mutableGraph(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e12 = graph.addEdge(v1, v2)
        val e22 = graph.addEdge(v2, v2)
        val e01 = graph.addEdge(v0, v1)
        val property = graph.createEdgeProperty<String>()
        property[e12] = "e12"
        property[e22] = "e22"
        property[e01] = "e01"
        val e12Ref = graph.createEdgeReference(e12)
        val e22Ref = graph.createEdgeReference(e22)

        graph.removeVertex(v0)

        assertThat(graph.edges).hasSize(2)
        assertThat(property[e12Ref]).isEqualTo("e12")
        assertThat(property[e22Ref]).isEqualTo("e22")
        assertThat(graph.edges.contains(e12Ref.unstable)).isTrue
        assertThat(graph.edges.contains(e22Ref.unstable)).isTrue
        for (edge in graph.edges) {
            assertThat(property[edge]).isIn("e12", "e22")
        }
    }

    // --- map ----------------------------------------------------------------------------------------------------------

    @Test
    fun vertexPropertyMapTransformsReadsOnly() {
        val graph = buildGraph(true) { addVertex(); addVertex() }
        val ints = graph.createVertexProperty<Int>(0)
        var index = 0
        for (vertex in graph.vertices) {
            ints[vertex] = index++
        }

        val strings: VertexProperty<out String> = ints.map { value -> value.toString() }

        assertThat(strings).isNotInstanceOf(MutableVertexProperty::class.java)
        assertThat(strings.graph).isSameAs(graph)
        assertThat(strings.type).isEqualTo(propertyTypeOf<String>())
        for (vertex in graph.vertices) {
            assertThat(strings[vertex]).isEqualTo(ints[vertex].toString())
        }

        // the mapping is a live view of the source
        ints[graph.vertices.first()] = 42
        assertThat(strings[graph.vertices.first()]).isEqualTo("42")

        val explicit = map(ints, propertyTypeOf<String>()) { value -> "v$value" }
        assertThat(explicit[graph.vertices.first()]).isEqualTo("v42")
    }

    @Test
    fun vertexPropertyMapWithReverseTransformSupportsWrites() {
        val graph = buildGraph(true) { addVertex() }
        val ints = graph.createVertexProperty<Int>(0)
        val v0 = graph.vertices.first()

        val strings = ints.map({ value -> value.toString() }, { value -> value.toInt() })

        assertThat(strings.graph).isSameAs(graph)
        assertThat(strings.type).isEqualTo(propertyTypeOf<String>())
        strings[v0] = "42"

        assertThat(ints[v0]).isEqualTo(42)
        assertThat(strings[v0]).isEqualTo("42")
        assertThat(strings.put(v0, "7")).isEqualTo("42")
        assertThat(ints[v0]).isEqualTo(7)
    }

    @Test
    fun edgePropertyMapTransformsReadsOnly() {
        var v0: Vertex
        var v1: Vertex
        val graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            addEdge(v0, v1)
            addEdge(v1, v0)
        }
        val ints = graph.createEdgeProperty<Int>(0)
        var index = 0
        for (edge in graph.edges) {
            ints[edge] = index++
        }

        val strings: EdgeProperty<out String> = ints.map { value -> value.toString() }

        assertThat(strings).isNotInstanceOf(MutableEdgeProperty::class.java)
        assertThat(strings.graph).isSameAs(graph)
        assertThat(strings.type).isEqualTo(propertyTypeOf<String>())
        for (edge in graph.edges) {
            assertThat(strings[edge]).isEqualTo(ints[edge].toString())
        }

        // the mapping is a live view of the source
        ints[graph.edges.first()] = 42
        assertThat(strings[graph.edges.first()]).isEqualTo("42")

        val explicit = map(ints, propertyTypeOf<String>()) { value -> "e$value" }
        assertThat(explicit[graph.edges.first()]).isEqualTo("e42")
    }

    @Test
    fun edgePropertyMapWithReverseTransformSupportsWrites() {
        var v0: Vertex
        var v1: Vertex
        val graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            addEdge(v0, v1)
        }
        val ints = graph.createEdgeProperty<Int>(0)
        val e0 = graph.edges.first()

        val strings = ints.map({ value -> value.toString() }, { value -> value.toInt() })

        assertThat(strings.graph).isSameAs(graph)
        assertThat(strings.type).isEqualTo(propertyTypeOf<String>())
        strings[e0] = "42"

        assertThat(ints[e0]).isEqualTo(42)
        assertThat(strings[e0]).isEqualTo("42")
        assertThat(strings.put(e0, "7")).isEqualTo("42")
        assertThat(ints[e0]).isEqualTo(7)
    }

    // --- copies -------------------------------------------------------------------------------------------------------

    @Test
    fun vertexPropertyCopyIntoAndCopyFromCopyAllValues() {
        val graph = buildGraph(true) { addVertex(); addVertex(); addVertex() }
        val source = graph.createVertexProperty<Int>(0)
        var index = 0
        for (vertex in graph.vertices) {
            source[vertex] = ++index
        }

        val target = graph.createVertexProperty<Int>(-1)
        source.copyInto(target)
        for (vertex in graph.vertices) {
            assertThat(target[vertex]).isEqualTo(source[vertex])
        }

        val other = graph.createVertexProperty<Int>(-1)
        other.copyFrom(source)
        for (vertex in graph.vertices) {
            assertThat(other[vertex]).isEqualTo(source[vertex])
        }

        // copies are independent of the source
        source[graph.vertices.first()] = 99
        assertThat(target[graph.vertices.first()]).isEqualTo(1)
        assertThat(other[graph.vertices.first()]).isEqualTo(1)
    }

    @Test
    fun edgePropertyCopyIntoAndCopyFromCopyAllValues() {
        var v0: Vertex
        var v1: Vertex
        var v2: Vertex
        val graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            v2 = addVertex()
            addEdge(v0, v1)
            addEdge(v1, v2)
            addEdge(v2, v0)
        }
        val source = graph.createEdgeProperty<Int>(0)
        var index = 0
        for (edge in graph.edges) {
            source[edge] = ++index
        }

        val target = graph.createEdgeProperty<Int>(-1)
        source.copyInto(target)
        for (edge in graph.edges) {
            assertThat(target[edge]).isEqualTo(source[edge])
        }

        val other = graph.createEdgeProperty<Int>(-1)
        other.copyFrom(source)
        for (edge in graph.edges) {
            assertThat(other[edge]).isEqualTo(source[edge])
        }

        // copies are independent of the source
        source[graph.edges.first()] = 99
        assertThat(target[graph.edges.first()]).isEqualTo(1)
        assertThat(other[graph.edges.first()]).isEqualTo(1)
    }

    @Test
    fun vertexPropertyCopyCreatesIndependentPropertyWithNewDefault() {
        val graph = buildGraph(true) { addVertex(); addVertex() }
        val source = graph.createVertexProperty<Int>(0)
        source[graph.vertices.elementAt(0)] = 5

        val copy = source.copy { -1 }

        assertThat(copy.graph).isSameAs(graph)
        assertThat(copy.type).isEqualTo(source.type)
        assertThat(copy[graph.vertices.elementAt(0)]).isEqualTo(5)
        assertThat(copy[graph.vertices.elementAt(1)]).isEqualTo(0)
        source[graph.vertices.elementAt(0)] = 6
        copy[graph.vertices.elementAt(1)] = 7
        assertThat(copy[graph.vertices.elementAt(0)]).isEqualTo(5)
        assertThat(source[graph.vertices.elementAt(1)]).isEqualTo(0)

        // the new default applies to vertices added afterwards
        val added = graph.addVertex()
        assertThat(copy[added]).isEqualTo(-1)
        assertThat(source[added]).isEqualTo(0)
    }

    @Test
    fun edgePropertyCopyCreatesIndependentPropertyWithNewDefault() {
        var v0: Vertex
        var v1: Vertex
        val graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            addEdge(v0, v1)
            addEdge(v1, v0)
        }
        val e0 = graph.edges.first()
        val e1 = graph.edges.last { it != e0 }
        val source = graph.createEdgeProperty<Int>(0)
        source[e0] = 5

        val copy = source.copy { -1 }

        assertThat(copy.graph).isSameAs(graph)
        assertThat(copy.type).isEqualTo(source.type)
        assertThat(copy[e0]).isEqualTo(5)
        assertThat(copy[e1]).isEqualTo(0)
        source[e0] = 6
        copy[e1] = 7
        assertThat(copy[e0]).isEqualTo(5)
        assertThat(source[e1]).isEqualTo(0)

        // the new default applies to edges added afterwards
        val added = graph.addEdge(graph.vertices.elementAt(0), graph.vertices.elementAt(0))
        assertThat(copy[added]).isEqualTo(-1)
        assertThat(source[added]).isEqualTo(0)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun propertiesCopyBetweenGraphsWithIdenticalIds(directed: Boolean) {
        val mutable = buildGraph(directed) {
            val v0 = addVertex()
            val v1 = addVertex()
            addEdge(v0, v1)
            addEdge(v1, v1)
        }
        val immutable = mutable.toImmutableGraph()
        val vertexSource = mutable.createVertexProperty { vertex -> "v${vertex.id}" }
        val edgeSource = mutable.createEdgeProperty { edge -> "e${edge.id}" }

        val vertexTarget = immutable.createVertexProperty<String>()
        val edgeTarget = immutable.createEdgeProperty<String>()
        vertexSource.copyInto(vertexTarget)
        edgeSource.copyInto(edgeTarget)
        for (vertex in immutable.vertices) assertThat(vertexTarget[vertex]).isEqualTo("v${vertex.id}")
        for (edge in immutable.edges) assertThat(edgeTarget[edge]).isEqualTo("e${edge.id}")

        val vertexBack = mutable.createVertexProperty<String>()
        val edgeBack = mutable.createEdgeProperty<String>()
        vertexBack.copyFrom(vertexTarget)
        edgeBack.copyFrom(edgeTarget)
        for (vertex in mutable.vertices) assertThat(vertexBack[vertex]).isEqualTo("v${vertex.id}")
        for (edge in mutable.edges) assertThat(edgeBack[edge]).isEqualTo("e${edge.id}")
    }

    // --- safeCast -----------------------------------------------------------------------------------------------------

    @Test
    fun vertexPropertySafeCast() {
        val graph = buildGraph(true) { addVertex() }
        val v0 = graph.vertices.first()
        val property: VertexProperty<*> = graph.createVertexProperty<Int>(0)
        val mutableProperty: MutableVertexProperty<*> = graph.createVertexProperty<Int>(0)
        val keyProperty: VertexKeyProperty<*> = graph.createVertexKeyProperty<Int>()
        val mutableKeyProperty: MutableVertexKeyProperty<*> = graph.createVertexKeyProperty<Int>()

        assertThat(property.safeCast<Int>()[v0]).isEqualTo(0)
        assertThat(mutableProperty.safeCast<Int>().put(v0, 5)).isEqualTo(0)
        mutableKeyProperty.safeCast<Int>()[v0] = 9
        assertThat(mutableKeyProperty.safeCast<Int>().getVertex(9)).isEqualTo(v0)
        assertThat(keyProperty.safeCast<Int>()).isSameAs(keyProperty)

        // a non-null type may be read as its nullable counterpart, but not the other way round
        assertThat(property.safeCast<Int?>()[v0]).isEqualTo(0)
        val nullable: VertexProperty<*> = graph.createVertexProperty<Int?>()
        assertThrows<TypeCastException> { nullable.safeCast<Int>() }
        assertThat(nullable.safeCast<Int?>()[v0]).isNull()

        assertThrows<TypeCastException> { property.safeCast<String>() }
        assertThrows<TypeCastException> { mutableProperty.safeCast<String>() }
        assertThrows<TypeCastException> { keyProperty.safeCast<String>() }
        assertThrows<TypeCastException> { mutableKeyProperty.safeCast<String>() }
        assertThrows<TypeCastException> { property.safeCast<Number>() }

        // an untyped property can never be cast safely
        val untyped: VertexProperty<*> = graph.createVertexProperty(PropertyType.obj<String>()) { "x" }
        assertThrows<TypeCastException> { untyped.safeCast<String>() }
        assertThrows<TypeCastException> { untyped.safeCast<String?>() }
    }

    @Test
    fun edgePropertySafeCast() {
        var v0: Vertex
        var v1: Vertex
        val graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            addEdge(v0, v1)
        }
        val e0 = graph.edges.first()
        val property: EdgeProperty<*> = graph.createEdgeProperty<Int>(0)
        val mutableProperty: MutableEdgeProperty<*> = graph.createEdgeProperty<Int>(0)
        val keyProperty: EdgeKeyProperty<*> = graph.createEdgeKeyProperty<Int>()
        val mutableKeyProperty: MutableEdgeKeyProperty<*> = graph.createEdgeKeyProperty<Int>()

        assertThat(property.safeCast<Int>()[e0]).isEqualTo(0)
        assertThat(mutableProperty.safeCast<Int>().put(e0, 5)).isEqualTo(0)
        mutableKeyProperty.safeCast<Int>()[e0] = 9
        assertThat(mutableKeyProperty.safeCast<Int>().getEdge(9)).isEqualTo(e0)
        assertThat(keyProperty.safeCast<Int>()).isSameAs(keyProperty)

        // a non-null type may be read as its nullable counterpart, but not the other way round
        assertThat(property.safeCast<Int?>()[e0]).isEqualTo(0)
        val nullable: EdgeProperty<*> = graph.createEdgeProperty<Int?>()
        assertThrows<TypeCastException> { nullable.safeCast<Int>() }
        assertThat(nullable.safeCast<Int?>()[e0]).isNull()

        assertThrows<TypeCastException> { property.safeCast<String>() }
        assertThrows<TypeCastException> { mutableProperty.safeCast<String>() }
        assertThrows<TypeCastException> { keyProperty.safeCast<String>() }
        assertThrows<TypeCastException> { mutableKeyProperty.safeCast<String>() }
        assertThrows<TypeCastException> { property.safeCast<Number>() }

        // an untyped property can never be cast safely
        val untyped: EdgeProperty<*> = graph.createEdgeProperty(PropertyType.obj<String>()) { "x" }
        assertThrows<TypeCastException> { untyped.safeCast<String>() }
        assertThrows<TypeCastException> { untyped.safeCast<String?>() }
    }

    // --- unit and id properties ---------------------------------------------------------------------------------------

    @ParameterizedTest(name = "graphType={0}")
    @MethodSource("graphTypes")
    fun unitVertexPropertyAlwaysReturnsUnit(graphType: GraphType) {
        val graph = graphType.loadGraph()

        for (property in listOf(unitVertexProperty(graph), graph.createVertexProperty<Unit> { }, graph.createVertexProperty(PropertyType.unit) { })) {
            assertThat(property.graph).isSameAs(graph)
            assertThat(property.type).isEqualTo(PropertyType.unit)
            assertThat(property.type.isUnitType()).isTrue
            for (vertex in graph.vertices) {
                assertThat(property[vertex]).isEqualTo(Unit)
                property[vertex] = Unit
                assertThat(property.put(vertex, Unit)).isEqualTo(Unit)
            }
        }
    }

    @ParameterizedTest(name = "graphType={0}")
    @MethodSource("graphTypes")
    fun unitEdgePropertyAlwaysReturnsUnit(graphType: GraphType) {
        val graph = graphType.loadGraph()

        for (property in listOf(unitEdgeProperty(graph), graph.createEdgeProperty<Unit> { }, graph.createEdgeProperty(PropertyType.unit) { })) {
            assertThat(property.graph).isSameAs(graph)
            assertThat(property.type).isEqualTo(PropertyType.unit)
            assertThat(property.type.isUnitType()).isTrue
            for (edge in graph.edges) {
                assertThat(property[edge]).isEqualTo(Unit)
                property[edge] = Unit
                assertThat(property.put(edge, Unit)).isEqualTo(Unit)
            }
        }
    }

    @ParameterizedTest(name = "graphType={0}")
    @MethodSource("mutableGraphTypes")
    fun vertexIdPropertyReturnsIds(graphType: GraphType) {
        val graph = graphType.loadMutableGraph()
        val property = graph.vertexIdProperty

        assertThat(property.graph).isSameAs(graph)
        assertThat(property.type).isEqualTo(propertyTypeOf<Int>())
        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(vertex.id)
            assertThat(property.hasVertex(vertex.id)).isTrue
            assertThat(property.getVertex(vertex.id)).isEqualTo(vertex)
        }
        assertThat(property.hasVertex(99)).isFalse
        assertThat(property.hasVertex(-1)).isFalse

        // the view is live
        val added = graph.addVertex()
        assertThat(property.hasVertex(added.id)).isTrue
        assertThat(property.getVertex(added.id)).isEqualTo(added)
    }

    @ParameterizedTest(name = "graphType={0}")
    @MethodSource("mutableGraphTypes")
    fun edgeIdPropertyReturnsIds(graphType: GraphType) {
        val graph = graphType.loadMutableGraph()
        val property = graph.edgeIdProperty

        assertThat(property.graph).isSameAs(graph)
        assertThat(property.type).isEqualTo(propertyTypeOf<Long>())
        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(edge.id)
            assertThat(property.hasEdge(edge.id)).isTrue
            assertThat(property.getEdge(edge.id)).isEqualTo(edge)
        }
        assertThat(property.hasEdge(-1L)).isFalse
        assertThat(property.hasEdge(Long.MAX_VALUE)).isFalse

        // the view is live
        val added = graph.addEdge(graph.vertices.elementAt(1), graph.addVertex())
        assertThat(property.hasEdge(added.id)).isTrue
        assertThat(property.getEdge(added.id)).isEqualTo(added)
    }

    // --- properties on views ------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun vertexPropertyOnPredicateFilteredGraph(directed: Boolean) {
        val graph = buildGraph(directed) { addVertex(); addVertex(); addVertex() }
        val v0 = graph.vertices.elementAt(0)
        val v1 = graph.vertices.elementAt(1)
        val v2 = graph.vertices.elementAt(2)
        val allowed = mutableSetOf(v0, v1)
        val filtered = graph.filter({ vertex -> vertex in allowed }, { true })
        var calls = 0
        val property = filtered.createVertexProperty { vertex -> calls++; vertex.id * 10 }

        assertThat(property.graph).isSameAs(filtered)
        // the default is computed on demand, once per vertex
        assertThat(calls).isEqualTo(0)
        assertThat(property[v1]).isEqualTo(10)
        assertThat(property[v1]).isEqualTo(10)
        assertThat(calls).isEqualTo(1)
        property[v0] = 5
        assertThat(property.put(v0, 6)).isEqualTo(5)

        // a vertex the predicate rejects is a foreign vertex
        assertThrows<IllegalArgumentException> { property[v2] }
        assertThrows<IllegalArgumentException> { property[v2] = 1 }
        assertThrows<IllegalArgumentException> { property.put(v2, 1) }

        // the value of a vertex which leaves and re-enters the filter is dropped
        allowed.remove(v0)
        assertThrows<IllegalArgumentException> { property[v0] }
        allowed.add(v0)
        assertThat(property[v0]).isEqualTo(0)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun edgePropertyOnPredicateFilteredGraph(directed: Boolean) {
        val graph = buildGraph(directed, multiEdge = true) {
            val v0 = addVertex()
            val v1 = addVertex()
            addEdge(v0, v1)
            addEdge(v0, v1)
            addEdge(v1, v1)
        }
        val e0 = graph.edges.first()
        val e1 = graph.edges.elementAt(1)
        val e2 = graph.edges.elementAt(2)
        val allowed = mutableSetOf(e0, e1)
        val filtered = graph.filter(null) { edge -> edge in allowed }
        var calls = 0
        val property = filtered.createEdgeProperty { edge -> calls++; edge.id.toInt() * 10 }

        assertThat(property.graph).isSameAs(filtered)
        // the default is computed on demand, once per edge
        assertThat(calls).isEqualTo(0)
        assertThat(property[e1]).isEqualTo(10)
        assertThat(property[e1]).isEqualTo(10)
        assertThat(calls).isEqualTo(1)
        property[e0] = 5
        assertThat(property.put(e0, 6)).isEqualTo(5)

        // an edge the predicate rejects is a foreign edge
        assertThrows<IllegalArgumentException> { property[e2] }
        assertThrows<IllegalArgumentException> { property[e2] = 1 }
        assertThrows<IllegalArgumentException> { property.put(e2, 1) }

        // the value of an edge which leaves and re-enters the filter is dropped
        allowed.remove(e0)
        assertThrows<IllegalArgumentException> { property[e0] }
        allowed.add(e0)
        assertThat(property[e0]).isEqualTo(0)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun propertiesOnInducedFilteredGraphFollowTheParent(directed: Boolean) {
        val graph = buildGraph(directed) {
            val v0 = addVertex()
            val v1 = addVertex()
            val v2 = addVertex()
            addEdge(v0, v1)
            addEdge(v1, v2)
        }
        val v0 = graph.vertices.elementAt(0)
        val v1 = graph.vertices.elementAt(1)
        val v2 = graph.vertices.elementAt(2)
        val e01 = graph.edge(v0, v1)
        val e12 = graph.edge(v1, v2)
        val filtered = graph.filter(vertexSetOf(v0, v1, v2), edgeSetOf(e01, e12))
        val vertexProperty = filtered.createVertexProperty<String>()
        val edgeProperty = filtered.createEdgeProperty<String>()
        vertexProperty[v1] = "v1"
        vertexProperty[v2] = "v2"
        edgeProperty[e12] = "e12"
        val v2Ref = filtered.createVertexReference(v2)
        val e12Ref = filtered.createEdgeReference(e12)

        assertThat(vertexProperty.graph).isSameAs(filtered)
        assertThat(edgeProperty.graph).isSameAs(filtered)

        // removing v0 from the parent moves v2 and renames e12; the filtered graph's properties follow
        graph.removeVertex(v0)
        assertThat(filtered.vertices).containsExactlyInAnyOrder(v1, v2Ref.unstable)
        assertThat(filtered.edges).containsExactlyInAnyOrder(e12Ref.unstable)
        assertThat(vertexProperty[v1]).isEqualTo("v1")
        assertThat(vertexProperty[v2Ref.unstable]).isEqualTo("v2")
        assertThat(edgeProperty[e12Ref.unstable]).isEqualTo("e12")
    }
}
