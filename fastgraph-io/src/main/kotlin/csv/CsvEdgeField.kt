package io.github.sooniln.fastgraph.io.csv

import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.staticTypeOf
import kotlin.reflect.typeOf

/**
 * Convenience function that will return one of the default types ([CsvEdgeField.unit], [CsvEdgeField.byte], etc...)
 * matching the given type. If there is no corresponding type, throws an exception (you should be creating your own
 * custom [CsvEdgeField] in this case).
 */
public inline fun <reified E> defaultCsvEdgeField(): CsvEdgeField<E> {
    @Suppress("UNCHECKED_CAST")
    return when (typeOf<E>()) {
        typeOf<Unit>() -> CsvEdgeField.unit
        typeOf<Byte>() -> CsvEdgeField.byte
        typeOf<Short>() -> CsvEdgeField.short
        typeOf<Int>() -> CsvEdgeField.int
        typeOf<Long>() -> CsvEdgeField.long
        typeOf<Float>() -> CsvEdgeField.float
        typeOf<Double>() -> CsvEdgeField.double
        typeOf<String?>() -> CsvEdgeField.string
        else -> throw IllegalArgumentException("Unsupported default type ${typeOf<E>()}, create a custom CsvEdgeField instead")
    } as CsvEdgeField<E>
}

/**
 * Representation of the edge column within a CSV file, including type information, the defaultValueFunction that can
 * be used to create an [io.github.sooniln.fastgraph.EdgeProperty], and parsing information.
 */
@OptIn(ExperimentalStdlibApi::class)
public class CsvEdgeField<E>(
    @get:JvmName("getType")
    @get:JvmExposeBoxed
    public val type: StaticType<E>,
    public val defaultValueFunction: EdgeFunction<E>,
    public val parser: (String) -> E,
) {
    public companion object {
        public val unit: CsvEdgeField<Unit> = CsvEdgeField(staticTypeOf(), {}) {}
        public val byte: CsvEdgeField<Byte> = CsvEdgeField(staticTypeOf(), {0}, String::toByte)
        public val short: CsvEdgeField<Short> = CsvEdgeField(staticTypeOf(), {0}, String::toShort)
        public val int: CsvEdgeField<Int> = CsvEdgeField(staticTypeOf(), {0}, String::toInt)
        public val long: CsvEdgeField<Long> = CsvEdgeField(staticTypeOf(), {0}, String::toLong)
        public val float: CsvEdgeField<Float> = CsvEdgeField(staticTypeOf(), {0F}, String::toFloat)
        public val double: CsvEdgeField<Double> = CsvEdgeField(staticTypeOf(), {0.0}, String::toDouble)
        public val string: CsvEdgeField<String?> = CsvEdgeField(staticTypeOf(), {null}) {it}
    }
}
