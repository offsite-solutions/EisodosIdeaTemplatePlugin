package com.eisodos.intellij.psi

import com.eisodos.intellij.EisodosTemplateFileType
import com.eisodos.intellij.EisodosTemplateLanguage
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class EisodosTemplatePsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, EisodosTemplateLanguage.INSTANCE) {
    override fun getFileType(): FileType = EisodosTemplateFileType.INSTANCE
    override fun toString(): String = "Eisodos Template File"
}