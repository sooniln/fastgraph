package io.github.sooniln.fastgraph

import com.google.common.graph.GraphBuilder
import com.google.common.graph.NetworkBuilder
import com.google.common.graph.ValueGraphBuilder
import io.github.sooniln.fastgraph.internal.nothingEdgeProperty
import org.jgrapht.Graphs
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.builder.GraphTypeBuilder
import java.util.zip.ZipInputStream
import kotlin.random.Random

object Loader {

    private const val RANDOM_SEED = 10099

    const val NUM_VERTICES = 168114
    const val NUM_EDGES = 6797557

    fun loadGuavaSimpleGraph(): com.google.common.graph.ImmutableGraph<Int> =
        load { numVertices, _, lineSequence ->
            val builder = GraphBuilder.undirected().expectedNodeCount(numVertices).immutable<Int>()
            lineSequence.forEach { (v1, v2, _) -> builder.putEdge(v1, v2) }
            val graph = builder.build()

            check(graph.nodes().size == NUM_VERTICES)
            check(graph.edges().size == NUM_EDGES)

            return@load graph
        }

    fun loadGuavaGraph(): com.google.common.graph.ImmutableValueGraph<Int, Float> =
        load { numVertices, _, lineSequence ->
            val builder = ValueGraphBuilder.undirected().expectedNodeCount(numVertices).immutable<Int, Float>()
            lineSequence.forEach { (v1, v2, e) -> builder.putEdgeValue(v1, v2, e) }
            val graph = builder.build()

            check(graph.nodes().size == NUM_VERTICES)
            check(graph.edges().size == NUM_EDGES)

            return@load graph
        }

    class GuavaWeightedEdge(val weight: Float)

    fun loadGuavaNetwork(): com.google.common.graph.ImmutableNetwork<Int, GuavaWeightedEdge> =
        load { numVertices, _, lineSequence ->
            val builder = NetworkBuilder.undirected().expectedNodeCount(numVertices).immutable<Int, GuavaWeightedEdge>()
            lineSequence.forEach { (v1, v2, e) -> builder.addEdge(v1, v2, GuavaWeightedEdge(e)) }
            val graph = builder.build()

            check(graph.nodes().size == NUM_VERTICES)
            check(graph.edges().size == NUM_EDGES)

            return@load graph
        }

    fun loadJGraphTSimpleGraph(): org.jgrapht.Graph<Int, DefaultEdge> = load { _, _, lineSequence ->
        val graph = GraphTypeBuilder
            .undirected<Int, DefaultEdge>()
            .allowingMultipleEdges(false)
            .allowingSelfLoops(true)
            .edgeClass(DefaultEdge::class.java)
            .weighted(false)
            .buildGraph()
        lineSequence.forEach { (v1, v2, _) ->
            Graphs.addEdgeWithVertices(graph, v1, v2)
        }

        check(graph.vertexSet().size == NUM_VERTICES)
        check(graph.edgeSet().size == NUM_EDGES)

        return@load graph
    }

    class JGraphWeightedEdge(val weight: Float) : DefaultEdge()

    fun loadJGraphTGraph(): org.jgrapht.Graph<Int, JGraphWeightedEdge> = load { _, _, lineSequence ->
        var edgeValue = 0f
        val graph = GraphTypeBuilder
            .undirected<Int, JGraphWeightedEdge>()
            .allowingMultipleEdges(false)
            .allowingSelfLoops(true)
            .edgeClass(JGraphWeightedEdge::class.java)
            .edgeSupplier { JGraphWeightedEdge(edgeValue) }
            .weighted(false)
            .buildGraph()
        lineSequence.forEach { (v1, v2, e) ->
            edgeValue = e
            Graphs.addEdgeWithVertices(graph, v1, v2)
        }

        check(graph.vertexSet().size == NUM_VERTICES)
        check(graph.edgeSet().size == NUM_EDGES)

        return@load graph
    }

    fun loadJGraphTNetwork(): org.jgrapht.Graph<Int, JGraphWeightedEdge> = load { _, _, lineSequence ->
        var edgeValue = 0f
        val graph = GraphTypeBuilder
            .undirected<Int, JGraphWeightedEdge>()
            .allowingMultipleEdges(true)
            .allowingSelfLoops(true)
            .edgeClass(JGraphWeightedEdge::class.java)
            .edgeSupplier { JGraphWeightedEdge(edgeValue) }
            .weighted(false)
            .buildGraph()
        lineSequence.forEach { (v1, v2, e) ->
            edgeValue = e
            Graphs.addEdgeWithVertices(graph, v1, v2)
        }

        check(graph.vertexSet().size == NUM_VERTICES)
        check(graph.edgeSet().size == NUM_EDGES)

        return@load graph
    }

    fun loadImmutableSimpleGraph(): ImmutableGraphAndProperties<Int, Nothing> =
        load { numVertices, numEdges, lineSequence ->
            val g = immutableGraph<Int, Nothing>(false).withVertexProperty().build {
                ensureVertexCapacity(numVertices)
                ensureEdgeCapacity(numEdges)
                lineSequence.forEach { (v1, v2, _) -> addEdge(v1, v2) }
            }

            check(g.graph.vertices.size == NUM_VERTICES)
            check(g.graph.edges.size == NUM_EDGES)

            return@load g
        }

    fun loadImmutableGraph(): ImmutableGraphAndProperties<Int, Float> = load { numVertices, numEdges, lineSequence ->
        val g = immutableGraph<Int, Float>(false).withVertexProperty().withEdgeProperty().build {
            ensureVertexCapacity(numVertices)
            ensureEdgeCapacity(numEdges)
            lineSequence.forEach { (v1, v2, e) -> addEdge(v1, v2, e) }
        }

        check(g.graph.vertices.size == NUM_VERTICES)
        check(g.graph.edges.size == NUM_EDGES)

        return@load g
    }

    fun loadImmutableNetwork(): ImmutableGraphAndProperties<Int, Float> = load { numVertices, numEdges, lineSequence ->
        val g = immutableGraph<Int, Float>(false, multiEdge = true).withVertexProperty().withEdgeProperty().build {
            ensureVertexCapacity(numVertices)
            ensureEdgeCapacity(numEdges)
            lineSequence.forEach { (v1, v2, e) -> addEdge(v1, v2, e) }
        }

        check(g.graph.vertices.size == NUM_VERTICES)
        check(g.graph.edges.size == NUM_EDGES)

        return@load g
    }

    fun loadMutableSimpleGraph(): GraphAndProperties = load { numVertices, numEdges, lineSequence ->
        val graph = mutableGraph(false)
        val vertexProperty = graph.createVertexProperty<Int> { 0 }
        buildGraph<Int, Float>(graph, vertexProperty) {
            ensureVertexCapacity(numVertices)
            ensureEdgeCapacity(numEdges)
            lineSequence.forEach { (v1, v2) -> addEdge(v1, v2) }
        }

        check(graph.vertices.size == NUM_VERTICES)
        check(graph.edges.size == NUM_EDGES)

        return GraphAndProperties(graph, vertexProperty, nothingEdgeProperty())
    }

    fun loadMutableGraph(): GraphAndProperties = load { numVertices, numEdges, lineSequence ->
        val graph = mutableGraph(false)
        val vertexProperty = graph.createVertexProperty<Int> { 0 }
        val edgeProperty = graph.createEdgeProperty<Float> { 0f }
        buildGraph(graph, vertexProperty, edgeProperty) {
            ensureVertexCapacity(numVertices)
            ensureEdgeCapacity(numEdges)
            lineSequence.forEach { (v1, v2, e) -> addEdge(v1, v2, e) }
        }

        check(graph.vertices.size == NUM_VERTICES)
        check(graph.edges.size == NUM_EDGES)

        return GraphAndProperties(graph, vertexProperty, edgeProperty)
    }

    fun loadMutableNetwork(): GraphAndProperties = load { numVertices, numEdges, lineSequence ->
        val graph = mutableGraph(false, multiEdge = true)
        val vertexProperty = graph.createVertexProperty<Int> { 0 }
        val edgeProperty = graph.createEdgeProperty<Float> { 0f }
        buildGraph(graph, vertexProperty, edgeProperty) {
            ensureVertexCapacity(numVertices)
            ensureEdgeCapacity(numEdges)
            lineSequence.forEach { (v1, v2, e) -> addEdge(v1, v2, e) }
        }

        check(graph.vertices.size == NUM_VERTICES)
        check(graph.edges.size == NUM_EDGES)

        return GraphAndProperties(graph, vertexProperty, edgeProperty)
    }

    data class GraphAndProperties(
        val graph: Graph,
        val vertexProperty: VertexProperty<Int>,
        val edgeProperty: EdgeProperty<Float>
    )

    private inline fun <T> load(loader: (Int, Int, Sequence<Edge>) -> T): T {
        val random = Random(RANDOM_SEED)
        ZipInputStream(this::class.java.getResourceAsStream("/large_twitch_edges.zip")!!).use { zis ->
            require(zis.nextEntry!!.name == "large_twitch_edges.csv")
            val inputStream = zis.bufferedReader()

            val vertexCapacity = inputStream.readLine().toInt()
            val edgeCapacity = inputStream.readLine().toInt()

            return loader(vertexCapacity, edgeCapacity, inputStream.lineSequence().map { line ->
                val vs = line.split(",")
                return@map Edge(vs[0].toInt(), vs[1].toInt(), random.nextFloat())
            })
        }
    }

    private data class Edge(val v1: Int, val v2: Int, val e: Float)
}
