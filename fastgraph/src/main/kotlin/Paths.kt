package io.github.sooniln.fastgraph


public interface PathTree {
    public val source: Vertex
    public val targets: VertexSet

    public fun path(target: Vertex): Path
}

public interface Path {
    public val vertices: VertexList
    public val edges: EdgeList

    public val source: Vertex get() = vertices[0]
    public val target: Vertex get() = vertices[vertices.lastIndex]

    public operator fun iterator(): Iterator<PathStep>
}

public interface PathStep {
    public val edge: Edge
    public val vertex: Vertex
}

internal data class SimplePathStep(override val edge: Edge, override val vertex: Vertex) : PathStep

internal class SimplePath(private val vertexIds: IntArray, private val edgeIds: LongArray) : Path {
    init {
        check(vertexIds.isNotEmpty())
        check(edgeIds.size == vertexIds.size - 1)
    }

    override val vertices: VertexList
        get() = vertexIds.asVertexList()
    override val edges: EdgeList
        get() = edgeIds.asEdgeList()

    override fun iterator(): Iterator<PathStep> = object : Iterator<PathStep> {
        private var index = 0

        override fun hasNext(): Boolean = index < edgeIds.size
        override fun next(): PathStep = SimplePathStep(Edge(edgeIds[index]), Vertex(vertexIds[++index]))
    }
}



