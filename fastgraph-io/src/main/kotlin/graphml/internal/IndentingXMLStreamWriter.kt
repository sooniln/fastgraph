package io.github.sooniln.fastgraph.io.graphml.internal

import io.github.sooniln.fastcollect.ByteArrayDeque
import javax.xml.stream.XMLStreamWriter

/**
 * Wraps [delegate], inserting a newline and depth-based indentation before every start/empty element, and before
 * an end element's closing tag - but only when that element actually had a nested child element written into it.
 * An element containing only text (e.g. GraphML's `<data>value</data>`) is left alone, so its text content isn't
 * corrupted by injected whitespace; a mix of child elements and direct text is not supported.
 */
internal class IndentingXMLStreamWriter(
    private val delegate: XMLStreamWriter,
    private val indent: String = "  ",
) : XMLStreamWriter by delegate {

    // One boolean (as a Byte) per currently open element, tracking whether it has had a child element written.
    private val hasChild = ByteArrayDeque(5)

    private fun beforeStartElement() {
        if (!hasChild.isEmpty()) hasChild[hasChild.size - 1] = TRUE
        delegate.writeCharacters("\n")
        delegate.writeCharacters(indent.repeat(hasChild.size))
    }

    override fun writeStartElement(localName: String) {
        beforeStartElement()
        hasChild.addLast(FALSE)
        delegate.writeStartElement(localName)
    }

    override fun writeStartElement(namespaceURI: String, localName: String) {
        beforeStartElement()
        hasChild.addLast(FALSE)
        delegate.writeStartElement(namespaceURI, localName)
    }

    override fun writeStartElement(prefix: String, localName: String, namespaceURI: String) {
        beforeStartElement()
        hasChild.addLast(FALSE)
        delegate.writeStartElement(prefix, localName, namespaceURI)
    }

    override fun writeEmptyElement(localName: String) {
        beforeStartElement()
        delegate.writeEmptyElement(localName)
    }

    override fun writeEmptyElement(namespaceURI: String, localName: String) {
        beforeStartElement()
        delegate.writeEmptyElement(namespaceURI, localName)
    }

    override fun writeEmptyElement(prefix: String, localName: String, namespaceURI: String) {
        beforeStartElement()
        delegate.writeEmptyElement(prefix, localName, namespaceURI)
    }

    override fun writeEndElement() {
        val hadChild = hasChild.removeLast() == TRUE
        if (hadChild) {
            delegate.writeCharacters("\n")
            delegate.writeCharacters(indent.repeat(hasChild.size))
        }
        delegate.writeEndElement()
    }

    private companion object {
        const val TRUE: Byte = 1
        const val FALSE: Byte = 0
    }
}
