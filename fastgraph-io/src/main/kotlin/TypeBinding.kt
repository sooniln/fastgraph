package io.github.sooniln.fastgraph.io

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.staticTypeOf

@OptIn(ExperimentalStdlibApi::class)
public class TypeBinding<out T>(
    @get:JvmName("getType")
    @get:JvmExposeBoxed
    public val type: StaticType<T>,
    public val defaultValue: T,
    public val parser: (String) -> T,
) {
    public companion object {
        public val unit: TypeBinding<Unit> = TypeBinding(staticTypeOf(), Unit) {}
        public val boolean: TypeBinding<Boolean> = TypeBinding(staticTypeOf(), false, String::toBooleanStrict)
        public val byte: TypeBinding<Byte> = TypeBinding(staticTypeOf(), 0, String::toByte)
        public val short: TypeBinding<Short> = TypeBinding(staticTypeOf(), 0, String::toShort)
        public val int: TypeBinding<Int> = TypeBinding(staticTypeOf(), 0, String::toInt)
        public val long: TypeBinding<Long> = TypeBinding(staticTypeOf(), 0, String::toLong)
        public val float: TypeBinding<Float> = TypeBinding(staticTypeOf(), 0F, String::toFloat)
        public val double: TypeBinding<Double> = TypeBinding(staticTypeOf(), 0.0, String::toDouble)
        public val string: TypeBinding<String?> = TypeBinding(staticTypeOf(), null, String::toString)

        // internal because using "" as the default value is not generalizable for public use - we use it internally
        // only in parsing situations where we know a priori that the default value will never actually be used
        internal val nonNullString: TypeBinding<String> = TypeBinding(staticTypeOf(), "", String::toString)
    }
}

internal class ParsingVertexProperty<V>(graph: Graph, binding: TypeBinding<V>) {
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

internal class ParsingEdgeProperty<E>(graph: Graph, binding: TypeBinding<E>) {
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
