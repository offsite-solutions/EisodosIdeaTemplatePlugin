package com.eisodos.intellij.parsing

import com.eisodos.intellij.psi.EisodosTemplatePsiElement
import com.eisodos.intellij.psi.EisodosTemplatePsiFile
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class EisodosTemplateParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = EisodosTemplateLexer()

    override fun createParser(project: Project?): PsiParser = EisodosTemplateParser()

    override fun getFileNodeType(): IFileElementType = EisodosTemplateTokenTypes.FILE

    override fun getCommentTokens(): TokenSet = EisodosTemplateTokenTypes.COMMENTS

    override fun getStringLiteralElements(): TokenSet = EisodosTemplateTokenTypes.STRINGS

    override fun getWhitespaceTokens(): TokenSet = EisodosTemplateTokenTypes.WHITESPACE

    override fun createElement(node: ASTNode): PsiElement {
        return EisodosTemplatePsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return EisodosTemplatePsiFile(viewProvider)
    }
}