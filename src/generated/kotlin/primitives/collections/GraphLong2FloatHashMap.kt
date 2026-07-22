package io.github.sooniln.fastgraph.primitives.collections

import io.github.sooniln.fastcollect.MutableFastIterator
import io.github.sooniln.fastcollect.floats.InlineFloatCollection
import io.github.sooniln.fastcollect.longs.Long2FloatMap
import io.github.sooniln.fastcollect.longs.Long2IntHashMap
import io.github.sooniln.fastcollect.longs.MutableLong2FloatMap
import io.github.sooniln.fastcollect.longs.MutableLong2IntMap
import io.github.sooniln.fastcollect.longs.MutableLongSet
import kotlin.math.max

@Suppress("OVERRIDE_BY_INLINE")
@JvmInline
internal value class GraphLong2FloatHashMap private constructor(@PublishedApi internal val map: Long2IntHashMap) :
    MutableLong2FloatMap {

    constructor(
        capacity: Int = 0,
        /** The default value should be the value that is ideally least likely to occur in the map. */
        defaultValue: Float = Float.NaN
    ) : this(Long2IntHashMap(capacity, defaultValue.toBits()))

    constructor(map: Long2FloatMap) : this() {
        putAll(map)
    }

    constructor(map: Map<Long, Float>) : this() {
        putAll(map)
    }

    override val size: Int
        inline get() = map.size

    override fun isDefaultValue(value: Float): Boolean = map.isDefaultValue(value.toBits())

    fun ensureCapacity(capacity: Int) {
        map.ensureCapacity(capacity)
    }

    fun trimToSize() {
        map.trimToSize()
    }

    override fun containsKey(key: Long): Boolean = map.containsKey(key)
    override fun containsValue(value: Float): Boolean = map.containsValue(value.toBits())
    override fun get(key: Long): Float = Float.fromBits(map[key])
    override fun getValue(key: Long): Float = Float.fromBits(map.getValue(key))
    override fun getOrDefault(key: Long, defaultValue: Float): Float =
        Float.fromBits(map.getOrDefault(key, defaultValue.toBits()))

    override fun put(key: Long, value: Float): Float = Float.fromBits(map.put(key, value.toBits()))
    fun putIfAbsent(key: Long, value: Float): Float = Float.fromBits(map.putIfAbsent(key, value.toBits()))
    override fun set(key: Long, value: Float) {
        map[key] = value.toBits()
    }

    override fun remove(key: Long): Float = Float.fromBits(map.remove(key))
    override fun clear() {
        map.clear()
    }

    override fun putAll(from: Long2FloatMap) {
        if (from is GraphLong2FloatHashMap) {
            map.putAll(from.map)
        } else {
            ensureCapacity(max(size + (from.size / 2), from.size))
            super.putAll(from)
        }
    }

    override fun putAll(from: Map<out Long, Float>) {
        ensureCapacity(max(size + (from.size / 2), from.size))
        super.putAll(from)
    }

    override val keys: MutableLongSet
        inline get() = map.keys
    override val values: InlineFloatCollection
        inline get() = InlineFloatCollection(map.values)

    override fun iterator(): EntryIterator = EntryIterator(map.iterator())

    override fun fastForEach(action: (Long, Float) -> Unit) =
        map.fastForEach { key, value -> action(key, Float.fromBits(value)) }

    override fun toString(): String {
        return Iterable { iterator() }.joinToString(", ", "{", "}")
    }

    @JvmInline
    value class EntryIterator(@PublishedApi internal val it: MutableFastIterator<MutableLong2IntMap.MutableEntry>) :
        MutableFastIterator<MutableLong2FloatMap.MutableEntry> {
        override fun hasNext(): Boolean = it.hasNext()
        override fun next(): Entry = Entry(it.next())
        override fun remove() {
            it.remove()
        }
    }

    @JvmInline
    value class Entry(@PublishedApi internal val entry: MutableLong2IntMap.MutableEntry) :
        MutableLong2FloatMap.MutableEntry {
        override val key: Long
            inline get() = entry.key
        override var value: Float
            inline get() = Float.fromBits(entry.value)
            inline set(value) {
                entry.value = value.toBits()
            }

        override fun toString(): String = "$key=$value"
    }
}
