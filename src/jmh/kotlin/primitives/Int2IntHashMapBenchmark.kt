package io.github.sooniln.fastgraph.primitives

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
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 4, time = 5)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class Int2IntHashMapBenchmark {

    @Param("5", "31", "1000", "10000", "1000000")
    private var size: Int = 100

    private lateinit var map: Int2IntHashMap
    private lateinit var inSet: IntArray
    private lateinit var outSet: IntArray

    private val random = Random(20001)

    @Setup
    fun setup() {
        map = Int2IntHashMap(size)
        inSet = IntArray(size)
        outSet = IntArray(size)
        for (i in 0..<size) {
            val k = random.nextInt(10000000)
            val v = random.nextInt(10000000)
            map[k] = v
            inSet[i] = k
        }

        var i = 0
        while (i < size) {
            val r = random.nextInt(10000000)
            if (!map.containsKey(r)) {
                outSet[i++] = r
            }
        }
    }

    @Benchmark
    fun getInSet(): Int {
        var c = 0
        for (i in inSet) {
            c += map[i]
        }
        return c
    }

    @Benchmark
    fun getOutSet(): Int {
        var c = 0
        for (i in outSet) {
            c += map.getOrDefault(i, 1)
        }
        return c
    }

    @Benchmark
    fun iterate(): Int {
        var c = 0
        for (i in map.primitiveEntries) {
            c += i.key + i.value
        }
        return c
    }

    @Benchmark
    fun iterateKeys(): Int {
        var c = 0
        for (i in map.keys) {
            c += i
        }
        return c
    }
}
