package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.typeOf

/**
 * Exercises the array-backed properties for [IndexedVertexGraph]/[IndexedEdgeGraph] implementations which are *not*
 * identity indexed, i.e. where the index of a vertex/edge is unrelated to its id. No such mutable graph ships with
 * the library, so a minimal fake is used which follows the change-listener contract: listeners are notified before
 * the mutation, and a non-last removal is reported as a re-assignment of the last index.
 */
class IndexedPropertyTest {

    private class FakeIndexedGraph : IndexedVertexGraph, IndexedEdgeGraph, Graph by mutableGraph(true) {
        private val vertexIds = ArrayList<Int>()
        private val vertexIndices = HashMap<Int, Int>()
        private val edgeIds = ArrayList<Long>()
        private val edgeIndices = HashMap<Long, Int>()
        private val vertexListeners = ArrayList<VertexChangeListener>()
        private val edgeListeners = ArrayList<EdgeChangeListener>()

        override val vertices: IndexedVertexSet = object : IndexedVertexSet, AbstractVertexSequencedSet() {
            override val size: Int get() = vertexIds.size
            override fun get(index: Int): Vertex = Vertex(vertexIds[index])
            override fun indexOf(element: Vertex): Int = vertexIndices[element.id] ?: -1
        }

        override val edges: IndexedEdgeSet = object : IndexedEdgeSet, AbstractEdgeSequencedSet() {
            override val size: Int get() = edgeIds.size
            override fun get(index: Int): Edge = Edge(edgeIds[index])
            override fun indexOf(element: Edge): Int = edgeIndices[element.id] ?: -1
        }

        override fun registerVertexChangeListener(listener: VertexChangeListener) { vertexListeners.add(listener) }
        override fun unregisterVertexChangeListener(listener: VertexChangeListener) { vertexListeners.remove(listener) }
        override fun registerEdgeChangeListener(listener: EdgeChangeListener) { edgeListeners.add(listener) }
        override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) { edgeListeners.remove(listener) }

        override fun <T> createVertexProperty(type: PropertyType<T>, defaultValueFunction: VertexFunction<T>) =
            createVertexProperty(this, type, defaultValueFunction)
        override fun <T> createEdgeProperty(type: PropertyType<T>, defaultValueFunction: EdgeFunction<T>) =
            createEdgeProperty(this, type, defaultValueFunction)
        override fun <T> createVertexKeyProperty(type: PropertyType<T>) =
            createVertexKeyProperty(this, type)
        override fun <T> createEdgeKeyProperty(type: PropertyType<T>) =
            createEdgeKeyProperty(this, type)

        fun addVertex(id: Int): Vertex {
            vertexIndices[id] = vertexIds.size
            vertexIds.add(id)
            val vertex = Vertex(id)
            vertexListeners.forEach { it.onVertexAdded(vertex) }
            return vertex
        }

        fun removeVertex(vertex: Vertex) {
            val index = vertexIndices.getValue(vertex.id)
            val lastIndex = vertexIds.lastIndex
            val last = Vertex(vertexIds[lastIndex])
            if (index != lastIndex) {
                vertexListeners.forEach { it.onVertexReassigned(last, vertex) }
            } else {
                vertexListeners.forEach { it.onVertexRemoved(vertex) }
            }
            vertexIndices.remove(vertex.id)
            vertexIds.removeAt(lastIndex)
            if (index != lastIndex) {
                vertexIds[index] = last.id
                vertexIndices[last.id] = index
            }
        }

        fun addEdge(id: Long): Edge {
            edgeIndices[id] = edgeIds.size
            edgeIds.add(id)
            val edge = Edge(id)
            edgeListeners.forEach { it.onEdgeAdded(edge) }
            return edge
        }

        fun removeEdge(edge: Edge) {
            val index = edgeIndices.getValue(edge.id)
            val lastIndex = edgeIds.lastIndex
            val last = Edge(edgeIds[lastIndex])
            if (index != lastIndex) {
                edgeListeners.forEach { it.onEdgeReassigned(last, edge) }
            } else {
                edgeListeners.forEach { it.onEdgeRemoved(edge) }
            }
            edgeIndices.remove(edge.id)
            edgeIds.removeAt(lastIndex)
            if (index != lastIndex) {
                edgeIds[index] = last.id
                edgeIndices[last.id] = index
            }
        }
    }

    companion object {
        // the Unit property is a constant stub which is never index-backed
        @JvmStatic
        fun propertyCases(): List<PropertyTest.PropertyCase<*>> =
            PropertyTest.propertyCases().filter { it.type.kType != typeOf<Unit>() }

        @JvmStatic
        fun keyCases(): List<KeyPropertyTest.KeyCase<*>> = KeyPropertyTest.keyCases()
    }

    @Suppress("UNCHECKED_CAST")
    @ParameterizedTest(name = "type={0}")
    @MethodSource("propertyCases")
    fun vertexPropertyFollowsIndexReassignment(case: PropertyTest.PropertyCase<*>) {
        val typedCase = case as PropertyTest.PropertyCase<Any?>
        val graph = FakeIndexedGraph()
        val v10 = graph.addVertex(10)
        val v20 = graph.addVertex(20)
        val property = graph.createVertexProperty(typedCase.type) { typedCase.defaultValue }
        val v30 = graph.addVertex(30)

        assertThat(property[v30]).isEqualTo(typedCase.defaultValue)
        property[v10] = typedCase.valueAt(0)
        property[v20] = typedCase.valueAt(1)
        property[v30] = typedCase.valueAt(2)
        assertThrows<IllegalArgumentException> { property[Vertex(99)] }
        assertThrows<IllegalArgumentException> { property[Vertex(99)] = typedCase.defaultValue }

        // removing the first vertex moves the last one into its index; values must follow the vertices
        graph.removeVertex(v10)
        assertThat(graph.vertices.indexOf(v30)).isEqualTo(0)
        assertThat(property[v30]).isEqualTo(typedCase.valueAt(2))
        assertThat(property[v20]).isEqualTo(typedCase.valueAt(1))
        assertThrows<IllegalArgumentException> { property[v10] }

        graph.removeVertex(v20)
        assertThat(property[v30]).isEqualTo(typedCase.valueAt(2))
        assertThrows<IllegalArgumentException> { property[v20] }

        val v40 = graph.addVertex(40)
        assertThat(property[v40]).isEqualTo(typedCase.defaultValue)
        assertThat(property.put(v40, typedCase.valueAt(3))).isEqualTo(typedCase.defaultValue)
        assertThat(property[v40]).isEqualTo(typedCase.valueAt(3))
    }

    @Suppress("UNCHECKED_CAST")
    @ParameterizedTest(name = "type={0}")
    @MethodSource("propertyCases")
    fun edgePropertyFollowsIndexReassignment(case: PropertyTest.PropertyCase<*>) {
        val typedCase = case as PropertyTest.PropertyCase<Any?>
        val graph = FakeIndexedGraph()
        val e10 = graph.addEdge(10)
        val e20 = graph.addEdge(20)
        val property = graph.createEdgeProperty(typedCase.type) { typedCase.defaultValue }
        val e30 = graph.addEdge(30)

        assertThat(property[e30]).isEqualTo(typedCase.defaultValue)
        property[e10] = typedCase.valueAt(0)
        property[e20] = typedCase.valueAt(1)
        property[e30] = typedCase.valueAt(2)
        assertThrows<IllegalArgumentException> { property[Edge(99)] }
        assertThrows<IllegalArgumentException> { property[Edge(99)] = typedCase.defaultValue }

        graph.removeEdge(e10)
        assertThat(graph.edges.indexOf(e30)).isEqualTo(0)
        assertThat(property[e30]).isEqualTo(typedCase.valueAt(2))
        assertThat(property[e20]).isEqualTo(typedCase.valueAt(1))
        assertThrows<IllegalArgumentException> { property[e10] }

        graph.removeEdge(e20)
        assertThat(property[e30]).isEqualTo(typedCase.valueAt(2))
        assertThrows<IllegalArgumentException> { property[e20] }

        val e40 = graph.addEdge(40)
        assertThat(property[e40]).isEqualTo(typedCase.defaultValue)
        assertThat(property.put(e40, typedCase.valueAt(3))).isEqualTo(typedCase.defaultValue)
        assertThat(property[e40]).isEqualTo(typedCase.valueAt(3))
    }

    @ParameterizedTest(name = "type={0}")
    @MethodSource("keyCases")
    fun <T> vertexKeyPropertyFollowsIndexReassignment(case: KeyPropertyTest.KeyCase<T>) {
        val graph = FakeIndexedGraph()
        val v10 = graph.addVertex(10)
        val v20 = graph.addVertex(20)
        val v30 = graph.addVertex(30)
        val property = graph.createVertexKeyProperty(case.type)

        property[v10] = case.keyAt(0)
        property[v20] = case.keyAt(1)
        property[v30] = case.keyAt(2)
        assertThat(property.getVertex(case.keyAt(2))).isEqualTo(v30)
        assertThrows<IllegalArgumentException> { property[v20] = case.keyAt(2) }
        assertThrows<IllegalArgumentException> { property[Vertex(99)] = case.keyAt(3) }

        graph.removeVertex(v10)
        assertThat(property[v30]).isEqualTo(case.keyAt(2))
        assertThat(property.getVertex(case.keyAt(2))).isEqualTo(v30)
        assertThat(property.getVertex(case.keyAt(1))).isEqualTo(v20)
        assertThat(property.hasVertex(case.keyAt(0))).isFalse

        // the key freed by v10 may be reused
        property[v30] = case.keyAt(0)
        assertThat(property.getVertex(case.keyAt(0))).isEqualTo(v30)
        assertThat(property.hasVertex(case.keyAt(2))).isFalse
    }

    @ParameterizedTest(name = "type={0}")
    @MethodSource("keyCases")
    fun <T> edgeKeyPropertyFollowsIndexReassignment(case: KeyPropertyTest.KeyCase<T>) {
        val graph = FakeIndexedGraph()
        val e10 = graph.addEdge(10)
        val e20 = graph.addEdge(20)
        val e30 = graph.addEdge(30)
        val property = graph.createEdgeKeyProperty(case.type)

        property[e10] = case.keyAt(0)
        property[e20] = case.keyAt(1)
        property[e30] = case.keyAt(2)
        assertThat(property.getEdge(case.keyAt(2))).isEqualTo(e30)
        assertThrows<IllegalArgumentException> { property[Edge(99)] = case.keyAt(3) }

        graph.removeEdge(e10)
        assertThat(property[e30]).isEqualTo(case.keyAt(2))
        assertThat(property.getEdge(case.keyAt(2))).isEqualTo(e30)
        assertThat(property.getEdge(case.keyAt(1))).isEqualTo(e20)
        assertThat(property.hasEdge(case.keyAt(0))).isFalse

        property[e30] = case.keyAt(0)
        assertThat(property.getEdge(case.keyAt(0))).isEqualTo(e30)
        assertThat(property.hasEdge(case.keyAt(2))).isFalse
    }

    @Test
    fun builtInGraphsAreIdentityIndexed() {
        assertThat(mutableGraph(true)).isInstanceOf(IdentityIndexedVertexGraph::class.java)
        assertThat(mutableGraph(true, indexEdges = true)).isInstanceOf(IdentityIndexedEdgeGraph::class.java)
        assertThat(buildImmutableGraph(true) { addVertex() }).isInstanceOf(IdentityIndexedVertexGraph::class.java)
        assertThat(buildImmutableGraph(true, indexEdges = true) { addVertex() })
            .isInstanceOf(IdentityIndexedEdgeGraph::class.java)
        assertThat(emptyImmutableGraph(true)).isInstanceOf(IdentityIndexedEdgeGraph::class.java)
    }
}
