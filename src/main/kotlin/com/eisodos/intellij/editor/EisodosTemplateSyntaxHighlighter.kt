package com.eisodos.intellij.editor

import com.eisodos.intellij.parsing.EisodosTemplateLexer
import com.eisodos.intellij.parsing.EisodosTemplateTokenTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class EisodosTemplateSyntaxHighlighter : SyntaxHighlighterBase() {

    companion object {
        // Parameter highlighting
        val PARAM_REF_KEY = createTextAttributesKey("EISODOS_PARAM_REF", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        val ASSIGN_OP_KEY = createTextAttributesKey("EISODOS_ASSIGN_OP", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val DEFAULT_OP_KEY = createTextAttributesKey("EISODOS_DEFAULT_OP", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val STRING_KEY = createTextAttributesKey("EISODOS_STRING", DefaultLanguageHighlighterColors.STRING)
        val SEMICOLON_KEY = createTextAttributesKey("EISODOS_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)

        // Short callback highlighting
        val CB_SHORT_DELIM_KEY = createTextAttributesKey("EISODOS_CB_SHORT_DELIM", DefaultLanguageHighlighterColors.BRACKETS)
        val CB_KEY_KEY = createTextAttributesKey("EISODOS_CB_KEY", DefaultLanguageHighlighterColors.METADATA)
        val CB_EQUALS_KEY = createTextAttributesKey("EISODOS_CB_EQUALS", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val CB_VALUE_KEY = createTextAttributesKey("EISODOS_CB_VALUE", DefaultLanguageHighlighterColors.STRING)
        val CB_SEPARATOR_KEY = createTextAttributesKey("EISODOS_CB_SEPARATOR", DefaultLanguageHighlighterColors.SEMICOLON)

        // Full callback highlighting
        val CB_FULL_DELIM_KEY = createTextAttributesKey("EISODOS_CB_FULL_DELIM", DefaultLanguageHighlighterColors.KEYWORD)
        val CB_FULL_KEY_KEY = createTextAttributesKey("EISODOS_CB_FULL_KEY", DefaultLanguageHighlighterColors.METADATA)
        val CB_FULL_EQUALS_KEY = createTextAttributesKey("EISODOS_CB_FULL_EQUALS", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val CB_FULL_VALUE_KEY = createTextAttributesKey("EISODOS_CB_FULL_VALUE", DefaultLanguageHighlighterColors.STRING)
        val CB_AT_KEY = createTextAttributesKey("EISODOS_CB_AT", DefaultLanguageHighlighterColors.METADATA)
        val CB_MULTILINE_DELIM_KEY = createTextAttributesKey("EISODOS_CB_MULTILINE_DELIM", DefaultLanguageHighlighterColors.KEYWORD)
        val CB_MULTILINE_CONTENT_KEY = createTextAttributesKey("EISODOS_CB_MULTILINE_CONTENT", DefaultLanguageHighlighterColors.STRING)

        // Language tag highlighting
        val LANG_DELIM_KEY = createTextAttributesKey("EISODOS_LANG_DELIM", DefaultLanguageHighlighterColors.BRACKETS)
        val LANG_ID_KEY = createTextAttributesKey("EISODOS_LANG_ID", DefaultLanguageHighlighterColors.CONSTANT)
        val LANG_COMMA_KEY = createTextAttributesKey("EISODOS_LANG_COMMA", DefaultLanguageHighlighterColors.COMMA)
        val LANG_DEFAULT_TEXT_KEY = createTextAttributesKey("EISODOS_LANG_DEFAULT_TEXT", DefaultLanguageHighlighterColors.STRING)
        val LANG_COLON_KEY = createTextAttributesKey("EISODOS_LANG_COLON", DefaultLanguageHighlighterColors.SEMICOLON)
        val LANG_PARAM_KEY = createTextAttributesKey("EISODOS_LANG_PARAM", DefaultLanguageHighlighterColors.PARAMETER)

        // Comment highlighting
        val COMMENT_KEY = createTextAttributesKey("EISODOS_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)

        // Error
        val BAD_CHAR_KEY = createTextAttributesKey("EISODOS_BAD_CHAR", HighlighterColors.BAD_CHARACTER)

        private val TOKEN_MAP = mapOf<IElementType, Array<TextAttributesKey>>(
            // Parameters
            EisodosTemplateTokenTypes.PARAM_REF to arrayOf(PARAM_REF_KEY),
            EisodosTemplateTokenTypes.ASSIGN_OP to arrayOf(ASSIGN_OP_KEY),
            EisodosTemplateTokenTypes.DEFAULT_OP to arrayOf(DEFAULT_OP_KEY),
            EisodosTemplateTokenTypes.SINGLE_QUOTE to arrayOf(STRING_KEY),
            EisodosTemplateTokenTypes.STRING_CONTENT to arrayOf(STRING_KEY),
            EisodosTemplateTokenTypes.SEMICOLON to arrayOf(SEMICOLON_KEY),

            // Short callback
            EisodosTemplateTokenTypes.CB_SHORT_OPEN to arrayOf(CB_SHORT_DELIM_KEY),
            EisodosTemplateTokenTypes.CB_SHORT_CLOSE to arrayOf(CB_SHORT_DELIM_KEY),
            EisodosTemplateTokenTypes.CB_KEY to arrayOf(CB_KEY_KEY),
            EisodosTemplateTokenTypes.CB_EQUALS to arrayOf(CB_EQUALS_KEY),
            EisodosTemplateTokenTypes.CB_VALUE to arrayOf(CB_VALUE_KEY),
            EisodosTemplateTokenTypes.CB_SEPARATOR to arrayOf(CB_SEPARATOR_KEY),

            // Full callback
            EisodosTemplateTokenTypes.CB_FULL_OPEN to arrayOf(CB_FULL_DELIM_KEY),
            EisodosTemplateTokenTypes.CB_FULL_CLOSE to arrayOf(CB_FULL_DELIM_KEY),
            EisodosTemplateTokenTypes.CB_FULL_KEY to arrayOf(CB_FULL_KEY_KEY),
            EisodosTemplateTokenTypes.CB_FULL_EQUALS to arrayOf(CB_FULL_EQUALS_KEY),
            EisodosTemplateTokenTypes.CB_FULL_VALUE to arrayOf(CB_FULL_VALUE_KEY),
            EisodosTemplateTokenTypes.CB_AT to arrayOf(CB_AT_KEY),
            EisodosTemplateTokenTypes.CB_MULTILINE_OPEN to arrayOf(CB_MULTILINE_DELIM_KEY),
            EisodosTemplateTokenTypes.CB_MULTILINE_CLOSE to arrayOf(CB_MULTILINE_DELIM_KEY),
            EisodosTemplateTokenTypes.CB_MULTILINE_CONTENT to arrayOf(CB_MULTILINE_CONTENT_KEY),

            // Language tags
            EisodosTemplateTokenTypes.LANG_OPEN to arrayOf(LANG_DELIM_KEY),
            EisodosTemplateTokenTypes.LANG_CLOSE to arrayOf(LANG_DELIM_KEY),
            EisodosTemplateTokenTypes.LANG_ID to arrayOf(LANG_ID_KEY),
            EisodosTemplateTokenTypes.LANG_COMMA to arrayOf(LANG_COMMA_KEY),
            EisodosTemplateTokenTypes.LANG_DEFAULT_TEXT to arrayOf(LANG_DEFAULT_TEXT_KEY),
            EisodosTemplateTokenTypes.LANG_COLON to arrayOf(LANG_COLON_KEY),
            EisodosTemplateTokenTypes.LANG_PARAM to arrayOf(LANG_PARAM_KEY),

            // Comments
            EisodosTemplateTokenTypes.COMMENT to arrayOf(COMMENT_KEY),

            // Error
            EisodosTemplateTokenTypes.BAD_CHARACTER to arrayOf(BAD_CHAR_KEY),
        )
    }

    override fun getHighlightingLexer(): Lexer = EisodosTemplateLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        return TOKEN_MAP[tokenType] ?: emptyArray()
    }
}