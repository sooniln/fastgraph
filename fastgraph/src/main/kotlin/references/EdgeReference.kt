/**
 * Methods dealing with edge references.
 */
@file:JvmName("EdgeReferences")

package io.github.sooniln.fastgraph.references

import io.github.sooniln.fastgraph.Edge

/**
 * A *stable* reference to an edge. This reference is guaranteed to never be invalidated when mutations are made to the
 * graph topology. A stable reference can be obtained through [io.github.sooniln.fastgraph.Graph.createEdgeReference]. [EdgeReference] is generally
 * a less efficient representation than [io.github.sooniln.fastgraph.Edge], in terms of both memory and CPU. Prefer [io.github.sooniln.fastgraph.Edge] unless reference
 * stability across mutations is a requirement.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface EdgeReference {

    /**
     * An unstable [io.github.sooniln.fastgraph.Edge] reference corresponding to this stable reference.
     */
    @get:JvmName("unstable")
    public val unstable: Edge
}
