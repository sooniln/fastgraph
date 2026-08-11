package io.github.sooniln.fastgraph

import com.google.common.graph.Network
import com.google.common.graph.ValueGraph
import io.github.sooniln.fastcollect.AbstractIntPriorityQueue
import io.github.sooniln.fastcollect.Int2FloatHashMap
import io.github.sooniln.fastcollect.Int2FloatMap
import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastcollect.IntHashSet
import io.github.sooniln.fastcollect.getOrElse
import org.jgrapht.Graphs

internal object Utils {

    private abstract class VertexPriorityQueue(graph: Graph) : AbstractIntPriorityQueue(graph.vertices.size) {
        private val indexMap = graph.createVertexProperty(-1)

        override fun onIndexChanged(element: Int, index: Int) {
            indexMap[Vertex(element)] = index
        }

        fun update(element: Int) {
            val index = indexMap[Vertex(element)]
            if (index == -1) {
                add(element)
            } else {
                updatePriority(element)
            }
        }
    }

    private abstract class IndexedPriorityQueue(capacity: Int) : AbstractIntPriorityQueue(capacity) {
        private val indexMap = Int2IntHashMap(capacity)

        override fun onIndexChanged(element: Int, index: Int) {
            indexMap[element] = index
        }

        fun update(element: Int) {
            updatePriority(indexMap.getOrElse(element) { add(element); return })
        }
    }

    fun dijkstras(graph: Graph, weights: EdgeProperty<Float>, start: Vertex): VertexProperty<Float> {
        val visited = graph.createVertexProperty(false)
        val distance = graph.createVertexProperty(Float.MAX_VALUE)

        val q = object : VertexPriorityQueue(graph) {
            override fun isHigherPriority(element1: Int, element2: Int): Boolean {
                return distance[Vertex(element1)] > distance[Vertex(element2)]
            }
        }

        distance[start] = 0f
        q.add(start.id)

        while (!q.isEmpty()) {
            val v = Vertex(q.removeFirst())
            if (visited[v]) continue
            visited[v] = true

            val curDistance = distance[v]
            for (edge in graph.outgoingEdges(v)) {
                val n = graph.edgeOpposite(edge, v)
                val newDistance = curDistance + weights[edge]
                if (newDistance < distance[n]) {
                    distance[n] = newDistance
                    q.update(n.id)
                }
            }
        }

        return distance
    }

    fun dijkstrasJGraphT(graph: org.jgrapht.Graph<Int, Loader.JGraphWeightedEdge>, start: Int): Int2FloatMap {
        val visited = IntHashSet(graph.vertexSet().size)
        val distance = Int2FloatHashMap(graph.vertexSet().size, Float.MAX_VALUE)

        val q = object : IndexedPriorityQueue(graph.vertexSet().size) {
            override fun isHigherPriority(element1: Int, element2: Int): Boolean {
                return distance[element1] > distance[element2]
            }
        }

        distance[start] = 0f
        q.add(start)

        while (!q.isEmpty()) {
            val v = q.removeFirst()
            if (visited.contains(v)) continue
            visited.add(v)

            val curDistance = distance[v]
            for (edge in graph.outgoingEdgesOf(v)) {
                val n = Graphs.getOppositeVertex(graph, edge, v)
                val newDistance = curDistance + edge.weight
                if (newDistance < distance[n]) {
                    distance[n] = newDistance
                    q.update(n)
                }
            }
        }

        return distance
    }

    @Suppress("UnstableApiUsage")
    fun dijkstrasGuava(graph: ValueGraph<Int, Float>, start: Int): Int2FloatMap {
        val visited = IntHashSet(graph.nodes().size)
        val distance = Int2FloatHashMap(graph.nodes().size, Float.MAX_VALUE)

        val q = object : IndexedPriorityQueue(graph.nodes().size) {
            override fun isHigherPriority(element1: Int, element2: Int): Boolean {
                return distance[element1] > distance[element2]
            }
        }

        distance[start] = 0f
        q.add(start)

        while (!q.isEmpty()) {
            val v = q.removeFirst()
            if (visited.contains(v)) continue
            visited.add(v)

            val curDistance = distance[v]
            for (edge in graph.incidentEdges(v)) {
                if (graph.isDirected && edge.source() != v) continue
                val n = edge.adjacentNode(v)
                val newDistance = curDistance + graph.edgeValue(edge).get()
                if (newDistance < distance[n]) {
                    distance[n] = newDistance
                    q.update(n)
                }
            }
        }

        return distance
    }

    @Suppress("UnstableApiUsage")
    fun dijkstrasGuava(graph: Network<Int, Loader.GuavaWeightedEdge>, start: Int): Int2FloatMap {
        val visited = IntHashSet(graph.nodes().size)
        val distance = Int2FloatHashMap(graph.nodes().size, Float.MAX_VALUE)

        val q = object : IndexedPriorityQueue(graph.nodes().size) {
            override fun isHigherPriority(element1: Int, element2: Int): Boolean {
                return distance[element1] > distance[element2]
            }
        }

        distance[start] = 0f
        q.add(start)

        while (!q.isEmpty()) {
            val v = q.removeFirst()
            if (visited.contains(v)) continue
            visited.add(v)

            val curDistance = distance[v]
            for (n in graph.successors(v)) {
                val edges = graph.edgesConnecting(v, n)
                for (edge in edges) {
                    val newDistance = curDistance + edge.weight
                    if (newDistance < distance[n]) {
                        distance[n] = newDistance
                        q.update(n)
                    }
                }
            }
        }

        return distance
    }
}
