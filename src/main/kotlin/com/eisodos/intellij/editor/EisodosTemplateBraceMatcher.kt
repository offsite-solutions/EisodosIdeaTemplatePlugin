package com.eisodos.intellij.editor

import com.eisodos.intellij.parsing.EisodosTemplateTokenTypes
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class EisodosTemplateBraceMatcher : PairedBraceMatcher {

    companion object {
        private val PAIRS = arrayOf(
            BracePair(EisodosTemplateTokenTypes.CB_SHORT_OPEN, EisodosTemplateTokenTypes.CB_SHORT_CLOSE, true),
            BracePair(EisodosTemplateTokenTypes.CB_FULL_OPEN, EisodosTemplateTokenTypes.CB_FULL_CLOSE, true),
            BracePair(EisodosTemplateTokenTypes.LANG_OPEN, EisodosTemplateTokenTypes.LANG_CLOSE, true),
            BracePair(EisodosTemplateTokenTypes.CB_MULTILINE_OPEN, EisodosTemplateTokenTypes.CB_MULTILINE_CLOSE, false),
        )
    }

    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}