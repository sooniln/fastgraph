package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.propertyTypeOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.typeOf

class PropertyTypeTest {

    @Test
    fun reifiedFactoryMatchesJavaConstants() {
        assertThat(propertyTypeOf<Unit>()).isEqualTo(PropertyType.unit)
        assertThat(propertyTypeOf<Boolean>()).isEqualTo(PropertyType.boolean)
        assertThat(propertyTypeOf<Byte>()).isEqualTo(PropertyType.byte)
        assertThat(propertyTypeOf<Short>()).isEqualTo(PropertyType.short)
        assertThat(propertyTypeOf<Int>()).isEqualTo(PropertyType.int)
        assertThat(propertyTypeOf<Long>()).isEqualTo(PropertyType.long)
        assertThat(propertyTypeOf<Float>()).isEqualTo(PropertyType.float)
        assertThat(propertyTypeOf<Double>()).isEqualTo(PropertyType.double)

        assertThat(propertyTypeOf<Int>()).isNotEqualTo(propertyTypeOf<Int?>())
        assertThat(propertyTypeOf<Int>()).isNotEqualTo(propertyTypeOf<Long>())
        assertThat(propertyTypeOf<List<Int>>()).isEqualTo(propertyTypeOf<List<Int>>())
        assertThat(propertyTypeOf<List<Int>>()).isNotEqualTo(propertyTypeOf<List<String>>())
        assertThat(propertyTypeOf<Int>().hashCode()).isEqualTo(PropertyType.int.hashCode())
    }

    @Test
    fun kTypeAndUnit() {
        assertThat(propertyTypeOf<String>().kType).isEqualTo(typeOf<String>())
        assertThat(propertyTypeOf<String?>().kType).isEqualTo(typeOf<String?>())
        assertThat(PropertyType.obj<String>().kType).isNull()

        assertThat(PropertyType.unit.isUnitType()).isTrue
        assertThat(propertyTypeOf<Unit>().isUnitType()).isTrue
        assertThat(propertyTypeOf<Unit?>().isUnitType()).isFalse
        assertThat(propertyTypeOf<Int>().isUnitType()).isFalse
        assertThat(PropertyType.obj<Unit>().isUnitType()).isFalse
    }

    @Test
    fun mayCastToRequiresAnExactClassifier() {
        val int = propertyTypeOf<Int>()
        val nullableInt = propertyTypeOf<Int?>()

        assertThat(int.mayCastTo(int)).isTrue
        assertThat(int.mayCastTo(typeOf<Int>())).isTrue
        // widening to nullable is safe, narrowing to non-null is not
        assertThat(int.mayCastTo(nullableInt)).isTrue
        assertThat(nullableInt.mayCastTo(int)).isFalse
        assertThat(nullableInt.mayCastTo(nullableInt)).isTrue
        // super-types are not accepted, even though the cast would be legal
        assertThat(int.mayCastTo(propertyTypeOf<Number>())).isFalse
        assertThat(int.mayCastTo(propertyTypeOf<Any>())).isFalse
        assertThat(int.mayCastTo(propertyTypeOf<Long>())).isFalse
        // generic arguments are ignored: only the classifier is compared
        assertThat(propertyTypeOf<List<Int>>().mayCastTo(propertyTypeOf<List<String>>())).isTrue
    }

    @Test
    fun untypedPropertyTypeNeverCasts() {
        val untyped = PropertyType.obj<String>()

        assertThat(untyped.mayCastTo(propertyTypeOf<String>())).isFalse
        assertThat(untyped.mayCastTo(propertyTypeOf<String?>())).isFalse
        assertThat(untyped.mayCastTo(untyped)).isFalse
        assertThat(propertyTypeOf<String>().mayCastTo(untyped)).isFalse
        assertThat(untyped.toString()).isEqualTo("null")
        assertThat(PropertyType.obj<Int>()).isEqualTo(PropertyType.obj<String>())
    }

    @Test
    fun constructorRequiresMatchingClassifier() {
        val type = PropertyType(typeOf<String>(), String::class)
        assertThat(type).isEqualTo(propertyTypeOf<String>())
        assertThat(type.toString()).isEqualTo(typeOf<String>().toString())

        assertThrows<IllegalArgumentException> { PropertyType(typeOf<String>(), Int::class) }
    }

    @Test
    fun untypedPropertiesAreUsable() {
        val graph = buildGraph(true) {
            val v0 = addVertex()
            addEdge(v0, v0)
        }
        val v0 = graph.vertices.first()
        val e0 = graph.edges.first()

        val vertexProperty = graph.createVertexProperty(PropertyType.obj<String?>()) { null }
        val edgeProperty = graph.createEdgeProperty(PropertyType.obj<String?>()) { null }
        assertThat(vertexProperty.type).isEqualTo(PropertyType.obj<String?>())
        assertThat(edgeProperty.type).isEqualTo(PropertyType.obj<String?>())
        assertThat(vertexProperty[v0]).isNull()
        assertThat(edgeProperty[e0]).isNull()
        vertexProperty[v0] = "v"
        edgeProperty[e0] = "e"
        assertThat(vertexProperty[v0]).isEqualTo("v")
        assertThat(edgeProperty[e0]).isEqualTo("e")

        val vertexKeys = graph.createVertexKeyProperty(PropertyType.obj<String>())
        val edgeKeys = graph.createEdgeKeyProperty(PropertyType.obj<String>())
        vertexKeys[v0] = "k"
        edgeKeys[e0] = "k"
        assertThat(vertexKeys.getVertex("k")).isEqualTo(v0)
        assertThat(edgeKeys.getEdge("k")).isEqualTo(e0)

        // the immutable copy keeps the same (untyped) type
        val immutable = buildImmutableGraph(true) { addVertex() }
        val immutableProperty = immutable.createVertexProperty(PropertyType.obj<String?>()) { "x" }
        assertThat(immutableProperty.type).isEqualTo(PropertyType.obj<String?>())
        assertThat(immutableProperty[immutable.vertices.first()]).isEqualTo("x")
    }
}
