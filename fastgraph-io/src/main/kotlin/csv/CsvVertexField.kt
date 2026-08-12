package io.github.sooniln.fastgraph.io.csv

import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.staticTypeOf
import kotlin.reflect.typeOf

/**
 * Convenience function that will return one of the default types ([CsvEdgeField.unit], [CsvEdgeField.byte], etc...)
 * matching the given type. If there is no corresponding type, throws an exception (you should be creating your own
 * custom [CsvEdgeField] in this case).
 */
public inline fun <reified V> defaultCsvVertexField(): CsvVertexField<V> {
    @Suppress("UNCHECKED_CAST")
    return when (typeOf<V>()) {
        typeOf<Unit>() -> CsvVertexField.unit
        typeOf<Byte>() -> CsvVertexField.byte
        typeOf<Short>() -> CsvVertexField.short
        typeOf<Int>() -> CsvVertexField.int
        typeOf<Long>() -> CsvVertexField.long
        typeOf<Float>() -> CsvVertexField.float
        typeOf<Double>() -> CsvVertexField.double
        typeOf<String?>() -> CsvVertexField.string
        else -> throw IllegalArgumentException("Unsupported default type ${typeOf<V>()}, create a custom CsvVertexField instead")
    } as CsvVertexField<V>
}

/**
 * Representation of the vertex column within a CSV file, including type information, the defaultValueFunction that can
 * be used to create a [io.github.sooniln.fastgraph.VertexProperty], and parsing information.
 */
@OptIn(ExperimentalStdlibApi::class)
public class CsvVertexField<V>(
    @get:JvmName("getType")
    @get:JvmExposeBoxed
    public val type: StaticType<V>,
    public val defaultValueFunction: VertexFunction<V>,
    public val parser: (String) -> V,
) {
    public companion object {
        public val unit: CsvVertexField<Unit> = CsvVertexField(staticTypeOf(), {}) {}
        public val byte: CsvVertexField<Byte> = CsvVertexField(staticTypeOf(), {0}, String::toByte)
        public val short: CsvVertexField<Short> = CsvVertexField(staticTypeOf(), {0}, String::toShort)
        public val int: CsvVertexField<Int> = CsvVertexField(staticTypeOf(), {0}, String::toInt)
        public val long: CsvVertexField<Long> = CsvVertexField(staticTypeOf(), {0}, String::toLong)
        public val float: CsvVertexField<Float> = CsvVertexField(staticTypeOf(), {0F}, String::toFloat)
        public val double: CsvVertexField<Double> = CsvVertexField(staticTypeOf(), {0.0}, String::toDouble)
        public val string: CsvVertexField<String?> = CsvVertexField(staticTypeOf(), {null}) {it}
    }
}
