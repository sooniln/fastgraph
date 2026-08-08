package io.github.sooniln.fastgraph

import com.google.common.graph.ImmutableValueGraph
import com.google.common.graph.Traverser
import io.github.sooniln.fastcollect.ints.Int2IntHashMap
import io.github.sooniln.fastcollect.ints.IntArrayDeque
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
@Suppress("UnstableApiUsage")
open class GuavaGraphBenchmark {

    lateinit var graph: ImmutableValueGraph<Int, Float>

    @Setup
    fun setup() {
        graph = Loader.loadGuavaGraph()
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
    fun edgeValues(): Double {
        var i = 0.0
        for (edge in graph.edges()) {
            i += graph.edgeValue(edge).get()
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
    fun outgoingEdgeValues(): Double {
        var i = 0.0
        for (source in graph.nodes()) {
            for (edge in graph.incidentEdges(source)) {
                i += graph.edgeValue(edge).get()
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

    @Benchmark
    fun dijkstras() = Utils.dijkstrasGuava(graph, graph.nodes().first())

    private class BFSIterator(private val graph: ImmutableValueGraph<Int, *>, start: Int) :
        IntIterator() {

        private val visited = Int2IntHashMap(graph.nodes().size)
        private val queue = IntArrayDeque()

        init {
            queue.addLast(start)
            visited[start] = 1
        }

        override fun nextInt(): Int {
            val next = queue.removeFirst()
            for (vertexId in graph.successors(next)) {
                if (visited[vertexId] != 1) {
                    visited[vertexId] = 1
                    queue.addLast(vertexId)
                }
            }
            return next
        }

        override fun hasNext(): Boolean = !queue.isEmpty()
    }
}
