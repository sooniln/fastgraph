package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.AbstractEdgeCollection
import io.github.sooniln.fastgraph.AbstractVertexCollection
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.primitives.IntHashSet
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

internal class VertexInducedSubgraph(private val graph: Graph, override val vertices: VertexSet) : Graph {

    init {
        for (vertex in vertices) {
            require(graph.vertices.contains(vertex))
        }
    }

    private fun validateVertex(vertex: Vertex): Vertex {
        if (!vertices.contains(vertex)) {
            throw IllegalArgumentException("$vertex not found in graph")
        }
        return vertex
    }

    override val directed: Boolean
        get() = graph.directed

    override val multiEdge: Boolean
        get() {
            if (!graph.multiEdge) return false
            val incidences = Int2ObjectOpenHashMap<IntHashSet>()
            for (edge in edges) {
                val sourceIntValue = edgeSource(edge).intValue
                var vertexIncidences = incidences.get(sourceIntValue)
                if (vertexIncidences == null) {
                    vertexIncidences = IntHashSet()
                    incidences.put(sourceIntValue, vertexIncidences)
                }
                val targetIntValue = edgeTarget(edge).intValue
                if (vertexIncidences.contains(targetIntValue)) {
                    return true
                }
                vertexIncidences.add(targetIntValue)
            }
            return false
        }

    override fun outDegree(vertex: Vertex): Int {
        return outgoingEdges(vertex).size
    }

    override fun inDegree(vertex: Vertex): Int {
        return incomingEdges(vertex).size
    }

    override fun successors(vertex: Vertex): VertexSet {
        return FilteringVertexSet(graph.successors(validateVertex(vertex)))
    }

    override fun predecessors(vertex: Vertex): VertexSet {
        return FilteringVertexSet(graph.predecessors(validateVertex(vertex)))
    }

    override fun outgoingEdges(vertex: Vertex): EdgeSet {
        return FilteringEdgeSet(graph.outgoingEdges(validateVertex(vertex)))
    }

    override fun incomingEdges(vertex: Vertex): EdgeSet {
        return FilteringEdgeSet(graph.incomingEdges(validateVertex(vertex)))
    }

    override val edges: EdgeSet = FilteringEdgeSet(graph.edges)

    override fun edgeSource(edge: Edge): Vertex = graph.edgeSource(edge)

    override fun edgeTarget(edge: Edge): Vertex = graph.edgeTarget(edge)

    override fun containsEdge(source: Vertex, target: Vertex): Boolean {
        return graph.containsEdge(validateVertex(source), validateVertex(target))
    }

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return graph.getEdges(validateVertex(source), validateVertex(target))
    }

    override fun <T : S?, S> createVertexProperty(
        clazz: Class<S>,
        initializer: VertexInitializer<T>
    ): VertexProperty<T> = FilteringVertexProperty(graph.createVertexProperty(clazz, initializer))

    override fun <T : S?, S> createEdgeProperty(
        clazz: Class<S>,
        initializer: EdgeInitializer<T>
    ): EdgeProperty<T> = FilteringEdgeProperty(graph.createEdgeProperty(clazz, initializer))

    override fun createVertexReference(vertex: Vertex): VertexReference = graph.createVertexReference(vertex)

    override fun createEdgeReference(edge: Edge): EdgeReference = graph.createEdgeReference(edge)

    private fun isEdgeInduced(edge: Edge): Boolean {
        return vertices.contains(graph.edgeTarget(edge)) && vertices.contains(graph.edgeSource(edge))
    }

    private inner class FilteringVertexSet(private val vertexSet: VertexSet) : AbstractVertexCollection(), VertexSet {

        override fun contains(element: Vertex): Boolean {
            return vertexSet.contains(element) && vertices.contains(element)
        }

        override val size: Int
            get() {
                var size = 0
                for (v in vertices) {
                    if (vertexSet.contains(v)) ++size
                }
                return size
            }

        override fun isEmpty(): Boolean {
            for (v in vertices) {
                if (vertexSet.contains(v)) return false
            }
            return true
        }

        override fun iterator(): VertexIterator {
            return FilteringVertexIterator(vertexSet.iterator())
        }
    }

    private inner class FilteringVertexIterator(private val it: VertexIterator) : VertexIterator {
        private var hasNext = true
        private var next = Vertex(0)

        init {
            increment()
        }

        override fun hasNext(): Boolean = hasNext

        override fun next(): Vertex {
            if (!hasNext) throw NoSuchElementException()
            val n = next
            increment()
            return n
        }

        private fun increment() {
            do {
                if (!it.hasNext()) {
                    hasNext = false
                    return
                }
                next = it.next()
            } while (!vertices.contains(next))
        }
    }

    private inner class FilteringEdgeSet(private val edgeSet: EdgeSet) : AbstractEdgeCollection(), EdgeSet {

        override fun contains(element: Edge): Boolean {
            return edgeSet.contains(element) && isEdgeInduced(element)
        }

        override val size: Int
            get() {
                var size = 0
                for (e in edgeSet) {
                    if (isEdgeInduced(e)) ++size
                }
                return size
            }

        override fun isEmpty(): Boolean {
            for (e in edgeSet) {
                if (isEdgeInduced(e)) return false
            }
            return true
        }

        override fun iterator(): EdgeIterator {
            return FilteringEdgeIterator(edgeSet.iterator())
        }
    }

    private inner class FilteringEdgeIterator(private val it: EdgeIterator) : EdgeIterator {
        private var hasNext = true
        private var next = Edge(0L)

        init {
            increment()
        }

        override fun hasNext(): Boolean = hasNext

        override fun next(): Edge {
            if (!hasNext) throw NoSuchElementException()
            val n = next
            increment()
            return n
        }

        private fun increment() {
            do {
                if (!it.hasNext()) {
                    hasNext = false
                    return
                }
                next = it.next()
            } while (!isEdgeInduced(next))
        }
    }

    private inner class FilteringVertexProperty<V>(private val vertexProperty: VertexProperty<V>) : VertexProperty<V> {
        override val graph: Graph
            get() = this@VertexInducedSubgraph

        override fun get(vertex: Vertex): V {
            if (!vertices.contains(vertex)) throw IllegalArgumentException("$vertex not found in graph")
            return vertexProperty[vertex]
        }

        override fun set(vertex: Vertex, value: V) {
            if (!vertices.contains(vertex)) throw IllegalArgumentException("$vertex not found in graph")
            vertexProperty[vertex] = value
        }
    }

    private inner class FilteringEdgeProperty<E>(private val edgeProperty: EdgeProperty<E>) : EdgeProperty<E> {
        override val graph: Graph
            get() = this@VertexInducedSubgraph

        override fun get(edge: Edge): E {
            if (!edges.contains(edge)) throw IllegalArgumentException("$edge not found in graph")
            return edgeProperty[edge]
        }

        override fun set(edge: Edge, value: E) {
            if (!edges.contains(edge)) throw IllegalArgumentException("$edge not found in graph")
            edgeProperty[edge] = value
        }
    }
}
