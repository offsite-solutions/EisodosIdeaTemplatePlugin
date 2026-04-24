package com.eisodos.intellij

import com.intellij.lang.Language
import com.intellij.psi.templateLanguages.TemplateLanguage

class EisodosTemplateLanguage private constructor() : Language(ID), TemplateLanguage {
    companion object {
        const val ID = "EisodosTemplate"
        @JvmField
        val INSTANCE = EisodosTemplateLanguage()
    }

    override fun getDisplayName(): String = "Eisodos Template"
}