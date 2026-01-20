package io.github.sooniln.fastgraph.primitives

import io.github.sooniln.fastgraph.primitives.Int2IntMap.Int2IntEntry

abstract class MutableIntIterator : IntIterator(), MutableIterator<Int>

fun emptyIntIterator(): MutableIntIterator = EmptyIntIterator

private object EmptyIntIterator : MutableIntIterator() {
    override fun hasNext(): Boolean = false
    override fun nextInt(): Int = throw NoSuchElementException()
    override fun remove() = throw IllegalStateException()
}

interface IntCollection : Collection<Int> {
    override fun contains(element: Int): Boolean

    override fun containsAll(elements: Collection<Int>): Boolean {
        for (element in elements) {
            if (!contains(element)) return false
        }
        return true
    }

    fun containsAll(elements: IntCollection): Boolean {
        for (element in elements) {
            if (!contains(element)) return false
        }
        return true
    }

    override fun iterator(): IntIterator
}

interface MutableIntCollection : IntCollection, MutableCollection<Int> {
    override fun isEmpty(): Boolean {
        return size == 0
    }

    override fun add(element: Int): Boolean
    override fun remove(element: Int): Boolean

    override fun addAll(elements: Collection<Int>): Boolean {
        var modified = false
        for (element in elements) {
            if (add(element)) modified = true
        }
        return modified
    }

    fun addAll(elements: IntCollection): Boolean {
        var modified = false
        for (element in elements) {
            if (add(element)) modified = true
        }
        return modified
    }

    override fun removeAll(elements: Collection<Int>): Boolean {
        var modified = false
        for (element in elements) {
            if (remove(element)) modified = true
        }
        return modified
    }

    fun removeAll(elements: IntCollection): Boolean {
        var modified = false
        for (element in elements) {
            if (remove(element)) modified = true
        }
        return modified
    }

    override fun retainAll(elements: Collection<Int>): Boolean {
        var modified = false
        val it = iterator()
        while (it.hasNext()) {
            if (!elements.contains(it.next())) {
                it.remove()
                modified = true
            }
        }
        return modified
    }

    fun retainAll(elements: IntCollection): Boolean {
        var modified = false
        val it = iterator()
        while (it.hasNext()) {
            if (!elements.contains(it.next())) {
                it.remove()
                modified = true
            }
        }
        return modified
    }

    override fun iterator(): MutableIntIterator
}

interface IntSet : Set<Int>, IntCollection {
    override fun containsAll(elements: Collection<Int>): Boolean = super.containsAll(elements)
}

interface MutableIntSet : IntSet, MutableIntCollection, MutableSet<Int> {
    override fun isEmpty(): Boolean = super.isEmpty()
    override fun addAll(elements: Collection<Int>): Boolean = super.addAll(elements)
    override fun removeAll(elements: Collection<Int>): Boolean = super.removeAll(elements)
    override fun retainAll(elements: Collection<Int>): Boolean = super.retainAll(elements)
}

interface Int2IntMap : Map<Int, Int> {
    /**
     * The value returned from various [Int2IntMap] and [MutableInt2IntMap] methods such as [get]/[MutableMap.put] to
     * indicate that no value was present. Normal objects collections would return null from these methods if there is
     * no valid return value. However, like normal collections, there is no way to distinguish between the poison value
     * being returned because nothing is present, and the poison value being returned because the poison value itself
     * was present. If it is necessary to distinguish between these possibilities, methods such as [getOrElse]/
     * [putOrElse] may be used.
     */
    val poisonValue: Int

    override val keys: IntSet
    override val values: IntCollection
    override val entries: Set<Map.Entry<Int, Int>>

    /**
     * [primitiveEntries] is primitively typed which allows for faster iteration, but entries within this field are not
     * guaranteed to support [Map.Entry.equals][Any.equals] correctly. If entry equality is required, use [entries]
     * instead.
     */
    val primitiveEntries: Set<Int2IntEntry>

    override fun containsKey(key: Int): Boolean
    override fun containsValue(value: Int): Boolean

    override operator fun get(key: Int): Int

    /** Like [get] but will throw [NoSuchElementException] if the key is not present. */
    //fun getValue(key: Int): Int

    interface Int2IntEntry : Map.Entry<Int, Int> {
        override val key: Int
        override val value: Int
    }
}

inline fun Int2IntMap.getOrElse(key: Int, defaultValue: () -> Int): Int {
    return if (containsKey(key)) {
        get(key)
    } else {
        defaultValue()
    }
}

interface MutableInt2IntMap : Int2IntMap, MutableMap<Int, Int> {

    override val keys: MutableIntSet
    override val values: MutableIntCollection
    override val entries: MutableSet<MutableMap.MutableEntry<Int, Int>>

    /**
     * [primitiveEntries] is primitively typed which allows for faster iteration, but entries within this field are not
     * guaranteed to support [Map.Entry.equals][Any.equals] or [MutableMap.MutableEntry.setValue]. If entry equality or
     * setting values through the iterator is required, use [entries] instead.
     */
    override val primitiveEntries: MutableSet<out MutableInt2IntEntry>

    operator fun set(key: Int, value: Int)
    override fun put(key: Int, value: Int): Int

    /** Like [put] but will throw [NoSuchElementException] if the key is not present. */
    //fun putValue(key: Int, value: Int): Int

    override fun remove(key: Int): Int

    /** Like [remove] but will throw [NoSuchElementException] if the key is not present. */
    //fun removeKey(key: Int): Int

    override fun putAll(from: Map<out Int, Int>) {
        for (entry in from.entries) {
            set(entry.key, entry.value)
        }
    }

    fun putAll(from: Int2IntMap) {
        for (entry in from.entries) {
            set(entry.key, entry.value)
        }
    }

    interface MutableInt2IntEntry : Int2IntEntry, MutableMap.MutableEntry<Int, Int> {
        override fun setValue(newValue: Int): Int
    }
}

inline fun MutableInt2IntMap.putOrElse(key: Int, value: Int, defaultValue: () -> Int): Int {
    return if (containsKey(key)) {
        put(key, value)
    } else {
        defaultValue()
    }
}

inline fun MutableInt2IntMap.removeOrElse(key: Int, defaultValue: () -> Int): Int {
    return if (containsKey(key)) {
        remove(key)
    } else {
        defaultValue()
    }
}
