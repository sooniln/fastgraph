@file:Suppress("UnusedImport")

package io.github.sooniln.fastgraph.primitives.collections

import io.github.sooniln.fastcollect.MutableFastIterator
import io.github.sooniln.fastcollect.longs.Long2AnyMap
import io.github.sooniln.fastcollect.longs.MutableLong2AnyMap
import io.github.sooniln.fastcollect.longs.MutableLongIterator
import io.github.sooniln.fastcollect.longs.MutableLongSet
import io.github.sooniln.fastgraph.primitives.ArrayUtils
import kotlin.math.max
import kotlin.math.min

internal class GraphLong2AnyHashMap<V> @JvmOverloads constructor(
    capacity: Int = 0,

    ) : MutableLong2AnyMap<V> {

    constructor(map: Long2AnyMap<V>) : this() {
        putAll(map)
    }

    constructor(map: Map<Long, V>) : this() {
        putAll(map)
    }

    private var keysArr = EMPTY_KEY_ARRAY

    @Suppress("UNCHECKED_CAST")
    private var valuesArr = EMPTY_VALUE_ARRAY as Array<V?>

    private inline val defaultValue: V? get() = null


    // threshold + size == capacity (rehash once threshold <= 0, if we haven't allocated yet then threshold.inv() is
    // our initial capacity)
    private var threshold = MIN_INITIAL_CAPACITY.inv()

    override var size: Int = 0
        private set

    init {
        ensureCapacity(capacity)
    }

    override fun isDefaultValue(value: V?): Boolean = value == defaultValue

    /**
     * Ensures that the map can hold at least given number of key/value pairs without any further resizing of the
     * backing array.
     */
    fun ensureCapacity(capacity: Int) {
        require(capacity >= 0) { "Capacity must be >= 0" }
        if (keysArr === EMPTY_KEY_ARRAY) {
            threshold = min(threshold, capacity.inv())
        } else if (capacity > threshold + size) {
            rehash(capacity)
        }
    }

    /**
     * Reduces the size of the backing array to the minimum required to hold the current number of elements.
     */
    fun trimToSize() {
        rehash(size)
    }

    override fun containsKey(key: Long): Boolean = findSlot(key, { true }, { false })

    override fun containsValue(value: V): Boolean {
        val keysArr = keysArr
        val valuesArr = valuesArr
        for (slot in keysArr.indices) {
            if (valuesArr[slot] == value && keysArr[slot] != EMPTY_KEY) return true
        }
        return false
    }

    override fun get(key: Long): V? = findSlot(key, { slot -> valuesArr[slot] }, { defaultValue })

    @Suppress("UNCHECKED_CAST", "USELESS_CAST")
    override fun getValue(key: Long): V =
        findSlot(key, { slot -> valuesArr[slot] as V }, { throw NoSuchElementException() })

    @Suppress("UNCHECKED_CAST", "USELESS_CAST")
    override fun getOrDefault(key: Long, defaultValue: V): V =
        findSlot(key, { slot -> valuesArr[slot] as V }, { defaultValue })

    override fun put(key: Long, value: V): V? {
        var returnValue = defaultValue
        set(key, {
            value
        }, { slot ->
            returnValue = valuesArr[slot]
            value
        })
        return returnValue
    }

    fun putIfAbsent(key: Long, value: V): V? {
        set(key, { value }, { slot -> return valuesArr[slot] })
        return defaultValue
    }

    override fun set(key: Long, value: V) {
        set(key, { value }, { value })
    }

    override fun remove(key: Long): V? {
        return findSlot(
            key,
            { slot ->
                val oldValue = valuesArr[slot]
                removeSlot(slot)
                oldValue
            },
            { defaultValue })
    }

    override fun clear() {
        if (keysArr !== EMPTY_KEY_ARRAY) {
            keysArr.fill(EMPTY_KEY)
            threshold += size

            valuesArr.fill(null)

        }
        size = 0
    }

    private inline fun <T> findSlot(key: Long, onFind: (slot: Int) -> T, onFail: () -> T): T {
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

    private inline fun set(key: Long, onAdd: () -> V, onReplace: (slot: Int) -> V) {
        require(key != EMPTY_KEY)

        if (threshold <= 0) increaseCapacity()

        val keysArr = keysArr
        val mask = keysArr.size - 1

        var slot = key.slot(mask)
        var currKey = keysArr[slot]
        while (currKey != EMPTY_KEY) {
            if (currKey == key) {
                valuesArr[slot] = onReplace(slot)
                return
            }

            slot = (slot + 1) and mask
            currKey = keysArr[slot]
        }


        keysArr[slot] = key
        valuesArr[slot] = onAdd()
        threshold -= 1
        size += 1
    }

    private fun removeSlot(slot: Int) {
        val keysArr = keysArr
        val valuesArr = valuesArr
        val mask = keysArr.size - 1

        var currSlot = slot
        var nextSlot = (currSlot + 1) and mask
        var nextKey = keysArr[nextSlot]
        while (nextKey != EMPTY_KEY && nextKey.slotDistance(nextSlot, mask) > 0) {
            keysArr[currSlot] = nextKey
            valuesArr[currSlot] = valuesArr[nextSlot]

            currSlot = nextSlot
            nextSlot = (nextSlot + 1) and mask
            nextKey = keysArr[nextSlot]
        }
        keysArr[currSlot] = EMPTY_KEY

        valuesArr[currSlot] = null

        threshold += 1
        size -= 1
    }

    override fun putAll(from: Long2AnyMap<V>) {
        if (from is GraphLong2AnyHashMap && from.size / 2 > size) {
            val oldKeysArr = keysArr
            val oldValuesArr = valuesArr

            resetTo(from)
            for (slot in oldKeysArr.indices) {
                val key = oldKeysArr[slot]
                if (key != EMPTY_KEY) {
                    @Suppress("UNCHECKED_CAST", "USELESS_CAST")
                    putIfAbsent(key, oldValuesArr[slot] as V)
                }
            }
            trimToSize()
        } else {
            ensureCapacity(max(size + (from.size / 2), from.size))
            for ((key, value) in from) {
                set(key, value)
            }
        }
    }

    override fun putAll(from: Map<out Long, V>) {
        ensureCapacity(max(size + (from.size / 2), from.size))
        for ((key, value) in from) {
            set(key, value)
        }
    }

    private fun resetTo(from: GraphLong2AnyHashMap<V>) {
        check(!from.isEmpty())

        keysArr = from.keysArr.copyOf()
        valuesArr = from.valuesArr.copyOf()
        size = from.size
        threshold = from.threshold
    }

    private var _keys: MutableLongSet? = null
    override val keys: MutableLongSet
        get() {
            return _keys ?: object : MutableLongSet {
                override val size: Int get() = this@GraphLong2AnyHashMap.size
                override fun contains(element: Long): Boolean = containsKey(element)
                override fun add(element: Long): Boolean = throw UnsupportedOperationException()
                override fun remove(element: Long): Boolean = throw UnsupportedOperationException()
                override fun iterator(): MutableLongIterator = KeyIterator()
                override fun fastForEach(action: (Long) -> Unit) = fastForEachKey { key -> action(key) }
                override fun clear() = throw UnsupportedOperationException()
            }
                .also { _keys = it }
        }

    private var _values: MutableCollection<V>? = null
    override val values: MutableCollection<V>
        get() {
            return _values ?: object : AbstractMutableCollection<V>() {

                override val size: Int get() = this@GraphLong2AnyHashMap.size
                override fun contains(element: V): Boolean = containsValue(element)
                override fun add(element: V): Boolean = throw UnsupportedOperationException()
                override fun remove(element: V): Boolean = throw UnsupportedOperationException()
                override fun iterator(): MutableIterator<V> = ValueIterator()

                override fun clear() = throw UnsupportedOperationException()
            }
                .also { _values = it }
        }

    private fun increaseCapacity() {
        check(threshold <= 0)
        if (threshold < 0) {
            rehash(threshold.inv())
        } else {
            rehash(size shl 1)
        }
    }

    @Suppress("UNCHECKED_CAST", "USELESS_CAST")
    private fun rehash(capacity: Int) {
        check(capacity >= size)

        if (capacity == 0 && keysArr !== EMPTY_KEY_ARRAY) {
            keysArr = EMPTY_KEY_ARRAY

            valuesArr = EMPTY_VALUE_ARRAY as Array<V?>

            threshold = MIN_INITIAL_CAPACITY.inv()
            return
        }

        // for small capacities we force loadFactor to 1.0 to save memory (small array scans are likely to be fast)
        val actualLoadFactor = if (capacity <= FORCE_LOAD_FACTOR_MAX) 1.0 else .75

        val newLength = arraySize(capacity, actualLoadFactor)
        if (keysArr.size == newLength) return

        val newKeysArr = LongArray(newLength)
        newKeysArr.fill(EMPTY_KEY)

        val newValuesArr = arrayOfNulls<Any>(newLength) as Array<V?>

        val newMask = newLength - 1

        for (slot in keysArr.indices) {
            val key = keysArr[slot]
            if (key != EMPTY_KEY) setRehashing(newKeysArr, newValuesArr, newMask, key, valuesArr[slot])
        }

        keysArr = newKeysArr
        valuesArr = newValuesArr

        // threshold must always maintain the invariant of at least 1 slot being open
        threshold = min((newLength * actualLoadFactor).toInt(), newMask) - size
    }

    // we can assume key doesn't exist in array

    private fun setRehashing(keysArr: LongArray, valuesArr: Array<V?>, mask: Int, key: Long, value: V?) {

        var slot = key.slot(mask)
        var currKey = keysArr[slot]
        while (currKey != EMPTY_KEY) {
            slot = (slot + 1) and mask
            currKey = keysArr[slot]
        }

        keysArr[slot] = key
        valuesArr[slot] = value
    }

    override operator fun iterator(): MutableFastIterator<MutableLong2AnyMap.MutableEntry<V>> = FastEntryIterator()

    override fun fastForEach(action: (Long, V) -> Unit) {
        val keysArr = keysArr
        val valuesArr = valuesArr

        for (slot in keysArr.indices) {
            val key = keysArr[slot]
            if (key != EMPTY_KEY) {
                @Suppress("UNCHECKED_CAST", "USELESS_CAST")
                action(key, valuesArr[slot] as V)
            }
        }
    }

    override fun fastForEachKey(action: (Long) -> Unit) {
        val keysArr = keysArr

        for (slot in keysArr.indices) {
            val key = keysArr[slot]
            if (key != EMPTY_KEY) {
                action(key)
            }
        }
    }

    private open inner class SlotIterator {
        private val keysArr = this@GraphLong2AnyHashMap.keysArr
        private val valuesArr = this@GraphLong2AnyHashMap.valuesArr
        private val mask = keysArr.size - 1

        private var slotsLeft = size
        private var slot = keysArr.size - 1
        private var previousSlot = -1

        init {
            if (slotsLeft > 0) decrement()
        }

        fun hasNext(): Boolean {
            return slotsLeft > 0
        }

        fun nextSlot() {
            if (slotsLeft <= 0) throw NoSuchElementException()
            previousSlot = slot
            if (--slotsLeft > 0) decrement()
        }

        fun key(): Long = keysArr[previousSlot]

        @Suppress("UNCHECKED_CAST", "USELESS_CAST")
        fun value(): V = valuesArr[previousSlot] as V

        fun updateValue(newValue: V) {
            check(previousSlot != -1)
            if (keysArr !== this@GraphLong2AnyHashMap.keysArr) throw ConcurrentModificationException()

            valuesArr[previousSlot] = newValue
        }

        fun remove() {
            check(previousSlot != -1)
            if (keysArr !== this@GraphLong2AnyHashMap.keysArr) throw ConcurrentModificationException()

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

    private inner class KeyIterator : MutableLongIterator() {
        private val it = SlotIterator()

        override fun hasNext(): Boolean = it.hasNext()

        override fun nextLong(): Long {
            it.nextSlot()
            return it.key()
        }

        override fun remove() = it.remove()
    }


    private inner class ValueIterator : MutableIterator<V> {

        private val it = SlotIterator()

        override fun hasNext(): Boolean = it.hasNext()


        override fun next(): V {

            it.nextSlot()
            return it.value()
        }

        override fun remove() = it.remove()
    }

    private inner class FastEntryIterator : SlotIterator(), MutableFastIterator<MutableLong2AnyMap.MutableEntry<V>>,
        MutableLong2AnyMap.MutableEntry<V> {

        override val key: Long get() = key()
        override var value: V
            get() = value()
            set(value) {
                updateValue(value)
            }

        override fun next(): MutableLong2AnyMap.MutableEntry<V> {
            nextSlot()
            return this
        }

        override fun equals(other: Any?): Boolean = other is Map.Entry<*, *> && other.key == key && other.value == value
        override fun hashCode(): Int = key.hashCode() xor value.hashCode()
        override fun toString(): String = "$key=$value"
    }

    private fun Long.slot(mask: Int): Int = this and mask
    private fun Long.slotDistance(slot: Int, mask: Int): Int = (slot - this) and mask

    private companion object {
        // the value of a field in an uninitialized primitive array
        @Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")
        private const val EMPTY_KEY = -1.toLong()

        private val EMPTY_KEY_ARRAY = longArrayOf(EMPTY_KEY)

        private val EMPTY_VALUE_ARRAY = arrayOfNulls<Any?>(1)


        private const val MIN_INITIAL_CAPACITY = 3

        private const val CACHE_LINE_SIZE = 64 / Long.SIZE_BYTES

        // we force the load factor to 1.0 up to the size of two cache lines
        private const val FORCE_LOAD_FACTOR_MAX = 2 * CACHE_LINE_SIZE

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
