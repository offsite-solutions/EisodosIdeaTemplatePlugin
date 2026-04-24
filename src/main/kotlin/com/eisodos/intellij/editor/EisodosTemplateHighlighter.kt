package com.eisodos.intellij.editor

import com.eisodos.intellij.parsing.EisodosTemplateTokenTypes
import com.intellij.ide.highlighter.HtmlFileType
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
            lang?.associatedFileType ?: HtmlFileType.INSTANCE
        }

        val outerHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(outerFileType, project, virtualFile)
        if (outerHighlighter != null) {
            registerLayer(EisodosTemplateTokenTypes.CONTENT, LayerDescriptor(outerHighlighter, ""))
        }
    }
}
