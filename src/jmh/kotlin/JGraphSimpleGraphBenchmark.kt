package io.github.sooniln.fastgraph

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue
import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultEdge
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
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

@Fork(1)
@Warmup(iterations = 4)
@Measurement(iterations = 4)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class JGraphSimpleGraphBenchmark {

    lateinit var graph: org.jgrapht.Graph<Int, DefaultEdge>

    @Setup
    fun setup() {
        graph = Loader.loadJGraphTSimpleGraph()

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
    fun edges(bh: Blackhole): Int {
        var i = 0
        for (edge in graph.edgeSet()) {
            bh.consume(edge)
            i++
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
    fun outgoingEdges(bh: Blackhole): Int {
        var i = 0
        for (source in graph.vertexSet()) {
            for (edge in graph.outgoingEdgesOf(source)) {
                bh.consume(edge)
                i++
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

    private class BFSIterator(private val graph: org.jgrapht.Graph<Int, *>, start: Int) : IntIterator() {

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
