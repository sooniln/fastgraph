package io.github.sooniln.fastgraph

import com.google.common.graph.Traverser
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue
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
open class GuavaSimpleGraphBenchmark {

    lateinit var graph: com.google.common.graph.ImmutableGraph<Int>

    @Setup
    fun setup() {
        graph = Loader.loadGuavaSimpleGraph()
    }

    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Benchmark
    fun vertexValues(): Int {
        var i = 0
        for (vertex in graph.nodes()) {
            i += vertex
        }
        return i
    }

    @Benchmark
    fun edges(bh: Blackhole): Int {
        var i = 0
        for (edge in graph.edges()) {
            bh.consume(edge)
            i++
        }
        return i
    }

    @Benchmark
    fun successors(): Int {
        var i = 0
        for (source in graph.nodes()) {
            for (target in graph.successors(source)) {
                i += target
            }
        }
        return i
    }

    @Benchmark
    fun outgoingEdges(bh: Blackhole): Int {
        var i = 0
        for (source in graph.nodes()) {
            for (edge in graph.incidentEdges(source)) {
                bh.consume(edge)
                i++
            }
        }
        return i
    }

    @Benchmark
    fun bfsGuava(): Int {
        var vertexIdSum = 0
        for (vertexId in Traverser.forGraph(graph).breadthFirst(graph.nodes().first())) {
            vertexIdSum += vertexId
        }
        return vertexIdSum
    }

    @Benchmark
    fun bfs(): Int {
        var vertexIdSum = 0
        for (vertexId in BFSIterator(graph, graph.nodes().first())) {
            vertexIdSum += vertexId
        }
        return vertexIdSum
    }

    private class BFSIterator(private val graph: com.google.common.graph.ImmutableGraph<Int>, start: Int) :
        IntIterator() {

        private val visited = Int2IntOpenHashMap(graph.nodes().size)
        private val queue = IntArrayFIFOQueue()

        init {
            queue.enqueue(start)
            visited[start] = 1
        }

        override fun nextInt(): Int {
            val next = queue.dequeueInt()
            for (vertexId in graph.successors(next)) {
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
