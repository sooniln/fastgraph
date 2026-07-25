package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastgraph.Vertex

abstract class OwnedVertexProperty<V> : VertexProperty<V> {
    abstract fun onVertexAdded(vertex: Vertex)
    abstract fun onVertexRemoved(vertexId: Int)
}

abstract class OwnedIndexedVertexProperty<V> : OwnedVertexProperty<V>() {
    override fun onVertexRemoved(vertexId: Int) = onVertexReindexed(vertexId, vertexId)
    abstract fun onVertexReindexed(oldVertexIndex: Int, newVertexIndex: Int)
}
