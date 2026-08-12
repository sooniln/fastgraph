package io.github.sooniln.fastgraph.io.graphml

import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.createEdgeProperty
import io.github.sooniln.fastgraph.createVertexProperty
import io.github.sooniln.fastgraph.mutableGraph
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class GraphMLWriterTest {

    @Test
    fun basicWrite() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val edge = graph.addEdge(a, b)

        val output = ByteArrayOutputStream()
        writeGraphML(output, graph) { indent = false; writeParseInfo = false }

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">" +
                "<graph id=\"G\" edgedefault=\"directed\">" +
                "<node id=\"n${a.id}\"></node>" +
                "<node id=\"n${b.id}\"></node>" +
                "<edge id=\"e${edge.id}\" source=\"n${a.id}\" target=\"n${b.id}\"></edge>" +
                "</graph></graphml>"
        )
    }

    @Test
    fun indentedOutputNestsElementsWithoutTouchingTextContent() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val label = graph.createVertexProperty<String>("hi")

        val output = ByteArrayOutputStream()
        writeGraphML(output, graph, vertexProperties = mapOf("label" to label)) { writeParseInfo = false }

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n" +
                "  <key id=\"d0\" for=\"node\" attr.name=\"label\" attr.type=\"string\"/>\n" +
                "  <graph id=\"G\" edgedefault=\"directed\">\n" +
                "    <node id=\"n${a.id}\">\n" +
                "      <data key=\"d0\">hi</data>\n" +
                "    </node>\n" +
                "  </graph>\n" +
                "</graphml>"
        )
    }

    @Test
    fun propertiesAreWrittenWithInferredAttrType() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val edge = graph.addEdge(a, b)
        val score = graph.createVertexProperty<Int>(0)
        score[a] = 5
        val label = graph.createEdgeProperty<String?>(null)
        label[edge] = "connects"

        val output = ByteArrayOutputStream()
        writeGraphML(
            output,
            graph,
            vertexProperties = mapOf("score" to score),
            edgeProperties = mapOf("label" to label),
        ) { indent = false; writeParseInfo = false }

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">" +
                "<key id=\"d0\" for=\"node\" attr.name=\"score\" attr.type=\"int\"/>" +
                "<key id=\"d1\" for=\"edge\" attr.name=\"label\" attr.type=\"string\"/>" +
                "<graph id=\"G\" edgedefault=\"directed\">" +
                "<node id=\"n${a.id}\"><data key=\"d0\">5</data></node>" +
                "<node id=\"n${b.id}\"><data key=\"d0\">0</data></node>" +
                "<edge id=\"e${edge.id}\" source=\"n${a.id}\" target=\"n${b.id}\"><data key=\"d1\">connects</data></edge>" +
                "</graph></graphml>"
        )
    }

    @Test
    fun nullPropertyValuesAreWrittenAsEmptyText() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val label = graph.createVertexProperty<String?>(null)

        val output = ByteArrayOutputStream()
        writeGraphML(output, graph, vertexProperties = mapOf("label" to label)) { indent = false; writeParseInfo = false }

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">" +
                "<key id=\"d0\" for=\"node\" attr.name=\"label\" attr.type=\"string\"/>" +
                "<graph id=\"G\" edgedefault=\"directed\">" +
                "<node id=\"n${a.id}\"><data key=\"d0\"></data></node>" +
                "</graph></graphml>"
        )
    }

    @Test
    fun canonicalNodeIdsClaimedWhenIndexedVertexGraph() {
        // mutableGraph(indexEdges = false) implements IndexedVertexGraph but not IndexedEdgeGraph.
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()

        val output = ByteArrayOutputStream()
        writeGraphML(output, graph) { indent = false }

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">" +
                "<graph id=\"G\" edgedefault=\"directed\" parse.nodes=\"1\" parse.edges=\"0\" parse.maxindegree=\"0\" " +
                "parse.maxoutdegree=\"0\" parse.order=\"nodesfirst\" parse.nodeids=\"canonical\">" +
                "<node id=\"n${a.id}\" parse.indegree=\"0\" parse.outdegree=\"0\"></node>" +
                "</graph></graphml>"
        )
    }

    @Test
    fun canonicalEdgeIdsClaimedWhenIndexedEdgeGraph() {
        // mutableGraph(indexEdges = true) implements both IndexedVertexGraph and IndexedEdgeGraph.
        val graph = mutableGraph(directed = true, indexEdges = true)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val edge = graph.addEdge(a, b)

        val output = ByteArrayOutputStream()
        writeGraphML(output, graph) { indent = false }

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">" +
                "<graph id=\"G\" edgedefault=\"directed\" parse.nodes=\"2\" parse.edges=\"1\" parse.maxindegree=\"1\" " +
                "parse.maxoutdegree=\"1\" parse.order=\"nodesfirst\" parse.nodeids=\"canonical\" parse.edgeids=\"canonical\">" +
                "<node id=\"n${a.id}\" parse.indegree=\"0\" parse.outdegree=\"1\"></node>" +
                "<node id=\"n${b.id}\" parse.indegree=\"1\" parse.outdegree=\"0\"></node>" +
                "<edge id=\"e${edge.id}\" source=\"n${a.id}\" target=\"n${b.id}\"></edge>" +
                "</graph></graphml>"
        )
    }

    @Test
    fun parseInfoAbsentWhenDisabled() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()

        val output = ByteArrayOutputStream()
        writeGraphML(output, graph) { indent = false; writeParseInfo = false }

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">" +
                "<graph id=\"G\" edgedefault=\"directed\">" +
                "<node id=\"n${a.id}\"></node>" +
                "</graph></graphml>"
        )
    }

    @Test
    fun roundTripsThroughReadGraphML() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val edge = graph.addEdge(a, b)
        val weight = graph.createEdgeProperty<Int>(0)
        weight[edge] = 9

        val output = ByteArrayOutputStream()
        writeGraphML(output, graph, edgeProperties = mapOf("weight" to weight))

        val result = readGraphML(output.toByteArray().inputStream())

        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(1)

        @Suppress("UNCHECKED_CAST")
        val weightBack = result.edgeProperties.getValue("weight") as MutableEdgeProperty<Int>

        assertThat(weightBack[result.graph.edges.single()]).isEqualTo(9)
    }
}
