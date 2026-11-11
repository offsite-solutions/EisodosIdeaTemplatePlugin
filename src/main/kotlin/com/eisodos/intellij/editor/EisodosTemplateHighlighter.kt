package com.eisodos.intellij.editor

import com.eisodos.intellij.parsing.EisodosTemplateTokenTypes
import com.intellij.lang.Language
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings

class EisodosTemplateHighlighter(
    project: Project?,
    virtualFile: VirtualFile?,
    colors: EditorColorsScheme
) : LayeredLexerEditorHighlighter(EisodosTemplateSyntaxHighlighter(), colors) {

    init {
        val outerFileType = if (project == null || virtualFile == null) {
            FileTypes.PLAIN_TEXT
        } else {
            val lang = TemplateDataLanguageMappings.getInstance(project).getMapping(virtualFile)
            // Looked up by ID: HtmlFileType moved into an XML plugin content module in 2026.3.
            (lang ?: Language.findLanguageByID("HTML"))?.associatedFileType ?: FileTypes.PLAIN_TEXT
        }

        val outerHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(outerFileType, project, virtualFile)
        if (outerHighlighter != null) {
            registerLayer(EisodosTemplateTokenTypes.CONTENT, LayerDescriptor(outerHighlighter, ""))
        }
    }
}
