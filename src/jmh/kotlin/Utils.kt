package io.github.sooniln.fastgraph

import com.google.common.graph.Network
import com.google.common.graph.ValueGraph
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.jgrapht.Graphs

internal object Utils {

    fun dijkstras(graph: Graph, weights: EdgeProperty<Float>, start: Vertex): VertexProperty<Float> {
        val visited = graph.createVertexProperty { false }
        val distance = graph.createVertexProperty { Float.MAX_VALUE }

        val q = IntHeapPriorityQueue(graph.vertices.size) { v1, v2 ->
            distance[Vertex(v1)].compareTo(distance[Vertex(v2)])
        }

        distance[start] = 0f
        q.enqueue(start.intValue)

        while (!q.isEmpty) {
            val v = Vertex(q.dequeueInt())
            if (visited[v]) continue
            visited[v] = true

            val curDistance = distance[v]
            for (edge in graph.outgoingEdges(v)) {
                val n = graph.edgeOpposite(edge, v)
                val newDistance = curDistance + weights[edge]
                if (newDistance < distance[n]) {
                    distance[n] = newDistance
                    q.enqueue(n.intValue)
                }
            }
        }

        return distance
    }

    fun dijkstrasJGraphT(graph: org.jgrapht.Graph<Int, Loader.JGraphWeightedEdge>, start: Int): Int2ObjectMap<Float> {
        val visited = IntOpenHashSet(graph.vertexSet().size)
        val distance = Int2ObjectOpenHashMap<Float>(graph.vertexSet().size)
        distance.defaultReturnValue(Float.MAX_VALUE)

        val q = IntHeapPriorityQueue(graph.vertexSet().size) { v1, v2 ->
            distance[v1].compareTo(distance[v2])
        }

        distance[start] = 0f
        q.enqueue(start)

        while (!q.isEmpty) {
            val v = q.dequeueInt()
            if (visited.contains(v)) continue
            visited.add(v)

            val curDistance = distance[v]
            for (edge in graph.outgoingEdgesOf(v)) {
                val n = Graphs.getOppositeVertex(graph, edge, v)
                val newDistance = curDistance + edge.weight
                if (newDistance < distance[n]) {
                    distance[n] = newDistance
                    q.enqueue(n)
                }
            }
        }

        return distance
    }

    fun dijkstrasGuava(graph: ValueGraph<Int, Float>, start: Int): Int2ObjectMap<Float> {
        val visited = IntOpenHashSet(graph.nodes().size)
        val distance = Int2ObjectOpenHashMap<Float>(graph.nodes().size)
        distance.defaultReturnValue(Float.MAX_VALUE)

        val q = IntHeapPriorityQueue(graph.nodes().size) { v1, v2 ->
            distance[v1].compareTo(distance[v2])
        }

        distance[start] = 0f
        q.enqueue(start)

        while (!q.isEmpty) {
            val v = q.dequeueInt()
            if (visited.contains(v)) continue
            visited.add(v)

            val curDistance = distance[v]
            for (edge in graph.incidentEdges(v)) {
                if (graph.isDirected && edge.source() != v) continue
                val n = edge.adjacentNode(v)
                val newDistance = curDistance + graph.edgeValue(edge).get()
                if (newDistance < distance[n]) {
                    distance[n] = newDistance
                    q.enqueue(n)
                }
            }
        }

        return distance
    }

    fun dijkstrasGuava(graph: Network<Int, Loader.GuavaWeightedEdge>, start: Int): Int2ObjectMap<Float> {
        val visited = IntOpenHashSet(graph.nodes().size)
        val distance = Int2ObjectOpenHashMap<Float>(graph.nodes().size)
        distance.defaultReturnValue(Float.MAX_VALUE)

        val q = IntHeapPriorityQueue(graph.nodes().size) { v1, v2 ->
            distance[v1].compareTo(distance[v2])
        }

        distance[start] = 0f
        q.enqueue(start)

        while (!q.isEmpty) {
            val v = q.dequeueInt()
            if (visited.contains(v)) continue
            visited.add(v)

            val curDistance = distance[v]
            for (n in graph.successors(v)) {
                val edges = graph.edgesConnecting(v, n)
                for (edge in edges) {
                    val newDistance = curDistance + edge.weight
                    if (newDistance < distance[n]) {
                        distance[n] = newDistance
                        q.enqueue(n)
                    }
                }
            }
        }

        return distance
    }
}
