package io.github.sooniln.fastgraph.io.dot

import io.github.sooniln.fastgraph.createEdgeProperty
import io.github.sooniln.fastgraph.createVertexProperty
import io.github.sooniln.fastgraph.mutableGraph
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class DotWriterTest {

    @Test
    fun basicWrite() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val b = graph.addVertex()
        graph.addEdge(a, b)

        val output = ByteArrayOutputStream()
        writeDot(output, DotGraph(graph))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "digraph G {\n" +
                "  \"${a.id}\";\n" +
                "  \"${b.id}\";\n" +
                "  \"${a.id}\" -> \"${b.id}\";\n" +
                "}\n"
        )
    }

    @Test
    fun undirectedGraphUsesGraphKeywordAndDashDashOperator() {
        val graph = mutableGraph(directed = false)
        val a = graph.addVertex()
        val b = graph.addVertex()
        graph.addEdge(a, b)

        val output = ByteArrayOutputStream()
        writeDot(output, DotGraph(graph))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "graph G {\n" +
                "  \"${a.id}\";\n" +
                "  \"${b.id}\";\n" +
                "  \"${a.id}\" -- \"${b.id}\";\n" +
                "}\n"
        )
    }

    @Test
    fun propertiesAreStringifiedViaToString() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val edge = graph.addEdge(a, b)
        val score = graph.createVertexProperty<Int>(0)
        score[a] = 5
        val label = graph.createEdgeProperty<String?>(null)
        label[edge] = "connects"

        val output = ByteArrayOutputStream()
        writeDot(output, DotGraph(graph, vertexProperties = mapOf("score" to score), edgeProperties = mapOf("label" to label)))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "digraph G {\n" +
                "  \"${a.id}\" [\"score\"=\"5\"];\n" +
                "  \"${b.id}\" [\"score\"=\"0\"];\n" +
                "  \"${a.id}\" -> \"${b.id}\" [\"label\"=\"connects\"];\n" +
                "}\n"
        )
    }

    @Test
    fun nullPropertyValuesOmitAttributeEntirely() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val label = graph.createVertexProperty<String?>(null)

        val output = ByteArrayOutputStream()
        writeDot(output, DotGraph(graph, vertexProperties = mapOf("label" to label)))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "digraph G {\n" +
                "  \"${a.id}\";\n" +
                "}\n"
        )
    }

    @Test
    fun specialCharactersInValuesAreEscapedAndRoundTrip() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val label = graph.createVertexProperty<String?>(null)
        label[a] = "say \"hi\" \\ ok"

        val output = ByteArrayOutputStream()
        writeDot(output, DotGraph(graph, vertexProperties = mapOf("label" to label)))
        val text = output.toString(Charsets.UTF_8)

        assertThat(text).contains("\\\"")
        assertThat(text).contains("\\\\")

        val result = readDot(text.byteInputStream())
        val readBackVertex = result.graph.vertices.single()
        assertThat(result.vertexProperties.getValue("label")[readBackVertex]).isEqualTo("say \"hi\" \\ ok")
    }

    @Test
    fun embeddedNewlineInValueIsWrittenRawAndRoundTrips() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val label = graph.createVertexProperty<String?>(null)
        label[a] = "line1\nline2"

        val output = ByteArrayOutputStream()
        writeDot(output, DotGraph(graph, vertexProperties = mapOf("label" to label)))
        val text = output.toString(Charsets.UTF_8)

        assertThat(text).contains("line1\nline2")
        assertThat(text).doesNotContain("\\n")

        val result = readDot(text.byteInputStream())
        val readBackVertex = result.graph.vertices.single()
        assertThat(result.vertexProperties.getValue("label")[readBackVertex]).isEqualTo("line1\nline2")
    }

    @Test
    fun graphAttributesAreWrittenAsGraphStatement() {
        val graph = mutableGraph(directed = true)
        graph.addVertex()

        val output = ByteArrayOutputStream()
        writeDot(output, DotGraph(graph, graphProperties = mapOf("rankdir" to "LR")))

        assertThat(output.toString(Charsets.UTF_8)).contains("  graph [\"rankdir\"=\"LR\"];\n")
    }

    @Test
    fun graphAttributesRoundTripThroughReadDot() {
        val graph = mutableGraph(directed = true)
        graph.addVertex()

        val output = ByteArrayOutputStream()
        writeDot(output, DotGraph(graph, graphProperties = mapOf("rankdir" to "LR")))

        val result = readDot(output.toByteArray().inputStream())

        assertThat(result.graphProperties).containsEntry("rankdir", "LR")
    }

    @Test
    fun roundTripsThroughReadDot() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val edge = graph.addEdge(a, b)
        val weight = graph.createEdgeProperty<Int>(0)
        weight[edge] = 9

        val output = ByteArrayOutputStream()
        writeDot(output, DotGraph(graph, edgeProperties = mapOf("weight" to weight)))

        val result = readDot(output.toByteArray().inputStream())

        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(1)
        assertThat(result.edgeProperties.getValue("weight")[result.graph.edges.single()]).isEqualTo("9")
    }
}
