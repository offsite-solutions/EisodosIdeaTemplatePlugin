package com.eisodos.intellij.file

import com.eisodos.intellij.EisodosTemplateLanguage
import com.eisodos.intellij.parsing.EisodosTemplateTokenTypes
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.ConfigurableTemplateLanguageFileViewProvider
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings

class EisodosTemplateFileViewProvider(
    manager: PsiManager,
    virtualFile: VirtualFile,
    eventSystemEnabled: Boolean
) : MultiplePsiFilesPerDocumentFileViewProvider(manager, virtualFile, eventSystemEnabled),
    ConfigurableTemplateLanguageFileViewProvider {

    companion object {
        private val TEMPLATE_DATA = TemplateDataElementType(
            "EISODOS_TEMPLATE_DATA",
            EisodosTemplateLanguage.INSTANCE,
            EisodosTemplateTokenTypes.CONTENT,
            EisodosTemplateTokenTypes.OUTER_ELEMENT_TYPE
        )
    }

    override fun getBaseLanguage(): Language = EisodosTemplateLanguage.INSTANCE

    override fun getLanguages(): Set<Language> {
        return setOf(EisodosTemplateLanguage.INSTANCE, getTemplateDataLanguage())
    }

    override fun getTemplateDataLanguage(): Language {
        val mappings = TemplateDataLanguageMappings.getInstance(manager.project)
        val dataLanguage = mappings.getMapping(virtualFile)
        if (dataLanguage != null) {
            val substituteLang = Language.findLanguageByID(dataLanguage.id)
            if (substituteLang != null) {
                return substituteLang
            }
        }
        // Looked up by ID: HTMLLanguage moved into an XML plugin content module in 2026.3.
        return Language.findLanguageByID("HTML") ?: PlainTextLanguage.INSTANCE
    }

    override fun createFile(lang: Language): PsiFile? {
        val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang) ?: return null

        if (lang.isKindOf(EisodosTemplateLanguage.INSTANCE)) {
            return parserDefinition.createFile(this)
        }

        if (lang === getTemplateDataLanguage()) {
            val file = parserDefinition.createFile(this) as? PsiFileImpl ?: return null
            file.contentElementType = TEMPLATE_DATA
            return file
        }

        return null
    }

    override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider {
        return EisodosTemplateFileViewProvider(manager, fileCopy, false)
    }
}