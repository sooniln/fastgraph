# Module FastGraph JGraphT

Adapter exposing FastGraph graphs as [Graph][org.jgrapht.Graph] instances, so that JGraphT's library of graph algorithms can
be run directly against a FastGraph graph. The views are live and never copy the underlying graph. See
[asJGraphT][io.github.sooniln.fastgraph.jgrapht.asJGraphT] for an unmodifiable view and
[asMutableJGraphT][io.github.sooniln.fastgraph.jgrapht.asMutableJGraphT] for a view that also supports JGraphT's mutation methods.
