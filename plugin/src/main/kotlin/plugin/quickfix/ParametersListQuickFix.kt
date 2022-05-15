package plugin.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.TypeEvalContext
import plugin.predictors.KInferencePredictor

/**
 * Quick fix for functions where at least one parameter is not type annotated
 */
class ParametersListQuickFix : LocalQuickFix {
    private val logger = thisLogger()

    override fun getFamilyName(): String {
        return name
    }

    override fun getName(): String {
        return "Annotate function parameters"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val parameterList = descriptor.psiElement
        if (parameterList !is PyParameterList) {
            return
        }
        val function = parameterList.containingFunction ?: return

        val generator = PyElementGenerator.getInstance(project)
        val newParameterList = generator.createParameterList(LanguageLevel.PYTHON38, "()")

        val context = TypeEvalContext.userInitiated(project, function.containingFile)

        val newParameters =
            (if (function.parameterList.parameters.none { it.isSelf }) mapOf() else mapOf("self" to listOf(""))) +
                KInferencePredictor().predictParameters(function, 3).map { it -> it.key to it.value.map { it.first } }

        function.parameterList.parameters.forEach { old ->
            newParameterList.addParameter(generator.createParameter(old.name!!,
                old.defaultValueText,
                old.asNamed?.annotationValue ?: if (old.isSelf) null else newParameters[old.name!!]?.firstOrNull {
                    typeCheck(old, it, context)
                },
                LanguageLevel.PYTHON38))
        }

        function.parameterList.replace(newParameterList)
    }

    private fun typeCheck(parameter: PyParameter, parameterType: String, context: TypeEvalContext): Boolean {
        return if (parameter !is PyNamedParameter) {
            false
        } else {
            val predictedType = PyTypeParser.getTypeByName(parameter, parameterType)
            val expectedType = parameter.getArgumentType(context)
            if (!PyTypeChecker.match(expectedType, predictedType, context)) {
                logger.warn("In parameter ${parameter.name} couldn't perform typecheck of expected type ${expectedType?.name}" +
                    " and predicted type ${predictedType?.name}")
                false
            } else {
                true
            }
        }

    }
}
