package io.github.sooniln.fastgraph.io.graphml

import io.github.sooniln.fastgraph.safeCast
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GraphMLTest {

    @Test
    fun basicDirectedGraph() {
        val xml = """
            <graphml>
              <graph edgedefault="directed">
                <node id="a"/>
                <node id="b"/>
                <edge source="a" target="b"/>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())

        assertThat(result.graph.directed).isTrue()
        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(1)
    }

    @Test
    fun undirectedGraph() {
        val xml = """
            <graphml>
              <graph edgedefault="undirected">
                <node id="a"/>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())

        assertThat(result.graph.directed).isFalse()
    }

    @Test
    fun allAttrTypesAreParsed() {
        val xml = """
            <graphml>
              <key id="b" for="node" attr.name="flag" attr.type="boolean"/>
              <key id="i" for="node" attr.name="count" attr.type="int"/>
              <key id="l" for="node" attr.name="big" attr.type="long"/>
              <key id="f" for="node" attr.name="ratio" attr.type="float"/>
              <key id="d" for="node" attr.name="precise" attr.type="double"/>
              <key id="s" for="node" attr.name="label" attr.type="string"/>
              <graph edgedefault="directed">
                <node id="a">
                  <data key="b">true</data>
                  <data key="i">42</data>
                  <data key="l">123456789012</data>
                  <data key="f">1.5</data>
                  <data key="d">2.5</data>
                  <data key="s">hello</data>
                </node>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())
        val vertex = result.graph.vertices.single()

        val flag = result.vertexProperties.getValue("flag").safeCast<Boolean>()
        val count = result.vertexProperties.getValue("count").safeCast<Int>()
        val big = result.vertexProperties.getValue("big").safeCast<Long>()
        val ratio = result.vertexProperties.getValue("ratio").safeCast<Float>()
        val precise = result.vertexProperties.getValue("precise").safeCast<Double>()
        val label = result.vertexProperties.getValue("label").safeCast<String?>()

        assertThat(flag[vertex]).isTrue()
        assertThat(count[vertex]).isEqualTo(42)
        assertThat(big[vertex]).isEqualTo(123456789012L)
        assertThat(ratio[vertex]).isEqualTo(1.5F)
        assertThat(precise[vertex]).isEqualTo(2.5)
        assertThat(label[vertex]).isEqualTo("hello")
    }

    @Test
    fun defaultValueAppliedWhenDataOmitted() {
        val xml = """
            <graphml>
              <key id="i" for="node" attr.name="count" attr.type="int"/>
              <graph edgedefault="directed">
                <node id="a"/>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())
        val vertex = result.graph.vertices.single()

        @Suppress("UNCHECKED_CAST")
        val count = result.vertexProperties.getValue("count").safeCast<Int>()

        assertThat(count[vertex]).isEqualTo(0)
    }

    @Test
    fun explicitDefaultElementIsUsed() {
        val xml = """
            <graphml>
              <key id="c" for="node" attr.name="color" attr.type="string">
                <default>yellow</default>
              </key>
              <graph edgedefault="directed">
                <node id="a"/>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())
        val vertex = result.graph.vertices.single()

        @Suppress("UNCHECKED_CAST")
        val color = result.vertexProperties.getValue("color").safeCast<String>()

        assertThat(color[vertex]).isEqualTo("yellow")
    }

    @Test
    fun keyForAllAppliesToNodesAndEdges() {
        val xml = """
            <graphml>
              <key id="x" for="all" attr.name="label" attr.type="string"/>
              <graph edgedefault="directed">
                <node id="a"><data key="x">nodeLabel</data></node>
                <node id="b"/>
                <edge source="a" target="b"><data key="x">edgeLabel</data></edge>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())

        assertThat(result.vertexProperties).containsKey("label")
        assertThat(result.edgeProperties).containsKey("label")
    }

    @Test
    fun duplicateNodeIdThrows() {
        val xml = """
            <graphml>
              <graph edgedefault="directed">
                <node id="a"/>
                <node id="a"/>
              </graph>
            </graphml>
        """.trimIndent()

        assertThrows<GraphMLFormatException> { readGraphML(xml.byteInputStream()) }
    }

    @Test
    fun edgeReferencingUnknownNodeThrows() {
        val xml = """
            <graphml>
              <graph edgedefault="directed">
                <node id="a"/>
                <edge source="a" target="ghost"/>
              </graph>
            </graphml>
        """.trimIndent()

        assertThrows<GraphMLFormatException> { readGraphML(xml.byteInputStream()) }
    }

    @Test
    fun unknownAttrTypeThrows() {
        val xml = """
            <graphml>
              <key id="x" for="node" attr.name="weird" attr.type="complex"/>
              <graph edgedefault="directed">
                <node id="a"/>
              </graph>
            </graphml>
        """.trimIndent()

        assertThrows<GraphMLFormatException> { readGraphML(xml.byteInputStream()) }
    }

    @Test
    fun unsupportedKeyForValueThrows() {
        val xml = """
            <graphml>
              <key id="x" for="hyperedge" attr.name="meta" attr.type="string"/>
              <graph edgedefault="directed">
                <node id="a"/>
              </graph>
            </graphml>
        """.trimIndent()

        assertThrows<GraphMLFormatException> { readGraphML(xml.byteInputStream()) }
    }

    @Test
    fun graphLevelDataIsParsedWithTypedValue() {
        val xml = """
            <graphml>
              <key id="x" for="graph" attr.name="count" attr.type="int"/>
              <graph edgedefault="directed">
                <data key="x">7</data>
                <node id="a"/>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())

        assertThat(result.graphAttributes).containsEntry("count", 7)
    }

    @Test
    fun graphLevelDefaultAppliedWhenDataOmitted() {
        val xml = """
            <graphml>
              <key id="x" for="graph" attr.name="label" attr.type="string">
                <default>untitled</default>
              </key>
              <graph edgedefault="directed">
                <node id="a"/>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())

        assertThat(result.graphAttributes).containsEntry("label", "untitled")
    }

    @Test
    fun graphLevelAttributeAbsentWhenNoDefaultOrData() {
        val xml = """
            <graphml>
              <key id="x" for="graph" attr.name="label" attr.type="string"/>
              <graph edgedefault="directed">
                <node id="a"/>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())

        assertThat(result.graphAttributes).isEmpty()
    }

    @Test
    fun keyForAllUsableAtGraphLevelToo() {
        val xml = """
            <graphml>
              <key id="x" for="all" attr.name="label" attr.type="string"/>
              <graph edgedefault="directed">
                <data key="x">graphLabel</data>
                <node id="a"><data key="x">nodeLabel</data></node>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())

        assertThat(result.graphAttributes).containsEntry("label", "graphLabel")
        assertThat(result.vertexProperties.getValue("label")[result.graph.vertices.single()]).isEqualTo("nodeLabel")
    }

    @Test
    fun malformedXmlThrows() {
        assertThrows<GraphMLFormatException> {
            readGraphML("<graphml><graph edgedefault=\"directed\">".byteInputStream())
        }
    }

    @Test
    fun parserFailureIsWrappedWithCause() {
        val xml = """
            <graphml>
              <key id="i" for="node" attr.name="count" attr.type="int"/>
              <graph edgedefault="directed">
                <node id="a"><data key="i">notanumber</data></node>
              </graph>
            </graphml>
        """.trimIndent()

        val exception = assertThrows<GraphMLFormatException> { readGraphML(xml.byteInputStream()) }

        assertThat(exception.cause).isInstanceOf(NumberFormatException::class.java)
    }

    @Test
    fun portsAreIgnored() {
        val xml = """
            <graphml>
              <graph edgedefault="directed">
                <node id="a">
                  <port name="North">
                    <port name="Inner"/>
                  </port>
                </node>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())

        assertThat(result.graph.vertices).hasSize(1)
    }

    @Test
    fun hyperedgesAreIgnored() {
        val xml = """
            <graphml>
              <graph edgedefault="directed">
                <node id="a"/>
                <node id="b"/>
                <node id="c"/>
                <hyperedge>
                  <endpoint node="a"/>
                  <endpoint node="b"/>
                  <endpoint node="c"/>
                </hyperedge>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())

        assertThat(result.graph.vertices).hasSize(3)
        assertThat(result.graph.edges).isEmpty()
    }

    @Test
    fun nestedGraphInsideNodeIsIgnored() {
        val xml = """
            <graphml>
              <graph edgedefault="directed">
                <node id="n1">
                  <graph edgedefault="directed">
                    <node id="n2"/>
                  </graph>
                </node>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())

        assertThat(result.graph.vertices).hasSize(1)
    }

    @Test
    fun secondTopLevelGraphIsIgnored() {
        val xml = """
            <graphml>
              <graph edgedefault="directed">
                <node id="a"/>
              </graph>
              <graph edgedefault="directed">
                <node id="b"/>
                <node id="c"/>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())

        assertThat(result.graph.vertices).hasSize(1)
    }

    @Test
    fun parseNodesHintDoesNotAffectResult() {
        val xml = """
            <graphml>
              <graph edgedefault="directed" parse.nodes="2">
                <node id="a"/>
                <node id="b"/>
                <edge source="a" target="b"/>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())

        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(1)
    }

    @Test
    fun undirectedEdgeInDirectedGraphProducesTwoEdges() {
        val xml = """
            <graphml>
              <key id="w" for="edge" attr.name="weight" attr.type="int"/>
              <graph edgedefault="directed">
                <node id="a"/>
                <node id="b"/>
                <edge source="a" target="b" directed="false">
                  <data key="w">7</data>
                </edge>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())
        val graph = result.graph

        assertThat(graph.edges).hasSize(2)
        val (a, b) = graph.vertices.toList()
        assertThat(graph.hasEdge(a, b)).isTrue()
        assertThat(graph.hasEdge(b, a)).isTrue()

        @Suppress("UNCHECKED_CAST")
        val weight = result.edgeProperties.getValue("weight").safeCast<Int>()

        for (edge in graph.edges) {
            assertThat(weight[edge]).isEqualTo(7)
        }
    }

    @Test
    fun directedEdgeInUndirectedGraphThrows() {
        val xml = """
            <graphml>
              <graph edgedefault="undirected">
                <node id="a"/>
                <node id="b"/>
                <edge source="a" target="b" directed="true"/>
              </graph>
            </graphml>
        """.trimIndent()

        assertThrows<GraphMLFormatException> { readGraphML(xml.byteInputStream()) }
    }

    @Test
    fun edgeBeforeReferencedNodeIsSupported() {
        val xml = """
            <graphml>
              <graph edgedefault="directed">
                <edge source="a" target="b"/>
                <node id="a"/>
                <node id="b"/>
              </graph>
            </graphml>
        """.trimIndent()

        val result = readGraphML(xml.byteInputStream())

        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(1)
    }
}
