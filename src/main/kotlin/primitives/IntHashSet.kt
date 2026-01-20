package io.github.sooniln.fastgraph.primitives

import java.util.Arrays
import kotlin.math.max

/**
 * A replacement for fastutils IntOpenHashSet which:
 *   * uses drastically less memory with < 32 elements
 *   * always has faster iteration
 *   * is often slightly faster for get/set with < 32 elements
 *   * is slightly slower for get/set with larger numbers of elements, but not by a hugely meaningful amount.
 *
 * Fastutils wastes an enormous amount of memory at low sizes, which becomes an issue for us when we have say 4 million
 * sets of <10 elements each... (such as in many sparse graphs).
 *
 * Implementation uses robin hood hashing with backwards shift deletion.
 */
class IntHashSet(
    capacity: Int = DEFAULT_INITIAL_CAPACITY,
    private val loadFactor: Float = DEFAULT_LOAD_FACTOR
) : MutableIntSet {

    init {
        require(loadFactor > 0 && loadFactor < 1) { "Load factor must be greater than 0 and smaller than 1" }
        require(capacity >= 0) { "The expected number of elements must be nonnegative" }
    }

    private var valuesArr = EMPTY_ARRAY
    private var arrayUsage = 0

    // use threshold to store the initial size before we allocate anything. since threshold cannot be negative, we use
    // the highest bit to store whether the set contains zero or not
    private var thresholdAndContainsZero = capacity

    private var threshold: Int
        inline get() = thresholdAndContainsZero and ARRAY_USAGE_MASK
        inline set(value) {
            thresholdAndContainsZero = value or (thresholdAndContainsZero and ARRAY_USAGE_MASK.inv())
        }

    private var containsZero: Boolean
        inline get() = thresholdAndContainsZero and ARRAY_USAGE_MASK.inv() != 0
        inline set(value) {
            thresholdAndContainsZero = if (value) {
                thresholdAndContainsZero or ARRAY_USAGE_MASK.inv()
            } else {
                thresholdAndContainsZero and ARRAY_USAGE_MASK
            }
        }

    constructor() : this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR)
    constructor(expectedCapacity: Int) : this(expectedCapacity, DEFAULT_LOAD_FACTOR)

    override val size: Int get() = if (containsZero) arrayUsage + 1 else arrayUsage

    override fun isEmpty(): Boolean {
        return size == 0
    }

    fun ensureCapacity(capacity: Int) {
        require(capacity >= 0) { "The expected number of elements must be nonnegative" }
        if (valuesArr.isEmpty()) {
            threshold = capacity
        } else {
            growTo(capacity)
        }
    }

    override fun add(element: Int): Boolean {
        if (element == 0) {
            if (containsZero) {
                return false
            } else {
                containsZero = true
                return true
            }
        }

        resizeIfNecessary()

        return if (isHashing()) addHashing(element) else addArray(element)
    }

    private fun addHashing(element: Int): Boolean {
        val mask = valuesArr.mask()
        var slot = element.slot(mask)
        var newValueSlotDistance = 0
        while (true) {
            var value = valuesArr[slot]
            when (value) {
                element -> {
                    return false
                }

                0 -> {
                    valuesArr[slot] = element
                    ++arrayUsage
                    return true
                }

                else -> {
                    var newValue = element
                    val valueSlotDistance = value.slotDistance(slot, mask)
                    if (newValueSlotDistance > valueSlotDistance) {
                        // move all slots right until we hit a zero slot. max slot distance is generally not high enough
                        // for System.arrayCopy() to outperform the manual loop here, especially with the additional
                        // complexity needed for System.arrayCopy().
                        do {
                            valuesArr[slot] = newValue
                            newValue = value

                            slot = (slot + 1) and mask
                            value = valuesArr[slot]
                        } while (value != 0)

                        valuesArr[slot] = newValue
                        ++arrayUsage
                        return true
                    }
                }
            }

            slot = (slot + 1) and mask
            newValueSlotDistance++
        }
    }

    private fun addArray(element: Int): Boolean {
        var slot = 0
        while (slot < arrayUsage) {
            if (valuesArr[slot] == element) return false
            ++slot
        }

        valuesArr[arrayUsage++] = element
        return true
    }

    override fun remove(element: Int): Boolean {
        if (element == 0) {
            if (containsZero) {
                containsZero = false
                return true
            }

            return false
        }

        val slot = findSlot(element)
        if (slot >= 0) {
            removeSlot(slot)
            return true
        }

        return false
    }

    override fun clear() {
        Arrays.setAll(valuesArr) { 0 }
        containsZero = false
        arrayUsage = 0
    }

    private fun findSlot(element: Int): Int {
        return if (isHashing()) findSlotHashing(element) else findSlotArray(element)
    }

    private fun findSlotHashing(element: Int): Int {
        assert(isHashing())
        assert(element != 0)

        val mask = valuesArr.mask()

        var slot = element.slot(mask)
        var value = valuesArr[slot]
        while (true) {
            // we could stop looking once the distance < current distance, but with the short runs present in
            // benchmarks it currently doesn't seem worth the effort?
            when (value) {
                0 -> return -1
                element -> return slot
            }

            slot = (slot + 1) and mask
            value = valuesArr[slot]
        }
    }

    private fun findSlotArray(element: Int): Int {
        assert(!isHashing())
        assert(element != 0)

        // iterate backwards under assumption more recently added values are more likely to be queried
        var slot = arrayUsage - 1
        while (slot >= 0) {
            if (valuesArr[slot] == element) {
                return slot
            }
            --slot
        }
        return -1
    }

    private fun removeSlot(slot: Int) {
        if (isHashing()) removeSlotHashing(slot) else removeSlotArray(slot)
    }

    private fun removeSlotHashing(slot: Int) {
        assert(isHashing())
        assert(valuesArr[slot] != 0)

        val mask = valuesArr.mask()

        // move all slots left until we hit a zero slot. max slot distance is generally not high enough for
        // System.arrayCopy() to outperform the manual loop here, especially with the additional complexity needed
        // for System.arrayCopy().
        var slot = slot
        var nextSlot = (slot + 1) and mask
        var nextValue = valuesArr[nextSlot]
        while (nextValue != 0 && nextValue.slotDistance(nextSlot, mask) > 0) {
            valuesArr[slot] = nextValue

            slot = nextSlot
            nextSlot = (nextSlot + 1) and mask
            nextValue = valuesArr[nextSlot]
        }
        valuesArr[slot] = 0
        --arrayUsage
    }

    private fun removeSlotArray(slot: Int) {
        assert(!isHashing())
        assert(valuesArr[slot] != 0)
        assert(slot < arrayUsage)

        val lastIndex = arrayUsage - 1
        if (slot < lastIndex) {
            System.arraycopy(valuesArr, slot + 1, valuesArr, slot, lastIndex - slot)
        }
        --arrayUsage
    }

    override fun contains(element: Int): Boolean {
        if (element == 0) return containsZero
        return findSlot(element) >= 0
    }

    override fun iterator(): MutableIntIterator {
        return object : MutableIntIterator() {
            private val valuesArr = this@IntHashSet.valuesArr
            private val arrayUsage = this@IntHashSet.arrayUsage
            private val mask = valuesArr.mask()

            private var entriesLeft = size
            private var slot = numSlots()
            private var previousSlot = slot + 1
            private var nextValue = 0

            init {
                if (!containsZero) decrement()
            }

            private fun numSlots() = if (isHashing()) valuesArr.size else arrayUsage

            override fun hasNext(): Boolean {
                return entriesLeft > 0
            }

            override fun nextInt(): Int {
                if (entriesLeft <= 0) throw NoSuchElementException()
                val v = nextValue
                --entriesLeft
                decrement()
                return v
            }

            override fun remove() {
                val slotsSize = numSlots()
                if (previousSlot < slotsSize) {
                    removeSlot(previousSlot)
                } else {
                    check(previousSlot == slotsSize)
                    assert(containsZero)
                    containsZero = false
                }
                previousSlot = slotsSize + 1
            }

            private fun decrement() {
                previousSlot = slot
                if (entriesLeft == 0) return

                do {
                    if (slot > 0) {
                        // simple subtraction is a lot faster if we can get away with it (ie 99% of the time)
                        --slot
                    } else {
                        slot = (slot - 1) and mask
                    }
                    nextValue = valuesArr[slot]
                } while (nextValue == 0)
            }
        }
    }

    private fun resizeIfNecessary() {
        if (valuesArr.isEmpty()) {
            assert(threshold > 0)
            growTo(threshold)
        } else if (arrayUsage >= threshold) {
            growTo(threshold shl 1)
        }
    }

    private fun growTo(capacity: Int) {
        val newLength = arraySize(capacity, loadFactor)
        if (valuesArr.size >= newLength) {
            return
        }

        if (valuesArr.isEmpty()) {
            valuesArr = IntArray(newLength)
            threshold = if (isHashing()) {
                (valuesArr.size * loadFactor).toInt()
            } else {
                valuesArr.size
            }
            return
        }

        if (isHashing()) {
            // TODO: better algorithm?
            val oldValues = valuesArr
            valuesArr = IntArray(newLength)
            threshold = (valuesArr.size * loadFactor).toInt()
            arrayUsage = 0
            for (v in oldValues) {
                if (v != 0) {
                    add(v)
                }
            }
        } else {
            assert(valuesArr.size <= HASHIFY_THRESHOLD)

            if (newLength <= HASHIFY_THRESHOLD) {
                valuesArr = valuesArr.copyOf(newLength)
                threshold = valuesArr.size
            } else {
                val oldValues = valuesArr
                val oldArrayUsage = arrayUsage
                valuesArr = IntArray(newLength)
                threshold = (valuesArr.size * loadFactor).toInt()
                arrayUsage = 0

                var slot = 0
                while (slot < oldArrayUsage) {
                    add(oldValues[slot])
                    ++slot
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other is Set<*>) {
            if (size != other.size) return false
            return other.containsAll(other)
        }

        return false
    }

    override fun hashCode(): Int {
        var result = 0
        for (element in this) {
            result = 31 * result + element
        }
        return result
    }

    override fun toString(): String {
        return joinToString(", ", "[", "]") { it.toString() }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isHashing(): Boolean = valuesArr.size > HASHIFY_THRESHOLD

    @Suppress("NOTHING_TO_INLINE")
    private inline fun mixHash(element: Int): Int {
        val h = element * INT_PHI
        return h xor (h ushr 16)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun IntArray.mask(): Int = size - 1

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Int.slot(mask: Int): Int {
        assert(mask == valuesArr.mask())
        return mixHash(this) and mask
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Int.slotDistance(slot: Int, mask: Int): Int {
        assert(this != 0)
        val idealSlot = slot(mask)
        return if (idealSlot <= slot) {
            slot - idealSlot
        } else {
            slot + valuesArr.size - idealSlot
        }
    }

    companion object {

        private val EMPTY_ARRAY = IntArray(0)

        /** 2<sup>32</sup> &middot; &phi;, &phi; = (&#x221A;5 &minus; 1)/2. */
        private const val INT_PHI: Int = -0x61c88647

        private const val DEFAULT_LOAD_FACTOR = .75f
        private const val DEFAULT_INITIAL_CAPACITY = 1 shl 2  // must be power of two
        private const val MAXIMUM_CAPACITY: Int = 1 shl 30 // must be power of two
        private const val HASHIFY_THRESHOLD: Int = 1 shl 5 // must be power of two
        private const val MIN_HASH_CAPACITY = 1 shl 4 // must be power of two

        private const val ARRAY_USAGE_MASK = 0x7FFFFFFF

        private fun arraySize(capacity: Int, loadFactor: Float): Int {
            return if (capacity <= HASHIFY_THRESHOLD) {
                capacity
            } else {
                max(minPowerOfTwo((capacity / loadFactor).toInt()), MIN_HASH_CAPACITY)
            }
        }

        private fun minPowerOfTwo(cap: Int): Int {
            val n = -1 ushr Integer.numberOfLeadingZeros(cap - 1)
            return if (n < 0) 1 else if (n >= MAXIMUM_CAPACITY) MAXIMUM_CAPACITY else n + 1
        }
    }
}
