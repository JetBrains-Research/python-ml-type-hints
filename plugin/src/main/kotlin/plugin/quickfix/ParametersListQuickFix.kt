package plugin.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.TypeEvalContext
import extractor.function.FunctionExtractor
import plugin.predictors.TypePredictor

/**
 * Quick fix for functions where at least one parameter is not type annotated
 */
class ParametersListQuickFix : LocalQuickFix {
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

        val extractor = FunctionExtractor()
        function.accept(extractor)
        val newParameters = (if (function.containingClass == null) mapOf() else mapOf("self" to "")) +
            TypePredictor.predictParameters(extractor.functions.first())

        val context = TypeEvalContext.userInitiated(project, function.containingFile)

        function.parameterList.parameters.forEach { old ->
            newParameterList.addParameter(
                generator.createParameter(
                    old.name!!,
                    old.defaultValueText,
                    old.asNamed?.annotationValue ?: if (old.isSelf || !typeCheck(old,
                            newParameters,
                            context)
                    ) null else newParameters[old.name!!],
                    LanguageLevel.PYTHON38
                )
            )
        }

        function.parameterList.replace(newParameterList)
    }

    private fun typeCheck(parameter: PyParameter, newParameters: Map<String, String>, context: TypeEvalContext) =
        if (parameter !is PyNamedParameter) {
            false
        } else {
            val predictedType = PyTypeParser.getTypeByName(parameter, newParameters[parameter.name] ?: "")
            val type = parameter.getArgumentType(context)
            PyTypeChecker.match(type, predictedType, context)
        }
}
