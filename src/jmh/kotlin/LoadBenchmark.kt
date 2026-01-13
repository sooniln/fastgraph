package io.github.sooniln.fastgraph

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

@Fork(1)
@Warmup(iterations = 4, time = 20)
@Measurement(iterations = 4, time = 20)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class LoadBenchmark {

    @Measurement(time = 20)
    @Benchmark
    fun loadGuavaSimpleGraph() = dump(Loader.loadGuavaSimpleGraph())

    @Measurement(time = 20)
    @Benchmark
    fun loadGuavaGraph() = dump(Loader.loadGuavaGraph())

    @Measurement(time = 20)
    @Benchmark
    fun loadGuavaNetwork() = dump(Loader.loadGuavaNetwork())

    @Measurement(time = 20)
    @Benchmark
    fun loadJGraphTSimpleGraph() = dump(Loader.loadJGraphTSimpleGraph())

    @Measurement(time = 20)
    @Benchmark
    fun loadJGraphTGraph() = dump(Loader.loadJGraphTGraph())

    @Measurement(time = 20)
    @Benchmark
    fun loadJGraphTNetwork() = dump(Loader.loadJGraphTNetwork())

    @Measurement(time = 20)
    @Benchmark
    fun loadImmutableSimpleGraph() = dump(Loader.loadImmutableSimpleGraph())

    @Measurement(time = 20)
    @Benchmark
    fun loadImmutableGraph() = dump(Loader.loadImmutableGraph())

    @Measurement(time = 20)
    @Benchmark
    fun loadImmutableNetwork() = dump(Loader.loadImmutableNetwork())

    @Measurement(time = 20)
    @Benchmark
    fun loadMutableSimpleGraph() = dump(Loader.loadMutableSimpleGraph())

    @Measurement(time = 20)
    @Benchmark
    fun loadMutableGraph() = dump(Loader.loadMutableGraph())

    @Measurement(time = 20)
    @Benchmark
    fun loadMutableNetwork() = dump(Loader.loadMutableNetwork())

    private fun <G : Graph, V, E> dump(o: PropertyGraph<G, V, E>): PropertyGraph<G, V, E> {
        /*val totalMem = GraphLayout.parseInstance(o.graph, o.vertexProperty, o.edgeProperty).totalSize()
        val topologyMem = GraphLayout.parseInstance(o.graph).totalSize()
        println("total: $totalMem")
        println("    topology: $topologyMem")
        println("    data: ${totalMem - topologyMem}")*/
        return o
    }

    private fun <T> dump(o: T): T {
        /*val totalMem = GraphLayout.parseInstance(o).totalSize()
        println("total: $totalMem")*/
        return o
    }
}
