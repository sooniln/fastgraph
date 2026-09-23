package io.github.sooniln.fastgraph.io

import io.github.sooniln.fastgraph.properties.propertyTypeOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PropertyBindingTest {

    @Test
    fun unitBindingHasUnitDefaultAndParsesToUnit() {
        assertThat(PropertyBinding.unit.type).isEqualTo(propertyTypeOf<Unit>())
        assertThat(PropertyBinding.unit.defaultValue).isEqualTo(Unit)
        assertThat(PropertyBinding.unit.parser("anything")).isEqualTo(Unit)
    }

    @Test
    fun booleanBindingHasFalseDefaultAndParsesStrictly() {
        assertThat(PropertyBinding.boolean.type).isEqualTo(propertyTypeOf<Boolean>())
        assertThat(PropertyBinding.boolean.defaultValue).isEqualTo(false)
        assertThat(PropertyBinding.boolean.parser("true")).isEqualTo(true)
        assertThat(PropertyBinding.boolean.parser("false")).isEqualTo(false)
    }

    @Test
    fun booleanBindingParserThrowsForNonStrictValue() {
        assertThrows<IllegalArgumentException> { PropertyBinding.boolean.parser("yes") }
    }

    @Test
    fun intBindingHasZeroDefaultAndParsesDecimal() {
        assertThat(PropertyBinding.int.type).isEqualTo(propertyTypeOf<Int>())
        assertThat(PropertyBinding.int.defaultValue).isEqualTo(0)
        assertThat(PropertyBinding.int.parser("5")).isEqualTo(5)
    }

    @Test
    fun intBindingParserThrowsForInvalidValue() {
        assertThrows<NumberFormatException> { PropertyBinding.int.parser("not a number") }
    }

    @Test
    fun longBindingHasZeroDefaultAndParsesDecimal() {
        assertThat(PropertyBinding.long.type).isEqualTo(propertyTypeOf<Long>())
        assertThat(PropertyBinding.long.defaultValue).isEqualTo(0L)
        assertThat(PropertyBinding.long.parser("5")).isEqualTo(5L)
    }

    @Test
    fun longBindingParserThrowsForInvalidValue() {
        assertThrows<NumberFormatException> { PropertyBinding.long.parser("not a number") }
    }

    @Test
    fun floatBindingHasZeroDefaultAndParsesDecimal() {
        assertThat(PropertyBinding.float.type).isEqualTo(propertyTypeOf<Float>())
        assertThat(PropertyBinding.float.defaultValue).isEqualTo(0F)
        assertThat(PropertyBinding.float.parser("5.5")).isEqualTo(5.5F)
    }

    @Test
    fun floatBindingParserThrowsForInvalidValue() {
        assertThrows<NumberFormatException> { PropertyBinding.float.parser("not a number") }
    }

    @Test
    fun doubleBindingHasZeroDefaultAndParsesDecimal() {
        assertThat(PropertyBinding.double.type).isEqualTo(propertyTypeOf<Double>())
        assertThat(PropertyBinding.double.defaultValue).isEqualTo(0.0)
        assertThat(PropertyBinding.double.parser("5.5")).isEqualTo(5.5)
    }

    @Test
    fun doubleBindingParserThrowsForInvalidValue() {
        assertThrows<NumberFormatException> { PropertyBinding.double.parser("not a number") }
    }

    @Test
    fun stringBindingHasNullDefaultAndParsesToItself() {
        assertThat(PropertyBinding.string.type).isEqualTo(propertyTypeOf<String?>())
        assertThat(PropertyBinding.string.defaultValue).isNull()
        assertThat(PropertyBinding.string.parser("hello")).isEqualTo("hello")
    }
}
