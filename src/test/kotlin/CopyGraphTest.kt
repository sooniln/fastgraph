package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CopyGraphTest {

    @ParameterizedTest(name = "immutable={0}, directed={1}")
    @CsvSource("true,true", "true,false", "false,true", "false,false")
    fun mutableGraphCopyDirected(immutable: Boolean, directed: Boolean) {
        val graph = buildGraph<String, Nothing>(mutableGraph(directed)) {
            addEdge("v0", "v1")
            addEdge("v1", "v2")
            addEdge("v2", "v0")
            addEdge("v2", "v2")
        }

        val map = if (immutable) immutableGraph(graph) else mutableGraph(graph)
        val copy = map.graph

        assertThat(copy.vertices.size).isEqualTo(3)
        assertThat(copy.edges.size).isEqualTo(4)

        if (directed) {
            assertThat(copy.vertices.map { copy.successors(it).size }).containsExactlyInAnyOrder(1, 1, 2)
        } else {
            assertThat(copy.vertices.map { copy.successors(it).size }).containsExactlyInAnyOrder(2, 2, 3)
        }

        for (vertex in graph.vertices) {
            assertThat(graph.outDegree(vertex)).isEqualTo(copy.outDegree(map.getCorrespondingVertex(vertex)))
        }
        for (edge in graph.edges) {
            assertThat(graph.outDegree(graph.edgeSource(edge))).isEqualTo(
                copy.outDegree(
                    copy.edgeSource(
                        map.getCorrespondingEdge(
                            edge
                        )
                    )
                )
            )
            assertThat(graph.outDegree(graph.edgeTarget(edge))).isEqualTo(
                copy.outDegree(
                    copy.edgeTarget(
                        map.getCorrespondingEdge(
                            edge
                        )
                    )
                )
            )
        }
    }

    @ParameterizedTest(name = "immutable={0}, directed={1}")
    @CsvSource("true,true", "true,false", "false,true", "false,false")
    fun mutablePropertyGraphCopyDirected(immutable: Boolean, directed: Boolean) {
        val propertyGraph =
            buildGraph(mutablePropertyGraph(directed, vertexInitializer = { "" }, edgeInitializer = { "" })) {
                addEdge("v0", "v1", "e0")
                addEdge("v1", "v2", "e1")
                addEdge("v2", "v0", "e2")
                addEdge("v2", "v2", "e3")
            }

        val map = if (immutable) {
            immutablePropertyGraph(propertyGraph)
        } else {
            mutablePropertyGraph(propertyGraph, { "" }, { "" })
        }
        val copy = map.graph

        assertThat(copy.vertices.size).isEqualTo(3)
        assertThat(copy.edges.size).isEqualTo(4)

        if (directed) {
            assertThat(copy.vertices.map { copy.successors(it).size }).containsExactlyInAnyOrder(1, 1, 2)
        } else {
            assertThat(copy.vertices.map { copy.successors(it).size }).containsExactlyInAnyOrder(2, 2, 3)
        }

        for (vertex in propertyGraph.graph.vertices) {
            assertThat(propertyGraph.graph.outDegree(vertex)).isEqualTo(copy.outDegree(map.getCorrespondingVertex(vertex)))
            assertThat(propertyGraph.vertexProperty[vertex]).isEqualTo(
                map.vertexProperty[map.getCorrespondingVertex(
                    vertex
                )]
            )
        }
        for (edge in propertyGraph.graph.edges) {
            assertThat(propertyGraph.graph.outDegree(propertyGraph.graph.edgeSource(edge))).isEqualTo(
                copy.outDegree(
                    copy.edgeSource(map.getCorrespondingEdge(edge))
                )
            )
            assertThat(propertyGraph.graph.outDegree(propertyGraph.graph.edgeTarget(edge))).isEqualTo(
                copy.outDegree(
                    copy.edgeTarget(map.getCorrespondingEdge(edge))
                )
            )
            assertThat(propertyGraph.edgeProperty[edge]).isEqualTo(map.edgeProperty[map.getCorrespondingEdge(edge)])
        }
    }
}
