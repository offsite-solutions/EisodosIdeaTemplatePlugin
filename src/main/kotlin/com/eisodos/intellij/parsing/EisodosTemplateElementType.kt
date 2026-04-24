package com.eisodos.intellij.parsing

import com.eisodos.intellij.EisodosTemplateLanguage
import com.intellij.psi.tree.IElementType

class EisodosTemplateElementType(debugName: String) : IElementType(debugName, EisodosTemplateLanguage.INSTANCE)

class EisodosTemplateCompositeElementType(debugName: String) : IElementType(debugName, EisodosTemplateLanguage.INSTANCE)