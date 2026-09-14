# Module FastGraph IO

Readers and writers for several common graph file formats for the FastGraph library. Each format loads a
[Graph][io.github.sooniln.fastgraph.Graph] together with the vertex and edge properties found in the document, and can write
a graph and a selection of its properties back out. [PropertyBinding][io.github.sooniln.fastgraph.io.PropertyBinding] describes how a
document attribute is parsed into a property.

# Package io.github.sooniln.fastgraph.io

Shared types used by all formats.

# Package io.github.sooniln.fastgraph.io.csv

CSV edge lists - one edge per record, with optional edge property columns.

# Package io.github.sooniln.fastgraph.io.dot

The Graphviz DOT language.

# Package io.github.sooniln.fastgraph.io.graphml

GraphML XML documents.
