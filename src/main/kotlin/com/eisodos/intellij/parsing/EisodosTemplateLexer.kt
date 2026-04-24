package com.eisodos.intellij.parsing

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Hand-written lexer for Eisodos template files.
 *
 * Recognizes these constructs within HTML content:
 * - $paramName              parameter reference
 * - $paramName:='value';    parameter assignment
 * - $paramName~='default';  parameter default
 * - [%key=val;key2=val2%]   short callback
 * - <%FUNC%...%FUNC%>       full callback
 * - [:LANG_ID,default:]     language tag
 * - ## comment               line comment
 */
class EisodosTemplateLexer : LexerBase() {

    private var buffer: CharSequence = ""
    private var bufferStart = 0
    private var bufferEnd = 0
    private var position = 0
    private var state = STATE_HTML

    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    // For full callback: the structure name (e.g., "FUNC")
    private var fullCbStructure: String = ""
    // Number of '>' chars in the current multiline open delimiter (e.g., 2 for >>=, 3 for >>>=)
    private var multilineDepth: Int = 2
    // For nested callbacks: the multiline depth of the enclosing multiline value (0 = not nested)
    private var outerMultilineDepth: Int = 0

    companion object {
        const val STATE_HTML = 0
        const val STATE_AFTER_PARAM = 1
        const val STATE_PARAM_STRING = 2
        const val STATE_PARAM_STRING_END = 3
        const val STATE_CB_SHORT = 4
        const val STATE_CB_SHORT_AFTER_KEY = 5
        const val STATE_CB_SHORT_VALUE = 6
        const val STATE_CB_FULL = 7
        const val STATE_CB_FULL_AFTER_KEY = 8
        const val STATE_CB_FULL_VALUE = 9
        const val STATE_CB_FULL_MULTILINE = 10
        const val STATE_LANG = 11
        const val STATE_LANG_AFTER_ID = 12
        const val STATE_LANG_DEFAULT = 13
        const val STATE_LANG_PARAMS = 14
        const val STATE_PARAM_SEMICOLON = 15

        // Encode full callback state in bits:
        //   bits 0-7:   base state enum
        //   bits 8-23:  structure name hash (16 bits)
        //   bits 24-27: multiline depth (4 bits, max 15)
        //   bits 28-31: outer multiline depth (4 bits, for nested callback support)
        fun encodeState(baseState: Int, structureHash: Int = 0, mlDepth: Int = 0, outerMlDepth: Int = 0): Int {
            return baseState or (structureHash shl 8) or (mlDepth shl 24) or (outerMlDepth shl 28)
        }

        fun decodeBaseState(state: Int): Int = state and 0xFF
        fun decodeStructureHash(state: Int): Int = (state ushr 8) and 0xFFFF
        fun decodeMultilineDepth(state: Int): Int = (state ushr 24) and 0xF
        fun decodeOuterMultilineDepth(state: Int): Int = (state ushr 28) and 0xF
    }

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferStart = startOffset
        this.bufferEnd = endOffset
        this.position = startOffset
        this.state = decodeBaseState(initialState)

        // Recover structure name and multiline depth from state if in full callback
        if (this.state in STATE_CB_FULL..STATE_CB_FULL_MULTILINE) {
            val hash = decodeStructureHash(initialState)
            this.fullCbStructure = if (hash == "FUNC".hashCode() and 0xFFFF) "FUNC" else "FUNC"
            this.multilineDepth = decodeMultilineDepth(initialState).coerceAtLeast(2)
            this.outerMultilineDepth = decodeOuterMultilineDepth(initialState)
        }

        advance()
    }

    override fun getState(): Int {
        val hash = if (state in STATE_CB_FULL..STATE_CB_FULL_MULTILINE) {
            fullCbStructure.hashCode() and 0xFFFF
        } else 0
        val mlDepth = if (state == STATE_CB_FULL_MULTILINE) multilineDepth else 0
        val outerMlDepth = if (state in STATE_CB_FULL..STATE_CB_FULL_MULTILINE) outerMultilineDepth else 0
        return encodeState(state, hash, mlDepth, outerMlDepth)
    }

    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = bufferEnd

    override fun advance() {
        tokenStart = position
        if (position >= bufferEnd) {
            tokenType = null
            return
        }

        when (state) {
            STATE_HTML -> lexHtml()
            STATE_AFTER_PARAM -> lexAfterParam()
            STATE_PARAM_STRING -> lexParamString()
            STATE_PARAM_STRING_END -> lexParamStringEnd()
            STATE_PARAM_SEMICOLON -> lexParamSemicolon()
            STATE_CB_SHORT -> lexShortCb()
            STATE_CB_SHORT_AFTER_KEY -> lexShortCbAfterKey()
            STATE_CB_SHORT_VALUE -> lexShortCbValue()
            STATE_CB_FULL -> lexFullCb()
            STATE_CB_FULL_AFTER_KEY -> lexFullCbAfterKey()
            STATE_CB_FULL_VALUE -> lexFullCbValue()
            STATE_CB_FULL_MULTILINE -> lexFullCbMultiline()
            STATE_LANG -> lexLang()
            STATE_LANG_AFTER_ID -> lexLangAfterId()
            STATE_LANG_DEFAULT -> lexLangDefault()
            STATE_LANG_PARAMS -> lexLangParams()
            else -> {
                // Safety fallback
                position++
                tokenEnd = position
                tokenType = EisodosTemplateTokenTypes.BAD_CHARACTER
            }
        }
    }

    // ---- STATE_HTML: scanning HTML content for template markers ----

    private fun lexHtml() {
        val c = charAt(position)

        // Check for ## comment at start of line
        if (c == '#' && peek(1) == '#' && isAtLineStart()) {
            lexComment()
            return
        }

        // Check for [: language tag
        if (c == '[' && peek(1) == ':') {
            emit(EisodosTemplateTokenTypes.LANG_OPEN, 2)
            state = STATE_LANG
            return
        }

        // Check for [% short callback
        if (c == '[' && peek(1) == '%') {
            emit(EisodosTemplateTokenTypes.CB_SHORT_OPEN, 2)
            state = STATE_CB_SHORT
            return
        }

        // Check for <%...% full callback opening
        if (c == '<' && peek(1) == '%') {
            lexFullCbOpen()
            return
        }

        // Check for $ parameter reference
        if (c == '$' && position + 1 < bufferEnd && isWordChar(charAt(position + 1))) {
            lexParamRef()
            return
        }

        // HTML content - scan until next template marker
        var end = position
        while (end < bufferEnd) {
            val ch = charAt(end)
            if (ch == '$' && end + 1 < bufferEnd && isWordChar(charAt(end + 1))) break
            if (ch == '[' && end + 1 < bufferEnd && (charAt(end + 1) == '%' || charAt(end + 1) == ':')) break
            if (ch == '<' && end + 1 < bufferEnd && charAt(end + 1) == '%') break
            if (ch == '#' && end + 1 < bufferEnd && charAt(end + 1) == '#' && isAtLineStart(end)) break
            end++
        }
        if (end == position) end++ // advance at least one char
        tokenEnd = end
        position = end
        tokenType = EisodosTemplateTokenTypes.CONTENT
    }

    private fun lexComment() {
        var end = position + 2
        while (end < bufferEnd && charAt(end) != '\n') end++
        if (end < bufferEnd && charAt(end) == '\n') end++ // include newline
        emit(EisodosTemplateTokenTypes.COMMENT, end - position)
    }

    private fun lexParamRef() {
        // $paramName - scan $ + word characters
        var end = position + 1 // skip $
        while (end < bufferEnd && isWordChar(charAt(end))) end++
        tokenEnd = end
        position = end
        tokenType = EisodosTemplateTokenTypes.PARAM_REF
        state = STATE_AFTER_PARAM
    }

    private fun lexFullCbOpen() {
        // <%STRUCT% - find the structure name between <% and %
        var nameStart = position + 2
        var nameEnd = nameStart
        while (nameEnd < bufferEnd && charAt(nameEnd) != '%') nameEnd++
        if (nameEnd < bufferEnd) nameEnd++ // include closing %

        fullCbStructure = buffer.subSequence(nameStart, nameEnd - 1).toString()
        emit(EisodosTemplateTokenTypes.CB_FULL_OPEN, nameEnd - position)
        state = STATE_CB_FULL
    }

    // ---- STATE_AFTER_PARAM: just emitted PARAM_REF, check for := or ~= ----

    private fun lexAfterParam() {
        if (position + 2 < bufferEnd && charAt(position) == ':' && charAt(position + 1) == '=' && charAt(position + 2) == '\'') {
            // :=' assignment operator (includes opening quote)
            emit(EisodosTemplateTokenTypes.ASSIGN_OP, 3)
            state = STATE_PARAM_STRING
            return
        }
        if (position + 2 < bufferEnd && charAt(position) == '~' && charAt(position + 1) == '=' && charAt(position + 2) == '\'') {
            // ~=' default operator (includes opening quote)
            emit(EisodosTemplateTokenTypes.DEFAULT_OP, 3)
            state = STATE_PARAM_STRING
            return
        }
        // No operator follows - back to HTML
        state = STATE_HTML
        lexHtml()
    }

    // ---- STATE_PARAM_STRING: inside '...' after := or ~= ----

    private fun lexParamString() {
        // Opening quote was already consumed by ASSIGN_OP / DEFAULT_OP.
        // Scan string content until closing quote.
        var end = position
        while (end < bufferEnd && charAt(end) != '\'') end++
        if (end > position) {
            tokenEnd = end
            position = end
            tokenType = EisodosTemplateTokenTypes.STRING_CONTENT
            state = STATE_PARAM_STRING_END
        } else {
            // Empty string or at closing quote
            state = STATE_PARAM_STRING_END
            lexParamStringEnd()
        }
    }

    private fun lexParamStringEnd() {
        if (position < bufferEnd && charAt(position) == '\'') {
            emit(EisodosTemplateTokenTypes.SINGLE_QUOTE, 1)
            // Semicolon will be emitted on the next advance() call
            state = if (position < bufferEnd && charAt(position) == ';') {
                STATE_PARAM_SEMICOLON
            } else {
                STATE_HTML
            }
            return
        }
        state = STATE_HTML
        lexHtml()
    }

    private fun lexParamSemicolon() {
        emit(EisodosTemplateTokenTypes.SEMICOLON, 1)
        state = STATE_HTML
    }

    // ---- STATE_CB_SHORT: inside [%...%] ----

    private fun lexShortCb() {
        val c = charAt(position)

        // Check for closing %]
        if (c == '%' && peek(1) == ']') {
            emit(EisodosTemplateTokenTypes.CB_SHORT_CLOSE, 2)
            state = STATE_HTML
            return
        }

        // Check for ; separator
        if (c == ';') {
            emit(EisodosTemplateTokenTypes.CB_SEPARATOR, 1)
            return
        }

        // Read key (up to = or ; or %])
        var end = position
        while (end < bufferEnd) {
            val ch = charAt(end)
            if (ch == '=' || ch == ';' || (ch == '%' && end + 1 < bufferEnd && charAt(end + 1) == ']')) break
            end++
        }
        if (end > position) {
            tokenEnd = end
            position = end
            tokenType = EisodosTemplateTokenTypes.CB_KEY
            state = STATE_CB_SHORT_AFTER_KEY
        } else {
            emit(EisodosTemplateTokenTypes.BAD_CHARACTER, 1)
        }
    }

    private fun lexShortCbAfterKey() {
        if (position < bufferEnd && charAt(position) == '=') {
            emit(EisodosTemplateTokenTypes.CB_EQUALS, 1)
            state = STATE_CB_SHORT_VALUE
            return
        }
        // No = after key, back to short cb scanning
        state = STATE_CB_SHORT
        lexShortCb()
    }

    private fun lexShortCbValue() {
        val c = charAt(position)

        // Check for closing %]
        if (c == '%' && peek(1) == ']') {
            // Empty value - go back to emit close
            state = STATE_CB_SHORT
            lexShortCb()
            return
        }

        // Read value (up to ; or %])
        var end = position
        while (end < bufferEnd) {
            val ch = charAt(end)
            if (ch == ';' || (ch == '%' && end + 1 < bufferEnd && charAt(end + 1) == ']')) break
            end++
        }
        if (end > position) {
            tokenEnd = end
            position = end
            tokenType = EisodosTemplateTokenTypes.CB_VALUE
        } else {
            tokenEnd = position
            tokenType = EisodosTemplateTokenTypes.CB_VALUE
        }
        state = STATE_CB_SHORT
    }

    // ---- STATE_CB_FULL: inside <%STRUCT%...%STRUCT%> ----

    private fun lexFullCb() {
        skipWhitespaceInFullCb()
        if (tokenType != null) return // emitted whitespace token

        if (position >= bufferEnd) {
            tokenType = null
            return
        }

        val c = charAt(position)

        // Check for closing tag %STRUCT%>
        val closeTag = "%${fullCbStructure}%>"
        if (position + closeTag.length <= bufferEnd && matchesAt(position, closeTag)) {
            emit(EisodosTemplateTokenTypes.CB_FULL_CLOSE, closeTag.length)
            if (outerMultilineDepth > 0) {
                // Nested callback closed — return to enclosing multiline value
                multilineDepth = outerMultilineDepth
                outerMultilineDepth = 0
                state = STATE_CB_FULL_MULTILINE
            } else {
                state = STATE_HTML
            }
            return
        }

        // Check for @ prefix
        if (c == '@') {
            emit(EisodosTemplateTokenTypes.CB_AT, 1)
            // Continue to read key
            return
        }

        // Check for multiline open >>=
        // This is handled after reading the key, in lexFullCbAfterKey

        // Read key (up to = or >> or newline or close tag)
        var end = position
        while (end < bufferEnd) {
            val ch = charAt(end)
            if (ch == '=' || ch == '\n' || ch == '\r') break
            if (ch == '>' && end + 1 < bufferEnd && charAt(end + 1) == '>') break
            if (ch == '%' && matchesAt(end, closeTag)) break
            end++
        }
        if (end > position) {
            tokenEnd = end
            position = end
            tokenType = EisodosTemplateTokenTypes.CB_FULL_KEY
            state = STATE_CB_FULL_AFTER_KEY
        } else if (c == '\n' || c == '\r') {
            lexFullCbNewline()
        } else {
            emit(EisodosTemplateTokenTypes.BAD_CHARACTER, 1)
        }
    }

    private fun lexFullCbAfterKey() {
        if (position >= bufferEnd) {
            state = STATE_CB_FULL
            tokenType = null
            return
        }

        val c = charAt(position)

        // Check for >>= multiline open (count consecutive '>' for nesting depth)
        if (c == '>' && peek(1) == '>') {
            var depth = 0
            var end = position
            while (end < bufferEnd && charAt(end) == '>') { depth++; end++ }
            multilineDepth = depth
            // Include just the = after the > chars (rest of line is multiline content)
            if (end < bufferEnd && charAt(end) == '=') end++
            emit(EisodosTemplateTokenTypes.CB_MULTILINE_OPEN, end - position)
            state = STATE_CB_FULL_MULTILINE
            return
        }

        if (c == '=') {
            emit(EisodosTemplateTokenTypes.CB_FULL_EQUALS, 1)
            state = STATE_CB_FULL_VALUE
            return
        }

        // No operator - back to full cb
        state = STATE_CB_FULL
        lexFullCb()
    }

    private fun lexFullCbValue() {
        // Read value until end of line or close tag
        val closeTag = "%${fullCbStructure}%>"
        var end = position
        while (end < bufferEnd) {
            val ch = charAt(end)
            if (ch == '\n' || ch == '\r') break
            if (ch == '%' && matchesAt(end, closeTag)) break
            end++
        }
        if (end > position) {
            tokenEnd = end
            position = end
            tokenType = EisodosTemplateTokenTypes.CB_FULL_VALUE
        }
        state = STATE_CB_FULL
    }

    private fun lexFullCbMultiline() {
        if (position >= bufferEnd) {
            tokenType = null
            return
        }

        // Check for nested callback open at current position (e.g., >>=<%FUNC% on same line)
        if (charAt(position) == '<' && position + 1 < bufferEnd && charAt(position + 1) == '%') {
            outerMultilineDepth = multilineDepth
            lexFullCbOpen()
            return
        }

        // Inside multiline value, scan for close delimiter.
        // The close delimiter is exactly multilineDepth '<' chars (e.g., << for >>=, <<< for >>>=).
        val closePattern = "<".repeat(multilineDepth)
        var end = position

        // Skip leading newline
        if (end < bufferEnd && (charAt(end) == '\n' || charAt(end) == '\r')) {
            if (charAt(end) == '\r' && end + 1 < bufferEnd && charAt(end + 1) == '\n') end++
            end++
        }

        // Scan lines for close delimiter or nested callback open
        while (end < bufferEnd) {
            // Check if this line is the multiline close (exactly N '<' chars, not more)
            val lineStart = end
            var lineEnd = end
            while (lineEnd < bufferEnd && charAt(lineEnd) != '\n' && charAt(lineEnd) != '\r') lineEnd++
            val line = buffer.subSequence(lineStart, lineEnd).toString().trim()

            if (line.startsWith(closePattern) && (line.length == closePattern.length || line[closePattern.length] != '<')) {
                // Found close delimiter with exact depth match
                if (lineStart > position) {
                    // Emit content before the close
                    tokenEnd = lineStart
                    position = lineStart
                    tokenType = EisodosTemplateTokenTypes.CB_MULTILINE_CONTENT
                    return
                }
                // Emit the close delimiter line
                if (lineEnd < bufferEnd) lineEnd++ // include newline
                emit(EisodosTemplateTokenTypes.CB_MULTILINE_CLOSE, lineEnd - position)
                state = STATE_CB_FULL
                return
            }

            // Check for nested callback open at line start
            if (charAt(lineStart) == '<' && lineStart + 1 < bufferEnd && charAt(lineStart + 1) == '%') {
                if (lineStart > position) {
                    // Emit content before the nested callback
                    tokenEnd = lineStart
                    position = lineStart
                    tokenType = EisodosTemplateTokenTypes.CB_MULTILINE_CONTENT
                    return
                }
                // Enter nested callback
                outerMultilineDepth = multilineDepth
                lexFullCbOpen()
                return
            }

            end = lineEnd
            if (end < bufferEnd) {
                if (charAt(end) == '\r' && end + 1 < bufferEnd && charAt(end + 1) == '\n') end++
                end++
            }
        }

        // No close found - emit rest as multiline content
        if (end > position) {
            tokenEnd = end
            position = end
            tokenType = EisodosTemplateTokenTypes.CB_MULTILINE_CONTENT
        }
        // Stay in multiline state (unclosed)
    }

    private fun skipWhitespaceInFullCb() {
        if (position < bufferEnd && (charAt(position) == '\n' || charAt(position) == '\r')) {
            lexFullCbNewline()
        } else if (position < bufferEnd && (charAt(position) == ' ' || charAt(position) == '\t')) {
            var end = position
            while (end < bufferEnd && (charAt(end) == ' ' || charAt(end) == '\t')) end++
            emit(EisodosTemplateTokenTypes.CB_FULL_NEWLINE, end - position)
        } else {
            tokenType = null
        }
    }

    private fun lexFullCbNewline() {
        var end = position
        while (end < bufferEnd && (charAt(end) == '\n' || charAt(end) == '\r' || charAt(end) == ' ' || charAt(end) == '\t')) {
            end++
        }
        emit(EisodosTemplateTokenTypes.CB_FULL_NEWLINE, end - position)
    }

    // ---- STATE_LANG: inside [:...:]  ----

    private fun lexLang() {
        if (position >= bufferEnd) {
            tokenType = null
            return
        }

        val c = charAt(position)

        // Check for closing :]
        if (c == ':' && peek(1) == ']') {
            emit(EisodosTemplateTokenTypes.LANG_CLOSE, 2)
            state = STATE_HTML
            return
        }

        // Read language ID (uppercase, digits, underscore, dot, hyphen, hash)
        var end = position
        while (end < bufferEnd) {
            val ch = charAt(end)
            if (ch == ',' || ch == ':' || (ch == ':' && peek(end - position + 1) == ']')) break
            if (!isLangIdChar(ch)) break
            end++
        }
        if (end > position) {
            tokenEnd = end
            position = end
            tokenType = EisodosTemplateTokenTypes.LANG_ID
            state = STATE_LANG_AFTER_ID
        } else {
            emit(EisodosTemplateTokenTypes.BAD_CHARACTER, 1)
        }
    }

    private fun lexLangAfterId() {
        if (position >= bufferEnd) {
            state = STATE_HTML
            tokenType = null
            return
        }

        val c = charAt(position)
        if (c == ',') {
            emit(EisodosTemplateTokenTypes.LANG_COMMA, 1)
            state = STATE_LANG_DEFAULT
            return
        }
        if (c == ':') {
            if (peek(1) == ']') {
                emit(EisodosTemplateTokenTypes.LANG_CLOSE, 2)
                state = STATE_HTML
                return
            }
            emit(EisodosTemplateTokenTypes.LANG_COLON, 1)
            state = STATE_LANG_PARAMS
            return
        }
        // Unexpected - go back to lang
        state = STATE_LANG
        lexLang()
    }

    private fun lexLangDefault() {
        if (position >= bufferEnd) {
            state = STATE_HTML
            tokenType = null
            return
        }

        val c = charAt(position)
        if (c == ':') {
            if (peek(1) == ']') {
                emit(EisodosTemplateTokenTypes.LANG_CLOSE, 2)
                state = STATE_HTML
                return
            }
            emit(EisodosTemplateTokenTypes.LANG_COLON, 1)
            state = STATE_LANG_PARAMS
            return
        }

        // Read default text until : or :]
        var end = position
        while (end < bufferEnd) {
            val ch = charAt(end)
            if (ch == ':') break
            end++
        }
        if (end > position) {
            tokenEnd = end
            position = end
            tokenType = EisodosTemplateTokenTypes.LANG_DEFAULT_TEXT
        } else {
            state = STATE_LANG
            lexLang()
        }
    }

    private fun lexLangParams() {
        if (position >= bufferEnd) {
            state = STATE_HTML
            tokenType = null
            return
        }

        val c = charAt(position)
        if (c == ':' && peek(1) == ']') {
            emit(EisodosTemplateTokenTypes.LANG_CLOSE, 2)
            state = STATE_HTML
            return
        }

        // Read params until :]
        var end = position
        while (end < bufferEnd) {
            if (charAt(end) == ':' && end + 1 < bufferEnd && charAt(end + 1) == ']') break
            end++
        }
        if (end > position) {
            tokenEnd = end
            position = end
            tokenType = EisodosTemplateTokenTypes.LANG_PARAM
        }
        // Stay in params state for the closing :]
    }

    // ---- Helper methods ----

    private fun charAt(pos: Int): Char = if (pos < bufferEnd) buffer[pos] else '\u0000'

    private fun peek(offset: Int): Char = charAt(position + offset)

    private fun emit(type: IElementType, length: Int) {
        tokenEnd = position + length
        position = tokenEnd
        tokenType = type
    }

    private fun isWordChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'

    private fun isLangIdChar(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '.' || c == '-' || c == '#'

    private fun isAtLineStart(pos: Int = position): Boolean {
        if (pos == bufferStart) return true
        if (pos > bufferStart && buffer[pos - 1] == '\n') return true
        return false
    }

    private fun matchesAt(pos: Int, text: String): Boolean {
        if (pos + text.length > bufferEnd) return false
        for (i in text.indices) {
            if (buffer[pos + i] != text[i]) return false
        }
        return true
    }
}