package plugin.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyParameterList
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

        function.parameterList.parameters.forEach { old ->
            newParameterList.addParameter(
                generator.createParameter(
                    old.name!!,
                    old.defaultValueText,
                    old.asNamed?.annotationValue ?: if (old.isSelf) null else newParameters[old.name!!],
                    LanguageLevel.PYTHON38
                )
            )
        }

        function.parameterList.replace(newParameterList)
    }
}
