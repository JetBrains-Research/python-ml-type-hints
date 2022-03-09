package plugin.typing

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.castSafelyTo
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyAnnotationOwner
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.PyTypeParser
import extractor.function.FunctionExtractor
import plugin.predictors.TypePredictor

class MLCompletionContributor : CompletionContributor() {
    init {
        // TODO fix completion list not appearing when typing first time
        extend(CompletionType.BASIC,
            psiElement().afterLeaf(psiElement(PyTokenTypes.DOT))
                .withParent(psiElement(PyReferenceExpression::class.java)
                    .withFirstChild(psiElement(PyReferenceExpression::class.java))),
            MLCompletionProvider())
    }

    private class MLCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val original = parameters.originalPosition ?: return
            val element = PsiTreeUtil
                .getParentOfType(original, PyReferenceExpression::class.java)
                ?.firstChild.castSafelyTo<PyReferenceExpression>()
                ?.reference?.resolve() ?: return

            val prefix = original.containingFile.text.substring(original.textOffset, parameters.offset)
            if (element is PyAnnotationOwner && element.annotation != null) {
                return
            }

            when (element) {
                is PyFunction -> {
                    val extractor = FunctionExtractor()
                    element.accept(extractor)
                    val type = TypePredictor.predictReturnType(extractor.functions.first())
                    val pyType = PyTypeParser.getTypeByName(element, type) ?: return
                    pyType.getCompletionVariants(prefix, element, context)
                        .filterIsInstance(LookupElement::class.java)
                        .forEach(result::addElement)
                }
                is PyNamedParameter -> {
                    val extractor = FunctionExtractor()
                    PsiTreeUtil.getParentOfType(element, PyFunction::class.java)?.accept(extractor)
                    if (extractor.functions.isEmpty()) {
                        return
                    }
                    val type = TypePredictor.predictParameters(extractor.functions.first())[element.name] ?: return
                    val pyType = PyTypeParser.getTypeByName(element, type) ?: return
                    pyType.getCompletionVariants(prefix, parameters.position, context)
                        .filterIsInstance(LookupElement::class.java)
                        .forEach(result::addElement)
                }
                else -> return
            }
        }

    }
}