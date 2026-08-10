package io.github.sooniln.fastgraph

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
@Warmup(iterations = 10, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class MutableSimpleGraphBenchmark {

    lateinit var graph: ValueGraph<Int, Unit>

    @Setup
    fun setup() {
        graph = Loader.loadMutableSimpleGraph()
    }

    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Benchmark
    fun vertices(): Int {
        var i = 0
        for (vertex in graph.vertices) {
            i += vertex.id
        }
        return i
    }

    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Benchmark
    fun vertexValues(): Int {
        var i = 0
        for (vertex in graph.vertices) {
            i += graph.vertexProperty[vertex]
        }
        return i
    }

    @Benchmark
    fun edges(bh: Blackhole): Int {
        var i = 0
        for (edge in graph.edges) {
            bh.consume(edge)
            i++
        }
        return i
    }

    @Benchmark
    fun successors(): Int {
        var i = 0
        for (source in graph.vertices) {
            for (target in graph.successors(source)) {
                i += target.id
            }
        }
        return i
    }

    @Benchmark
    fun outgoingEdges(bh: Blackhole): Int {
        var i = 0
        for (source in graph.vertices) {
            for (edge in graph.outgoingEdges(source)) {
                bh.consume(edge)
                i++
            }
        }
        return i
    }

    @Benchmark
    fun bfs(): Int {
        var n = 0
        for (vertex in Traversal.breadthFirst(graph, graph.vertices.first())) {
            n += graph.vertexProperty[vertex]
        }
        return n
    }
}
