package io.github.sooniln.fastgraph.io.dot.internal

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.io.ParsingEdgeProperty
import io.github.sooniln.fastgraph.io.ParsingVertexProperty
import io.github.sooniln.fastgraph.io.TypeBinding
import io.github.sooniln.fastgraph.io.dot.MutableDotGraph
import io.github.sooniln.fastgraph.mutableGraph

/**
 * Hand-rolled recursive-descent parser for the "practical subset" of the DOT grammar this library supports: flat
 * graph/digraph with node/edge statements (including chaining and subgraph endpoints), graph/node/edge default
 * attribute statements with correct nested scoping, and `strict` duplicate-edge merging. Subgraphs are flattened -
 * their node ids become ordinary vertices in the one graph, and subgraph/cluster identity itself is discarded.
 */
internal class DotParser(private val lexer: DotLexer) {

    // Lexer cursor (one token of lookahead).
    private var current: Token = lexer.nextToken()

    // Accumulated across the whole parse. A recursive-descent parser for a recursively-nested grammar
    // (subgraphs contain statements, which can contain subgraphs) naturally needs shared state that's
    // threaded implicitly via fields rather than passed through every call - unlike GraphML's flat,
    // two-level XML walk, which doesn't need this.
    private var strict = false
    private lateinit var graph: MutableGraph
    private val vertexIds = HashMap<Any, Vertex>()
    private lateinit var vertexIdProperty: ParsingVertexProperty<Any>
    private lateinit var propertyTypes: Map<String, TypeBinding<*>>
    private val vertexProperties = HashMap<String, ParsingVertexProperty<*>>()
    private val edgeProperties = HashMap<String, ParsingEdgeProperty<*>>()
    private var scope = Scope(null)
    private val graphAttributes = HashMap<String, Any?>()
    private var subgraphDepth = 0

    private fun advance(): Token {
        val token = current
        current = lexer.nextToken()
        return token
    }

    private fun expect(type: TokenType) {
        if (current.type != type) parseError("expected $type, found ${describe(current)}")
        advance()
    }

    private fun describe(token: Token): String =
        if (token.type == TokenType.EOF) "end of input" else "\"${token.text}\" (${token.type})"

    private fun parseError(message: String): Nothing =
        throw IllegalArgumentException("$message (line ${current.line}:${current.column})")

    /**
     * Runs [block], annotating any [RuntimeException] it throws with the position of the token currently being
     * examined - used to give value-conversion and graph-mutation failures (which don't call [parseError]
     * themselves) the same positioned-message treatment as syntax errors, while preserving the original exception
     * as the cause.
     */
    private inline fun <T> parsingValue(block: () -> T): T =
        try {
            block()
        } catch (e: RuntimeException) {
            throw IllegalArgumentException("${e.message} (line ${current.line}:${current.column})", e)
        }

    fun parse(
        multiEdge: Boolean,
        indexEdges: Boolean,
        nodeIdType: TypeBinding<Any>,
        attributeTypes: Map<String, TypeBinding<*>>
    ): MutableDotGraph {
        strict = if (current.type == TokenType.STRICT) {
            advance()
            true
        } else {
            false
        }
        val directed = when (current.type) {
            TokenType.DIGRAPH -> true
            TokenType.GRAPH -> false
            else -> parseError("expected \"graph\" or \"digraph\", found ${describe(current)}")
        }
        advance()
        val id = if (current.type == TokenType.ID) advance().text else null

        graph = mutableGraph(directed, multiEdge && !strict, indexEdges)
        vertexIdProperty = ParsingVertexProperty(graph, nodeIdType)
        propertyTypes = attributeTypes

        expect(TokenType.LBRACE)
        parseStatementList()
        expect(TokenType.RBRACE)
        if (current.type != TokenType.EOF) parseError("unexpected content after closing '}'")

        return MutableDotGraph(
            graph,
            id,
            vertexIdProperty.property,
            vertexProperties.mapValues { it.value.property },
            edgeProperties.mapValues { it.value.property },
            graphAttributes)
    }

    private fun parseStatementList(): LinkedHashSet<String> {
        val ids = LinkedHashSet<String>()
        while (current.type != TokenType.RBRACE && current.type != TokenType.EOF) {
            ids += parseStatement()
            if (current.type == TokenType.SEMICOLON) advance()
        }
        return ids
    }

    // Statement dispatch, mirroring the DOT grammar's `stmt` production:
    //   parseStatement -> parsePropertyStatement   ("graph"/"node"/"edge" [attr_list])
    //                   -> parseSubgraph            ("subgraph"? id? '{' ... '}', recurses via parseStatementList)
    //                   -> parseIdStatement          (ID '=' ID, or ID [port] [attr_list], or the start of an edge_stmt)
    //                        -> parseEdgeStatementTail (chains via parseEndpoint, which itself can recurse into parseSubgraph)
    private fun parseStatement(): Set<String> = when (current.type) {
        // "graph [...]" at the top level populates graphAttrs. Inside a nested subgraph it has nowhere to live -
        // subgraph/cluster identity is already discarded elsewhere - so it's parsed for correct token consumption,
        // then discarded, same as always.
        TokenType.GRAPH -> if (subgraphDepth == 0) {
                    val attributes = HashMap<String, String>()
                    parsePropertyStatement(attributes).also {
                        for ((attribute, value) in attributes) {
                            graphAttributes[attribute] =
                                parsingValue { propertyTypes.getOrDefault(attribute, TypeBinding.string).parser(value) }
                        }
                    }
                } else {
                    parsePropertyStatement(null)
                }
        TokenType.NODE -> parsePropertyStatement(scope.nodeDefaults)
        TokenType.EDGE -> parsePropertyStatement(scope.edgeDefaults)
        TokenType.SUBGRAPH, TokenType.LBRACE -> parseSubgraph()
        TokenType.ID -> parseIdStatement()
        else -> parseError("unexpected token ${describe(current)}")
    }

    private fun parsePropertyStatement(target: MutableMap<String, String>?): Set<String> {
        advance() // consume "graph"/"node"/"edge"
        val attrs = parsePropertyList()
        target?.putAll(attrs)
        return emptySet()
    }

    /** Parses zero or more bracketed `[a_list]` groups, merging them left-to-right (later values win). */
    private fun parsePropertyList(): Map<String, String> {
        val attrs = HashMap<String, String>()
        while (current.type == TokenType.LBRACKET) {
            advance()
            while (current.type != TokenType.RBRACKET) {
                val key = expectIdToken()
                expect(TokenType.EQUALS)
                val value = expectIdToken()
                attrs[key] = value
                if (current.type == TokenType.SEMICOLON || current.type == TokenType.COMMA) advance()
            }
            advance() // consume ']'
        }
        return attrs
    }

    /** Reads an ID token, joining any `+`-concatenated adjacent string literals into one value. */
    private fun expectIdToken(): String {
        if (current.type != TokenType.ID) parseError("expected an identifier, found ${describe(current)}")
        val sb = StringBuilder(advance().text)
        while (current.type == TokenType.PLUS) {
            advance()
            if (current.type != TokenType.ID) parseError("expected an identifier after '+', found ${describe(current)}")
            sb.append(advance().text)
        }
        return sb.toString()
    }

    /** Ports and compass points (`id:port`, `id:port:compass`, `id:compass`) are parsed and discarded. */
    private fun skipOptionalPort() {
        if (current.type == TokenType.COLON) {
            advance()
            expectIdToken()
            if (current.type == TokenType.COLON) {
                advance()
                expectIdToken()
            }
        }
    }

    private fun parseIdStatement(): Set<String> {
        val nodeId = expectIdToken()
        if (current.type == TokenType.EQUALS) {
            advance()
            val value = expectIdToken() // plain "ID = ID" graph attribute shorthand, same as "graph [...]"
            if (subgraphDepth == 0) {
                graphAttributes[nodeId] = parsingValue { propertyTypes.getOrDefault(nodeId, TypeBinding.string).parser(value) }
            }
            return emptySet()
        }
        skipOptionalPort()
        return if (current.type == TokenType.EDGEOP_DIRECTED || current.type == TokenType.EDGEOP_UNDIRECTED) {
            parseEdgeStatementTail(setOf(nodeId))
        } else {
            val vertex = resolveVertex(nodeId)
            applyVertexProperties(vertex, mergeProperties(scope.nodeDefaults, parsePropertyList()))
            setOf(nodeId)
        }
    }

    private fun parseEndpoint(): Set<String> {
        return if (current.type == TokenType.SUBGRAPH || current.type == TokenType.LBRACE) {
            parseSubgraph()
        } else {
            val id = expectIdToken()
            skipOptionalPort()
            setOf(id)
        }
    }

    /**
     * Builds the chain of endpoint sets for `A -> B -> C` (chaining) and `A -> {B C}` (subgraph fan-out) alike,
     * then creates an edge for every pair in the cross product of each consecutive pair of endpoint sets.
     */
    private fun parseEdgeStatementTail(first: Set<String>): Set<String> {
        val chain = mutableListOf(first)
        val allIds = LinkedHashSet(first)
        while (current.type == TokenType.EDGEOP_DIRECTED || current.type == TokenType.EDGEOP_UNDIRECTED) {
            val expectedOp = if (graph.directed) TokenType.EDGEOP_DIRECTED else TokenType.EDGEOP_UNDIRECTED
            if (current.type != expectedOp) {
                parseError(
                    "${if (graph.directed) "digraph" else "graph"} requires \"" +
                        "${if (graph.directed) "->" else "--"}\", found \"${current.text}\""
                )
            }
            advance()
            val next = parseEndpoint()
            chain += next
            allIds += next
        }
        val attrs = mergeProperties(scope.edgeDefaults, parsePropertyList())
        for (i in 0 until chain.size - 1) {
            for (srcId in chain[i]) {
                for (dstId in chain[i + 1]) {
                    val source = resolveVertex(srcId)
                    val target = resolveVertex(dstId)
                    val edge = parsingValue {
                        if (strict && graph.hasEdge(source, target)) graph.edge(source, target) else graph.addEdge(source, target)
                    }
                    applyEdgeProperties(edge, attrs)
                }
            }
        }
        return allIds
    }

    private fun parseSubgraph(): LinkedHashSet<String> {
        if (current.type == TokenType.SUBGRAPH) {
            advance()
            if (current.type == TokenType.ID) advance() // subgraph name; discarded, no cluster identity kept
        }
        expect(TokenType.LBRACE)
        val outer = scope
        scope = Scope(outer)
        subgraphDepth++
        val ids = parseStatementList()
        subgraphDepth--
        scope = outer
        expect(TokenType.RBRACE)
        return ids
    }

    private fun mergeProperties(defaults: Map<String, String>, explicit: Map<String, String>): Map<String, String> =
        if (explicit.isEmpty()) defaults else HashMap(defaults).apply { putAll(explicit) }

    private fun applyVertexProperties(vertex: Vertex, attrs: Map<String, String>) {
        for ((name, value) in attrs) {
            val property = vertexProperties.getOrPut(name) {
                ParsingVertexProperty(graph, propertyTypes.getOrDefault(name, TypeBinding.string))
            }
            parsingValue { property.parseAndSet(vertex, value) }
        }
    }

    private fun applyEdgeProperties(edge: Edge, attrs: Map<String, String>) {
        for ((name, value) in attrs) {
            val property = edgeProperties.getOrPut(name) {
                ParsingEdgeProperty(graph, propertyTypes.getOrDefault(name, TypeBinding.string))
            }
            parsingValue { property.parseAndSet(edge, value) }
        }
    }

    private fun resolveVertex(id: String): Vertex {
        val parsedId = parsingValue { vertexIdProperty.parse(id) }
        return vertexIds.getOrPut(parsedId) { graph.addVertex().also { vertexIdProperty[it] = parsedId } }
    }

    /**
     * Tracks the node/edge default attributes currently in effect. Constructing a child [Scope] copies the parent's
     * maps, so mutating a nested `{}` block's defaults never affects the parent - entering/leaving a block is just
     * swapping which [Scope] instance is current, relying on the JVM call stack to pop back on return.
     */
    private class Scope(parent: Scope?) {
        val nodeDefaults: MutableMap<String, String> = HashMap(parent?.nodeDefaults ?: emptyMap())
        val edgeDefaults: MutableMap<String, String> = HashMap(parent?.edgeDefaults ?: emptyMap())
    }
}
