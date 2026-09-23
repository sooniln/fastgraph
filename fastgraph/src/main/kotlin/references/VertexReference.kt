/**
 * Methods dealing with vertex references.
 */
@file:JvmName("VertexReferences")

package io.github.sooniln.fastgraph.references

import io.github.sooniln.fastgraph.Vertex

/**
 * A *stable* reference to a vertex. This reference is guaranteed to never be invalidated when mutations are made to the
 * graph topology. A stable reference can be obtained through [io.github.sooniln.fastgraph.Graph.createVertexReference]. [VertexReference] is
 * generally a less efficient representation than [io.github.sooniln.fastgraph.Vertex], in terms of both memory and CPU. Prefer [io.github.sooniln.fastgraph.Vertex] unless
 * reference stability across mutations is a requirement.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface VertexReference {

    /**
     * An unstable [io.github.sooniln.fastgraph.Vertex] reference corresponding to this stable reference.
     */
    @get:JvmName("unstable")
    public val unstable: Vertex
}
