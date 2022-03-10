package plugin.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.TypeEvalContext
import extractor.function.FunctionExtractor
import extractor.utils.checkEqual
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

        val extractor = FunctionExtractor()
        function.accept(extractor)
        val functionData = extractor.functions.first { checkEqual(function, it) }


        val context = TypeEvalContext.userInitiated(project, function.containingFile)
        val predictedTypes = TypePredictor.predictReturnType(functionData, topN = 100)
            .filter { typeCheck(function, it, context) }.let { it + listOf("Any") }
        println("predicted type for function ${functionData.fullName} is ${predictedTypes.first()}")
        val popup = JBPopupFactory.getInstance()
            .createListPopup(object : BaseListPopupStep<String>(null, predictedTypes) {
                override fun getTextFor(value: String): String {
                    return value
                }

                override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                    if (finalChoice) {
                        writeCommandAction(project, function.containingFile)
                            .withName("INSERT_TYPE_ANNOTATION")
                            .run<RuntimeException> {
                                val generator = PyElementGenerator.getInstance(project)
                                swapFunctionPsi(generator, function, selectedValue)
                            }
                    }
                    return null
                }
            })
        popup.showInBestPositionFor(FileEditorManager.getInstance(project)
            .selectedTextEditor ?: return)

    }

    private fun swapFunctionPsi(
        generator: PyElementGenerator,
        function: PyFunction,
        newReturnType: String,
    ) {
        val newFunction = generator.createFromText(
            LanguageLevel.PYTHON38,
            PyFunction::class.java,
            (if (function.decoratorList != null) "@Override\n" else "") +
                "def fun(args) -> $newReturnType:\n" +
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

    private fun typeCheck(
        function: PyFunction,
        returnType: String,
        context: TypeEvalContext,
    ): Boolean {
        val expectedType = context.getReturnType(function)
        val predictedType = PyTypeParser.getTypeByName(function, returnType)
        return if (!PyTypeChecker.match(expectedType, predictedType, context) || predictedType == null) {
            println("In function ${function.name} couldn't perform typecheck " +
                "of expected type ${expectedType?.name} and predicted type ${predictedType?.name}")
            false
        } else {
            true
        }
    }

}
