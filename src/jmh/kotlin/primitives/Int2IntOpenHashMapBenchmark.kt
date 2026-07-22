package io.github.sooniln.fastgraph.primitives

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
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
open class Int2IntOpenHashMapBenchmark {

    @Param("5", "31", "1000", "10000", "1000000")
    private var size: Int = 100

    private lateinit var map: Int2IntOpenHashMap
    private lateinit var inSet: IntArray
    private lateinit var outSet: IntArray

    private val random = Random(20001)

    @Setup
    fun setup() {
        map = Int2IntOpenHashMap(size)
        inSet = IntArray(size)
        outSet = IntArray(size)
        for (i in 0..<size) {
            val k = random.nextInt()
            val v = random.nextInt()
            map[k] = v
            inSet[i] = k
        }

        var i = 0
        while (i < size) {
            val r = random.nextInt()
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
        val it = map.int2IntEntrySet().iterator()
        while (it.hasNext()) {
            val i = it.next()
            c += i.intKey + i.intValue
        }
        return c
    }

    @Benchmark
    fun iterateKeys(): Int {
        var c = 0
        val it = map.keys.iterator()
        while (it.hasNext()) {
            c += it.nextInt()
        }
        return c
    }

    @Benchmark
    fun iterateValues(): Int {
        var c = 0
        val it = map.values.iterator()
        while (it.hasNext()) {
            c += it.nextInt()
        }
        return c
    }
}
