package io.github.sooniln.fastgraph.io

import io.github.sooniln.fastgraph.staticTypeOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TypeBindingTest {

    @Test
    fun unitBindingHasUnitDefaultAndParsesToUnit() {
        assertThat(TypeBinding.unit.type).isEqualTo(staticTypeOf<Unit>())
        assertThat(TypeBinding.unit.defaultValue).isEqualTo(Unit)
        assertThat(TypeBinding.unit.parser("anything")).isEqualTo(Unit)
    }

    @Test
    fun booleanBindingHasFalseDefaultAndParsesStrictly() {
        assertThat(TypeBinding.boolean.type).isEqualTo(staticTypeOf<Boolean>())
        assertThat(TypeBinding.boolean.defaultValue).isEqualTo(false)
        assertThat(TypeBinding.boolean.parser("true")).isEqualTo(true)
        assertThat(TypeBinding.boolean.parser("false")).isEqualTo(false)
    }

    @Test
    fun booleanBindingParserThrowsForNonStrictValue() {
        assertThrows<IllegalArgumentException> { TypeBinding.boolean.parser("yes") }
    }

    @Test
    fun byteBindingHasZeroDefaultAndParsesDecimal() {
        assertThat(TypeBinding.byte.type).isEqualTo(staticTypeOf<Byte>())
        assertThat(TypeBinding.byte.defaultValue).isEqualTo(0.toByte())
        assertThat(TypeBinding.byte.parser("5")).isEqualTo(5.toByte())
    }

    @Test
    fun byteBindingParserThrowsForInvalidValue() {
        assertThrows<NumberFormatException> { TypeBinding.byte.parser("not a byte") }
    }

    @Test
    fun shortBindingHasZeroDefaultAndParsesDecimal() {
        assertThat(TypeBinding.short.type).isEqualTo(staticTypeOf<Short>())
        assertThat(TypeBinding.short.defaultValue).isEqualTo(0.toShort())
        assertThat(TypeBinding.short.parser("5")).isEqualTo(5.toShort())
    }

    @Test
    fun shortBindingParserThrowsForInvalidValue() {
        assertThrows<NumberFormatException> { TypeBinding.short.parser("not a short") }
    }

    @Test
    fun intBindingHasZeroDefaultAndParsesDecimal() {
        assertThat(TypeBinding.int.type).isEqualTo(staticTypeOf<Int>())
        assertThat(TypeBinding.int.defaultValue).isEqualTo(0)
        assertThat(TypeBinding.int.parser("5")).isEqualTo(5)
    }

    @Test
    fun intBindingParserThrowsForInvalidValue() {
        assertThrows<NumberFormatException> { TypeBinding.int.parser("not a number") }
    }

    @Test
    fun longBindingHasZeroDefaultAndParsesDecimal() {
        assertThat(TypeBinding.long.type).isEqualTo(staticTypeOf<Long>())
        assertThat(TypeBinding.long.defaultValue).isEqualTo(0L)
        assertThat(TypeBinding.long.parser("5")).isEqualTo(5L)
    }

    @Test
    fun longBindingParserThrowsForInvalidValue() {
        assertThrows<NumberFormatException> { TypeBinding.long.parser("not a number") }
    }

    @Test
    fun floatBindingHasZeroDefaultAndParsesDecimal() {
        assertThat(TypeBinding.float.type).isEqualTo(staticTypeOf<Float>())
        assertThat(TypeBinding.float.defaultValue).isEqualTo(0F)
        assertThat(TypeBinding.float.parser("5.5")).isEqualTo(5.5F)
    }

    @Test
    fun floatBindingParserThrowsForInvalidValue() {
        assertThrows<NumberFormatException> { TypeBinding.float.parser("not a number") }
    }

    @Test
    fun doubleBindingHasZeroDefaultAndParsesDecimal() {
        assertThat(TypeBinding.double.type).isEqualTo(staticTypeOf<Double>())
        assertThat(TypeBinding.double.defaultValue).isEqualTo(0.0)
        assertThat(TypeBinding.double.parser("5.5")).isEqualTo(5.5)
    }

    @Test
    fun doubleBindingParserThrowsForInvalidValue() {
        assertThrows<NumberFormatException> { TypeBinding.double.parser("not a number") }
    }

    @Test
    fun stringBindingHasNullDefaultAndParsesToItself() {
        assertThat(TypeBinding.string.type).isEqualTo(staticTypeOf<String?>())
        assertThat(TypeBinding.string.defaultValue).isNull()
        assertThat(TypeBinding.string.parser("hello")).isEqualTo("hello")
    }
}
