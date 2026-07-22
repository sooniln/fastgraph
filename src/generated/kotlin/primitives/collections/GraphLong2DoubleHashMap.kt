package io.github.sooniln.fastgraph.primitives.collections

import io.github.sooniln.fastcollect.MutableFastIterator
import io.github.sooniln.fastcollect.doubles.InlineDoubleCollection
import io.github.sooniln.fastcollect.longs.Long2DoubleMap
import io.github.sooniln.fastcollect.longs.Long2LongHashMap
import io.github.sooniln.fastcollect.longs.MutableLong2DoubleMap
import io.github.sooniln.fastcollect.longs.MutableLong2LongMap
import io.github.sooniln.fastcollect.longs.MutableLongSet
import kotlin.math.max

@Suppress("OVERRIDE_BY_INLINE")
@JvmInline
internal value class GraphLong2DoubleHashMap private constructor(@PublishedApi internal val map: Long2LongHashMap) :
    MutableLong2DoubleMap {

    constructor(
        capacity: Int = 0,
        /** The default value should be the value that is ideally least likely to occur in the map. */
        defaultValue: Double = Double.NaN
    ) : this(Long2LongHashMap(capacity, defaultValue.toBits()))

    constructor(map: Long2DoubleMap) : this() {
        putAll(map)
    }

    constructor(map: Map<Long, Double>) : this() {
        putAll(map)
    }

    override val size: Int
        inline get() = map.size

    override fun isDefaultValue(value: Double): Boolean = map.isDefaultValue(value.toBits())

    fun ensureCapacity(capacity: Int) {
        map.ensureCapacity(capacity)
    }

    fun trimToSize() {
        map.trimToSize()
    }

    override fun containsKey(key: Long): Boolean = map.containsKey(key)
    override fun containsValue(value: Double): Boolean = map.containsValue(value.toBits())
    override fun get(key: Long): Double = Double.fromBits(map[key])
    override fun getValue(key: Long): Double = Double.fromBits(map.getValue(key))
    override fun getOrDefault(key: Long, defaultValue: Double): Double =
        Double.fromBits(map.getOrDefault(key, defaultValue.toBits()))

    override fun put(key: Long, value: Double): Double = Double.fromBits(map.put(key, value.toBits()))
    fun putIfAbsent(key: Long, value: Double): Double = Double.fromBits(map.putIfAbsent(key, value.toBits()))
    override fun set(key: Long, value: Double) {
        map[key] = value.toBits()
    }

    override fun remove(key: Long): Double = Double.fromBits(map.remove(key))
    override fun clear() {
        map.clear()
    }

    override fun putAll(from: Long2DoubleMap) {
        if (from is GraphLong2DoubleHashMap) {
            map.putAll(from.map)
        } else {
            ensureCapacity(max(size + (from.size / 2), from.size))
            super.putAll(from)
        }
    }

    override fun putAll(from: Map<out Long, Double>) {
        ensureCapacity(max(size + (from.size / 2), from.size))
        super.putAll(from)
    }

    override val keys: MutableLongSet
        inline get() = map.keys
    override val values: InlineDoubleCollection
        inline get() = InlineDoubleCollection(map.values)

    override fun iterator(): EntryIterator = EntryIterator(map.iterator())

    override fun fastForEach(action: (Long, Double) -> Unit) =
        map.fastForEach { key, value -> action(key, Double.fromBits(value)) }

    override fun toString(): String {
        return Iterable { iterator() }.joinToString(", ", "{", "}")
    }

    @JvmInline
    value class EntryIterator(@PublishedApi internal val it: MutableFastIterator<MutableLong2LongMap.MutableEntry>) :
        MutableFastIterator<MutableLong2DoubleMap.MutableEntry> {
        override fun hasNext(): Boolean = it.hasNext()
        override fun next(): Entry = Entry(it.next())
        override fun remove() {
            it.remove()
        }
    }

    @JvmInline
    value class Entry(@PublishedApi internal val entry: MutableLong2LongMap.MutableEntry) :
        MutableLong2DoubleMap.MutableEntry {
        override val key: Long
            inline get() = entry.key
        override var value: Double
            inline get() = Double.fromBits(entry.value)
            inline set(value) {
                entry.value = value.toBits()
            }

        override fun toString(): String = "$key=$value"
    }
}
