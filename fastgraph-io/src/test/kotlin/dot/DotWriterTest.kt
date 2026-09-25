package io.github.sooniln.fastgraph.io.dot

import io.github.sooniln.fastgraph.mutableGraph
import io.github.sooniln.fastgraph.edgeSetOf
import io.github.sooniln.fastgraph.filtered.filter
import io.github.sooniln.fastgraph.vertexSetOf
import io.github.sooniln.fastgraph.properties.safeCast
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
            "strict digraph G {\n" +
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
            "strict graph G {\n" +
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
            "strict digraph G {\n" +
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
            "strict digraph G {\n" +
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

    @Test
    fun parallelEdgesAndSelfLoopsAreWrittenAsNonStrictGraphAndRoundTrip() {
        val graph = mutableGraph(directed = true, multiEdge = true)
        val a = graph.addVertex()
        val b = graph.addVertex()
        graph.addEdge(a, b)
        graph.addEdge(a, b)
        graph.addEdge(b, b)

        val output = ByteArrayOutputStream()
        writeDot(output, DotGraph(graph))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "digraph G {\n" +
                "  \"${a.id}\";\n" +
                "  \"${b.id}\";\n" +
                "  \"${a.id}\" -> \"${b.id}\";\n" +
                "  \"${a.id}\" -> \"${b.id}\";\n" +
                "  \"${b.id}\" -> \"${b.id}\";\n" +
                "}\n"
        )

        val result = readDot(output.toByteArray().inputStream(), multiEdge = true)
        assertThat(result.graph.multiEdge).isTrue
        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(3)
        val ra = result.vertexIdProperty.safeCast<String>().getVertex(a.id.toString())
        val rb = result.vertexIdProperty.safeCast<String>().getVertex(b.id.toString())
        assertThat(result.graph.edges(ra, rb)).hasSize(2)
        assertThat(result.graph.edges(rb, rb)).hasSize(1)
    }

    @Test
    fun immutableAndFilteredSourceGraphsAreWritten() {
        val graph = mutableGraph(directed = false)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val c = graph.addVertex()
        val ab = graph.addEdge(a, b)
        graph.addEdge(b, c)

        val mutableOutput = ByteArrayOutputStream()
        writeDot(mutableOutput, DotGraph(graph))

        // an immutable copy has the same ids, so it writes the same statements (iteration order may differ)
        val immutableOutput = ByteArrayOutputStream()
        writeDot(immutableOutput, DotGraph(graph.toImmutableGraph().target))
        assertThat(immutableOutput.toString(Charsets.UTF_8).lines())
            .containsExactlyInAnyOrderElementsOf(mutableOutput.toString(Charsets.UTF_8).lines())

        // a filtered view writes only its own vertices and edges
        val filteredOutput = ByteArrayOutputStream()
        writeDot(filteredOutput, DotGraph(graph.filter(vertexSetOf(a, b), edgeSetOf(ab))))
        assertThat(filteredOutput.toString(Charsets.UTF_8).lines()).containsExactlyInAnyOrder(
            "strict graph G {",
            "  \"${a.id}\";",
            "  \"${b.id}\";",
            "  \"${a.id}\" -- \"${b.id}\";",
            "}",
            "",
        )
    }

    @Test
    fun vertexAndEdgePropertiesAreWrittenSymmetrically() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val edge = graph.addEdge(a, b)
        val vertexLabel = graph.createVertexProperty<String?>(null)
        val edgeLabel = graph.createEdgeProperty<String?>(null)
        val vertexWeight = graph.createVertexProperty<Int>(1)
        val edgeWeight = graph.createEdgeProperty<Int>(2)
        vertexLabel[a] = "start"
        edgeLabel[edge] = "link"

        val output = ByteArrayOutputStream()
        writeDot(
            output,
            DotGraph(
                graph,
                vertexProperties = mapOf("label" to vertexLabel, "weight" to vertexWeight),
                edgeProperties = mapOf("label" to edgeLabel, "weight" to edgeWeight),
            ),
        )

        // null values omit the attribute on both vertices and edges; other values are stringified
        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "strict digraph G {\n" +
                "  \"${a.id}\" [\"label\"=\"start\", \"weight\"=\"1\"];\n" +
                "  \"${b.id}\" [\"weight\"=\"1\"];\n" +
                "  \"${a.id}\" -> \"${b.id}\" [\"label\"=\"link\", \"weight\"=\"2\"];\n" +
                "}\n"
        )

        val result = readDot(output.toByteArray().inputStream())
        val ra = result.vertexIdProperty.safeCast<String>().getVertex(a.id.toString())
        val rb = result.vertexIdProperty.safeCast<String>().getVertex(b.id.toString())
        assertThat(result.vertexProperties.getValue("label")[ra]).isEqualTo("start")
        assertThat(result.vertexProperties.getValue("label")[rb]).isNull()
        assertThat(result.vertexProperties.getValue("weight")[rb]).isEqualTo("1")
        assertThat(result.edgeProperties.getValue("label")[result.graph.edges.single()]).isEqualTo("link")
        assertThat(result.edgeProperties.getValue("weight")[result.graph.edges.single()]).isEqualTo("2")
    }
}
