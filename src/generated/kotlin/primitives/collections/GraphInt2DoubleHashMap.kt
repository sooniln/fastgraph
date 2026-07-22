package io.github.sooniln.fastgraph.primitives.collections

import io.github.sooniln.fastcollect.MutableFastIterator
import io.github.sooniln.fastcollect.doubles.InlineDoubleCollection
import io.github.sooniln.fastcollect.ints.Int2DoubleMap
import io.github.sooniln.fastcollect.ints.Int2LongHashMap
import io.github.sooniln.fastcollect.ints.MutableInt2DoubleMap
import io.github.sooniln.fastcollect.ints.MutableInt2LongMap
import io.github.sooniln.fastcollect.ints.MutableIntSet
import kotlin.math.max

@Suppress("OVERRIDE_BY_INLINE")
@JvmInline
internal value class GraphInt2DoubleHashMap private constructor(@PublishedApi internal val map: Int2LongHashMap) :
    MutableInt2DoubleMap {

    constructor(
        capacity: Int = 0,
        /** The default value should be the value that is ideally least likely to occur in the map. */
        defaultValue: Double = Double.NaN
    ) : this(Int2LongHashMap(capacity, defaultValue.toBits()))

    constructor(map: Int2DoubleMap) : this() {
        putAll(map)
    }

    constructor(map: Map<Int, Double>) : this() {
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

    override fun containsKey(key: Int): Boolean = map.containsKey(key)
    override fun containsValue(value: Double): Boolean = map.containsValue(value.toBits())
    override fun get(key: Int): Double = Double.fromBits(map[key])
    override fun getValue(key: Int): Double = Double.fromBits(map.getValue(key))
    override fun getOrDefault(key: Int, defaultValue: Double): Double =
        Double.fromBits(map.getOrDefault(key, defaultValue.toBits()))

    override fun put(key: Int, value: Double): Double = Double.fromBits(map.put(key, value.toBits()))
    fun putIfAbsent(key: Int, value: Double): Double = Double.fromBits(map.putIfAbsent(key, value.toBits()))
    override fun set(key: Int, value: Double) {
        map[key] = value.toBits()
    }

    override fun remove(key: Int): Double = Double.fromBits(map.remove(key))
    override fun clear() {
        map.clear()
    }

    override fun putAll(from: Int2DoubleMap) {
        if (from is GraphInt2DoubleHashMap) {
            map.putAll(from.map)
        } else {
            ensureCapacity(max(size + (from.size / 2), from.size))
            super.putAll(from)
        }
    }

    override fun putAll(from: Map<out Int, Double>) {
        ensureCapacity(max(size + (from.size / 2), from.size))
        super.putAll(from)
    }

    override val keys: MutableIntSet
        inline get() = map.keys
    override val values: InlineDoubleCollection
        inline get() = InlineDoubleCollection(map.values)

    override fun iterator(): EntryIterator = EntryIterator(map.iterator())

    override fun fastForEach(action: (Int, Double) -> Unit) =
        map.fastForEach { key, value -> action(key, Double.fromBits(value)) }

    override fun toString(): String {
        return Iterable { iterator() }.joinToString(", ", "{", "}")
    }

    @JvmInline
    value class EntryIterator(@PublishedApi internal val it: MutableFastIterator<MutableInt2LongMap.MutableEntry>) :
        MutableFastIterator<MutableInt2DoubleMap.MutableEntry> {
        override fun hasNext(): Boolean = it.hasNext()
        override fun next(): Entry = Entry(it.next())
        override fun remove() {
            it.remove()
        }
    }

    @JvmInline
    value class Entry(@PublishedApi internal val entry: MutableInt2LongMap.MutableEntry) :
        MutableInt2DoubleMap.MutableEntry {
        override val key: Int
            inline get() = entry.key
        override var value: Double
            inline get() = Double.fromBits(entry.value)
            inline set(value) {
                entry.value = value.toBits()
            }

        override fun toString(): String = "$key=$value"
    }
}
