package com.eisodos.intellij.parsing

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

/**
 * Simple parser for Eisodos template files.
 * The heavy lifting is done by the lexer; the parser just wraps
 * token sequences into composite PSI nodes.
 */
class EisodosTemplateParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()

        while (!builder.eof()) {
            when (builder.tokenType) {
                EisodosTemplateTokenTypes.PARAM_REF -> parseParamExpression(builder)
                EisodosTemplateTokenTypes.CB_SHORT_OPEN -> parseShortCallback(builder)
                EisodosTemplateTokenTypes.CB_FULL_OPEN -> parseFullCallback(builder)
                EisodosTemplateTokenTypes.LANG_OPEN -> parseLangTag(builder)
                EisodosTemplateTokenTypes.COMMENT -> {
                    val m = builder.mark()
                    builder.advanceLexer()
                    m.done(EisodosTemplateCompositeElementType("COMMENT_BLOCK"))
                }
                else -> builder.advanceLexer()
            }
        }

        rootMarker.done(root)
        return builder.treeBuilt
    }

    private fun parseParamExpression(builder: PsiBuilder) {
        val m = builder.mark()
        builder.advanceLexer() // PARAM_REF

        // Check for := or ~= operator
        if (builder.tokenType == EisodosTemplateTokenTypes.ASSIGN_OP ||
            builder.tokenType == EisodosTemplateTokenTypes.DEFAULT_OP
        ) {
            builder.advanceLexer() // operator (includes opening ')
            if (builder.tokenType == EisodosTemplateTokenTypes.STRING_CONTENT) {
                builder.advanceLexer() // string content
            }
            if (builder.tokenType == EisodosTemplateTokenTypes.SINGLE_QUOTE) {
                builder.advanceLexer() // closing '
            }
            if (builder.tokenType == EisodosTemplateTokenTypes.SEMICOLON) {
                builder.advanceLexer() // ;
            }
        }
        m.done(EisodosTemplateCompositeElementType("PARAM_EXPRESSION"))
    }

    private fun parseShortCallback(builder: PsiBuilder) {
        val m = builder.mark()
        builder.advanceLexer() // [%

        while (!builder.eof() && builder.tokenType != EisodosTemplateTokenTypes.CB_SHORT_CLOSE) {
            builder.advanceLexer()
        }
        if (builder.tokenType == EisodosTemplateTokenTypes.CB_SHORT_CLOSE) {
            builder.advanceLexer() // %]
        }
        m.done(EisodosTemplateCompositeElementType("SHORT_CALLBACK"))
    }

    private fun parseFullCallback(builder: PsiBuilder) {
        val m = builder.mark()
        builder.advanceLexer() // <%FUNC%

        while (!builder.eof() && builder.tokenType != EisodosTemplateTokenTypes.CB_FULL_CLOSE) {
            builder.advanceLexer()
        }
        if (builder.tokenType == EisodosTemplateTokenTypes.CB_FULL_CLOSE) {
            builder.advanceLexer() // %FUNC%>
        }
        m.done(EisodosTemplateCompositeElementType("FULL_CALLBACK"))
    }

    private fun parseLangTag(builder: PsiBuilder) {
        val m = builder.mark()
        builder.advanceLexer() // [:

        while (!builder.eof() && builder.tokenType != EisodosTemplateTokenTypes.LANG_CLOSE) {
            builder.advanceLexer()
        }
        if (builder.tokenType == EisodosTemplateTokenTypes.LANG_CLOSE) {
            builder.advanceLexer() // :]
        }
        m.done(EisodosTemplateCompositeElementType("LANG_TAG"))
    }
}