package io.github.sooniln.fastgraph.io.csv

import io.github.sooniln.fastgraph.EdgeProperty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CsvEdgeListTest {

    @Test
    fun basicDirectedEdgeList() {
        val result = readCsvEdgeList("a,b\nb,c\n".byteInputStream(), directed = true, multiEdge = false, vertexColumn = CsvVertexField.string)

        assertThat(result.graph.directed).isTrue()
        assertThat(result.graph.vertices).hasSize(3)
        assertThat(result.graph.edges).hasSize(2)
    }

    @Test
    fun undirectedEdgeList() {
        val result = readCsvEdgeList("a,b\n".byteInputStream(), directed = false, multiEdge = false, vertexColumn = CsvVertexField.string)

        assertThat(result.graph.directed).isFalse()
    }

    @Test
    fun duplicateVertexValuesAreDeduplicated() {
        val result = readCsvEdgeList("a,b\na,c\n".byteInputStream(), directed = true, multiEdge = false, vertexColumn = CsvVertexField.string)

        assertThat(result.graph.vertices).hasSize(3)
        assertThat(result.graph.edges).hasSize(2)
    }

    @Test
    fun selfLoopSupported() {
        val result = readCsvEdgeList("a,a\n".byteInputStream(), directed = true, multiEdge = false, vertexColumn = CsvVertexField.string)

        assertThat(result.graph.vertices).hasSize(1)
        assertThat(result.graph.edges).hasSize(1)
    }

    @Test
    fun duplicateEdgeWithoutMultiEdgeThrows() {
        // readCsvEdgeList wraps any RuntimeException raised while processing a record - including the
        // IllegalArgumentException MutableGraph.addEdge throws for a duplicate edge - as a CsvFormatException so the
        // failure is reported with line context; the original exception is preserved as the cause.
        val exception = assertThrows<CsvFormatException> {
            readCsvEdgeList("a,b\na,b\n".byteInputStream(), directed = true, multiEdge = false, vertexColumn = CsvVertexField.string)
        }

        assertThat(exception.cause).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun duplicateEdgeWithMultiEdgeAllowed() {
        val result = readCsvEdgeList("a,b\na,b\n".byteInputStream(), directed = true, multiEdge = true, vertexColumn = CsvVertexField.string)

        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(2)
    }

    @Test
    fun headerRowIsSkipped() {
        val result = readCsvEdgeList(
            "source,target\na,b\n".byteInputStream(),
            directed = true,
            multiEdge = false,
            vertexColumn = CsvVertexField.string,
            options = { hasHeader = true },
        )

        assertThat(result.graph.vertices).hasSize(2)
        assertThat(result.graph.edges).hasSize(1)
    }

    @Test
    fun commentLinesAreSkipped() {
        val result = readCsvEdgeList(
            "# comment\na,b\n# another\nb,c\n".byteInputStream(),
            directed = true,
            multiEdge = false,
            vertexColumn = CsvVertexField.string,
            options = { comment = '#' },
        )

        assertThat(result.graph.vertices).hasSize(3)
        assertThat(result.graph.edges).hasSize(2)
    }

    @Test
    fun customDelimiter() {
        val result = readCsvEdgeList(
            "a\tb\n".byteInputStream(),
            directed = true,
            multiEdge = false,
            vertexColumn = CsvVertexField.string,
            options = { delimiter = '\t' },
        )

        assertThat(result.graph.edges).hasSize(1)
    }

    @Test
    fun quotedFieldCanContainDelimiter() {
        val result = readCsvEdgeList("\"a,x\",b\n".byteInputStream(), directed = true, multiEdge = false, vertexColumn = CsvVertexField.string)

        assertThat(result.graph.vertices).hasSize(2)
    }

    @Test
    fun edgePropertyColumnsArePopulated() {
        val result = readCsvEdgeList(
            "a,b,42,3.5\n".byteInputStream(),
            directed = true,
            multiEdge = false,
            vertexColumn = CsvVertexField.string,
            edgeColumns = listOf(CsvEdgeField.int, CsvEdgeField.double),
        )
        val edge = result.graph.edges.single()

        @Suppress("UNCHECKED_CAST")
        val intProperty = result.edgeProperties[0] as EdgeProperty<Int>

        @Suppress("UNCHECKED_CAST")
        val doubleProperty = result.edgeProperties[1] as EdgeProperty<Double>

        assertThat(intProperty[edge]).isEqualTo(42)
        assertThat(doubleProperty[edge]).isEqualTo(3.5)
    }

    @Test
    fun rowWithWrongColumnCountThrowsCsvFormatException() {
        assertThrows<CsvFormatException> {
            readCsvEdgeList("a\n".byteInputStream(), directed = true, multiEdge = false, vertexColumn = CsvVertexField.string)
        }
    }

    @Test
    fun unterminatedQuoteThrowsCsvFormatException() {
        assertThrows<CsvFormatException> {
            readCsvEdgeList("\"a,b\n".byteInputStream(), directed = true, multiEdge = false, vertexColumn = CsvVertexField.string)
        }
    }

    @Test
    fun parserFailureIsWrappedWithCause() {
        val exception = assertThrows<CsvFormatException> {
            readCsvEdgeList(
                "a,b,notanumber\n".byteInputStream(),
                directed = true,
                multiEdge = false,
                vertexColumn = CsvVertexField.string,
                edgeColumns = listOf(CsvEdgeField.int),
            )
        }

        assertThat(exception.cause).isInstanceOf(NumberFormatException::class.java)
    }
}
