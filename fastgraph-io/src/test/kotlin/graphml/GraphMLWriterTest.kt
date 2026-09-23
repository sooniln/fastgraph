package io.github.sooniln.fastgraph.io.graphml

import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.mutableGraph
import io.github.sooniln.fastgraph.edgeSetOf
import io.github.sooniln.fastgraph.filtered.filter
import io.github.sooniln.fastgraph.vertexSetOf
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
    fun canonicalNodeIdsClaimedWhenIdentityIndexedVertexSet() {
        // mutableGraph(indexEdges = false) has an IdentityIndexedVertexSet but not an IdentityIndexedEdgeSet.
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
    fun canonicalEdgeIdsClaimedWhenIdentityIndexedEdgeSet() {
        // mutableGraph(indexEdges = true) has both an IdentityIndexedVertexSet and an IdentityIndexedEdgeSet.
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

    @Test
    fun parallelEdgesAndSelfLoopsRoundTrip() {
        val graph = mutableGraph(directed = true, multiEdge = true)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val ab0 = graph.addEdge(a, b)
        val ab1 = graph.addEdge(a, b)
        val bb = graph.addEdge(b, b)
        val weight = graph.createEdgeProperty<Int>(0)
        weight[ab0] = 1
        weight[ab1] = 2
        weight[bb] = 3

        val output = ByteArrayOutputStream()
        writeGraphML(output, GraphMLGraph(graph, edgeProperties = mapOf("weight" to weight)))

        val result = readGraphML(output.toByteArray().inputStream(), multiEdge = true)
        assertThat(result.graph.multiEdge).isTrue
        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(3)
        @Suppress("UNCHECKED_CAST")
        val weightBack = result.edgeProperties.getValue("weight") as MutableEdgeProperty<Int>
        assertThat(result.graph.edges.map { weightBack[it] }).containsExactlyInAnyOrder(1, 2, 3)
        val loop = result.graph.edges.single { result.graph.edgeSource(it) == result.graph.edgeTarget(it) }
        assertThat(weightBack[loop]).isEqualTo(3)
        val parallel = result.graph.edges.filter { it != loop }
        assertThat(parallel.map { result.graph.edgeSource(it) to result.graph.edgeTarget(it) }.toSet()).hasSize(1)
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
        writeGraphML(mutableOutput, GraphMLGraph(graph))

        // an immutable copy has the same ids, so it writes the same elements (iteration order may differ)
        val immutableOutput = ByteArrayOutputStream()
        writeGraphML(immutableOutput, GraphMLGraph(graph.toImmutableGraph()))
        assertThat(immutableOutput.toString(Charsets.UTF_8).lines())
            .containsExactlyInAnyOrderElementsOf(mutableOutput.toString(Charsets.UTF_8).lines())

        // a filtered view writes only its own vertices and edges, and is not identity indexed
        val filteredOutput = ByteArrayOutputStream()
        writeGraphML(filteredOutput, GraphMLGraph(graph.filter(vertexSetOf(a, b), edgeSetOf(ab))))
        val result = readGraphML(filteredOutput.toByteArray().inputStream())
        assertThat(result.graph.directed).isFalse
        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(1)
        assertThat(filteredOutput.toString(Charsets.UTF_8)).doesNotContain("parse.nodeids=\"canonical\"")
    }

    @Test
    fun nullAndTypedPropertiesAreWrittenSymmetricallyForVerticesAndEdges() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val b = graph.addVertex()
        val edge = graph.addEdge(a, b)
        val vertexLabel = graph.createVertexProperty<String?>(null)
        val edgeLabel = graph.createEdgeProperty<String?>(null)
        val vertexScore = graph.createVertexProperty<Double>(0.5)
        val edgeScore = graph.createEdgeProperty<Long>(7L)
        val vertexFlag = graph.createVertexProperty<Boolean>(true)
        val edgeFlag = graph.createEdgeProperty<Boolean>(false)
        vertexLabel[a] = "start"

        val output = ByteArrayOutputStream()
        writeGraphML(
            output,
            GraphMLGraph(
                graph,
                vertexProperties = mapOf("label" to vertexLabel, "score" to vertexScore, "flag" to vertexFlag),
                edgeProperties = mapOf("label" to edgeLabel, "score" to edgeScore, "flag" to edgeFlag),
            ),
        )

        val text = output.toString(Charsets.UTF_8)
        assertThat(text).contains("for=\"node\" attr.name=\"label\" attr.type=\"string\"")
        assertThat(text).contains("for=\"edge\" attr.name=\"label\" attr.type=\"string\"")
        assertThat(text).contains("for=\"node\" attr.name=\"score\" attr.type=\"double\"")
        assertThat(text).contains("for=\"edge\" attr.name=\"score\" attr.type=\"long\"")
        assertThat(text).contains("for=\"node\" attr.name=\"flag\" attr.type=\"boolean\"")
        assertThat(text).contains("for=\"edge\" attr.name=\"flag\" attr.type=\"boolean\"")

        val result = readGraphML(output.toByteArray().inputStream())
        val ra = result.graph.vertices.single { result.vertexProperties.getValue("label")[it] == "start" }
        val rb = result.graph.vertices.single { it != ra }
        val re = result.graph.edges.single()
        assertThat(result.vertexProperties.getValue("label")[rb]).isEqualTo("")
        assertThat(result.edgeProperties.getValue("label")[re]).isEqualTo("")
        assertThat(result.vertexProperties.getValue("score")[ra]).isEqualTo(0.5)
        assertThat(result.edgeProperties.getValue("score")[re]).isEqualTo(7L)
        assertThat(result.vertexProperties.getValue("flag")[rb]).isEqualTo(true)
        assertThat(result.edgeProperties.getValue("flag")[re]).isEqualTo(false)
    }
}
