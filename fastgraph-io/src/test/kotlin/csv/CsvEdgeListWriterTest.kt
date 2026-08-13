package io.github.sooniln.fastgraph.io.csv

import io.github.sooniln.fastgraph.buildValueGraph
import io.github.sooniln.fastgraph.createEdgeProperty
import io.github.sooniln.fastgraph.createVertexProperty
import io.github.sooniln.fastgraph.mutableGraph
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class CsvEdgeListWriterTest {

    @Test
    fun basicWrite() {
        val graph = mutableGraph(directed = true)
        val vertexProperty = graph.createVertexProperty<String>("")
        val a = graph.addVertex().also { vertexProperty[it] = "a" }
        val b = graph.addVertex().also { vertexProperty[it] = "b" }
        graph.addEdge(a, b)

        val output = ByteArrayOutputStream()
        writeCsvEdgeList(output, CsvEdgeListGraph(graph, vertexProperty))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo("a,b\n")
    }

    @Test
    fun multipleEdgesAreWrittenInInsertionOrder() {
        val graph = mutableGraph(directed = true)
        val vertexProperty = graph.createVertexProperty<String>("")
        val a = graph.addVertex().also { vertexProperty[it] = "a" }
        val b = graph.addVertex().also { vertexProperty[it] = "b" }
        val c = graph.addVertex().also { vertexProperty[it] = "c" }
        graph.addEdge(a, b)
        graph.addEdge(b, c)

        val output = ByteArrayOutputStream()
        writeCsvEdgeList(output, CsvEdgeListGraph(graph, vertexProperty))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo("a,b\nb,c\n")
    }

    @Test
    fun edgePropertyValuesAreWritten() {
        val graph = mutableGraph(directed = true)
        val vertexProperty = graph.createVertexProperty<String>("")
        val a = graph.addVertex().also { vertexProperty[it] = "a" }
        val b = graph.addVertex().also { vertexProperty[it] = "b" }
        val edge = graph.addEdge(a, b)
        val weight = graph.createEdgeProperty<Int>(0)
        weight[edge] = 42

        val output = ByteArrayOutputStream()
        writeCsvEdgeList(output, CsvEdgeListGraph(graph, vertexProperty, listOf(weight)))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo("a,b,42\n")
    }

    @Test
    fun nullValuesAreWrittenAsEmptyFields() {
        val graph = mutableGraph(directed = true)
        val vertexProperty = graph.createVertexProperty<String>("")
        val a = graph.addVertex().also { vertexProperty[it] = "a" }
        val b = graph.addVertex()
        graph.addEdge(a, b)

        val output = ByteArrayOutputStream()
        writeCsvEdgeList(output, CsvEdgeListGraph(graph, vertexProperty))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo("a,\n")
    }

    @Test
    fun fieldsRequiringQuotingAreQuoted() {
        val graph = mutableGraph(directed = true)
        val vertexProperty = graph.createVertexProperty<String>("")
        val a = graph.addVertex().also { vertexProperty[it] = "a,x" }
        val b = graph.addVertex().also { vertexProperty[it] = "b" }
        graph.addEdge(a, b)

        val output = ByteArrayOutputStream()
        writeCsvEdgeList(output, CsvEdgeListGraph(graph, vertexProperty))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo("\"a,x\",b\n")
    }

    @Test
    fun untypedWriteUsesVertexIds() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val b = graph.addVertex()
        graph.addEdge(a, b)

        val output = ByteArrayOutputStream()
        writeCsvEdgeList(output, CsvEdgeListGraph(graph))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo("${a.id},${b.id}\n")
    }

    @Test
    fun valueGraphOverloadWritesVertexAndEdgeProperty() {
        val valueGraph = buildValueGraph(directed = true, vertexDefaultValue = "", edgeDefaultValue = 0) {
            addEdge("a", "b", 42)
        }

        val output = ByteArrayOutputStream()
        writeCsvEdgeList(output, CsvEdgeListGraph(valueGraph))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo("a,b,42\n")
    }

    @Test
    fun valueGraphWithUnitEdgePropertyOmitsEdgeColumn() {
        val valueGraph = buildValueGraph(directed = true, vertexDefaultValue = "", edgeDefaultValue = Unit) {
            addEdge("a", "b")
        }

        val output = ByteArrayOutputStream()
        writeCsvEdgeList(output, CsvEdgeListGraph(valueGraph))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo("a,b\n")
    }

    @Test
    fun customDelimiterOptionIsRespected() {
        val graph = mutableGraph(directed = true)
        val vertexProperty = graph.createVertexProperty<String>("")
        val a = graph.addVertex().also { vertexProperty[it] = "a" }
        val b = graph.addVertex().also { vertexProperty[it] = "b" }
        graph.addEdge(a, b)

        val output = ByteArrayOutputStream()
        writeCsvEdgeList(output, CsvEdgeListGraph(graph, vertexProperty), CsvOptions(delimiter = '\t'))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo("a\tb\n")
    }

    @Test
    fun pathOverloadWritesFile(@TempDir tempDir: Path) {
        val graph = mutableGraph(directed = true)
        val vertexProperty = graph.createVertexProperty<String>("")
        val a = graph.addVertex().also { vertexProperty[it] = "a" }
        val b = graph.addVertex().also { vertexProperty[it] = "b" }
        graph.addEdge(a, b)

        val path = tempDir.resolve("edges.csv")
        Files.newOutputStream(path).use { writeCsvEdgeList(it, CsvEdgeListGraph(graph, vertexProperty)) }

        assertThat(Files.readString(path)).isEqualTo("a,b\n")
    }

    @Test
    fun roundTripsThroughLoadCsvEdgeList() {
        val graph = mutableGraph(directed = true)
        val vertexProperty = graph.createVertexProperty<String>("")
        val a = graph.addVertex().also { vertexProperty[it] = "a" }
        val b = graph.addVertex().also { vertexProperty[it] = "b" }
        val c = graph.addVertex().also { vertexProperty[it] = "c" }
        graph.addEdge(a, b)
        graph.addEdge(b, c)

        val output = ByteArrayOutputStream()
        writeCsvEdgeList(output, CsvEdgeListGraph(graph, vertexProperty))

        val result = readCsvEdgeList(
            output.toByteArray().inputStream(),
            directed = true,
        )

        assertThat(result.graph.vertices).hasSize(3)
        assertThat(result.graph.edges).hasSize(2)
    }
}
