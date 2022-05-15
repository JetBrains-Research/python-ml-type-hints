package plugin.typing

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns.psiElement
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyReferenceExpression
import plugin.predictors.Type4PyPredictor
import plugin.typing.providers.MLCompletionProvider

class MLCompletionContributor : CompletionContributor(), DumbAware {
    init {
        /*extend(
            CompletionType.BASIC,
            psiElement().afterLeaf(psiElement(PyTokenTypes.DOT))
                .withParent(
                    psiElement(PyReferenceExpression::class.java)
                        .withFirstChild(psiElement(PyReferenceExpression::class.java))
                ),
            MLCompletionProvider(KInferencePredictor())
        )*/
        extend(
            CompletionType.BASIC,
            psiElement().afterLeaf(psiElement(PyTokenTypes.DOT))
                .withParent(
                    psiElement(PyReferenceExpression::class.java)
                        .withFirstChild(psiElement(PyReferenceExpression::class.java))
                ),
            MLCompletionProvider(Type4PyPredictor(true))
        )
    }
}
