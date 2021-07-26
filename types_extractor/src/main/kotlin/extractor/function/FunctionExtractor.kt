package extractor.function

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.addIfNotNull
import com.jetbrains.python.codeInsight.PyPsiIndexUtil
import com.jetbrains.python.psi.*
import org.jetbrains.annotations.Nullable
import kotlin.math.min
import kotlin.math.max

class FunctionExtractor : PyRecursiveElementVisitor() {
    val functions: MutableList<Function> = mutableListOf()
    val avalTypes: MutableList<String> = mutableListOf()
    val preprocessor = Preprocessor()

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
        // println(element.javaClass.name)
        val docstring = function.docStringValue.orEmpty()
        val structuredDocstring = function.structuredDocString
        val parameters = function.parameterList.parameters.filter { !it.isSelf }.map { it.asNamed }
        val parameterTypes = parameters.map { it?.annotationValue.orEmpty() }
        val parameterNames = parameters.map { it?.name.orEmpty() }

        val body = function.statementList.statements
            .map {
                it.text
                    .split("\\s+".toRegex())
                    .map { it.replace("""^[,\.]|[,\.]$""".toRegex(), "") }
                    .map { it.filter { it.isLetterOrDigit() } }
                    .filter { it.any { it.isLetterOrDigit() } }
            }
            .filter { it.isNotEmpty() }
        val argOccurrences = findOccurrences(body, parameterNames, 5)



        functions.add(
            preprocessor.preprocess(
                Function(
                    name = function.name.orEmpty(),
                    docstring = docstring,
                    argDescriptions = structuredDocstring?.parameters?.map {
                        function.structuredDocString?.getParamDescription(it).orEmpty()
                    } ?: ArrayList<String>(parameterNames.size),
                    argNames = parameterNames,
                    argOccurrences = argOccurrences,
                    argTypes = parameterTypes,
                    fnDescription = structuredDocstring?.description,
                    lineNumber = StringUtil.offsetToLineNumber(function.containingFile.text, function.textOffset),
                    returnDescr = structuredDocstring?.returnDescription,
                    returnExpr = getReturnStatements(function),
                    returnType = function.annotationValue.orEmpty(),
                    usages = listOf() // findUsages(function).map { it.text }
                )
            )
        )
    }

    private fun findOccurrences(body: List<List<String>>, parameterNames: List<String>, window: Int): List<String> {
        val occurrences = mutableListOf<String>()
        for (name in parameterNames) {
            for (line in body) {
//                println(line)
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
        var element: @Nullable PsiElement? = usageInfo?.element?.parent ?: return null

        /*while (element != null && ) {
            element = element.parent
        }*/

        while (element != null && (element !is PyTargetExpression && element !is PyCallExpression && element !is PyReturnStatement)) {
            // println(element.javaClass)
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