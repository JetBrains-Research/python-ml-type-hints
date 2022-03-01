package plugin.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFunction
import extractor.function.FunctionExtractor
import plugin.predictors.TypePredictor

class FunctionQuickFix : LocalQuickFix {
    override fun getFamilyName(): String {
        return name
    }

    override fun getName(): String {
        return "Annotate function return type"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val function = descriptor.psiElement
        if (function !is PyFunction) return

        val generator = PyElementGenerator.getInstance(project)

        val extractor = FunctionExtractor()
        function.accept(extractor)
        val functionData = extractor.functions.first()
        val returnType = TypePredictor.predictReturnType(functionData)

        val newFunction = generator.createFromText(
            LanguageLevel.PYTHON38,
            PyFunction::class.java,
            "def fun(args) -> ${returnType}:\n" +
                "    \"\"\"" +
                "    \"\"\"" +
                "    pass"
        )

        function.nameIdentifier?.let { newFunction.nameIdentifier?.replace(it) }
        function.parameterList.let { newFunction.parameterList.replace(it) }
        function.statementList.let { newFunction.statementList.replace(it) }
        function.docStringExpression?.let { newFunction.docStringExpression?.replace(it) }
        function.decoratorList?.let { newFunction.decoratorList?.replace(it) }

        function.replace(newFunction)
    }
}
