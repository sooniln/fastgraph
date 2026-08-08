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
@Warmup(iterations = 4)
@Measurement(iterations = 4)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class ImmutableGraphBenchmark {

    lateinit var graph: ValueGraph<Int, Float>

    @Setup
    fun setup() {
        graph = Loader.loadImmutableGraph()
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
    fun edgeValues(): Double {
        var i = 0.0
        for (edge in graph.edges) {
            i += graph.edgeProperty[edge]
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
    fun outgoingEdgeValues(): Double {
        var i = 0.0
        for (source in graph.vertices) {
            for (edge in graph.outgoingEdges(source)) {
                i += graph.edgeProperty[edge]
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

    @Benchmark
    fun dijkstras() = Utils.dijkstras(graph, graph.edgeProperty, graph.vertices.first())
}
