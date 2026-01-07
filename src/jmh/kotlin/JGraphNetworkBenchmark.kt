package io.github.sooniln.fastgraph

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue
import org.jgrapht.Graph
import org.jgrapht.Graphs
import org.jgrapht.alg.shortestpath.IntVertexDijkstraShortestPath
import org.jgrapht.traverse.BreadthFirstIterator
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

@Fork(1)
@Warmup(iterations = 4)
@Measurement(iterations = 4)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class JGraphNetworkBenchmark {

    lateinit var graph: Graph<Int, Loader.JGraphWeightedEdge>

    @Setup
    fun setup() {
        graph = Loader.loadJGraphTNetwork()

        /*val totalMem = GraphLayout.parseInstance(graph).totalSize()
        println("total: $totalMem")*/
    }

    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Benchmark
    fun vertexValues(): Int {
        var i = 0
        for (vertex in graph.vertexSet()) {
            i += vertex
        }
        return i
    }

    @Benchmark
    fun edgeValues(): Double {
        var i = 0.0
        for (edge in graph.edgeSet()) {
            i += edge.weight
        }
        return i
    }

    @Benchmark
    fun successors(): Int {
        var i = 0
        for (source in graph.vertexSet()) {
            for (target in Graphs.successorListOf(graph, source)) {
                i += target
            }
        }
        return i
    }

    @Benchmark
    fun outgoingEdgeValues(): Double {
        var i = 0.0
        for (source in graph.vertexSet()) {
            for (edge in graph.outgoingEdgesOf(source)) {
                i += edge.weight
            }
        }
        return i
    }

    @Benchmark
    fun bfsJGraph(): Int {
        var vertexIdSum = 0
        for (vertexId in BreadthFirstIterator(graph, graph.vertexSet().first())) {
            vertexIdSum += vertexId
        }
        return vertexIdSum
    }

    @Benchmark
    fun bfs(): Int {
        var vertexIdSum = 0
        for (vertexId in BFSIterator(graph, graph.vertexSet().first())) {
            vertexIdSum += vertexId
        }
        return vertexIdSum
    }

    @Benchmark
    fun dijkstrasJGraph() = IntVertexDijkstraShortestPath(graph).getPaths(graph.vertexSet().first())

    @Benchmark
    fun dijkstras() = Utils.dijkstrasJGraphT(graph, graph.vertexSet().first())

    private class BFSIterator(private val graph: Graph<Int, *>, start: Int) : IntIterator() {

        private val visited = Int2IntOpenHashMap(graph.vertexSet().size)
        private val queue = IntArrayFIFOQueue()

        init {
            queue.enqueue(start)
            visited[start] = 1
        }

        override fun nextInt(): Int {
            val next = queue.dequeueInt()
            for (vertexId in Graphs.successorListOf(graph, next)) {
                if (visited[vertexId] != 1) {
                    visited[vertexId] = 1
                    queue.enqueue(vertexId)
                }
            }
            return next
        }

        override fun hasNext(): Boolean = !queue.isEmpty
    }
}
