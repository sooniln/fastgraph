package io.github.sooniln.fastgraph.primitives

import com.google.common.collect.Iterators
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class Int2IntHashMapTest {

    private val random = Random(99)

    private val expectedMap = HashMap<Int, Int>()
    private val map = Int2IntHashMap2()

    @Test
    fun add() {
        for (i in 0..500) {
            map[i] = i
            expectedMap[i] = i
            assertThat(map.size).isEqualTo(expectedMap.size)
            assertThat(map.containsKey(i)).isTrue()
            assertThat(map.containsValue(i)).isTrue()
            assertThat(map[i]).isEqualTo(i)
            assertThat(Iterators.size(map.primitiveEntries.iterator())).isEqualTo(map.size)
            assertThat(map.keys.containsAll(expectedMap.keys)).isTrue()
        }
        repeat(500) {
            val k = random.nextInt()
            val v = random.nextInt()
            map[k] = v
            expectedMap[k] = v
            assertThat(map.size).isEqualTo(expectedMap.size)
            assertThat(map.containsKey(k)).isTrue()
            assertThat(map.containsValue(v)).isTrue()
            assertThat(map[k]).isEqualTo(v)
            assertThat(Iterators.size(map.primitiveEntries.iterator())).isEqualTo(map.size)
            assertThat(map.keys.containsAll(expectedMap.keys)).isTrue()
        }

        assertThat(map).isEqualTo(expectedMap)
        assertThat(expectedMap).isEqualTo(map)

        assertThat(map.entries).containsExactlyInAnyOrderElementsOf(expectedMap.entries)
        assertThat(expectedMap.entries).containsExactlyInAnyOrderElementsOf(map.entries)
    }

    @Test
    fun iterate() {
        for (i in 1..100) {
            map[i] = i
            expectedMap[i] = i
            assertThat(Iterators.size(map.primitiveEntries.iterator())).isEqualTo(map.size)
        }

        assertThat(map).isEqualTo(expectedMap)
        assertThat(expectedMap).isEqualTo(map)

        assertThat(map.entries).containsExactlyInAnyOrderElementsOf(expectedMap.entries)
        assertThat(expectedMap.entries).containsExactlyInAnyOrderElementsOf(map.entries)
    }

    @Test
    fun preallocateAndAdd() {
        map.ensureCapacity(1024)
        repeat(1024) {
            val k = random.nextInt()
            val v = random.nextInt()
            map[k] = v
            expectedMap[k] = v
            assertThat(map.size).isEqualTo(expectedMap.size)
            assertThat(map.containsKey(k)).isTrue()
            assertThat(map.containsValue(v)).isTrue()
            assertThat(map[k]).isEqualTo(v)
            assertThat(Iterators.size(map.primitiveEntries.iterator())).isEqualTo(map.size)
            assertThat(map.keys.containsAll(expectedMap.keys)).isTrue()
        }

        assertThat(map).isEqualTo(expectedMap)
        assertThat(expectedMap).isEqualTo(map)

        assertThat(map.entries).containsExactlyInAnyOrderElementsOf(expectedMap.entries)
        assertThat(expectedMap.entries).containsExactlyInAnyOrderElementsOf(map.entries)
    }

    @Test
    fun remove() {
        repeat(30) {
            val k = random.nextInt(30)
            val v = random.nextInt()
            map[k] = v
            expectedMap[k] = v
        }

        repeat(30) {
            val k = random.nextInt(30)
            map.remove(k)
            expectedMap.remove(k)
            assertThat(map.containsKey(k)).isFalse()
        }

        assertThat(map).isEqualTo(expectedMap)
        assertThat(expectedMap).isEqualTo(map)

        assertThat(map.entries).containsExactlyInAnyOrderElementsOf(expectedMap.entries)
        assertThat(expectedMap.entries).containsExactlyInAnyOrderElementsOf(map.entries)

        repeat(100) {
            val k = random.nextInt(200)
            val v = random.nextInt()
            map[k] = v
            expectedMap[k] = v
        }

        repeat(100) {
            val k = random.nextInt(200)
            assertThat(map.containsKey(k)).isEqualTo(expectedMap.containsKey(k))
            map.remove(k)
            expectedMap.remove(k)
            assertThat(map.containsKey(k)).isEqualTo(expectedMap.containsKey(k))
            assertThat(map.keys.containsAll(expectedMap.keys)).isTrue()
        }

        assertThat(map).isEqualTo(expectedMap)
        assertThat(expectedMap).isEqualTo(map)

        assertThat(map.entries).containsExactlyInAnyOrderElementsOf(expectedMap.entries)
        assertThat(expectedMap.entries).containsExactlyInAnyOrderElementsOf(map.entries)
    }

    @Test
    fun iteratorRemove() {
        repeat(30) {
            val k = random.nextInt()
            val v = random.nextInt()
            map[k] = v
            expectedMap[k] = v
        }

        var it = map.keys.iterator()
        while (it.hasNext()) {
            val v = it.next()
            assertThat(map.containsKey(v)).isTrue()
            assertThat(expectedMap.containsKey(v)).isTrue()
            it.remove()
            expectedMap.remove(v)
            assertThat(map.containsKey(v)).isFalse()
            assertThat(map.keys.containsAll(expectedMap.keys)).isTrue()
        }

        assertThat(map).isEmpty()
        assertThat(map.keys.iterator().hasNext()).isFalse

        assertThat(map).isEqualTo(expectedMap)
        assertThat(expectedMap).isEqualTo(map)

        assertThat(map.entries).containsExactlyInAnyOrderElementsOf(expectedMap.entries)
        assertThat(expectedMap.entries).containsExactlyInAnyOrderElementsOf(map.entries)

        repeat(33) {
            val k = random.nextInt()
            val v = random.nextInt()
            map[k] = v
            expectedMap[k] = v
        }

        assertThat(map).isEqualTo(expectedMap)
        assertThat(expectedMap).isEqualTo(map)

        assertThat(map.entries).containsExactlyInAnyOrderElementsOf(expectedMap.entries)
        assertThat(expectedMap.entries).containsExactlyInAnyOrderElementsOf(map.entries)

        it = map.keys.iterator()
        while (it.hasNext()) {
            val v = it.next()
            assertThat(map.containsKey(v)).isTrue()
            assertThat(expectedMap.containsKey(v)).isTrue()
            it.remove()
            expectedMap.remove(v)
            assertThat(map.containsKey(v)).isFalse()
            assertThat(map.keys.containsAll(expectedMap.keys)).isTrue()
        }

        assertThat(map).isEmpty()
        assertThat(map.keys.iterator().hasNext()).isFalse

        assertThat(map).isEqualTo(expectedMap)
        assertThat(expectedMap).isEqualTo(map)

        assertThat(map.entries).containsExactlyInAnyOrderElementsOf(expectedMap.entries)
        assertThat(expectedMap.entries).containsExactlyInAnyOrderElementsOf(map.entries)
    }
}
