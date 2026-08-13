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
        writeGraphML(output, GraphMLGraph(graph))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n" +
                "  <graph id=\"G\" edgedefault=\"directed\" parse.nodes=\"2\" parse.edges=\"1\" parse.order=\"nodesfirst\" " +
                "parse.nodeids=\"canonical\">\n" +
                "    <node id=\"n${a.id}\" parse.indegree=\"0\" parse.outdegree=\"1\"></node>\n" +
                "    <node id=\"n${b.id}\" parse.indegree=\"1\" parse.outdegree=\"0\"></node>\n" +
                "    <edge id=\"e${edge.id}\" source=\"n${a.id}\" target=\"n${b.id}\"></edge>\n" +
                "  </graph>\n" +
                "</graphml>"
        )
    }

    @Test
    fun indentedOutputNestsElementsWithoutTouchingTextContent() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val label = graph.createVertexProperty<String>("hi")

        val output = ByteArrayOutputStream()
        writeGraphML(output, GraphMLGraph(graph, vertexProperties = mapOf("label" to label)))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n" +
                "  <key id=\"d0\" for=\"node\" attr.name=\"label\" attr.type=\"string\"/>\n" +
                "  <graph id=\"G\" edgedefault=\"directed\" parse.nodes=\"1\" parse.edges=\"0\" parse.order=\"nodesfirst\" " +
                "parse.nodeids=\"canonical\">\n" +
                "    <node id=\"n${a.id}\" parse.indegree=\"0\" parse.outdegree=\"0\">\n" +
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
            GraphMLGraph(
                graph,
                vertexProperties = mapOf("score" to score),
                edgeProperties = mapOf("label" to label),
            ),
        )

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n" +
                "  <key id=\"d0\" for=\"node\" attr.name=\"score\" attr.type=\"int\"/>\n" +
                "  <key id=\"d1\" for=\"edge\" attr.name=\"label\" attr.type=\"string\"/>\n" +
                "  <graph id=\"G\" edgedefault=\"directed\" parse.nodes=\"2\" parse.edges=\"1\" parse.order=\"nodesfirst\" " +
                "parse.nodeids=\"canonical\">\n" +
                "    <node id=\"n${a.id}\" parse.indegree=\"0\" parse.outdegree=\"1\">\n" +
                "      <data key=\"d0\">5</data>\n" +
                "    </node>\n" +
                "    <node id=\"n${b.id}\" parse.indegree=\"1\" parse.outdegree=\"0\">\n" +
                "      <data key=\"d0\">0</data>\n" +
                "    </node>\n" +
                "    <edge id=\"e${edge.id}\" source=\"n${a.id}\" target=\"n${b.id}\">\n" +
                "      <data key=\"d1\">connects</data>\n" +
                "    </edge>\n" +
                "  </graph>\n" +
                "</graphml>"
        )
    }

    @Test
    fun nullPropertyValuesAreWrittenAsEmptyText() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val label = graph.createVertexProperty<String?>(null)

        val output = ByteArrayOutputStream()
        writeGraphML(output, GraphMLGraph(graph, vertexProperties = mapOf("label" to label)))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n" +
                "  <key id=\"d0\" for=\"node\" attr.name=\"label\" attr.type=\"string\"/>\n" +
                "  <graph id=\"G\" edgedefault=\"directed\" parse.nodes=\"1\" parse.edges=\"0\" parse.order=\"nodesfirst\" " +
                "parse.nodeids=\"canonical\">\n" +
                "    <node id=\"n${a.id}\" parse.indegree=\"0\" parse.outdegree=\"0\">\n" +
                "      <data key=\"d0\"></data>\n" +
                "    </node>\n" +
                "  </graph>\n" +
                "</graphml>"
        )
    }

    @Test
    fun canonicalNodeIdsClaimedWhenIndexedVertexGraph() {
        // mutableGraph(indexEdges = false) implements IndexedVertexGraph but not IndexedEdgeGraph.
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()

        val output = ByteArrayOutputStream()
        writeGraphML(output, GraphMLGraph(graph))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n" +
                "  <graph id=\"G\" edgedefault=\"directed\" parse.nodes=\"1\" parse.edges=\"0\" parse.order=\"nodesfirst\" " +
                "parse.nodeids=\"canonical\">\n" +
                "    <node id=\"n${a.id}\" parse.indegree=\"0\" parse.outdegree=\"0\"></node>\n" +
                "  </graph>\n" +
                "</graphml>"
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
        writeGraphML(output, GraphMLGraph(graph))

        assertThat(output.toString(Charsets.UTF_8)).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n" +
                "  <graph id=\"G\" edgedefault=\"directed\" parse.nodes=\"2\" parse.edges=\"1\" parse.order=\"nodesfirst\" " +
                "parse.nodeids=\"canonical\" parse.edgeids=\"canonical\">\n" +
                "    <node id=\"n${a.id}\" parse.indegree=\"0\" parse.outdegree=\"1\"></node>\n" +
                "    <node id=\"n${b.id}\" parse.indegree=\"1\" parse.outdegree=\"0\"></node>\n" +
                "    <edge id=\"e${edge.id}\" source=\"n${a.id}\" target=\"n${b.id}\"></edge>\n" +
                "  </graph>\n" +
                "</graphml>"
        )
    }

    @Test
    fun graphAttributesAreWrittenWithInferredAttrType() {
        val graph = mutableGraph(directed = true)
        graph.addVertex()

        val output = ByteArrayOutputStream()
        writeGraphML(output, GraphMLGraph(graph, graphAttributes = mapOf("count" to 7, "label" to "hi")))

        val xml = output.toString(Charsets.UTF_8)
        assertThat(xml).contains("<key id=\"d0\" for=\"graph\" attr.name=\"count\" attr.type=\"int\"/>")
        assertThat(xml).contains("<key id=\"d1\" for=\"graph\" attr.name=\"label\" attr.type=\"string\"/>")
        assertThat(xml).contains("<data key=\"d0\">7</data>")
        assertThat(xml).contains("<data key=\"d1\">hi</data>")
    }

    @Test
    fun graphAttributesRoundTripThroughReadGraphML() {
        val graph = mutableGraph(directed = true)
        graph.addVertex()

        val output = ByteArrayOutputStream()
        writeGraphML(output, GraphMLGraph(graph, graphAttributes = mapOf("count" to 7, "label" to "hi")))

        val result = readGraphML(output.toByteArray().inputStream())

        assertThat(result.graphAttributes).containsEntry("count", 7)
        assertThat(result.graphAttributes).containsEntry("label", "hi")
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
        writeGraphML(output, GraphMLGraph(graph, edgeProperties = mapOf("weight" to weight)))

        val result = readGraphML(output.toByteArray().inputStream())

        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(1)

        @Suppress("UNCHECKED_CAST")
        val weightBack = result.edgeProperties.getValue("weight") as MutableEdgeProperty<Int>

        assertThat(weightBack[result.graph.edges.single()]).isEqualTo(9)
    }
}
