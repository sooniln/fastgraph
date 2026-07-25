package io.github.sooniln.fastgraph.primitives.collections

import io.github.sooniln.fastcollect.ints.AbstractMutableIntSet
import io.github.sooniln.fastcollect.ints.MutableIntIterator
import io.github.sooniln.fastcollect.ints.IntCollection
import io.github.sooniln.fastcollect.ints.IntConsumer
import io.github.sooniln.fastgraph.primitives.ArrayUtils
import kotlin.jvm.JvmOverloads
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal class GraphIntHashSet @JvmOverloads constructor(
    capacity: Int = 0,
) : AbstractMutableIntSet() {

    public constructor(elements: IntCollection): this() { addAll(elements) }
    public constructor(elements: Collection<Int>): this() { addAll(elements) }

    private var keysArr = EMPTY_ARRAY

    // threshold + size == capacity (rehash once threshold <= 0, if we haven't allocated yet then threshold.inv() is
    // our initial capacity)
    private var threshold = MIN_INITIAL_CAPACITY.inv()

    override var size: Int = 0
        private set

    init {
        ensureCapacity(capacity)
    }

    /**
     * Ensures that the set can hold at least given number of elements without any further resizing of the backing
     * array.
     */
    public fun ensureCapacity(capacity: Int) {
        require(capacity >= 0) { "The expected number of elements must be nonnegative" }
        if (keysArr === EMPTY_ARRAY) {
            threshold = min(threshold, capacity.inv())
        } else if (capacity > threshold + size) {
            rehash(capacity)
        }
    }

    /**
     * Reduces the size of the backing array to the minimum required to hold the current number of elements.
     */
    public fun trimToSize() {
        rehash(size)
    }

    override fun contains(element: Int): Boolean = findSlot(element, { true }, { false })

    override fun add(element: Int): Boolean = add(element, { false }, { true })

    override fun remove(element: Int): Boolean {
        return findSlot(
            element,
            { slot ->
                removeSlot(slot)
                true
            },
            { false })
    }

    override fun clear() {
        if (keysArr !== EMPTY_ARRAY) {
            keysArr.fill(EMPTY_KEY)
            threshold += size
        }
        size = 0
    }

    private inline fun <T> findSlot(key: Int, onFind: (slot: Int) -> T, onFail: () -> T): T {
        require(key != EMPTY_KEY)

        val keysArr = keysArr
        val mask = keysArr.size - 1

        var slot = key.slot(mask)
        var currKey = keysArr[slot]
        while (currKey != EMPTY_KEY) {
            if (currKey == key) {
                return onFind(slot)
            }

            slot = (slot + 1) and mask
            currKey = keysArr[slot]
        }

        return onFail()
    }

    private inline fun <T> add(key: Int, onPresent: () -> T, onAbsent: () -> T): T {
        require(key != EMPTY_KEY)

        if (threshold <= 0) increaseCapacity()

        val keysArr = keysArr
        val mask = keysArr.size - 1

        var slot = key.slot(mask)
        var currKey = keysArr[slot]
        while (currKey != EMPTY_KEY) {
            if (currKey == key) {
                return onPresent()
            }

            slot = (slot + 1) and mask
            currKey = keysArr[slot]
        }

        keysArr[slot] = key
        threshold -= 1
        size += 1
        return onAbsent()
    }

    private fun removeSlot(slot: Int) {
        val keysArr = keysArr
        val mask = keysArr.size - 1

        var currSlot = slot
        var nextSlot = (currSlot + 1) and mask
        var nextKey = keysArr[nextSlot]
        while (nextKey != EMPTY_KEY && nextKey.slotDistance(nextSlot, mask) > 0) {
            keysArr[currSlot] = nextKey

            currSlot = nextSlot
            nextSlot = (nextSlot + 1) and mask
            nextKey = keysArr[nextSlot]
        }

        keysArr[currSlot] = EMPTY_KEY
        threshold += 1
        size -= 1
    }

    override fun addAll(elements: IntCollection): Boolean {
        var modified = false
        if (elements is GraphIntHashSet && elements.size / 2 > size) {
            val oldKeysArr = keysArr

            resetTo(elements)
            for (key in oldKeysArr) {
                if (key != EMPTY_KEY) {
                    modified = add(key) or modified
                }
            }
            trimToSize()
        } else {
            ensureCapacity(max(size + (elements.size / 2), elements.size))
            for (element in elements) {
                modified = add(element) or modified
            }
        }
        return modified
    }

    override fun addAll(elements: Collection<Int>): Boolean {
        if (elements is IntCollection) {
            return addAll(elements)
        } else {
            ensureCapacity(max(size + (elements.size / 2), elements.size))
            var modified = false
            for (element in elements) {
                modified = add(element) or modified
            }
            return modified
        }
    }

    private fun resetTo(elements: GraphIntHashSet) {
        check(!elements.isEmpty())

        keysArr = elements.keysArr.copyOf()
        size = elements.size
        threshold = elements.threshold
    }

    private fun increaseCapacity() {
        check(threshold <= 0)
        if (threshold < 0) {
            rehash(threshold.inv())
        } else {
            rehash(size shl 1)
        }
    }

    private fun rehash(capacity: Int) {
        check(capacity >= size)

        if (capacity == 0 && keysArr !== EMPTY_ARRAY) {
            keysArr = EMPTY_ARRAY
            threshold = MIN_INITIAL_CAPACITY.inv()
            return
        }

        // for small capacities we force loadFactor to 1.0 to save memory (small array scans are likely to be fast)
        val actualLoadFactor = if (capacity <= FORCE_LOAD_FACTOR_MAX) 1.0 else 7.0/8.0

        val newLength = arraySize(capacity, actualLoadFactor)
        if (keysArr.size == newLength) return

        val newKeysArr = IntArray(newLength)
        newKeysArr.fill(EMPTY_KEY)
        val newMask = newKeysArr.size - 1

        for (slot in keysArr.indices) {
            val key = keysArr[slot]
            if (key != EMPTY_KEY) addRehashing(newKeysArr, newMask, key)
        }

        keysArr = newKeysArr

        // threshold must always maintain the invariant of at least 1 slot being open
        threshold = min((newKeysArr.size * actualLoadFactor).toInt(), newKeysArr.size - 1) - size
    }

    // we can assume key doesn't exist in array
    private fun addRehashing(keysArr: IntArray, mask: Int, key: Int) {
        var slot = key.slot(mask)
        var currKey = keysArr[slot]
        while (currKey != EMPTY_KEY) {
            slot = (slot + 1) and mask
            currKey = keysArr[slot]
        }

        keysArr[slot] = key
    }

    override fun iterator(): MutableIntIterator = Iterator()

    override fun foreach(action: IntConsumer) {
        for (key in keysArr) {
            if (key != EMPTY_KEY) {
                action.accept(key)
            }
        }
    }

    private inner class Iterator : MutableIntIterator() {
        private val keysArr = this@GraphIntHashSet.keysArr
        private val mask = keysArr.size - 1

        private var slotsLeft = size
        private var slot = keysArr.size - 1
        private var previousSlot = -1

        init {
            if (slotsLeft > 0) decrement()
        }

        override fun hasNext(): Boolean {
            return slotsLeft > 0
        }

        override fun nextInt(): Int {
            if (slotsLeft <= 0) throw NoSuchElementException()
            previousSlot = slot
            if (--slotsLeft > 0) decrement()
            return keysArr[previousSlot]
        }

        override fun remove() {
            check(previousSlot != -1)
            if (keysArr !== this@GraphIntHashSet.keysArr) throw ConcurrentModificationException()

            removeSlot(previousSlot)
            previousSlot = -1

            // if removal wrapped all the way around to our next slot then we need to adjust
            if (keysArr[slot] == EMPTY_KEY) {
                slot = (slot - 1) and mask
            }
        }

        private fun decrement() {
            do {
                slot = (slot - 1) and mask
            } while (keysArr[slot] == EMPTY_KEY)
        }
    }

    private fun Int.slot(mask: Int): Int = this and mask
    private fun Int.slotDistance(slot: Int, mask: Int): Int = (slot - this) and mask

    private companion object {
        // the value of a field in an uninitialized primitive array
        @Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")
        private const val EMPTY_KEY: Int = (-1).toInt()

        private val EMPTY_ARRAY = intArrayOf(EMPTY_KEY)

        private const val MIN_INITIAL_CAPACITY = 7

        private const val CACHE_LINE_SIZE = 64 / Int.SIZE_BYTES

        // we force the load factor to 1.0 up to the size of two cache lines
        private const val FORCE_LOAD_FACTOR_MAX: Int = 2 * CACHE_LINE_SIZE

        private fun arraySize(capacity: Int, loadFactor: Double): Int {
            check(capacity >= 0)
            // array must always maintain the invariant of at least one slot remaining open
            val requiredArraySize = max((capacity / loadFactor).toInt(), capacity + 1)
            val actualArraySize = ArrayUtils.minPowerOfTwo(requiredArraySize)
            if (actualArraySize < requiredArraySize) throw Error("Required array length $requiredArraySize is too large")
            return actualArraySize
        }
    }
}
