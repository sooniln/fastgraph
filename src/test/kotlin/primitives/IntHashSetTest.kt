package io.github.sooniln.fastgraph.primitives

import com.google.common.collect.Iterables
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class IntHashSetTest {

    private val random = Random(System.currentTimeMillis())

    private val expectedSet = HashSet<Int>()
    private val set = IntHashSet()

    @Test
    fun add() {
        for (i in 0..500) {
            assertThat(set.add(i)).isEqualTo(expectedSet.add(i))
            assertThat(set.size).isEqualTo(expectedSet.size)
            assertThat(Iterables.size(set)).isEqualTo(set.size)
            assertThat(set.contains(i)).isTrue()
        }
        repeat(500) {
            val r = random.nextInt()
            assertThat(set.add(r)).isEqualTo(expectedSet.add(r))
            assertThat(set.size).isEqualTo(expectedSet.size)
            assertThat(Iterables.size(set)).isEqualTo(set.size)
            assertThat(set.contains(r)).isTrue()
        }

        assertThat(set).isEqualTo(expectedSet)
        assertThat(expectedSet).isEqualTo(set)

        assertThat(set).containsExactlyInAnyOrderElementsOf(expectedSet)
        assertThat(expectedSet).containsExactlyInAnyOrderElementsOf(set)
    }

    @Test
    fun preallocateAndAdd() {
        set.ensureCapacity(1024)
        repeat(1024) {
            val r = random.nextInt()
            assertThat(set.add(r)).isEqualTo(expectedSet.add(r))
            assertThat(set.size).isEqualTo(expectedSet.size)
            assertThat(set.contains(r)).isTrue()
            assertThat(Iterables.size(set)).isEqualTo(set.size)
        }

        assertThat(set).isEqualTo(expectedSet)
        assertThat(expectedSet).isEqualTo(set)

        assertThat(set).containsExactlyInAnyOrderElementsOf(expectedSet)
        assertThat(expectedSet).containsExactlyInAnyOrderElementsOf(set)
    }

    @Test
    fun contains() {
        repeat(100) {
            val r = random.nextInt(200)
            assertThat(set.add(r)).isEqualTo(expectedSet.add(r))
        }

        repeat(100) {
            val r = random.nextInt(200)
            assertThat(set.contains(r)).isEqualTo(expectedSet.contains(r))
        }
    }

    @Test
    fun remove() {
        repeat(30) {
            val r = random.nextInt(30)
            assertThat(set.add(r)).isEqualTo(expectedSet.add(r))
        }

        repeat(30) {
            val r = random.nextInt(30)
            assertThat(set.remove(r)).isEqualTo(expectedSet.remove(r))
            assertThat(set.contains(r)).isFalse()
        }

        assertThat(set).isEqualTo(expectedSet)
        assertThat(expectedSet).isEqualTo(set)

        assertThat(set).containsExactlyInAnyOrderElementsOf(expectedSet)
        assertThat(expectedSet).containsExactlyInAnyOrderElementsOf(set)

        repeat(100) {
            val r = random.nextInt(200)
            assertThat(set.add(r)).isEqualTo(expectedSet.add(r))
        }

        repeat(100) {
            val r = random.nextInt(200)
            assertThat(set.remove(r)).isEqualTo(expectedSet.remove(r))
            assertThat(set.contains(r)).isFalse()
        }

        assertThat(set).isEqualTo(expectedSet)
        assertThat(expectedSet).isEqualTo(set)

        assertThat(set).containsExactlyInAnyOrderElementsOf(expectedSet)
        assertThat(expectedSet).containsExactlyInAnyOrderElementsOf(set)
    }

    @Test
    fun iteratorRemove() {
        repeat(30) {
            val r = random.nextInt(30)
            assertThat(set.add(r)).isEqualTo(expectedSet.add(r))
        }

        var it = set.iterator()
        while (it.hasNext()) {
            val v = it.next()
            assertThat(set.contains(v)).isTrue()
            assertThat(expectedSet.contains(v)).isTrue()
            it.remove()
            expectedSet.remove(v)
            assertThat(set.contains(v)).isFalse()
            assertThat(set.containsAll(expectedSet)).isTrue()
        }

        assertThat(set).isEmpty()
        assertThat(set.iterator().hasNext()).isFalse

        repeat(100) {
            val r = random.nextInt(30)
            assertThat(set.add(r)).isEqualTo(expectedSet.add(r))
        }

        it = set.iterator()
        while (it.hasNext()) {
            val v = it.next()
            assertThat(set.contains(v)).isTrue()
            assertThat(expectedSet.contains(v)).isTrue()
            it.remove()
            expectedSet.remove(v)
            assertThat(set.contains(v)).isFalse()
            assertThat(set.containsAll(expectedSet)).isTrue()
        }

        assertThat(set).isEmpty()
        assertThat(set.iterator().hasNext()).isFalse
    }
}
