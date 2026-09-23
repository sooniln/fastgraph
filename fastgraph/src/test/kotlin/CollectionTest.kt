package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.filtered.filter
import io.github.sooniln.fastgraph.paths.Path
import io.github.sooniln.fastgraph.paths.PathForest
import io.github.sooniln.fastgraph.paths.buildPathForest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Contracts of the vertex/edge collection types and identifiers themselves, independent of any particular graph
 * operation: factories, equality/hashing across every implementation that can hold the same elements, and string forms.
 */
class CollectionTest {

    private lateinit var mutable: MutableGraph
    private lateinit var immutable: ImmutableGraph
    private lateinit var tree: PathForest
    private lateinit var path: Path
    private var v0 = Vertex(-1)
    private var v1 = Vertex(-1)
    private var v2 = Vertex(-1)
    private var e01 = Edge(-1)
    private var e12 = Edge(-1)
    private var e02 = Edge(-1)

    // v0 -> v1, v2 -> v1, v0 -> v2; the path tree reaches v2 before v1 so that its index order differs from the graphs'
    private fun construct(directed: Boolean, indexEdges: Boolean) {
        mutable = buildGraph(directed, indexEdges = indexEdges) {
            v0 = addVertex()
            v1 = addVertex()
            v2 = addVertex()
            e01 = addEdge(v0, v1)
            e12 = addEdge(v2, v1)
            e02 = addEdge(v0, v2)
        }
        immutable = mutable.toImmutableGraph()
        tree = mutable.buildPathForest {
            addRoot(v0)
            setParentEdge(v2, e02)
            setParentEdge(v1, e01)
        }
        path = mutable.buildPathForest {
            addRoot(v0)
            setParentEdge(v2, e02)
            setParentEdge(v1, e12)
        }.materializePath(v1)
    }

    private fun vertexSetsOfAllVertices(): List<Pair<String, VertexSet>> = listOf(
        "mutable.vertices" to mutable.vertices,
        "immutable.vertices" to immutable.vertices,
        "tree.vertices" to tree.vertices,
        "path.vertices" to path.vertices,
        "vertexSetOf" to vertexSetOf(v2, v0, v1),
        "induced.vertices" to mutable.filter(vertexSetOf(v0, v1, v2), mutable.edges).vertices,
        "predicated.vertices" to mutable.filter({ true }, { true }).vertices,
    )

    private fun edgeSetsOfEdgesFromV0(): List<Pair<String, EdgeSet>> = listOf(
        "mutable.outgoingEdges" to mutable.outgoingEdges(v0),
        "immutable.outgoingEdges" to immutable.outgoingEdges(v0),
        "tree.edges" to tree.edges,
        "edgeSetOf" to edgeSetOf(e02, e01),
        "induced.edges" to mutable.filter(null, edgeSetOf(e01, e02)).edges,
        "predicated.edges" to mutable.filter(null) { edge -> edge != e12 }.edges,
    )

    // --- equality across implementations ------------------------------------------------------------------------------

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun vertexSetEqualityIsElementBasedSymmetricAndHashConsistent(directed: Boolean) {
        for (indexEdges in listOf(false, true)) {
            construct(directed, indexEdges)
            val sets = vertexSetsOfAllVertices()
            val plain = setOf(v0, v1, v2)

            for ((leftName, left) in sets) {
                for ((rightName, right) in sets) {
                    assertThat(left == right).describedAs("$leftName == $rightName").isTrue
                    assertThat(left.hashCode()).describedAs("hash $leftName vs $rightName").isEqualTo(right.hashCode())
                }
                assertThat(left == plain).describedAs("$leftName == Set").isTrue
                assertThat(plain == left).describedAs("Set == $leftName").isTrue
                assertThat(left.hashCode()).describedAs("hash $leftName vs Set").isEqualTo(plain.hashCode())
                assertThat(left == vertexSetOf(v0, v1)).describedAs("$leftName == subset").isFalse
                assertThat(vertexSetOf(v0, v1) == left).describedAs("subset == $leftName").isFalse
                assertThat(left == setOf(v0, v1, Vertex(9))).describedAs("$leftName == other").isFalse
                assertThat(left.equals(listOf(v0, v1, v2))).describedAs("$leftName == List").isFalse
            }
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun edgeSetEqualityIsElementBasedSymmetricAndHashConsistent(directed: Boolean) {
        for (indexEdges in listOf(false, true)) {
            construct(directed, indexEdges)
            val sets = edgeSetsOfEdgesFromV0()
            val plain = setOf(e01, e02)

            for ((leftName, left) in sets) {
                for ((rightName, right) in sets) {
                    assertThat(left == right).describedAs("$leftName == $rightName").isTrue
                    assertThat(left.hashCode()).describedAs("hash $leftName vs $rightName").isEqualTo(right.hashCode())
                }
                assertThat(left == plain).describedAs("$leftName == Set").isTrue
                assertThat(plain == left).describedAs("Set == $leftName").isTrue
                assertThat(left.hashCode()).describedAs("hash $leftName vs Set").isEqualTo(plain.hashCode())
                assertThat(left == edgeSetOf(e01)).describedAs("$leftName == subset").isFalse
                assertThat(edgeSetOf(e01) == left).describedAs("subset == $leftName").isFalse
                assertThat(left == mutable.edges).describedAs("$leftName == all edges").isFalse
                assertThat(mutable.edges == left).describedAs("all edges == $leftName").isFalse
                assertThat(left.equals(listOf(e01, e02))).describedAs("$leftName == List").isFalse
            }

            // whole-graph edge sets across implementations
            assertThat(mutable.edges == immutable.edges).isTrue
            assertThat(immutable.edges == mutable.edges).isTrue
            assertThat(mutable.edges.hashCode()).isEqualTo(immutable.edges.hashCode())
            assertThat(mutable.edges == setOf(e01, e12, e02)).isTrue
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun sequencedEqualityIsOrderSensitive(directed: Boolean) {
        construct(directed, indexEdges = true)
        val mutableVertices = mutable.vertices as VertexSequencedSet
        val immutableVertices = immutable.vertices as VertexSequencedSet

        assertThat(mutableVertices.equalsSequenced(immutableVertices)).isTrue
        assertThat(immutableVertices.equalsSequenced(mutableVertices)).isTrue
        assertThat(tree.vertices).containsExactlyInAnyOrder(v0, v2, v1)
        // the path reached v2 before v1
        assertThat(mutableVertices.equalsSequenced(path.vertices)).isFalse
        assertThat(tree.vertices == mutableVertices).isTrue

        val mutableEdges = mutable.edges as EdgeSequencedSet
        val immutableEdges = immutable.edges as EdgeSequencedSet
        assertThat(mutableEdges.equalsSequenced(immutableEdges)).isTrue
        assertThat(immutableEdges.equalsSequenced(mutableEdges)).isTrue
        assertThat(tree.edges).containsExactlyInAnyOrder(e02, e01)
        assertThat(mutableEdges.equalsSequenced(path.edges)).isFalse
    }

    // --- factories ----------------------------------------------------------------------------------------------------

    @Test
    fun vertexSetOfFactory() {
        assertThat(vertexSetOf<Vertex>()).isSameAs(emptyVertexSet())

        val single = vertexSetOf(Vertex(3))
        assertThat(single).hasSize(1)
        assertThat(single.contains(Vertex(3))).isTrue
        assertThat(single.contains(Vertex(2))).isFalse
        assertThat(single.isEmpty()).isFalse
        assertThat(single).containsExactly(Vertex(3))
        assertThat(single.toIntArray()).containsExactly(3)
        assertThat(single).isEqualTo(setOf(Vertex(3)))

        val duplicates = vertexSetOf(Vertex(3), Vertex(3))
        assertThat(duplicates).hasSize(1)
        assertThat(duplicates).isEqualTo(single)
        assertThat(single).isEqualTo(duplicates)
        assertThat(duplicates.hashCode()).isEqualTo(single.hashCode())

        val several = vertexSetOf(Vertex(5), Vertex(1), Vertex(3))
        assertThat(several).hasSize(3)
        assertThat(several).containsExactlyInAnyOrder(Vertex(1), Vertex(3), Vertex(5))
        assertThat(several.toIntArray()).containsExactlyInAnyOrder(1, 3, 5)
        assertThat(several.contains(Vertex(1))).isTrue
        assertThat(several.contains(Vertex(2))).isFalse
        assertThat(several.containsAll(vertexSetOf(Vertex(1), Vertex(5)))).isTrue
        assertThat(several.containsAll(listOf(Vertex(1), Vertex(5)))).isTrue
        assertThat(several.containsAll(vertexSetOf(Vertex(1), Vertex(2)))).isFalse
        assertThat(several.containsAll(listOf(Vertex(2)))).isFalse
        assertThat(several.containsAll(emptyVertexSet())).isTrue
    }

    @Test
    fun edgeSetOfFactory() {
        assertThat(edgeSetOf<Edge>()).isSameAs(emptyEdgeSet())

        val single = edgeSetOf(Edge(3))
        assertThat(single).hasSize(1)
        assertThat(single.contains(Edge(3))).isTrue
        assertThat(single.contains(Edge(2))).isFalse
        assertThat(single.isEmpty()).isFalse
        assertThat(single).containsExactly(Edge(3))
        assertThat(single.toLongArray()).containsExactly(3L)
        assertThat(single).isEqualTo(setOf(Edge(3)))

        val duplicates = edgeSetOf(Edge(3), Edge(3))
        assertThat(duplicates).hasSize(1)
        assertThat(duplicates).isEqualTo(single)
        assertThat(single).isEqualTo(duplicates)
        assertThat(duplicates.hashCode()).isEqualTo(single.hashCode())

        val several = edgeSetOf(Edge(5), Edge(1), Edge(3))
        assertThat(several).hasSize(3)
        assertThat(several).containsExactlyInAnyOrder(Edge(1), Edge(3), Edge(5))
        assertThat(several.toLongArray()).containsExactlyInAnyOrder(1L, 3L, 5L)
        assertThat(several.contains(Edge(1))).isTrue
        assertThat(several.contains(Edge(2))).isFalse
        assertThat(several.containsAll(edgeSetOf(Edge(1), Edge(5)))).isTrue
        assertThat(several.containsAll(listOf(Edge(1), Edge(5)))).isTrue
        assertThat(several.containsAll(edgeSetOf(Edge(1), Edge(2)))).isFalse
        assertThat(several.containsAll(listOf(Edge(2)))).isFalse
        assertThat(several.containsAll(emptyEdgeSet())).isTrue
    }

    @Test
    fun emptyVertexSetContract() {
        val empty = emptyVertexSet()

        assertThat(empty).isEmpty()
        assertThat(empty.size).isEqualTo(0)
        assertThat(empty.isEmpty()).isTrue
        assertThat(empty.contains(Vertex(0))).isFalse
        assertThat(empty.indexOf(Vertex(0))).isEqualTo(-1)
        assertThat(empty.lastIndexOf(Vertex(0))).isEqualTo(-1)
        assertThat(empty.toIntArray()).isEmpty()
        assertThrows<IndexOutOfBoundsException> { empty[0] }
        assertThrows<NoSuchElementException> { empty.first() }
        assertThrows<NoSuchElementException> { empty.last() }
        assertThat(empty).isEqualTo(emptySet<Vertex>())
        assertThat(emptySet<Vertex>()).isEqualTo(empty)
        assertThat(empty.hashCode()).isEqualTo(emptySet<Vertex>().hashCode())
        assertThat(empty).isNotEqualTo(vertexSetOf(Vertex(0)))

        val iterator = (empty as MutableVertexSet).iterator()
        assertThat(iterator.hasNext()).isFalse
        assertThrows<NoSuchElementException> { iterator.next() }
        assertThrows<IllegalStateException> { iterator.remove() }

        val standalone = emptyVertexIterator()
        assertThat(standalone.hasNext()).isFalse
        assertThrows<NoSuchElementException> { standalone.next() }
        assertThrows<IllegalStateException> { standalone.remove() }
    }

    @Test
    fun emptyEdgeSetContract() {
        val empty = emptyEdgeSet()

        assertThat(empty).isEmpty()
        assertThat(empty.size).isEqualTo(0)
        assertThat(empty.isEmpty()).isTrue
        assertThat(empty.contains(Edge(0))).isFalse
        assertThat(empty.indexOf(Edge(0))).isEqualTo(-1)
        assertThat(empty.lastIndexOf(Edge(0))).isEqualTo(-1)
        assertThat(empty.toLongArray()).isEmpty()
        assertThrows<IndexOutOfBoundsException> { empty[0] }
        assertThrows<NoSuchElementException> { empty.first() }
        assertThrows<NoSuchElementException> { empty.last() }
        assertThat(empty).isEqualTo(emptySet<Edge>())
        assertThat(emptySet<Edge>()).isEqualTo(empty)
        assertThat(empty.hashCode()).isEqualTo(emptySet<Edge>().hashCode())
        assertThat(empty).isNotEqualTo(edgeSetOf(Edge(0)))

        val iterator = (empty as MutableEdgeSet).iterator()
        assertThat(iterator.hasNext()).isFalse
        assertThrows<NoSuchElementException> { iterator.next() }
        assertThrows<IllegalStateException> { iterator.remove() }

        val standalone = emptyEdgeIterator()
        assertThat(standalone.hasNext()).isFalse
        assertThrows<NoSuchElementException> { standalone.next() }
        assertThrows<IllegalStateException> { standalone.remove() }
    }

    // --- sequenced collections ----------------------------------------------------------------------------------------

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun sequencedVertexAccessors(directed: Boolean) {
        construct(directed, indexEdges = true)

        for (vertices in listOf(immutable.vertices as VertexSequencedSet, mutable.vertices as VertexSequencedSet)) {
            assertThat(vertices.first()).isEqualTo(v0)
            assertThat(vertices.last()).isEqualTo(v2)
            assertThat(vertices.lastIndex).isEqualTo(2)
            assertThat(vertices.indexOf(v1)).isEqualTo(1)
            assertThat(vertices.lastIndexOf(v1)).isEqualTo(1)
            assertThat(vertices[1]).isEqualTo(v1)
            assertThat(vertices.toIntArray()).containsExactly(0, 1, 2)
            assertThat(vertices.iterator().asSequence().toList()).containsExactly(v0, v1, v2)
        }

        assertThat(path.vertices).containsExactly(v0, v2, v1)
        assertThat(path.vertices.first()).isEqualTo(v0)
        assertThat(path.vertices.last()).isEqualTo(v1)
        assertThat(path.vertices.lastIndex).isEqualTo(2)
        assertThat(path.vertices.indexOf(v2)).isEqualTo(1)
        assertThat(path.vertices.lastIndexOf(v2)).isEqualTo(1)
        assertThat(path.vertices.indexOf(Vertex(9))).isEqualTo(-1)
        assertThat(path.vertices.toIntArray()).containsExactly(v0.id, v2.id, v1.id)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun sequencedEdgeAccessors(directed: Boolean) {
        construct(directed, indexEdges = true)

        for (edges in listOf(immutable.edges as EdgeSequencedSet, mutable.edges as EdgeSequencedSet)) {
            assertThat(edges.first()).isEqualTo(e01)
            assertThat(edges.last()).isEqualTo(e02)
            assertThat(edges.lastIndex).isEqualTo(2)
            assertThat(edges.indexOf(e12)).isEqualTo(1)
            assertThat(edges.lastIndexOf(e12)).isEqualTo(1)
            assertThat(edges[1]).isEqualTo(e12)
            assertThat(edges.toLongArray()).containsExactly(e01.id, e12.id, e02.id)
            assertThat(edges.iterator().asSequence().toList()).containsExactly(e01, e12, e02)
        }

        assertThat(path.edges).containsExactly(e02, e12)
        assertThat(path.edges.first()).isEqualTo(e02)
        assertThat(path.edges.last()).isEqualTo(e12)
        assertThat(path.edges.lastIndex).isEqualTo(1)
        assertThat(path.edges.indexOf(e12)).isEqualTo(1)
        assertThat(path.edges.lastIndexOf(e12)).isEqualTo(1)
        assertThat(path.edges.indexOf(e01)).isEqualTo(-1)
        assertThat(path.edges.toLongArray()).containsExactly(e02.id, e12.id)
    }

    // --- string forms -------------------------------------------------------------------------------------------------

    @Test
    fun identifierToString() {
        assertThat(Vertex(0).toString()).isEqualTo("Vertex(0x0)")
        assertThat(Vertex(10).toString()).isEqualTo("Vertex(0xa)")
        assertThat(Vertex(255).toString()).isEqualTo("Vertex(0xff)")
        assertThat(Vertex(-1).toString()).isEqualTo("Vertex(0xffffffff)")

        assertThat(Edge(0).toString()).isEqualTo("Edge(0)")
        assertThat(Edge(10).toString()).isEqualTo("Edge(10)")
        assertThat(Edge(-1).toString()).isEqualTo("Edge(-1)")
        assertThat(IdentityIndexedEdge(10).toString()).isEqualTo("Edge(10)")
        assertThat(CanonicalEdge.from(true, Vertex(1), Vertex(2)).toString()).isEqualTo("Edge(1 -> 2)")
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun collectionToString(directed: Boolean) {
        construct(directed, indexEdges = true)

        // sets print in braces, in index order for indexed sets (a path forest indexes its roots last)
        assertThat(mutable.vertices.toString()).isEqualTo("{Vertex(0x0), Vertex(0x1), Vertex(0x2)}")
        assertThat(immutable.vertices.toString()).isEqualTo("{Vertex(0x0), Vertex(0x1), Vertex(0x2)}")
        assertThat(tree.vertices.toString()).isEqualTo("{Vertex(0x2), Vertex(0x1), Vertex(0x0)}")
        assertThat(mutable.edges.toString()).isEqualTo("{${e01}, ${e12}, ${e02}}")
        assertThat(immutable.edges.toString()).isEqualTo("{${e01}, ${e12}, ${e02}}")
        assertThat(tree.edges.toString()).isEqualTo("{${e02}, ${e01}}")
        assertThat(emptyVertexSet().toString()).isEqualTo("{}")
        assertThat(emptyEdgeSet().toString()).isEqualTo("{}")
        assertThat(vertexSetOf(v1).toString()).isEqualTo("{Vertex(0x1)}")
        assertThat(edgeSetOf(e01).toString()).isEqualTo("{${e01}}")
    }

    // --- identifier conversions ---------------------------------------------------------------------------------------

    @Test
    fun canonicalEdgeEncodesEndpoints() {
        val a = Vertex(3)
        val b = Vertex(7)

        val directed = CanonicalEdge.from(true, b, a)
        assertThat(directed.source).isEqualTo(b)
        assertThat(directed.target).isEqualTo(a)
        assertThat(directed.opposite(a)).isEqualTo(b)
        assertThat(directed.opposite(b)).isEqualTo(a)
        assertThrows<IllegalArgumentException> { directed.opposite(Vertex(5)) }
        val (source, target) = directed
        assertThat(source).isEqualTo(b)
        assertThat(target).isEqualTo(a)
        assertThat(CanonicalEdge.from(true, a, b)).isNotEqualTo(directed)
        assertThat(CanonicalEdge.from(directed.toEdge())).isEqualTo(directed)

        // undirected edges normalise the endpoint order so that both spellings are the same edge
        val undirected = CanonicalEdge.from(false, b, a)
        assertThat(undirected).isEqualTo(CanonicalEdge.from(false, a, b))
        assertThat(setOf(undirected.source, undirected.target)).containsExactlyInAnyOrder(a, b)
        assertThat(undirected.opposite(a)).isEqualTo(b)
        assertThat(undirected.opposite(b)).isEqualTo(a)

        val loop = CanonicalEdge.from(false, a, a)
        assertThat(loop.source).isEqualTo(a)
        assertThat(loop.target).isEqualTo(a)
        assertThat(loop.opposite(a)).isEqualTo(a)

        // the edge ids of a canonical edge graph are canonical edges
        val graph = mutableGraph(false)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val edge = graph.addEdge(v1, v0)
        assertThat(edge).isEqualTo(CanonicalEdge.from(false, v0, v1).toEdge())
        assertThat(graph.edges.contains(CanonicalEdge.from(false, v1, v0).toEdge())).isTrue
    }

    @Test
    fun identityIndexedEdgeRoundTrips() {
        val edge = Edge(42)
        val indexed = IdentityIndexedEdge.from(edge)

        assertThat(indexed.id).isEqualTo(42)
        assertThat(indexed.toEdge()).isEqualTo(edge)
        assertThat(IdentityIndexedEdge(42)).isEqualTo(indexed)

        val mutable = mutableGraph(true, indexEdges = true)
        val v0 = mutable.addVertex()
        val v1 = mutable.addVertex()
        val e0 = mutable.addEdge(v0, v1)
        val e1 = mutable.addEdge(v1, v0)
        assertThat(e0).isEqualTo(IdentityIndexedEdge(0).toEdge())
        assertThat(e1).isEqualTo(IdentityIndexedEdge(1).toEdge())
        assertThat(mutable.edges).isInstanceOf(IdentityIndexedEdgeSet::class.java)
        val ie1 = IdentityIndexedEdge.from(e1)
        assertThat(mutable.edgeSource(ie1.toEdge())).isEqualTo(v1)
        assertThat(mutable.edgeTarget(ie1.toEdge())).isEqualTo(v0)
        assertThat(mutable.edgeOpposite(ie1.toEdge(), v0)).isEqualTo(v1)
        assertThat(mutable.edgeSource(ie1.toEdge(), v0)).isEqualTo(v1)
        assertThat(mutable.edgeTarget(ie1.toEdge(), v1)).isEqualTo(v0)
        assertThat(mutable.createEdgeReference(ie1.toEdge()).unstable).isEqualTo(e1)
        assertThat(mutable.edgeSource(IdentityIndexedEdge(0).toEdge(), v1)).isEqualTo(v0)
        assertThat(mutable.edgeTarget(IdentityIndexedEdge(0).toEdge(), v0)).isEqualTo(v1)
        assertThat(mutable.edgeOpposite(IdentityIndexedEdge(0).toEdge(), v0)).isEqualTo(v1)
        assertThrows<IllegalArgumentException> { mutable.edgeOpposite(IdentityIndexedEdge(0).toEdge(), Vertex(5)) }
    }

    @Test
    fun canonicalEdgeAccessors() {
        val mutable = mutableGraph(false)
        val v0 = mutable.addVertex()
        val v1 = mutable.addVertex()
        val edge = mutable.addEdge(v0, v1)
        assertThat(mutable.edges).isInstanceOf(CanonicalEdgeSet::class.java)

        val canonical = CanonicalEdge.from(edge)
        assertThat(mutable.edgeSource(canonical.toEdge(), v0)).isEqualTo(v1)
        assertThat(mutable.edgeTarget(canonical.toEdge(), v0)).isEqualTo(v1)
        assertThat(mutable.createEdgeReference(canonical.toEdge()).unstable).isEqualTo(edge)
        assertThat(mutable.edgeSource(edge, v0)).isEqualTo(v1)
        assertThat(mutable.edgeTarget(edge, v0)).isEqualTo(v1)
        assertThat(mutable.edgeSource(edge, mutable.createVertexReference(v1))).isEqualTo(v0)
        assertThat(mutable.edgeTarget(edge, mutable.createVertexReference(v1))).isEqualTo(v0)
    }
}
