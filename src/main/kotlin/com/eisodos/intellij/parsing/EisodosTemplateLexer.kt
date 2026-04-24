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
    // When entering a language tag from a callback value, remember where to return
    private var langReturnState: Int = STATE_HTML
    // When entering a short callback from inside a full callback value, remember where to return
    private var shortCbReturnState: Int = STATE_HTML

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

        // Recover lang return state if in language tag state
        if (this.state in STATE_LANG..STATE_LANG_PARAMS) {
            this.langReturnState = decodeStructureHash(initialState)
        }

        // Recover short callback return state and full callback context
        if (this.state in STATE_CB_SHORT..STATE_CB_SHORT_VALUE) {
            this.shortCbReturnState = decodeStructureHash(initialState) and 0xF
            if (this.shortCbReturnState in STATE_CB_FULL..STATE_CB_FULL_MULTILINE) {
                this.fullCbStructure = "FUNC"
                this.multilineDepth = decodeMultilineDepth(initialState).coerceAtLeast(2)
                this.outerMultilineDepth = decodeOuterMultilineDepth(initialState)
            }
        }

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
        if (state in STATE_LANG..STATE_LANG_PARAMS) {
            // For lang states, encode return state in bits 8-11
            return encodeState(state, langReturnState, 0, 0)
        }
        if (state in STATE_CB_SHORT..STATE_CB_SHORT_VALUE) {
            // For short callback states, encode return state and preserved full callback context
            return encodeState(state, shortCbReturnState,
                if (shortCbReturnState in STATE_CB_FULL..STATE_CB_FULL_MULTILINE) multilineDepth else 0,
                if (shortCbReturnState in STATE_CB_FULL..STATE_CB_FULL_MULTILINE) outerMultilineDepth else 0)
        }
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
            langReturnState = STATE_HTML
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
            state = shortCbReturnState
            shortCbReturnState = STATE_HTML
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

        // Check for $param inside value
        if (c == '$' && position + 1 < bufferEnd && isWordChar(charAt(position + 1))) {
            lexParamRefInCallback(STATE_CB_SHORT_VALUE)
            return
        }

        // Check for [: language tag inside value
        if (c == '[' && peek(1) == ':') {
            lexLangTagInCallback(STATE_CB_SHORT_VALUE)
            return
        }

        // Read value (up to ; or %] or $param or [:)
        var end = position
        while (end < bufferEnd) {
            val ch = charAt(end)
            if (ch == ';' || (ch == '%' && end + 1 < bufferEnd && charAt(end + 1) == ']')) break
            if (ch == '$' && end + 1 < bufferEnd && isWordChar(charAt(end + 1))) break
            if (ch == '[' && end + 1 < bufferEnd && charAt(end + 1) == ':') break
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
        // If stopped at an inline construct ($, [:), stay in value state
        if (end < bufferEnd) {
            val ch = charAt(end)
            if (ch == '$' || ch == '[') {
                state = STATE_CB_SHORT_VALUE
                return
            }
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
        // Check for $param inside value
        if (charAt(position) == '$' && position + 1 < bufferEnd && isWordChar(charAt(position + 1))) {
            lexParamRefInCallback(STATE_CB_FULL_VALUE)
            return
        }

        // Check for [: language tag inside value
        if (charAt(position) == '[' && peek(1) == ':') {
            lexLangTagInCallback(STATE_CB_FULL_VALUE)
            return
        }

        // Check for [% short callback inside value
        if (charAt(position) == '[' && peek(1) == '%') {
            lexShortCbInCallback(STATE_CB_FULL_VALUE)
            return
        }

        // Check for ## comment inside value
        if (charAt(position) == '#' && peek(1) == '#') {
            lexCommentInCallback(STATE_CB_FULL)
            return
        }

        // Read value until end of line or close tag or $param or [: or [% or ##
        val closeTag = "%${fullCbStructure}%>"
        var end = position
        while (end < bufferEnd) {
            val ch = charAt(end)
            if (ch == '\n' || ch == '\r') break
            if (ch == '%' && matchesAt(end, closeTag)) break
            if (ch == '$' && end + 1 < bufferEnd && isWordChar(charAt(end + 1))) break
            if (ch == '[' && end + 1 < bufferEnd && (charAt(end + 1) == ':' || charAt(end + 1) == '%')) break
            if (ch == '#' && end + 1 < bufferEnd && charAt(end + 1) == '#') break
            end++
        }
        if (end > position) {
            tokenEnd = end
            position = end
            tokenType = EisodosTemplateTokenTypes.CB_FULL_VALUE
        }
        // If stopped at an inline construct ($, [:, [%, ##), stay in value state
        // so the next advance() handles it; otherwise return to full cb key scanning
        if (end < bufferEnd) {
            val ch = charAt(end)
            if (ch == '$' || ch == '[' || ch == '#') {
                state = STATE_CB_FULL_VALUE
                return
            }
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

        // Check for $param at current position
        if (charAt(position) == '$' && position + 1 < bufferEnd && isWordChar(charAt(position + 1))) {
            lexParamRefInCallback(STATE_CB_FULL_MULTILINE)
            return
        }

        // Check for [: language tag at current position
        if (charAt(position) == '[' && peek(1) == ':') {
            lexLangTagInCallback(STATE_CB_FULL_MULTILINE)
            return
        }

        // Check for [% short callback at current position
        if (charAt(position) == '[' && peek(1) == '%') {
            lexShortCbInCallback(STATE_CB_FULL_MULTILINE)
            return
        }

        // Check for ## comment at current position
        if (charAt(position) == '#' && peek(1) == '#') {
            lexCommentInCallback(STATE_CB_FULL_MULTILINE)
            return
        }

        // Inside multiline value, scan for close delimiter.
        // The close delimiter is exactly multilineDepth '<' chars (e.g., << for >>=, <<< for >>>=).
        val closePattern = "<".repeat(multilineDepth)

        // Check for close delimiter at current position (at line start)
        if (isAtLineStart() && isMultilineCloseAt(position, closePattern)) {
            var end = position
            while (end < bufferEnd && charAt(end) != '\n' && charAt(end) != '\r') end++
            if (end < bufferEnd) end++ // include newline
            emit(EisodosTemplateTokenTypes.CB_MULTILINE_CLOSE, end - position)
            state = STATE_CB_FULL
            return
        }

        // Scan content character-by-character until close delimiter, nested callback, $param, [:, or ##
        var end = position
        while (end < bufferEnd) {
            val ch = charAt(end)

            // Check for $param
            if (ch == '$' && end + 1 < bufferEnd && isWordChar(charAt(end + 1))) break

            // Check for [: language tag
            if (ch == '[' && end + 1 < bufferEnd && charAt(end + 1) == ':') break

            // Check for [% short callback
            if (ch == '[' && end + 1 < bufferEnd && charAt(end + 1) == '%') break

            // Check for ## comment
            if (ch == '#' && end + 1 < bufferEnd && charAt(end + 1) == '#') break

            // Check for nested callback open
            if (ch == '<' && end + 1 < bufferEnd && charAt(end + 1) == '%') break

            // At line start, check for close delimiter
            if (isAtLineStart(end) && isMultilineCloseAt(end, closePattern)) break

            end++
        }

        // Emit content before the break point
        if (end > position) {
            tokenEnd = end
            position = end
            tokenType = EisodosTemplateTokenTypes.CB_MULTILINE_CONTENT
        }
        // Stay in multiline state
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
            state = langReturnState
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
            state = langReturnState
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
                state = langReturnState
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
            state = langReturnState
            tokenType = null
            return
        }

        val c = charAt(position)
        if (c == ':') {
            if (peek(1) == ']') {
                emit(EisodosTemplateTokenTypes.LANG_CLOSE, 2)
                state = langReturnState
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
            state = langReturnState
            tokenType = null
            return
        }

        val c = charAt(position)
        if (c == ':' && peek(1) == ']') {
            emit(EisodosTemplateTokenTypes.LANG_CLOSE, 2)
            state = langReturnState
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

    // ---- Shared helpers for $param and [:lang:] inside callback values ----

    private fun lexParamRefInCallback(returnState: Int) {
        var end = position + 1 // skip $
        while (end < bufferEnd && isWordChar(charAt(end))) end++
        tokenEnd = end
        position = end
        tokenType = EisodosTemplateTokenTypes.PARAM_REF
        state = returnState
    }

    private fun lexLangTagInCallback(returnState: Int) {
        langReturnState = returnState
        emit(EisodosTemplateTokenTypes.LANG_OPEN, 2)
        state = STATE_LANG
    }

    private fun lexShortCbInCallback(returnState: Int) {
        shortCbReturnState = returnState
        emit(EisodosTemplateTokenTypes.CB_SHORT_OPEN, 2)
        state = STATE_CB_SHORT
    }

    private fun lexCommentInCallback(returnState: Int) {
        var end = position + 2
        while (end < bufferEnd && charAt(end) != '\n') end++
        // Don't consume the newline — let the caller's state handle it
        tokenEnd = end
        position = end
        tokenType = EisodosTemplateTokenTypes.COMMENT
        state = returnState
    }

    private fun isMultilineCloseAt(pos: Int, closePattern: String): Boolean {
        var p = pos
        while (p < bufferEnd && (charAt(p) == ' ' || charAt(p) == '\t')) p++
        if (p + closePattern.length > bufferEnd) return false
        for (i in closePattern.indices) {
            if (charAt(p + i) != closePattern[i]) return false
        }
        val afterPattern = p + closePattern.length
        if (afterPattern < bufferEnd && charAt(afterPattern) == '<') return false
        return true
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