# Getting Started

This document attempts to explain the basic concepts behind FastGraph and covers many of the APIs. It is not a complete
reference however, please see the code documentation for more extensive information.

## Basic Concepts

FastGraph's most basic component are `Graph`, `Vertex` and `Edge`. The `Graph` class represents a graph topology (but
not associated data) - a graph consists of a set of vertices and edges. These are represented by the `Vertex` and `Edge`
classes respectively. Note that these are both inline value classes for efficiency - `Vertex` is a wrapper around `int`
and `Edge` is a wrapper around `long`. When accessing these APIs through other JVM languages it's thus likely that these
will be exposed as the raw types, rather than the Kotlin wrapper classes.

From this it's important to note that `Vertex` and `Edge` by themselves have no sense of ownership - you cannot
determine what `Graph` a vertex or edge belongs to in isolation. It is the client's responsibility to keep track of
vertices and edges and what graph they belong to. `Graph` implementations make a best effort to throw exceptions if
passed in an invalid vertex or edge, but this is only a best effort.

With a `Graph` you can obtain the set of vertices and edges:

```kotlin
val graph = ...

for (vertex in graph.vertices) {
    ...
}
for (edge in graph.edges) {
    ...
}
```

Iteration here is very efficient and avoids any unnecessary boxing/unboxing penalties that you might normally see with
value class iteration.

Graphs can be either directed or undirected (as specified at construction time). In addition, `Graph` supports
self-loops (edges connecting the same vertex to itself) and multi-edges (multiple edges that connect the same pair of
vertices in the same direction). Note that multi-edge support must generally be specified at construction time however.

Some examples of the variety of operations you can perform to get topological information out of a graph:

```kotlin
val graph = ...

val myVertex = ...
for (vertex in graph.successors(myVertex)) {
    ...
}
for (vertex in graph.predecessors(myVertex)) {
    ...
}
for (edge in graph.outgoingEdges(myVertex)) {
    ...
}
for (edge in graph.incomingEdges(myVertex)) {
    ...
}

val otherVertex = ...
if (graph.containsEdge(myVertex, otherVertex)) {
    ...
}
for (edge in graph.getEdges(myVertex, otherVertex)) {
    ...
}

val edge = graph.getEdge(myVertex, otherVertex)
val sourceVertex = graph.edgeSource(edge)
val targetVertex = graph.edgeTarget(edge)
```

FastGraph also provides more convenient accessors that take advantage of Kotlin context APIs. The above example can also
be expressed as:

```kotlin
val graph = ...

context(graph) {
    val myVertex = ...
    for (vertex in myVertex.successors()) {
        ...
    }
    for (vertex in myVertex.predecessors()) {
        ...
    }
    for (edge in myVertex.outgoingEdges()) {
        ...
    }
    for (edge in myVertex.incomingEdges()) {
        ...
    }

    val otherVertex = ...
    val edge = graph.getEdge(myVertex, otherVertex)
    val sourceVertex = edge.source
    val targetVertex = edge.target
}
```

## Graph Data

Graph topology can be quite interesting in and of itself, but usually we have some data associated with vertices and
edges. FastGraph achieves this via the `VertexProperty` and `EdgeProperty` classes. These are conceptually maps from a
vertex/edge to some piece of data. Every `Graph` instance allows the creation of new properties via
`Graph.createVertexProperty()` and `Graph.createEdgeProperty`. Since the vertex/edge property is separate from the graph
itself, properties can be created on immutable graphs. These properties can then be read/written to via `Vertex` and
`Edge` keys. Creating a property usually requires supplying an initializer as well, so that a vertex/edge will have an
assigned value even if it's never been written to before. Note that initializers are stored for the lifetime of the
property, and thus can be a source of memory leaks...

```kotlin
val graph = ...

val vertexId = graph.createVertexProperty<Int> { 0 }
val edgeWeight = graph.createEdgeProperty<Float> { 0f }

val myVertex = ...
vertexId[myVertex] = 99
println(vertexId[myVertex])

val myEdge = ...
edgeWeight[myEdge] = 5.0f
println(edgeWeight[myEdge])

// context APIs can also be used for a more convenient form
context(vertexId, edgeWeight) {
    myVertex.property = 99
    myEdge.property = 5.0f
    println(myVertex.property)
    println(myEdge.property)
}
```

`VertexProperty` and `EdgeProperty` instances constructed through a `Graph` instance are guaranteed to remain in sync
with the graph. If a vertex or edge is added/removed from the graph, then it will appear/disappear from all graph
properties.

### Properties in Algorithms

The ability for any client to create a property on a graph solves a common problem in implementing many graph
algorithms - many algorithms associate data with edges or vertices, but the algorithm itself has no knowledge of the
internal layout of the graph in memory, and thus is forced to use inefficient data structures that can slow down the
algorithm. Consider a classical simple implementation of breadth first search (similar to any of the 100s of
results you can find by Googling this):

```kotlin
fun bfs(graph: Graph, start: Vertex) {
        val queue = LinkedList<Vertex>()
        val visited = HashSet<Vertex>()

        queue.add(start)
        visited.add(start)

        println("BFS Traversal starting from vertex " + start + ":")
        while (!queue.isEmpty()) {
            val next = queue.poll()
            print("$next ")
            for (successor in graph.successors(next)) {
                if (!visited.contains(successor)) {
                    visited.add(successor)
                    queue.add(successor)
                }
            }
        }
        println("\nTraversal complete.")
    }
```

Ignoring the obvious inefficiencies of using LinkedList for the queue here (this is modeled from Google results after
all), note that we use a HashSet to track the visited vertices. For most graph libraries (including JGraph and Guava
Graphs) this is the obvious and perhaps only solution, since so further assumptions can be made about graph internals.

Consider how one might write the same algorithm with a `VertexProperty` however:

```kotlin
fun bfs(graph: Graph, start: Vertex) {
    val queue = LinkedList<Vertex>()
    val visited = graph.createVertexProperty { false }

    queue.add(start)
    visited.add(start)

    println("BFS Traversal starting from vertex " + start + ":")
    while (!queue.isEmpty()) {
        val next = queue.poll()
        print("$next ")
        for (successor in graph.successors(next)) {
            if (!visited[successor]) {
                visited[successor] = true
                queue.add(successor)
            }
        }
    }
    println("\nTraversal complete.")
}
```

Now, for example, if the graph knows that it stores vertices in an indexed format from 0..maxVertex for example, the
visited property can be implemented via a boolean array. Indexing and retrieval into a boolean array is practically
guaranteed to be both substantially faster and use much less memory than a hashmap (improving cache hit rates), speeding
up the algorithm.

## Graph Mutations

Using the `mutableGraph()` factory method will return a `MutableGraph` - a graph that also allows for modifications such
as adding/removing vertices and edges. `Graph` and `MutableGraph` function in much the same way as Kotlin offers `List`
and `MutableList` (or any other collection type). Methods that do not need to modify a graph should accept `Graph` as
input, not `MutableGraph`, and so on and so forth.

`MutableGraph` offers several methods to mutate graph topology:

```kotlin
val mutableGraph = mutableGraph(directed = false)

// create and return a new vertex
val vertex1 = mutableGraph.addVertex()
val vertex2 = mutableGraph.addVertex()

// create and return a new edge
val edge1 = mutableGraph.addEdge(vertex1, vertex1)
val edge2 = mutableGraph.addEdge(vertex1, vertex2)

// remove a vertex (this will also remove all edges that reference this vertex)
// edge2 is also removed after this statement
mutableGraph.removeVertex(vertex2)

// remove an edge
mutableGraph.removeEdge(edge1)
```

> [!CAUTION]
> There are important caveats to consider when making changes to graph topology which we will consider in the next
> section on reference stability!

## Vertex/Edge Reference Stability

FastGraph supports 2 types of references:

* `Vertex` and `Edge` - unstable references (generally referred to simply as references here)
* `VertexReference` and `EdgeReference` - stable references

**Unstable references are not guaranteed to be valid after changes to the graph topology!** The exact details of what
invalidates unstable references are decided by the graph implementation (and should be documented appropriately). The
graph implementations provided by FastGraph guarantee that the only ways an unstable reference can be invalidated are:

* `Vertex` references can only be invalidated if some other vertex is removed.
* `Edge` references can only be invalidated if a vertex or some other edge is removed.
* Removing a vertex may invalidate all `Vertex` and `Edge` references in the same graph.
* Removing an edge may invalidate all `Edge` references in the same graph.
* Adding a vertex/edge will never invalidate any references.

This means our prior example of mutating graph topology was actually dangerous:

```kotlin
val mutableGraph = mutableGraph(directed = false)

val vertex1 = mutableGraph.addVertex()
val vertex2 = mutableGraph.addVertex()

// WARNING: this will invalidate the vertex2 reference after this statement
mutableGraph.removeVertex(vertex1)

// ERROR: this is undefined behavior and may throw an exception or worse, make unintended changes to the graph
// NOTE: graphs make a best effort to detect invalid references and throw exceptions, but this is not always possible
mutableGraph.removeVertex(vertex2)
```

> [!IMPORTANT]
> The takeaway here is, are you performing subtractive changes (removing a vertex or edge) to graph topology on
> FastGraph implementations? If no, then using `Vertex` and `Edge` references is perfectly safe, and the most efficient
> choice. If yes, then you may want to consider using stable references like `VertexReference` and `EdgeReference`
> instead.

Stable references such as `VertexReference` and `EdgeReference` are guaranteed to remain valid across any possible
changes. The only thing that will invalidate `VertexReference` and `EdgeReference` is removing the vertex/edge that the
reference points to from the graph, and that will only affect the removed reference and no other references. The caveat
however, is that `VertexReference` and `EdgeReference` are more expensive in terms of both memory and CPU than `Vertex`
and `Edge`, and should thus generally be used sparingly and only when actually necessary.

A stable reference can be obtained via `Graph.createVertexReference()` and `Graph.createEdgeReference()` (or via the
context APIs `Vertex.createReference()` and `Edge.createReference()`). For example:

```kotlin
val mutableGraph = mutableGraph(directed = false)

context(graph) {
    val vertex1Ref = mutableGraph.addVertex().createReference()
    val vertex2Ref = mutableGraph.addVertex().createReference()

    // this will not invalidate vertex2Ref since it is a stable reference
    // this will invalidate vertex1Ref since it's being removed from the graph
    mutableGraph.removeVertex(vertex1Ref)

    // this is now safe and will always function as intended
    mutableGraph.removeVertex(vertex2Ref)
}
```

## Immutable Graphs

FastGraph provides an `ImmutableGraph` representation which conforms to standard immutable guarantees:

* **Shallow immutability:** Vertices and edge can never be added to or removed from `ImmutableGraph`.
* **Deterministic iteration:** The iteration order of vertices and edges in `ImmutableGraph` will never change.
* **Thread safety**: It is safe to access `ImmutableGraph` concurrently from multiple threads.
* **Integrity**: `ImmutableGraph` cannot be implemented outside the fastgraph package (which would allow these
  guarantees to be violated).

Immutable graphs can be constructed via the `immutableGraph()` factory method and `ImmutableGraphBuilder`. For example:

```kotlin
// to construct an immutable graph with no vertex or edge properties
val graph = immutableGraph<String, Nothing>(directed = false).build {
    ensureVertexCapacity(3)
    ensureEdgeCapacity(3)
    addEdge("vertex1", "vertex2")
    addEdge("vertex2", "vertex3")
    addEdge("vertex3", "vertex1")
}.graph

// to construct an immutable graph with vertex or edge properties
val graphAndProperties = immutableGraph<String, Float>(directed = false).withVertexProperty().withEdgeProperty().build {
    ensureVertexCapacity(3)
    ensureEdgeCapacity(3)
    addEdge("vertex1", "vertex2", 1f)
    addEdge("vertex2", "vertex3", 2f)
    addEdge("vertex3", "vertex1", 1f)
}
val graph = graphAndProperties.graph
val vertexName = graphAndProperties.vertexProperty
val edgeWeight = graphAndProperties.edgeProperty
```

`ImmutableGraph` is the most CPU and memory efficient of all graph representations for the most part, and should be
preferred if graph topology mutation is not required.

## Implementation Details

The following discussion of FastGraph implementation details is accurate at the time of this writing, but is not
guaranteed to always be accurate (since these are implementation details after all).

### Internal Representations

Internally, FastGraph generally chooses between two implementations of the `Graph` interface:

* Graph implementations (confusingly named the same as the interface)
    * Use less memory to represent the graph topology
    * Do not support multi-edges.
    * Generally do not treat edges as first class objects, so edge operations and values are expected to be slower.
* Network implementations
    * Use more memory to represent the graph topology
    * Support multi-edges.
    * Treat edges as first class objects, so edge operations and values are expected to be faster.
    * Can often represent edge property values in a more memory efficient manner. Since properties can often use more
      memory than topology, this means that using network implementations can sometimes reduce the total amount of
      memory
      used by a graph and properties, even if more memory is required by the topology.

Graph factory methods such as `mutableGraph()` and `immutableGraph()` generally allow for hints such `optimizeEdges`
which can force the use of a network implementation.

### Memory Saving Tricks

FastGraph `Graph` implementations generally attempt to save memory by only loading the indexes that require incoming
edge information on demand. I.e., after construction, the methods which do not require incoming edge information
(`outDegree()`, `successors()`, and `outgoingEdges()`) can be called without any extra overhead (besides the overhead
inherent in the method itself). However, the first time a method is called which requires incoming edge information
(`inDegree()`, `predecessors()`, and `incomingEdges()`), the graph implementation will load the incoming edge index into
memory (growing the in-memory size of the graph), and then proceeds to call the method normally. The index remains in
memory for the duration of the `Graph`, and further calls to those methods proceed as usual.

Since undirected graphs do not have 'incoming' edges (or alternatively since incoming edges are the same as outgoing
edges in undirected graphs), they do not display the above behavior.

> [!TIP]
> If your algorithms do not require incoming edge information, do not call any of the methods which require it in order
> to avoid growing the in-memory size of the graph (and paying the one-time cost of loading the incoming edge index).
