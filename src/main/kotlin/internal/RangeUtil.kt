package io.github.sooniln.fastgraph.internal

internal fun checkElementIndex(index: Int, size: Int) {
    if (index !in 0..<size) {
        throw IndexOutOfBoundsException("index: $index, size: $size")
    }
}

internal fun checkPositionIndex(index: Int, size: Int) {
    if (index !in 0..size) {
        throw IndexOutOfBoundsException("index: $index, size: $size")
    }
}

internal fun checkRangeIndexes(fromIndex: Int, toIndex: Int, size: Int) {
    if (fromIndex < 0 || toIndex > size) {
        throw IndexOutOfBoundsException("fromIndex: $fromIndex, toIndex: $toIndex, size: $size")
    }
    if (fromIndex > toIndex) {
        throw IllegalArgumentException("fromIndex: $fromIndex > toIndex: $toIndex")
    }
}
