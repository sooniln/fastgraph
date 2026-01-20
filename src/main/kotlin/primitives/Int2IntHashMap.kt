package io.github.sooniln.fastgraph.primitives

import io.github.sooniln.fastgraph.primitives.MutableInt2IntMap.MutableInt2IntEntry
import java.util.AbstractMap
import java.util.Arrays
import kotlin.math.max

/**
 * A replacement for fastutils Int2IntOpenHashMap which:
 *   * uses drastically less memory with < 32 elements
 *   * always has faster iteration
 *   * is often slightly faster for get/set with < 32 elements
 *   * is slightly slower for get/set with larger numbers of elements, but not by a hugely meaningful amount.
 *
 * Fastutils wastes an enormous amount of memory at low sizes, which becomes an issue for us when we have say 4 million
 * maps of <10 elements each... (such as in many sparse graphs).
 *
 * Implementation uses robin hood hashing with backwards shift deletion.
 */
class Int2IntHashMap(
    capacity: Int = DEFAULT_INITIAL_CAPACITY,
    private val loadFactor: Float = DEFAULT_LOAD_FACTOR,
    override val poisonValue: Int = -1
) : MutableInt2IntMap {

    init {
        require(loadFactor > 0 && loadFactor < 1) { "Load factor must be greater than 0 and smaller than 1" }
        require(capacity >= 0) { "The expected number of elements must be nonnegative" }
    }

    private var keysAndValuesArr = EMPTY_ARRAY

    private var arrayUsage = 0
    private var zeroValue = 0

    // use threshold to store the initial size before we allocate anything, and since threshold cannot be negative, we
    // also use the highest bit to store whether the map contains zero or not
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

    override val size: Int
        get() {
            val arraySize = arrayUsage shr 1
            return if (containsZero) arraySize + 1 else arraySize
        }

    override fun isEmpty(): Boolean {
        return size == 0
    }

    fun ensureCapacity(capacity: Int) {
        require(capacity >= 0) { "The expected number of elements must be nonnegative" }
        if (keysAndValuesArr.isEmpty()) {
            threshold = capacity
        } else {
            growTo(capacity)
        }
    }

    override operator fun set(key: Int, value: Int) {
        put(key, value)
    }

    override fun put(key: Int, value: Int): Int {
        if (key == 0) {
            if (!containsZero) {
                containsZero = true
                zeroValue = value
                return poisonValue
            } else {
                val oldValue = zeroValue
                zeroValue = value
                return oldValue
            }
        }

        resizeIfNecessary()

        return if (isHashing()) putInternalHashing(key, value) else putInternalArray(key, value)
    }

    private fun putInternalHashing(key: Int, value: Int): Int {
        assert(isHashing())
        assert(key != 0)

        val mask = keysAndValuesArr.mask()

        var slot = key.slot()
        var newKeySlotDistance = 0
        while (true) {
            var currKey = keysAndValuesArr[slot]
            when (currKey) {
                key -> {
                    val oldValue = keysAndValuesArr[slot + 1]
                    keysAndValuesArr[slot + 1] = value
                    return oldValue
                }

                0 -> {
                    keysAndValuesArr[slot] = key
                    keysAndValuesArr[slot + 1] = value
                    arrayUsage += 2
                    return poisonValue
                }

                else -> {
                    if (newKeySlotDistance > currKey.slotDistance(slot)) {
                        var currValue = keysAndValuesArr[slot + 1]
                        var newKey = key
                        var newValue = value
                        // move all slots right until we hit a zero slot. max slot distance is generally not high
                        // enough for System.arrayCopy() to outperform the manual loop here, especially with the
                        // additional complexity needed for System.arrayCopy().
                        do {
                            keysAndValuesArr[slot] = newKey
                            keysAndValuesArr[slot + 1] = newValue
                            newKey = currKey
                            newValue = currValue

                            slot = (slot + 2) and mask
                            currKey = keysAndValuesArr[slot]
                            currValue = keysAndValuesArr[slot + 1]
                        } while (currKey != 0)

                        keysAndValuesArr[slot] = newKey
                        keysAndValuesArr[slot + 1] = newValue
                        arrayUsage += 2
                        return poisonValue
                    }
                }
            }

            slot = (slot + 2) and mask
            newKeySlotDistance += 2
        }
    }

    private fun putInternalArray(key: Int, value: Int): Int {
        assert(!isHashing())
        assert(key != 0)

        var slot = 0
        while (slot < arrayUsage) {
            if (keysAndValuesArr[slot] == key) {
                val oldValue = keysAndValuesArr[slot + 1]
                keysAndValuesArr[slot + 1] = value
                return oldValue
            }

            slot += 2
        }

        keysAndValuesArr[slot] = key
        keysAndValuesArr[slot + 1] = value
        arrayUsage += 2
        return poisonValue
    }

    override fun remove(key: Int): Int {
        if (key == 0) {
            if (containsZero) {
                containsZero = false
                return zeroValue
            }
        } else {
            val slot = findSlot(key)
            if (slot >= 0) {
                val oldValue = keysAndValuesArr[slot + 1]
                removeSlot(slot)
                return oldValue
            }
        }

        return poisonValue
    }

    override fun clear() {
        Arrays.setAll(keysAndValuesArr) { 0 }
        containsZero = false
        arrayUsage = 0
    }

    private fun findSlot(key: Int): Int {
        return if (isHashing()) findSlotHashing(key) else findSlotArray(key)
    }

    private fun findSlotHashing(key: Int): Int {
        assert(isHashing())
        assert(key != 0)

        val mask = keysAndValuesArr.mask()

        var slot = key.slot()
        var currKey = keysAndValuesArr[slot]
        while (true) {
            // TODO: we could stop looking once the distance < current distance
            when (currKey) {
                0 -> return -1
                key -> return slot
            }

            slot = (slot + 2) and mask
            currKey = keysAndValuesArr[slot]
        }
    }

    private fun findSlotArray(key: Int): Int {
        assert(!isHashing())
        assert(key != 0)

        // iterate backwards under assumption more recently added values are more likely to be queried
        var slot = arrayUsage - 2
        while (slot >= 0) {
            if (keysAndValuesArr[slot] == key) {
                return slot
            }
            slot -= 2
        }

        return -1
    }

    private fun removeSlot(slot: Int) {
        if (isHashing()) removeSlotHashing(slot) else removeSlotArray(slot)
    }

    fun removeSlotHashing(slot: Int) {
        assert(isHashing())

        val mask = keysAndValuesArr.mask()

        // move all slots left until we hit a zero slot. max slot distance is generally not high enough for
        // System.arrayCopy() to outperform the manual loop here, especially with the additional complexity needed
        // for System.arrayCopy().
        var currSlot = slot
        var nextSlot = (currSlot + 2) and mask
        var nextKey = keysAndValuesArr[nextSlot]
        var nextValue = keysAndValuesArr[nextSlot + 1]
        while (nextKey != 0 && nextKey.slotDistance(nextSlot) > 0) {
            keysAndValuesArr[currSlot] = nextKey
            keysAndValuesArr[currSlot + 1] = nextValue

            currSlot = nextSlot
            nextSlot = (nextSlot + 2) and mask
            nextKey = keysAndValuesArr[nextSlot]
            nextValue = keysAndValuesArr[nextSlot + 1]
        }
        keysAndValuesArr[currSlot] = 0
        arrayUsage -= 2
    }

    private fun removeSlotArray(slot: Int) {
        assert(!isHashing())
        assert(slot < arrayUsage)

        val lastIndex = arrayUsage - 2
        if (slot < lastIndex) {
            System.arraycopy(keysAndValuesArr, slot + 2, keysAndValuesArr, slot, lastIndex - slot)
        }
        arrayUsage -= 2
    }

    override fun putAll(from: Int2IntMap) {
        if (from is Int2IntHashMap) {
            for (entry in primitiveEntries) {
                set(entry.key, entry.value)
            }
        } else {
            for (entry in primitiveEntries) {
                set(entry.key, entry.value)
            }
        }
    }

    override val keys: MutableIntSet by lazy {
        object : MutableIntSet {
            override val size: Int get() = this@Int2IntHashMap.size
            override fun contains(element: Int): Boolean = containsKey(element)
            override fun add(element: Int): Boolean = throw UnsupportedOperationException()
            override fun remove(element: Int): Boolean = throw UnsupportedOperationException()
            override fun iterator(): MutableIntIterator = KeyIterator()
            override fun clear() = this@Int2IntHashMap.clear()
        }
    }

    override val values: MutableIntCollection by lazy {
        object : MutableIntCollection {
            override val size: Int get() = this@Int2IntHashMap.size
            override fun contains(element: Int): Boolean = containsValue(element)
            override fun add(element: Int): Boolean = throw UnsupportedOperationException()
            override fun remove(element: Int): Boolean = throw UnsupportedOperationException()
            override fun iterator(): MutableIntIterator = ValueIterator()
            override fun clear() = this@Int2IntHashMap.clear()
        }
    }

    override val entries: MutableSet<MutableMap.MutableEntry<Int, Int>> by lazy {
        object : AbstractMutableSet<MutableMap.MutableEntry<Int, Int>>() {
            override val size: Int get() = this@Int2IntHashMap.size
            override fun add(element: MutableMap.MutableEntry<Int, Int>): Boolean =
                throw UnsupportedOperationException()

            override fun iterator(): MutableIterator<MutableMap.MutableEntry<Int, Int>> = EntryIterator()
        }
    }

    override val primitiveEntries: MutableEntrySet by lazy { MutableEntrySet() }

    override fun containsKey(key: Int): Boolean {
        return if (key == 0) {
            containsZero
        } else {
            findSlot(key) >= 0
        }
    }

    override fun containsValue(value: Int): Boolean {
        if (containsZero && zeroValue == value) return true

        if (isHashing()) {
            var slot = 0
            while (slot < keysAndValuesArr.size) {
                if (keysAndValuesArr[slot] != 0 && keysAndValuesArr[slot + 1] == value) return true
                slot += 2
            }
            return false
        } else {
            var valueSlot = arrayUsage - 1
            while (valueSlot >= 1) {
                if (keysAndValuesArr[valueSlot] == value) return true
                valueSlot -= 2
            }
            return false
        }
    }

    override operator fun get(key: Int): Int {
        if (key == 0) {
            return if (containsZero) {
                zeroValue
            } else {
                poisonValue
            }
        } else {
            val slot = findSlot(key)
            return if (slot >= 0) {
                keysAndValuesArr[slot + 1]
            } else {
                poisonValue
            }
        }
    }

    private fun resizeIfNecessary() {
        if (keysAndValuesArr.isEmpty()) {
            assert(threshold > 0)
            growTo(threshold)
        } else if (arrayUsage >= threshold) {
            growTo(threshold)
        }
    }

    private fun growTo(capacity: Int) {
        val newLength = arraySize(capacity, loadFactor)
        if (keysAndValuesArr.size >= newLength) {
            return
        }

        if (newLength <= HASHIFY_THRESHOLD) {
            keysAndValuesArr = keysAndValuesArr.copyOf(newLength)
            threshold = keysAndValuesArr.size
            return
        }

        val oldValues = keysAndValuesArr
        val oldArrayUsage = arrayUsage

        keysAndValuesArr = IntArray(newLength)
        arrayUsage = 0
        threshold = (keysAndValuesArr.size * loadFactor).toInt()

        if (oldValues.size <= HASHIFY_THRESHOLD) {
            var slot = 0
            while (slot < oldArrayUsage) {
                set(oldValues[slot], oldValues[slot + 1])
                slot += 2
            }
        } else {
            // TODO: better algorithm?
            for (slot in 0..<oldValues.size step 2) {
                val key = oldValues[slot]
                if (key != 0) {
                    set(key, oldValues[slot + 1])
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other is Map<*, *>) {
            if (other.size != size) return false

            for (entry in primitiveEntries) {
                val key = entry.key
                val value = entry.value
                if (value != other[key]) return false
            }

            return true
        }

        return false
    }

    override fun hashCode(): Int {
        var result = 0
        for (element in primitiveEntries) {
            result = 31 * result + element.key
            result = 31 * result + element.value
        }
        return result
    }

    override fun toString(): String {
        return primitiveEntries.joinToString(", ", "{", "}") { "${it.key}=${it.value}" }
    }

    // we use a duplicate implementation instead of reusing the entry iterator because we can get a little extra speed
    // out of this (no need to load value + construct entry structure from key/value)
    inner class KeyIterator : MutableIntIterator() {
        private val keysAndValuesArr = this@Int2IntHashMap.keysAndValuesArr
        private val arrayUsage = this@Int2IntHashMap.arrayUsage
        private val mask = keysAndValuesArr.mask()

        private var entriesLeft = size
        private var slot = numSlots()
        private var previousSlot = slot + 1
        private var nextKey = 0

        init {
            if (!containsZero) decrement()
        }

        private fun numSlots() = if (isHashing()) keysAndValuesArr.size else arrayUsage

        override fun hasNext(): Boolean {
            return entriesLeft > 0
        }

        override fun nextInt(): Int {
            if (entriesLeft <= 0) throw NoSuchElementException()
            val k = nextKey
            --entriesLeft
            decrement()
            return k
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
            if (entriesLeft <= 0) return

            do {
                if (slot > 0) {
                    // simple subtraction is a lot faster if we can get away with it (ie 99% of the time)
                    slot -= 2
                } else {
                    slot = (slot - 2) and mask
                }
                nextKey = keysAndValuesArr[slot]
            } while (nextKey == 0)
        }
    }

    inner class ValueIterator : MutableIntIterator() {
        private val it = primitiveEntries.iterator()

        override fun hasNext(): Boolean = it.hasNext()
        override fun nextInt(): Int = it.next().value
        override fun remove() = it.remove()
    }

    inner class EntryIterator : MutableIterator<MutableMap.MutableEntry<Int, Int>> {
        private val it = primitiveEntries.iterator()

        override fun hasNext(): Boolean = it.hasNext()
        override fun remove() = it.remove()

        override fun next(): MutableMap.MutableEntry<Int, Int> {
            val next = it.next()
            return object : AbstractMap.SimpleEntry<Int, Int>(next.key, next.value) {
                override fun setValue(newValue: Int): Int {
                    set(key, newValue)
                    return super.setValue(newValue)
                }
            }
        }
    }

    inner class MutableEntrySet : AbstractMutableSet<Entry>() {
        override val size: Int get() = this@Int2IntHashMap.size
        override fun add(element: Entry): Boolean = throw UnsupportedOperationException()
        override fun iterator(): MutableEntryIterator = object : MutableEntryIterator {
            private val keysAndValuesArr = this@Int2IntHashMap.keysAndValuesArr
            private val arrayUsage = this@Int2IntHashMap.arrayUsage
            private val mask = keysAndValuesArr.mask()

            private var entriesLeft = size
            private var slot = numSlots()
            private var previousSlot = slot + 1
            private var nextEntry: Entry = Entry.of(0, zeroValue)

            init {
                if (!containsZero) decrement()
            }

            private fun numSlots() = if (isHashing()) keysAndValuesArr.size else arrayUsage

            override fun hasNext(): Boolean {
                return entriesLeft > 0
            }

            override fun next(): Entry {
                if (entriesLeft <= 0) throw NoSuchElementException()
                val e = nextEntry
                --entriesLeft
                decrement()
                return e
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
                if (entriesLeft <= 0) return

                var key: Int
                do {
                    if (slot > 0) {
                        // simple subtraction is a lot faster if we can get away with it (ie 99% of the time)
                        slot -= 2
                    } else {
                        slot = (slot - 2) and mask
                    }
                    key = keysAndValuesArr[slot]
                } while (key == 0)

                nextEntry = Entry.of(key, keysAndValuesArr[slot + 1])
            }
        }
    }

    @Suppress("OVERRIDE_BY_INLINE")
    @JvmInline
    value class Entry private constructor(@PublishedApi internal val longValue: Long) : MutableInt2IntEntry,
        MutableMap.MutableEntry<Int, Int> {
        override val key: Int
            inline get() = longValue.ushr(32).toInt()
        override val value: Int
            inline get() = longValue.toInt()

        override fun setValue(newValue: Int): Int = throw UnsupportedOperationException()

        companion object {
            internal fun of(key: Int, value: Int): Entry =
                Entry(key.toLong().shl(32).or(value.toLong().and(0xFFFFFFFF)))
        }
    }

    interface MutableEntryIterator : MutableIterator<Entry> {
        override fun next(): Entry
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isHashing(): Boolean = keysAndValuesArr.size > HASHIFY_THRESHOLD

    @Suppress("NOTHING_TO_INLINE")
    private inline fun mixHash(element: Int): Int {
        val h = element * INT_PHI
        return h xor (h ushr 16)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun IntArray.mask() = size - 1

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Int.slot(): Int = (mixHash(this) and ((keysAndValuesArr.size shr 1) - 1)) shl 1

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Int.slotDistance(slot: Int): Int {
        assert(this != 0)
        val idealSlot = slot()
        return if (idealSlot <= slot) {
            slot - idealSlot
        } else {
            slot + keysAndValuesArr.size - idealSlot
        }
    }

    companion object {

        private val EMPTY_ARRAY = IntArray(0)

        /** 2<sup>32</sup> &middot; &phi;, &phi; = (&#x221A;5 &minus; 1)/2. */
        private const val INT_PHI: Int = -0x61c88647

        private const val DEFAULT_LOAD_FACTOR = .75f
        private const val DEFAULT_INITIAL_CAPACITY = 1 shl 2  // must be power of two
        private const val MAXIMUM_CAPACITY: Int = 1 shl 29 // must be power of two
        private const val HASHIFY_THRESHOLD: Int = 1 shl 6 // must be power of two
        private const val MIN_HASH_CAPACITY = 1 shl 4 // must be power of two

        private const val ARRAY_USAGE_MASK = 0x7FFFFFFF

        private fun arraySize(capacity: Int, loadFactor: Float): Int {
            return if (capacity <= HASHIFY_THRESHOLD) {
                capacity shl 1
            } else {
                max(minPowerOfTwo((capacity / loadFactor).toInt()), MIN_HASH_CAPACITY) shl 1
            }
        }

        private fun minPowerOfTwo(cap: Int): Int {
            val n = -1 ushr Integer.numberOfLeadingZeros(cap - 1)
            return if (n < 0) 1 else if (n >= MAXIMUM_CAPACITY) MAXIMUM_CAPACITY else n + 1
        }
    }
}
