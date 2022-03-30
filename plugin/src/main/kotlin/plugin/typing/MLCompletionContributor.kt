package plugin.typing

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
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
import extractor.utils.checkEqual
import plugin.predictors.TypePredictor

class MLCompletionContributor : CompletionContributor(), DumbAware {
    init {
        // TODO fix completion list not appearing when typing first time
        extend(CompletionType.BASIC,
            psiElement().afterLeaf(psiElement(PyTokenTypes.DOT))
                .withParent(psiElement(PyReferenceExpression::class.java)
                    .withFirstChild(psiElement(PyReferenceExpression::class.java))),
            MLCompletionProvider())
    }

    private class MLCompletionProvider : CompletionProvider<CompletionParameters>() {
        private val logger = thisLogger()

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val original = parameters.position
            logger.debug("captured is  ${original.text} of class ${original.javaClass}")
            val element = PsiTreeUtil
                .getParentOfType(original, PyReferenceExpression::class.java)
                ?.firstChild.castSafelyTo<PyReferenceExpression>()
                ?.reference?.resolve() ?: return
            logger.debug("reference is ${element.text} of class ${element.javaClass}")

            val prefix = original.containingFile.text.substring(original.textOffset, parameters.offset)
            if (element is PyAnnotationOwner && element.annotation != null) {
                return
            }

            when (element) {
                is PyFunction -> {
                    val extractor = FunctionExtractor(element.project, element.containingFile)
                    element.accept(extractor)
                    val types = TypePredictor.predictReturnType(extractor.functions.first {
                        checkEqual(element, it)
                    }, topN = 3)
                    types.forEach { type ->
                        val pyType = PyTypeParser.getTypeByName(element, type) ?: return@forEach
                        pyType.getCompletionVariants(prefix, element, context)
                            .filterIsInstance(LookupElement::class.java)
                            .forEach(result::addElement)
                    }
                }
                is PyNamedParameter -> {
                    val extractor = FunctionExtractor(element.project, element.containingFile)
                    val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
                    function?.accept(extractor)
                    if (extractor.functions.isEmpty()) {
                        return
                    }
                    val types = TypePredictor.predictParameters(extractor.functions.first {
                        checkEqual(function, it)
                    }, topN = 3)[element.name] ?: return
                    types.forEach { type ->
                        val pyType = PyTypeParser.getTypeByName(element, type) ?: return@forEach
                        pyType.getCompletionVariants(prefix, parameters.position, context)
                            .filterIsInstance(LookupElement::class.java)
                            .forEach(result::addElement)
                    }
                }
            }
        }
    }
}
