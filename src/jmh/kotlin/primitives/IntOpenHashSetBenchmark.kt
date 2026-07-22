package io.github.sooniln.fastgraph.primitives

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit
import kotlin.random.Random


@Fork(1)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 4, time = 5)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class IntOpenHashSetBenchmark {

    @Param("5", "31", "1000", "10000", "1000000")
    private var size: Int = 31

    private lateinit var set: IntOpenHashSet
    private lateinit var inSet: IntArray
    private lateinit var outSet: IntArray

    private val random = Random(20001)

    @Setup
    fun setup() {
        set = IntOpenHashSet(size)
        inSet = IntArray(size)
        outSet = IntArray(size)
        for (i in 0..<size) {
            val r = random.nextInt(size * 10)
            set.add(r)
            inSet[i] = r
        }

        var i = 0
        while (i < size) {
            val r = random.nextInt(size * 10)
            if (!set.contains(r)) {
                outSet[i++] = r
            }
        }
    }

    @Benchmark
    fun getInSet(): Int {
        var c = 0
        for (i in inSet) {
            if (set.contains(i)) ++c
        }
        return c
    }

    @Benchmark
    fun getOutSet(): Int {
        var c = 0
        for (i in outSet) {
            if (set.contains(i)) ++c
        }
        return c
    }

    @Benchmark
    fun iterate(): Int {
        var c = 0
        val it = set.iterator()
        while (it.hasNext()) {
            c += it.nextInt()
        }
        return c
    }
}
