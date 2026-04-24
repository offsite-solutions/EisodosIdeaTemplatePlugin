package com.eisodos.intellij.editor

import com.eisodos.intellij.EisodosTemplateIcons
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class EisodosTemplateColorSettingsPage : ColorSettingsPage {

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Parameter reference", EisodosTemplateSyntaxHighlighter.PARAM_REF_KEY),
            AttributesDescriptor("Operators (:=, ~=)", EisodosTemplateSyntaxHighlighter.ASSIGN_OP_KEY),
            AttributesDescriptor("String values", EisodosTemplateSyntaxHighlighter.STRING_KEY),
            AttributesDescriptor("Semicolons", EisodosTemplateSyntaxHighlighter.SEMICOLON_KEY),

            AttributesDescriptor("Callback//Short callback delimiters ([%, %])", EisodosTemplateSyntaxHighlighter.CB_SHORT_DELIM_KEY),
            AttributesDescriptor("Callback//Callback key", EisodosTemplateSyntaxHighlighter.CB_KEY_KEY),
            AttributesDescriptor("Callback//Callback equals", EisodosTemplateSyntaxHighlighter.CB_EQUALS_KEY),
            AttributesDescriptor("Callback//Callback value", EisodosTemplateSyntaxHighlighter.CB_VALUE_KEY),
            AttributesDescriptor("Callback//Callback separator (;)", EisodosTemplateSyntaxHighlighter.CB_SEPARATOR_KEY),

            AttributesDescriptor("Callback//Full callback delimiters (<%FUNC%, %FUNC%>)", EisodosTemplateSyntaxHighlighter.CB_FULL_DELIM_KEY),
            AttributesDescriptor("Callback//Full callback key", EisodosTemplateSyntaxHighlighter.CB_FULL_KEY_KEY),
            AttributesDescriptor("Callback//Full callback value", EisodosTemplateSyntaxHighlighter.CB_FULL_VALUE_KEY),
            AttributesDescriptor("Callback//@ prefix", EisodosTemplateSyntaxHighlighter.CB_AT_KEY),
            AttributesDescriptor("Callback//Multiline delimiters (>>=, <<=)", EisodosTemplateSyntaxHighlighter.CB_MULTILINE_DELIM_KEY),
            AttributesDescriptor("Callback//Multiline content", EisodosTemplateSyntaxHighlighter.CB_MULTILINE_CONTENT_KEY),

            AttributesDescriptor("Language tag//Delimiters ([: :])", EisodosTemplateSyntaxHighlighter.LANG_DELIM_KEY),
            AttributesDescriptor("Language tag//Language ID", EisodosTemplateSyntaxHighlighter.LANG_ID_KEY),
            AttributesDescriptor("Language tag//Default text", EisodosTemplateSyntaxHighlighter.LANG_DEFAULT_TEXT_KEY),
            AttributesDescriptor("Language tag//Parameters", EisodosTemplateSyntaxHighlighter.LANG_PARAM_KEY),

            AttributesDescriptor("Comment", EisodosTemplateSyntaxHighlighter.COMMENT_KEY),
            AttributesDescriptor("Bad character", EisodosTemplateSyntaxHighlighter.BAD_CHAR_KEY),
        )
    }

    override fun getIcon(): Icon = EisodosTemplateIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = EisodosTemplateSyntaxHighlighter()

    override fun getDemoText(): String = """
<html>
<body>
## This is a comment line
<h1>${'$'}page_title</h1>
<p>${'$'}greeting~='Hello World';</p>
<p>${'$'}custom_var:='assigned value';</p>

<div class="[%funcjob=eqs;param=is_active;value=Y;true=active;false=inactive%]">
    [:WELCOME_MSG,Welcome:]
    [:ERROR_LABEL,Error:param1;param2:]
</div>

<%FUNC%
_function_name=renderCard
@name=${'$'}user_name
role=admin
content>>=
<p>Multiline content here</p>
<<=
%FUNC%>
</body>
</html>
""".trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Eisodos Template"
}