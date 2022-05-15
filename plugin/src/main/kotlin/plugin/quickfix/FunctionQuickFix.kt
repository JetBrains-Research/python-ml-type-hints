package plugin.quickfix

//import plugin.inspections.PyTypeCheckerInspector
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.thisLogger
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
import plugin.predictors.KInferencePredictor

class FunctionQuickFix : LocalQuickFix {
    private val logger = thisLogger()

    override fun getFamilyName(): String {
        return name
    }

    override fun getName(): String {
        return "Annotate function return type"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val function = descriptor.psiElement
        if (function !is PyFunction) return

        val context = TypeEvalContext.userInitiated(project, function.containingFile)

        val predictedTypes = KInferencePredictor().predictReturnType(function, topN = 10).map { it.first }
            .filter {
                typeCheck(function, it, context)
//                typeCheckWithInspection(function, it, project)
            }.let { it + listOf("typing.Any") }
        logger.warn("predicted type for function ${function.name} is ${predictedTypes.first()}")
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
                                swapFunctionPsi(function, selectedValue, project)
                            }
                    }
                    return null
                }
            })
        popup.showInBestPositionFor(FileEditorManager.getInstance(project)
            .selectedTextEditor ?: return)

    }

    private fun swapFunctionPsi(
        function: PyFunction,
        newReturnType: String,
        project: Project,
    ) {
        val newFunction = generateNewFunctionWithType(function, newReturnType, project)
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
            logger.warn("In function ${function.name} couldn't perform typecheck " +
                "of expected type ${expectedType?.name} and predicted type ${predictedType?.name}")
            false
        } else {
            true
        }
    }

    /*private fun typeCheckWithInspection(function: PyFunction, type: String, project: Project): Boolean {
        val typeChecker = PyTypeCheckerInspector()
        val file = function.containingFile
        function.copy()

        val newFile = PsiFileFactory.getInstance(project).createFileFromText(file.name, file.fileType, file.text)
        val functionInCopy =
            PyUtil.findNonWhitespaceAtOffset(newFile, function.textOffset)?.parentOfType<PyFunction>() ?: return false
        val newFunction = generateNewFunctionWithType(functionInCopy, type, project)
        functionInCopy.replace(newFunction)
        val mistakeBeforeSwap = typeChecker.runTypeCheck(file)
        val mistakeAfterSwap = typeChecker.runTypeCheck(newFile)

        return mistakeAfterSwap <= mistakeBeforeSwap

    }*/

    private fun generateNewFunctionWithType(function: PyFunction, type: String, project: Project): PyFunction {
        val generator = PyElementGenerator.getInstance(project)
        val newFunction = generator.createFromText(
            LanguageLevel.PYTHON38,
            PyFunction::class.java,
            (if (function.decoratorList != null) "@Override\n" else "") +
                "def fun(args) -> $type:\n" +
                "    \"\"\"" +
                "    \"\"\"" +
                "    pass"
        )

        function.nameIdentifier?.let { newFunction.nameIdentifier?.replace(it) }
        function.parameterList.let { newFunction.parameterList.replace(it) }
        function.statementList.let { newFunction.statementList.replace(it) }
        function.docStringExpression?.let { newFunction.docStringExpression?.replace(it) }
        function.decoratorList?.let { newFunction.decoratorList?.replace(it) }

        return newFunction
    }
}
