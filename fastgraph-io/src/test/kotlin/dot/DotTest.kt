package io.github.sooniln.fastgraph.io.dot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DotTest {

    @Test
    fun basicDirectedGraph() {
        val result = readDot("digraph { a -> b }".byteInputStream())

        assertThat(result.graph.directed).isTrue()
        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(1)
    }

    @Test
    fun undirectedGraph() {
        val result = readDot("graph { a -- b }".byteInputStream())

        assertThat(result.graph.directed).isFalse()
    }

    @Test
    fun strictKeywordIsParsed() {
        val result = readDot("strict digraph { a -> b }".byteInputStream())

        assertThat(result.graph.edges).hasSize(1)
    }

    @Test
    fun graphIdIsCaptured() {
        val result = readDot("digraph MyGraph { a }".byteInputStream())

        assertThat(result.graphId).isEqualTo("MyGraph")
    }

    @Test
    fun nodeAndEdgeAttributesAreParsedAsStringProperties() {
        val dot = """
            digraph {
              a [color="red"]
              a -> b [weight="5"]
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())
        val a = result.graph.vertices.first { result.vertexIdProperty[it] == "a" }
        val edge = result.graph.edges.single()

        assertThat(result.vertexProperties.getValue("color")[a]).isEqualTo("red")
        assertThat(result.edgeProperties.getValue("weight")[edge]).isEqualTo("5")
    }

    @Test
    fun defaultAttributesApplyOnlyProspectively() {
        val dot = """
            digraph {
              node[color="red"]
              a
              node[color="blue"]
              b
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())
        val color = result.vertexProperties.getValue("color")
        val a = result.graph.vertices.first { result.vertexIdProperty[it] == "a" }
        val b = result.graph.vertices.first { result.vertexIdProperty[it] == "b" }

        assertThat(color[a]).isEqualTo("red")
        assertThat(color[b]).isEqualTo("blue")
    }

    @Test
    fun explicitAttrOverridesActiveDefault() {
        val dot = """
            digraph {
              node[color="red"]
              a[color="green"]
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())
        val a = result.graph.vertices.single()

        assertThat(result.vertexProperties.getValue("color")[a]).isEqualTo("green")
    }

    @Test
    fun subgraphScopeDoesNotLeakAttrsToParent() {
        val dot = """
            digraph {
              node[color="red"]
              { node[color="blue"]; a }
              b
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())
        val color = result.vertexProperties.getValue("color")
        val a = result.graph.vertices.first { result.vertexIdProperty[it] == "a" }
        val b = result.graph.vertices.first { result.vertexIdProperty[it] == "b" }

        assertThat(color[a]).isEqualTo("blue")
        assertThat(color[b]).isEqualTo("red")
    }

    @Test
    fun subgraphInheritsParentDefaultsAtEntry() {
        val dot = """
            digraph {
              node[color="red"]
              { a }
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())
        val a = result.graph.vertices.single()

        assertThat(result.vertexProperties.getValue("color")[a]).isEqualTo("red")
    }

    @Test
    fun edgeChainingCreatesConsecutivePairEdges() {
        val result = readDot("digraph { a -> b -> c }".byteInputStream())
        val graph = result.graph
        val a = graph.vertices.first { result.vertexIdProperty[it] == "a" }
        val b = graph.vertices.first { result.vertexIdProperty[it] == "b" }
        val c = graph.vertices.first { result.vertexIdProperty[it] == "c" }

        assertThat(graph.edges).hasSize(2)
        assertThat(graph.hasEdge(a, b)).isTrue()
        assertThat(graph.hasEdge(b, c)).isTrue()
    }

    @Test
    fun subgraphEdgeEndpointExpansion() {
        val result = readDot("digraph { a -> {b c} }".byteInputStream())
        val graph = result.graph
        val a = graph.vertices.first { result.vertexIdProperty[it] == "a" }
        val b = graph.vertices.first { result.vertexIdProperty[it] == "b" }
        val c = graph.vertices.first { result.vertexIdProperty[it] == "c" }

        assertThat(graph.edges).hasSize(2)
        assertThat(graph.hasEdge(a, b)).isTrue()
        assertThat(graph.hasEdge(a, c)).isTrue()
    }

    @Test
    fun nestedSubgraphEdgeEndpointsAreFlattened() {
        val result = readDot("digraph { a -> {b; {c d}} }".byteInputStream())
        val graph = result.graph
        val a = graph.vertices.first { result.vertexIdProperty[it] == "a" }
        val b = graph.vertices.first { result.vertexIdProperty[it] == "b" }
        val c = graph.vertices.first { result.vertexIdProperty[it] == "c" }
        val d = graph.vertices.first { result.vertexIdProperty[it] == "d" }

        assertThat(graph.edges).hasSize(3)
        assertThat(graph.hasEdge(a, b)).isTrue()
        assertThat(graph.hasEdge(a, c)).isTrue()
        assertThat(graph.hasEdge(a, d)).isTrue()
    }

    @Test
    fun strictDuplicateEdgeMergesAttributes() {
        val dot = """
            strict digraph {
              a -> b [x="1"]
              a -> b [y="2"]
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())

        assertThat(result.graph.edges).hasSize(1)
        val edge = result.graph.edges.single()
        assertThat(result.edgeProperties.getValue("x")[edge]).isEqualTo("1")
        assertThat(result.edgeProperties.getValue("y")[edge]).isEqualTo("2")
    }

    @Test
    fun strictUndirectedDuplicateEdgeMergesRegardlessOfOrder() {
        val dot = """
            strict graph {
              a -- b [x="1"]
              b -- a [y="2"]
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())

        assertThat(result.graph.edges).hasSize(1)
        val edge = result.graph.edges.single()
        assertThat(result.edgeProperties.getValue("x")[edge]).isEqualTo("1")
        assertThat(result.edgeProperties.getValue("y")[edge]).isEqualTo("2")
    }

    @Test
    fun nonStrictDuplicateEdgeCreatesSecondEdgeWhenMultiEdgeEnabled() {
        val result = readDot("digraph { a -> b; a -> b }".byteInputStream(), multiEdge = true)

        assertThat(result.graph.edges).hasSize(2)
    }

    @Test
    fun nonStrictDuplicateEdgeThrowsWhenMultiEdgeDisabled() {
        val exception = assertThrows<DotFormatException> {
            readDot("digraph { a -> b; a -> b }".byteInputStream())
        }

        assertThat(exception.cause).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun portsAndCompassPointsAreIgnored() {
        val result = readDot("digraph { a:north -> b:south:ne }".byteInputStream())

        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(1)
        assertThat(result.graph.vertices.map { result.vertexIdProperty[it] }).containsExactlyInAnyOrder("a", "b")
    }

    @Test
    fun htmlStringIdsAreUsedAsOpaqueText() {
        val result = readDot("digraph { a [label=<<B>bold</B>>] }".byteInputStream())
        val a = result.graph.vertices.single()

        assertThat(result.vertexProperties.getValue("label")[a]).isEqualTo("<B>bold</B>")
    }

    @Test
    fun quotedStringPlusConcatenation() {
        val result = readDot("digraph { a [label=\"foo\" + \"bar\"] }".byteInputStream())
        val a = result.graph.vertices.single()

        assertThat(result.vertexProperties.getValue("label")[a]).isEqualTo("foobar")
    }

    @Test
    fun quotedStringBackslashNewlineContinuationIsElided() {
        val dot = "digraph { a [label=\"foo\\\nbar\"] }"

        val result = readDot(dot.byteInputStream())
        val a = result.graph.vertices.single()

        assertThat(result.vertexProperties.getValue("label")[a]).isEqualTo("foobar")
    }

    @Test
    fun rawEmbeddedNewlineInQuotedStringIsPreserved() {
        val dot = "digraph { a [label=\"foo\nbar\"] }"

        val result = readDot(dot.byteInputStream())
        val a = result.graph.vertices.single()

        assertThat(result.vertexProperties.getValue("label")[a]).isEqualTo("foo\nbar")
    }

    @Test
    fun edgeBeforeReferencedNodeIsSupported() {
        val result = readDot("digraph { a -> b }".byteInputStream())

        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(1)
    }

    @Test
    fun selfLoopIsSupported() {
        val result = readDot("digraph { a -> a }".byteInputStream())
        val a = result.graph.vertices.single()

        assertThat(result.graph.edges).hasSize(1)
        assertThat(result.graph.hasEdge(a, a)).isTrue()
    }

    @Test
    fun lineCommentIsStripped() {
        val dot = """
            digraph {
              // this is a comment
              a -> b
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())

        assertThat(result.graph.edges).hasSize(1)
    }

    @Test
    fun blockCommentIsStripped() {
        val dot = """
            digraph {
              /* this is a
                 multi-line comment */
              a -> b
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())

        assertThat(result.graph.edges).hasSize(1)
    }

    @Test
    fun hashLineCommentIsStripped() {
        val dot = """
            digraph {
              # this is a preprocessor-style comment
              a -> b
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())

        assertThat(result.graph.edges).hasSize(1)
    }

    @Test
    fun reDeclaringNodeMergesAttributesInsteadOfThrowing() {
        val dot = """
            digraph {
              a [color="red"]
              a [shape="box"]
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())

        assertThat(result.graph.vertices).hasSize(1)
        val a = result.graph.vertices.single()
        assertThat(result.vertexProperties.getValue("color")[a]).isEqualTo("red")
        assertThat(result.vertexProperties.getValue("shape")[a]).isEqualTo("box")
    }

    @Test
    fun plainIdEqualsIdGraphAttributeIsCaptured() {
        val dot = """
            digraph {
              rankdir=LR
              a -> b
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())

        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(1)
        assertThat(result.graphProperties).containsEntry("rankdir", "LR")
    }

    @Test
    fun graphAttrStatementIsCaptured() {
        val dot = """
            digraph {
              graph[rankdir="LR", label="my graph"]
              a -> b
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())

        assertThat(result.graphProperties).containsEntry("rankdir", "LR")
        assertThat(result.graphProperties).containsEntry("label", "my graph")
    }

    @Test
    fun nestedSubgraphGraphAttrDoesNotLeakToTopLevel() {
        val dot = """
            digraph {
              { graph[rankdir="LR"]; a }
            }
        """.trimIndent()

        val result = readDot(dot.byteInputStream())

        assertThat(result.graphProperties).isEmpty()
    }

    @Test
    fun arrowOperatorInUndirectedGraphThrows() {
        assertThrows<DotFormatException> { readDot("graph { a -> b }".byteInputStream()) }
    }

    @Test
    fun dashDashOperatorInDirectedGraphThrows() {
        assertThrows<DotFormatException> { readDot("digraph { a -- b }".byteInputStream()) }
    }

    @Test
    fun unterminatedBraceThrows() {
        assertThrows<DotFormatException> { readDot("digraph { a -> b".byteInputStream()) }
    }

    @Test
    fun unterminatedQuotedStringThrows() {
        assertThrows<DotFormatException> { readDot("digraph { a [label=\"foo] }".byteInputStream()) }
    }

    @Test
    fun unterminatedHtmlStringThrows() {
        assertThrows<DotFormatException> { readDot("digraph { a [label=<foo] }".byteInputStream()) }
    }

    @Test
    fun trailingContentAfterClosingBraceThrows() {
        assertThrows<DotFormatException> {
            readDot("digraph { a -> b } digraph { c -> d }".byteInputStream())
        }
    }
}
