package plugin.typing.providers

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.castSafelyTo
import com.jetbrains.python.psi.PyAnnotationOwner
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.PyTypeParser
import plugin.exceptions.PredictionException
import plugin.predictors.TypePredictor

class MLCompletionProvider(private val predictor: TypePredictor) : CompletionProvider<CompletionParameters>() {
    private val logger = thisLogger()

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val original = parameters.position
//            logger.warn("captured is  ${original.text} of class ${original.javaClass}")
        val element = PsiTreeUtil
            .getParentOfType(original, PyReferenceExpression::class.java)
            ?.firstChild.castSafelyTo<PyReferenceExpression>()
            ?.reference?.resolve() ?: return
//            logger.warn("reference is ${element.text} of class ${element.javaClass}")

        val prefix = original.containingFile.text.substring(original.textOffset, parameters.offset)
        if (element is PyAnnotationOwner && element.annotation != null) {
            return
        }
        try {
            when (element) {
                is PyFunction -> {
                    val types = predictor.predictReturnType(element, topN = 4)
                    types.forEach { type ->
                        val pyType = PyTypeParser.getTypeByName(element, type.first) ?: return@forEach
                        pyType.getCompletionVariants(prefix, element, context)
                            .filterIsInstance(LookupElement::class.java)
                            .map { lookupElement -> PrioritizedLookupElement.withPriority(lookupElement, type.second) }
                            .forEach(result::addElement)
                    }
                }
                is PyNamedParameter -> {
                    val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java) ?: return
                    val types = predictor.predictParameters(function, topN = 4)[element.name] ?: return
                    logger.warn("predicted types are ${types.joinToString()}")
                    types.forEach { type ->
                        val pyType = PyTypeParser.getTypeByName(element, type.first) ?: return@forEach
                        pyType.getCompletionVariants(prefix, parameters.position, context)
                            .filterIsInstance(LookupElement::class.java)
                            .map { lookupElement ->  PrioritizedLookupElement.withPriority(lookupElement, type.second) }
                            .forEach(result::addElement)
                    }
                }
            }
        } catch (e: PredictionException) {
            logger.warn(e)
        }
    }
}