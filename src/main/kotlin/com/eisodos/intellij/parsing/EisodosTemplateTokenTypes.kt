package com.eisodos.intellij.parsing

import com.eisodos.intellij.EisodosTemplateLanguage
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.OuterLanguageElementType
import com.intellij.psi.tree.TokenSet

object EisodosTemplateTokenTypes {
    // File element
    @JvmField val FILE = IFileElementType("EISODOS_TEMPLATE_FILE", EisodosTemplateLanguage.INSTANCE)

    // Outer language (HTML content between template constructs)
    @JvmField val CONTENT = EisodosTemplateElementType("CONTENT")
    @JvmField val OUTER_ELEMENT_TYPE = OuterLanguageElementType("EISODOS_OUTER", EisodosTemplateLanguage.INSTANCE)

    // Parameter tokens
    @JvmField val PARAM_REF = EisodosTemplateElementType("PARAM_REF")           // $paramName
    @JvmField val ASSIGN_OP = EisodosTemplateElementType("ASSIGN_OP")           // :=
    @JvmField val DEFAULT_OP = EisodosTemplateElementType("DEFAULT_OP")         // ~=
    @JvmField val SINGLE_QUOTE = EisodosTemplateElementType("SINGLE_QUOTE")     // '
    @JvmField val STRING_CONTENT = EisodosTemplateElementType("STRING_CONTENT") // content inside quotes
    @JvmField val SEMICOLON = EisodosTemplateElementType("SEMICOLON")           // ;

    // Short callback [%...%]
    @JvmField val CB_SHORT_OPEN = EisodosTemplateElementType("CB_SHORT_OPEN")   // [%
    @JvmField val CB_SHORT_CLOSE = EisodosTemplateElementType("CB_SHORT_CLOSE") // %]
    @JvmField val CB_KEY = EisodosTemplateElementType("CB_KEY")                 // key before =
    @JvmField val CB_EQUALS = EisodosTemplateElementType("CB_EQUALS")           // = inside callback
    @JvmField val CB_VALUE = EisodosTemplateElementType("CB_VALUE")             // value after =
    @JvmField val CB_SEPARATOR = EisodosTemplateElementType("CB_SEPARATOR")     // ; separator in short callback

    // Full callback <%FUNC%...%FUNC%>
    @JvmField val CB_FULL_OPEN = EisodosTemplateElementType("CB_FULL_OPEN")     // <%FUNC%
    @JvmField val CB_FULL_CLOSE = EisodosTemplateElementType("CB_FULL_CLOSE")   // %FUNC%>
    @JvmField val CB_FULL_KEY = EisodosTemplateElementType("CB_FULL_KEY")       // key in full callback
    @JvmField val CB_FULL_EQUALS = EisodosTemplateElementType("CB_FULL_EQUALS") // = in full callback
    @JvmField val CB_FULL_VALUE = EisodosTemplateElementType("CB_FULL_VALUE")   // value in full callback
    @JvmField val CB_FULL_NEWLINE = EisodosTemplateElementType("CB_FULL_NEWLINE")
    @JvmField val CB_AT = EisodosTemplateElementType("CB_AT")                   // @ prefix
    @JvmField val CB_MULTILINE_OPEN = EisodosTemplateElementType("CB_MULTILINE_OPEN")   // >>=
    @JvmField val CB_MULTILINE_CLOSE = EisodosTemplateElementType("CB_MULTILINE_CLOSE") // <<=
    @JvmField val CB_MULTILINE_CONTENT = EisodosTemplateElementType("CB_MULTILINE_CONTENT")

    // Language tags [:...:]
    @JvmField val LANG_OPEN = EisodosTemplateElementType("LANG_OPEN")           // [:
    @JvmField val LANG_CLOSE = EisodosTemplateElementType("LANG_CLOSE")         // :]
    @JvmField val LANG_ID = EisodosTemplateElementType("LANG_ID")               // LANGUAGE_ID
    @JvmField val LANG_COMMA = EisodosTemplateElementType("LANG_COMMA")         // ,
    @JvmField val LANG_DEFAULT_TEXT = EisodosTemplateElementType("LANG_DEFAULT_TEXT")
    @JvmField val LANG_COLON = EisodosTemplateElementType("LANG_COLON")         // : separator
    @JvmField val LANG_PARAM = EisodosTemplateElementType("LANG_PARAM")         // params after :

    // Comments
    @JvmField val COMMENT = EisodosTemplateElementType("COMMENT")               // ## ...

    // Error
    @JvmField val BAD_CHARACTER = EisodosTemplateElementType("BAD_CHARACTER")

    // Token sets for parser/highlighter
    @JvmField val COMMENTS = TokenSet.create(COMMENT)
    @JvmField val STRINGS = TokenSet.create(STRING_CONTENT, CB_VALUE, CB_FULL_VALUE)
    @JvmField val WHITESPACE = TokenSet.create(CB_FULL_NEWLINE)
}