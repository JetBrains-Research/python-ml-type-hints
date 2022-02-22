package extractor.function

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.addIfNotNull
import com.jetbrains.python.codeInsight.PyPsiIndexUtil
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyReturnStatement
import com.jetbrains.python.psi.PyTargetExpression
import kotlin.math.max
import kotlin.math.min

class FunctionExtractor : PyRecursiveElementVisitor() {
    val functions: MutableList<Function> = mutableListOf()
    val avalTypes: MutableList<String> = mutableListOf()
    private val preprocessor = Preprocessor()

    override fun visitPyElement(node: PyElement) {
        super.visitPyElement(node)
        if (node !is PyImportStatementBase) {
            return
        }

        val import = node.importElements.mapNotNull { it.name }
        avalTypes.addAll(import)
    }

    override fun visitPyClass(node: PyClass) {
        super.visitPyClass(node)

        avalTypes.addIfNotNull(node.name)
    }

    override fun visitPyFunction(function: PyFunction) {
        super.visitElement(function)
        val docstring = function.docStringValue.orEmpty()
        val structuredDocstring = function.structuredDocString
        val parameters = function.parameterList.parameters.filter { !it.isSelf }.map { it.asNamed }
        val parameterTypes = parameters.map { it?.annotationValue.orEmpty() }
        val parameterNames = parameters.map { it?.name.orEmpty() }

        val body = function.statementList.statements
            .map { statement ->
                statement.text
                    .split("\\s+".toRegex())
                    .map { it.replace("""^[,.]|[,.]$""".toRegex(), "") }
                    .map { it.filter { it.isLetterOrDigit() } }
                    .filter { it -> it.any { it.isLetterOrDigit() } }
            }
            .filter { it.isNotEmpty() }
        val argOccurrences = parameterNames // findOccurrences(body, parameterNames, 5)

        functions.add(
            preprocessor.preprocess(
                Function(
                    name = function.name.orEmpty(),
                    docstring = docstring,
                    argDescriptions = parameterNames.map {
                        structuredDocstring?.getParamDescription(it).orEmpty()
                    },
                    argNames = parameterNames,
                    argOccurrences = argOccurrences,
                    argTypes = parameterTypes,
                    fnDescription = structuredDocstring?.description.orEmpty(),
                    lineNumber = StringUtil.offsetToLineNumber(function.containingFile.text, function.textOffset),
                    returnDescr = structuredDocstring?.returnDescription.orEmpty(),
                    returnExpr = getReturnStatements(function),
                    returnType = function.annotationValue.orEmpty(),
                    usages = listOf(), // findUsages(function).map { it.text }
                    fullName = function.name.orEmpty(),
                    argFullNames = parameterNames
                )
            )
        )
    }

    // TODO make one string for each occurrence
    private fun findOccurrences(body: List<List<String>>, parameterNames: List<String>, window: Int): List<String> {
        val occurrences = mutableListOf<String>()
        for (name in parameterNames) {
            val hasOccurred = false
            for (line in body) {
                if (name in line) {
                    val loc = line.indexOf(name)
                    occurrences.add(
                        line.subList(max(0, loc - window / 2), min(loc + window / 2, line.size))
                            .joinToString(" ")
                    )
                }
            }
        }
        return occurrences
    }

    private fun findUsages(function: PyFunction): Collection<PsiElement> {
        return PyPsiIndexUtil.findUsages(function, false).mapNotNull { getCaller(it) }
    }

    private fun getCaller(usageInfo: UsageInfo?): PsiElement? {
        var element: PsiElement = usageInfo?.element?.parent ?: return null

        while (element !is PyTargetExpression && element !is PyCallExpression && element !is PyReturnStatement) {
            element = element.parent
        }

        while (element is PyCallExpression) {
            element = element.parent
        }

        return element
    }

    private fun getReturnStatements(element: PyFunction): List<String> {
        val visitor = ReturnVisitor()
        element.accept(visitor)
        return visitor.returnList
    }

    private class ReturnVisitor : PyRecursiveElementVisitor() {
        val returnList: MutableList<String> = mutableListOf()

        override fun visitPyReturnStatement(node: PyReturnStatement) {
            super.visitPyReturnStatement(node)

            returnList.add(node.text)
        }
    }
}
