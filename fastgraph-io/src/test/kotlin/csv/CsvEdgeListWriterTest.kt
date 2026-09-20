package io.github.sooniln.fastgraph.io.csv

import io.github.sooniln.fastgraph.buildValueGraph
import io.github.sooniln.fastgraph.createEdgeProperty
import io.github.sooniln.fastgraph.createVertexKeyProperty
import io.github.sooniln.fastgraph.mutableGraph
import io.github.sooniln.fastgraph.copyInto
import io.github.sooniln.fastgraph.edgeSetOf
import io.github.sooniln.fastgraph.filter
import io.github.sooniln.fastgraph.toImmutableGraph
import io.github.sooniln.fastgraph.vertexSetOf
import io.github.sooniln.fastgraph.safeCast
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
        val vertexProperty = graph.createVertexKeyProperty<String>()
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
        val vertexProperty = graph.createVertexKeyProperty<String>()
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
        val vertexProperty = graph.createVertexKeyProperty<String>()
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
    fun fieldsRequiringQuotingAreQuoted() {
        val graph = mutableGraph(directed = true)
        val vertexProperty = graph.createVertexKeyProperty<String>()
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
    fun customDelimiterOptionIsRespected() {
        val graph = mutableGraph(directed = true)
        val vertexProperty = graph.createVertexKeyProperty<String>()
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
        val vertexProperty = graph.createVertexKeyProperty<String>()
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
        val vertexProperty = graph.createVertexKeyProperty<String>()
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

    @Test
    fun parallelEdgesAndSelfLoopsRoundTrip() {
        val graph = mutableGraph(directed = true, multiEdge = true)
        val vertexProperty = graph.createVertexKeyProperty<String>()
        val a = graph.addVertex().also { vertexProperty[it] = "a" }
        val b = graph.addVertex().also { vertexProperty[it] = "b" }
        graph.addEdge(a, b)
        graph.addEdge(a, b)
        graph.addEdge(b, b)

        val output = ByteArrayOutputStream()
        writeCsvEdgeList(output, CsvEdgeListGraph(graph, vertexProperty))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo("a,b\na,b\nb,b\n")

        val result = readCsvEdgeList(output.toByteArray().inputStream(), directed = true, multiEdge = true)
        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(3)
        val ra = result.vertexProperty.safeCast<String>().getVertex("a")
        val rb = result.vertexProperty.safeCast<String>().getVertex("b")
        assertThat(result.graph.edges(ra, rb)).hasSize(2)
        assertThat(result.graph.edges(rb, rb)).hasSize(1)
    }

    @Test
    fun immutableAndFilteredSourceGraphsAreWritten() {
        val graph = mutableGraph(directed = false)
        val vertexProperty = graph.createVertexKeyProperty<String>()
        val a = graph.addVertex().also { vertexProperty[it] = "a" }
        val b = graph.addVertex().also { vertexProperty[it] = "b" }
        val c = graph.addVertex().also { vertexProperty[it] = "c" }
        val ab = graph.addEdge(a, b)
        graph.addEdge(b, c)

        val mutableOutput = ByteArrayOutputStream()
        writeCsvEdgeList(mutableOutput, CsvEdgeListGraph(graph, vertexProperty))

        // an immutable copy writes the same edge list (the key property is copied onto the copy)
        val immutable = graph.toImmutableGraph()
        val immutableKeys = immutable.createVertexKeyProperty<String>()
        vertexProperty.copyInto(immutableKeys)
        val immutableOutput = ByteArrayOutputStream()
        writeCsvEdgeList(immutableOutput, CsvEdgeListGraph(immutable, immutableKeys))
        assertThat(immutableOutput.toString(Charsets.UTF_8)).isEqualTo(mutableOutput.toString(Charsets.UTF_8))
        assertThat(mutableOutput.toString(Charsets.UTF_8).lines().filter { it.isNotEmpty() }).hasSize(2)

        // a filtered view writes only its own edges
        val filtered = graph.filter(vertexSetOf(a, b), edgeSetOf(ab))
        val filteredKeys = filtered.createVertexKeyProperty<String>()
        filteredKeys[a] = "a"
        filteredKeys[b] = "b"
        val filteredOutput = ByteArrayOutputStream()
        writeCsvEdgeList(filteredOutput, CsvEdgeListGraph(filtered, filteredKeys))
        assertThat(filteredOutput.toString(Charsets.UTF_8)).isEqualTo("a,b\n")
    }
}
