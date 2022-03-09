package plugin.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.TypeEvalContext
import extractor.function.Function
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
        val functionData = extractor.functions.first { checkEqual(function, it) }

        val predictedType = TypePredictor.predictReturnType(functionData)

        println("predicted type for function ${functionData.fullName} is $predictedType")
        val returnType = PyTypeParser.getTypeByName(function, predictedType)
        val context = TypeEvalContext.userInitiated(project, function.containingFile)
        val expectedType = context.getReturnType(function)
        if (!PyTypeChecker.match(expectedType, returnType, context) || returnType == null) {
            println("couldn't perform typecheck of expected type ${expectedType?.name} and predicted type ${returnType?.name}")
            return
        }

        val newFunction = generator.createFromText(
            LanguageLevel.PYTHON38,
            PyFunction::class.java,
            (if (function.decoratorList != null) "@Override\n" else "") +
                "def fun(args) -> ${returnType.name}:\n" +
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

    private fun checkEqual(function: PyFunction, functionData: Function) =
        function.name == functionData.fullName &&
            StringUtil.offsetToLineNumber(function.containingFile.text,
                function.textOffset) == functionData.lineNumber &&
            function.parameterList.parameters
                .filter { !it.isSelf }
                .map { it.asNamed }
                .map { it?.name } == functionData.argFullNames

}
