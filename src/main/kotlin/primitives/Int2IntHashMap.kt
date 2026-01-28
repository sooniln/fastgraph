package io.github.sooniln.fastgraph.primitives

import io.github.sooniln.fastgraph.primitives.MutableInt2IntMap.MutableInt2IntEntry
import java.util.AbstractMap
import java.util.Arrays
import kotlin.collections.MutableMap.MutableEntry
import kotlin.math.max

/**
 * A replacement for fastutils Int2IntOpenHashMap which:
 *   * uses drastically less memory with < 32 elements
 *   * generally has faster iteration
 *   * is often slightly faster for get/set with < 32 elements
 *   * is slightly slower for get/set with larger numbers of elements, but not by a hugely meaningful amount.
 *
 * Fastutils wastes an enormous amount of memory at low sizes, which becomes an issue for us when we have say 4 million
 * maps of <10 elements each... (such as in many sparse graphs).
 *
 * Implementation uses robin hood hashing with backwards shift deletion.
 */
internal class Int2IntHashMap(
    capacity: Int = DEFAULT_INITIAL_CAPACITY,
    private val loadFactor: Float = DEFAULT_LOAD_FACTOR,
    override val poisonValue: Int = -1
) : MutableInt2IntMap {

    init {
        require(loadFactor > 0 && loadFactor < 1) { "Load factor must be greater than 0 and smaller than 1" }
        require(capacity >= 0) { "The expected number of elements must be nonnegative" }
    }

    private var keysArr = EMPTY_ARRAY
    private var valuesArr = EMPTY_ARRAY

    private var arrayUsage = 0
    private var zeroValue = 0

    // use threshold to store the initial size before we allocate anything, and since threshold cannot be negative, we
    // also use the highest bit to store whether the map contains zero or not
    private var thresholdAndContainsZero = if (capacity == 0) DEFAULT_INITIAL_CAPACITY else capacity

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

    override val size: Int get() = if (containsZero) arrayUsage + 1 else arrayUsage

    override fun isEmpty(): Boolean {
        return size == 0
    }

    fun ensureCapacity(capacity: Int) {
        require(capacity >= 0) { "The expected number of elements must be nonnegative" }
        if (keysArr.isEmpty()) {
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

    override fun putIfAbsent(key: Int, value: Int): Int {
        if (key == 0) {
            if (containsZero) {
                return zeroValue
            } else {
                zeroValue = value
                containsZero = true
                return poisonValue
            }
        } else {
            val slot = findSlot(key)
            if (slot >= 0) {
                return valuesArr[slot]
            } else {
                put(key, value)
                return poisonValue
            }
        }
    }

    private fun putInternalHashing(key: Int, value: Int): Int {
        assert(isHashing())
        assert(key != 0)

        val mask = keysArr.mask()

        var slot = key.slot(mask)
        var newKeySlotDistance = 0
        while (true) {
            var currKey = keysArr[slot]
            when (currKey) {
                key -> {
                    val oldValue = valuesArr[slot]
                    valuesArr[slot] = value
                    return oldValue
                }
                0 -> {
                    keysArr[slot] = key
                    valuesArr[slot] = value
                    ++arrayUsage
                    return poisonValue
                }
                else -> {
                    if (newKeySlotDistance > currKey.slotDistance(slot, mask)) {
                        var currValue = valuesArr[slot]
                        var newKey = key
                        var newValue = value
                        // move all slots right until we hit a zero slot. max slot distance is generally not high
                        // enough for System.arrayCopy() to outperform the manual loop here, especially with the
                        // additional complexity needed for System.arrayCopy().
                        do {
                            keysArr[slot] = newKey
                            valuesArr[slot] = newValue
                            newKey = currKey
                            newValue = currValue

                            slot = slot.nextSlot(mask)
                            currKey = keysArr[slot]
                            currValue = valuesArr[slot]
                        } while (currKey != 0)

                        keysArr[slot] = newKey
                        valuesArr[slot] = newValue
                        ++arrayUsage
                        return poisonValue
                    }
                }
            }

            slot = slot.nextSlot(mask)
            ++newKeySlotDistance
        }
    }

    private fun putInternalArray(key: Int, value: Int): Int {
        assert(!isHashing())
        assert(key != 0)

        var slot = 0
        while (slot < arrayUsage) {
            if (keysArr[slot] == key) {
                val oldValue = valuesArr[slot]
                valuesArr[slot] = value
                return oldValue
            }

            ++slot
        }

        keysArr[slot] = key
        valuesArr[slot] = value
        ++arrayUsage
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
                val oldValue = valuesArr[slot]
                removeSlot(slot)
                return oldValue
            }
        }

        return poisonValue
    }

    override fun clear() {
        Arrays.setAll(keysArr) { 0 }
        containsZero = false
        arrayUsage = 0
    }

    private fun findSlot(key: Int): Int {
        return if (isHashing()) findSlotHashing(key) else findSlotArray(key)
    }

    private fun findSlotHashing(key: Int): Int {
        assert(isHashing())
        assert(key != 0)

        val mask = keysArr.mask()

        var slot = key.slot(mask)
        var currKey = keysArr[slot]
        while (true) {
            // TODO: we could stop looking once the distance < current distance
            when (currKey) {
                0 -> return -1
                key -> return slot
            }

            slot = slot.nextSlot(mask)
            currKey = keysArr[slot]
        }
    }

    private fun findSlotArray(key: Int): Int {
        assert(!isHashing())
        assert(key != 0)

        // iterate backwards under assumption more recently added values are more likely to be queried
        var slot = arrayUsage - 1
        while (slot >= 0) {
            if (keysArr[slot] == key) {
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

        val mask = keysArr.mask()

        // move all slots left until we hit a zero slot. max slot distance is generally not high enough for
        // System.arrayCopy() to outperform the manual loop here, especially with the additional complexity needed
        // for System.arrayCopy().
        var currSlot = slot
        var nextSlot = currSlot.nextSlot(mask)
        var nextKey = keysArr[nextSlot]
        var nextValue = valuesArr[nextSlot]
        while (nextKey != 0 && nextKey.slotDistance(nextSlot, mask) > 0) {
            keysArr[currSlot] = nextKey
            valuesArr[currSlot] = nextValue

            currSlot = nextSlot
            nextSlot = nextSlot.nextSlot(mask)
            nextKey = keysArr[nextSlot]
            nextValue = valuesArr[nextSlot]
        }
        keysArr[currSlot] = 0
        --arrayUsage
    }

    private fun removeSlotArray(slot: Int) {
        assert(!isHashing())
        assert(slot < arrayUsage)

        val lastIndex = arrayUsage - 1
        if (slot < lastIndex) {
            System.arraycopy(keysArr, slot + 1, keysArr, slot, lastIndex - slot)
            System.arraycopy(valuesArr, slot + 1, valuesArr, slot, lastIndex - slot)
        }
        --arrayUsage
    }

    override fun putAll(from: Int2IntMap) {
        if (from is Int2IntHashMap) {
            for (entry in from.primitiveEntries) {
                set(entry.key, entry.value)
            }
        } else {
            for (entry in from.primitiveEntries) {
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

    override val primitiveEntries: MutableEntrySet by lazy {
        object : AbstractMutableSet<Entry>(), MutableEntrySet {
            override val size: Int get() = this@Int2IntHashMap.size
            override fun add(element: Entry): Boolean = throw UnsupportedOperationException()
            override fun iterator(): MutableEntryIterator = EntryIterator()
        }
    }

    override val entries: MutableSet<MutableEntry<Int, Int>> by lazy {
        object : AbstractMutableSet<MutableEntry<Int, Int>>() {
            override val size: Int get() = this@Int2IntHashMap.size
            override fun add(element: MutableEntry<Int, Int>): Boolean = throw UnsupportedOperationException()
            override fun iterator(): MutableIterator<MutableEntry<Int, Int>> = MapEntryIterator()
        }
    }

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
            while (slot < keysArr.size) {
                if (keysArr[slot] != 0 && valuesArr[slot] == value) return true
                ++slot
            }
            return false
        } else {
            var slot = arrayUsage - 1
            while (slot >= 0) {
                if (valuesArr[slot] == value) return true
                --slot
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
                valuesArr[slot]
            } else {
                poisonValue
            }
        }
    }

    override fun getOrDefault(key: Int, defaultValue: Int): Int {
        if (key == 0) {
            return if (containsZero) {
                zeroValue
            } else {
                defaultValue
            }
        } else {
            val slot = findSlot(key)
            return if (slot >= 0) {
                valuesArr[slot]
            } else {
                defaultValue
            }
        }
    }

    private fun resizeIfNecessary() {
        if (keysArr.isEmpty()) {
            assert(threshold > 0)
            growTo(threshold)
        } else if (arrayUsage >= threshold) {
            growTo(threshold shl 1)
        }
    }

    private fun growTo(capacity: Int) {
        val newLength = arraySize(capacity, loadFactor)
        if (keysArr.size >= newLength) {
            return
        }

        if (newLength <= HASHIFY_THRESHOLD) {
            keysArr = keysArr.copyOf(newLength)
            valuesArr = valuesArr.copyOf(newLength)
            threshold = keysArr.size
            return
        }

        val oldKeys = keysArr
        val oldValues = valuesArr
        val oldArrayUsage = arrayUsage

        keysArr = IntArray(newLength)
        valuesArr = IntArray(newLength)
        arrayUsage = 0
        threshold = (keysArr.size * loadFactor).toInt()

        if (oldValues.size <= HASHIFY_THRESHOLD) {
            var slot = 0
            while (slot < oldArrayUsage) {
                set(oldKeys[slot], oldValues[slot])
                ++slot
            }
        } else {
            // TODO: better algorithm?
            for (slot in 0..<oldKeys.size) {
                val key = oldKeys[slot]
                if (key != 0) {
                    set(key, oldValues[slot])
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
        for (entry in primitiveEntries) {
            result += entry.key xor entry.value
        }
        return result
    }

    override fun toString(): String {
        return primitiveEntries.joinToString(", ", "{", "}") { "${it.key}=${it.value}" }
    }

    private inner class KeyIterator : MutableIntIterator() {
        private val keysArr = this@Int2IntHashMap.keysArr
        private val arrayUsage = this@Int2IntHashMap.arrayUsage

        private var entriesLeft = size
        private var slot = numSlots()
        private var previousSlot = -1
        private var nextKey = 0

        init {
            if (entriesLeft > 0 && !containsZero) decrement()
        }

        private fun numSlots() = if (isHashing()) keysArr.size else arrayUsage

        override fun hasNext(): Boolean {
            return entriesLeft > 0
        }

        override fun nextInt(): Int {
            if (entriesLeft-- <= 0) throw NoSuchElementException()
            val k = nextKey
            decrement()
            return k
        }

        override fun remove() {
            check(previousSlot != -1)
            if (previousSlot < numSlots()) {
                removeSlot(previousSlot)
            } else {
                assert(previousSlot == numSlots() && containsZero)
                containsZero = false
            }
            previousSlot = -1
        }

        private fun decrement() {
            previousSlot = slot
            if (entriesLeft <= 0) return

            do {
                if (slot > 0) {
                    // simple subtraction is a lot faster if we can get away with it (ie 99% of the time)
                    --slot
                } else {
                    slot = (slot - 1) and keysArr.mask()
                }
            } while (keysArr[slot] == 0)
            nextKey = keysArr[slot]
        }
    }

    private inner class ValueIterator : MutableIntIterator() {
        private val keysArr = this@Int2IntHashMap.keysArr
        private val valuesArr = this@Int2IntHashMap.valuesArr
        private val arrayUsage = this@Int2IntHashMap.arrayUsage

        private var entriesLeft = size
        private var slot = numSlots()
        private var previousSlot = -1
        private var nextValue = zeroValue

        init {
            if (entriesLeft > 0 && !containsZero) decrement()
        }

        private fun numSlots() = if (isHashing()) keysArr.size else arrayUsage

        override fun hasNext(): Boolean {
            return entriesLeft > 0
        }

        override fun nextInt(): Int {
            if (entriesLeft-- <= 0) throw NoSuchElementException()
            val v = nextValue
            decrement()
            return v
        }

        override fun remove() {
            check(previousSlot != -1)
            if (previousSlot < numSlots()) {
                removeSlot(previousSlot)
            } else {
                assert(previousSlot == numSlots() && containsZero)
                containsZero = false
            }
            previousSlot = -1
        }

        private fun decrement() {
            previousSlot = slot
            if (entriesLeft <= 0) return

            do {
                if (slot > 0) {
                    // simple subtraction is a lot faster if we can get away with it (ie 99% of the time)
                    --slot
                } else {
                    slot = (slot - 1) and keysArr.mask()
                }
            } while (keysArr[slot] == 0)
            nextValue = valuesArr[slot]
        }
    }

    private inner class EntryIterator : MutableEntryIterator {
        private val keysArr = this@Int2IntHashMap.keysArr
        private val valuesArr = this@Int2IntHashMap.valuesArr
        private val arrayUsage = this@Int2IntHashMap.arrayUsage

        private var entriesLeft = size
        private var slot = numSlots()
        private var previousSlot = -1
        private var nextEntry = Entry.of(0, zeroValue)

        init {
            if (entriesLeft > 0 && !containsZero) decrement()
        }

        private fun numSlots() = if (isHashing()) keysArr.size else arrayUsage

        override fun hasNext(): Boolean {
            return entriesLeft > 0
        }

        override fun next(): Entry {
            if (entriesLeft-- <= 0) throw NoSuchElementException()
            val e = nextEntry
            decrement()
            return e
        }

        override fun remove() {
            check(previousSlot != -1)
            if (previousSlot < numSlots()) {
                removeSlot(previousSlot)
            } else {
                assert(previousSlot == numSlots() && containsZero)
                containsZero = false
            }
            previousSlot = -1
        }

        private fun decrement() {
            previousSlot = slot
            if (entriesLeft <= 0) return

            do {
                if (slot > 0) {
                    // simple subtraction is a lot faster if we can get away with it (ie 99% of the time)
                    --slot
                } else {
                    slot = (slot - 1) and keysArr.mask()
                }
            } while (keysArr[slot] == 0)
            nextEntry = Entry.of(keysArr[slot], valuesArr[slot])
        }
    }

    private inner class MapEntryIterator : MutableIterator<MutableEntry<Int, Int>> {
        private val it = primitiveEntries.iterator()

        override fun hasNext(): Boolean = it.hasNext()

        override fun next(): MutableEntry<Int, Int> {
            val next = it.next()
            return object : AbstractMap.SimpleEntry<Int, Int>(next.key, next.value) {
                override fun setValue(newValue: Int): Int {
                    set(key, newValue)
                    return super.setValue(newValue)
                }
            }
        }

        override fun remove() = it.remove()
    }

    @Suppress("OVERRIDE_BY_INLINE")
    @JvmInline
    value class Entry private constructor(
        @PublishedApi internal val longValue: Long
    ) : MutableInt2IntEntry, MutableEntry<Int, Int> {
        override val key: Int
            inline get() = longValue.ushr(32).toInt()
        override val value: Int
            inline get() = longValue.toInt()

        operator fun component1() = key
        operator fun component2() = value

        override fun setValue(newValue: Int): Int = throw UnsupportedOperationException()

        companion object {
            internal fun of(key: Int, value: Int): Entry =
                Entry(key.toLong().shl(32).or(value.toLong().and(0xFFFFFFFF)))
        }
    }

    interface MutableEntrySet : MutableSet<Entry> {
        override fun iterator(): MutableEntryIterator
    }

    interface MutableEntryIterator : MutableIterator<Entry> {
        override fun next(): Entry
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isHashing(): Boolean = keysArr.size > HASHIFY_THRESHOLD

    @Suppress("NOTHING_TO_INLINE")
    private inline fun mixHash(element: Int): Int {
        val h = element * INT_PHI
        return h xor (h ushr 16)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun IntArray.mask() = size - 1

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Int.slot(mask: Int): Int {
        assert(mask == keysArr.mask())
        return mixHash(this) and mask
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Int.nextSlot(mask: Int): Int {
        assert(mask == keysArr.mask())
        return (this + 1) and mask
    }

    private fun Int.slotDistance(slot: Int, mask: Int): Int {
        assert(this != 0)
        val idealSlot = slot(mask)
        return if (idealSlot <= slot) {
            slot - idealSlot
        } else {
            slot + keysArr.size - idealSlot
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
