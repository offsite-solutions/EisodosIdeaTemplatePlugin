package com.eisodos.intellij

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class EisodosTemplateFileType private constructor() : LanguageFileType(EisodosTemplateLanguage.INSTANCE) {
    companion object {
        @JvmField
        val INSTANCE = EisodosTemplateFileType()
    }

    override fun getName(): String = "Eisodos Template"
    override fun getDescription(): String = "Eisodos template file"
    override fun getDefaultExtension(): String = "template"
    override fun getIcon(): Icon = EisodosTemplateIcons.FILE
}