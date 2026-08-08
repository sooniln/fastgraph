package io.github.sooniln.fastgraph

@Suppress("INAPPLICABLE_JVM_NAME")
public sealed interface Homomorphism {
    public val inputGraph: Graph
    public val outputGraph: Graph

    @JvmName("getVertex")
    public operator fun get(vertex: Vertex): Vertex
    @JvmName("getEdge")
    public operator fun get(edge: Edge): Edge
}

public sealed interface BijectiveHomomorphism : Homomorphism

public sealed interface Isomorphism : BijectiveHomomorphism {
    public fun inverse(): Isomorphism
}

@Suppress("INAPPLICABLE_JVM_NAME")
public class IdentityIsomorphism(
    override val inputGraph: Graph,
    override val outputGraph: Graph
) : Isomorphism {

    init {
        require(inputGraph !== outputGraph)
    }

    @JvmName("getVertex")
    override fun get(vertex: Vertex): Vertex = vertex
    @JvmName("getEdge")
    override fun get(edge: Edge): Edge = edge

    override fun inverse(): IdentityIsomorphism = IdentityIsomorphism(outputGraph, inputGraph)
}
