module io.github.sooniln.fastgraph.io {
    requires transitive kotlin.stdlib;
    requires transitive io.github.sooniln.fastgraph;
    requires java.xml;
    exports io.github.sooniln.fastgraph.io.csv;
    exports io.github.sooniln.fastgraph.io.graphml;
}
