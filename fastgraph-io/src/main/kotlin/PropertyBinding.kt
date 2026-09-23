package io.github.sooniln.fastgraph.io

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.properties.propertyTypeOf

@OptIn(ExperimentalStdlibApi::class)
public class PropertyBinding<out T>(
    @get:JvmName("getType")
    @get:JvmExposeBoxed
    public val type: PropertyType<T>,
    public val defaultValue: T,
    public val parser: (String) -> T,
) {
    public companion object {
        public val unit: PropertyBinding<Unit> = PropertyBinding(propertyTypeOf(), Unit) {}
        public val boolean: PropertyBinding<Boolean> = PropertyBinding(propertyTypeOf(), false, String::toBooleanStrict)
        public val int: PropertyBinding<Int> = PropertyBinding(propertyTypeOf(), 0, String::toInt)
        public val long: PropertyBinding<Long> = PropertyBinding(propertyTypeOf(), 0, String::toLong)
        public val float: PropertyBinding<Float> = PropertyBinding(propertyTypeOf(), 0F, String::toFloat)
        public val double: PropertyBinding<Double> = PropertyBinding(propertyTypeOf(), 0.0, String::toDouble)
        public val string: PropertyBinding<String?> = PropertyBinding(propertyTypeOf(), null, String::toString)

        // internal because using "" as the default value is not generalizable for public use - we use it internally
        // only in parsing situations where we know a priori that the default value will never actually be used
        internal val nonNullString: PropertyBinding<String> = PropertyBinding(propertyTypeOf(), "", String::toString)
    }
}

internal class ParsingVertexKeyProperty<V>(graph: Graph, binding: PropertyBinding<V>) {
    val property: MutableVertexKeyProperty<V>
    private val parser: (String) -> V

    init {
        property = graph.createVertexKeyProperty(binding.type)
        parser = binding.parser
    }

    internal fun parse(input: String): V = parser(input)

    internal operator fun set(vertex: Vertex, value: V) {
        property[vertex] = value
    }

    internal fun parseAndSet(vertex: Vertex, valueString: String) {
        property[vertex] = parser(valueString)
    }
}

internal class ParsingVertexProperty<V>(graph: Graph, binding: PropertyBinding<V>) {
    val property: MutableVertexProperty<V>
    private val parser: (String) -> V

    init {
        val defaultValue = binding.defaultValue
        property = graph.createVertexProperty(binding.type) { defaultValue }
        parser = binding.parser
    }

    internal fun parse(input: String): V = parser(input)

    internal operator fun set(vertex: Vertex, value: V) {
        property[vertex] = value
    }

    internal fun parseAndSet(vertex: Vertex, valueString: String) {
        property[vertex] = parser(valueString)
    }
}

internal class ParsingEdgeProperty<E>(graph: Graph, binding: PropertyBinding<E>) {
    val property: MutableEdgeProperty<E>
    private val parser: (String) -> E

    init {
        val defaultValue = binding.defaultValue
        property = graph.createEdgeProperty(binding.type) { defaultValue }
        parser = binding.parser
    }

    internal fun parse(input: String): E = parser(input)

    internal operator fun set(edge: Edge, value: E) {
        property[edge] = value
    }

    internal fun parseAndSet(edge: Edge, valueString: String) {
        property[edge] = parser(valueString)
    }
}
