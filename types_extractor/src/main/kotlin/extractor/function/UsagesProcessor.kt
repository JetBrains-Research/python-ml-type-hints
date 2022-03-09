package extractor.function

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.jetbrains.python.codeInsight.PyPsiIndexUtil
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyAugAssignmentStatement
import com.jetbrains.python.psi.PyConditionalStatementPart
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyReturnStatement

class UsagesProcessor {
    fun findUsages(function: PyFunction): Collection<PsiElement> {
        return try {
            PyPsiIndexUtil.findUsages(function, false).mapNotNull { getCaller(it) }
        } catch (e: NullPointerException) {
            println("couldn't find usages for function ${function.text}")
            listOf()
        }
    }

    private fun getCaller(usageInfo: UsageInfo?): PsiElement? {
        val element: PsiElement = usageInfo?.element ?: return null
        try {
            val caller = PsiTreeUtil.getParentOfType(
                element,
                PyExpressionStatement::class.java,
                PyAssignmentStatement::class.java,
                PyAugAssignmentStatement::class.java,
                PyConditionalStatementPart::class.java,
                PyReturnStatement::class.java
            )

            return when (caller) {
                is PyExpressionStatement -> {
                    caller.expression
                }
                is PyAssignmentStatement -> {
                    getAssignmentFrom(caller, element)

                }
                is PyAugAssignmentStatement -> {
                    val assignment = PyElementGenerator.getInstance(element.project)
                        .createFromText(LanguageLevel.PYTHON38, PyAssignmentStatement::class.java, "val = val")

                    assignment.leftHandSideExpression!!.replace(caller.target)
                    assignment.assignedValue!!.replace(caller.value ?: return null)

                    assignment
                }
                is PyConditionalStatementPart -> {
                    caller.condition
                }
                is PyReturnStatement -> {
                    caller.expression
                }
                else -> null
            }
        } catch (e: NullPointerException) {
            println("couldn't find function call for ${element.text}")
            return null
        }
    }

    private fun getAssignmentFrom(caller: PyAssignmentStatement, element: PsiElement): PsiElement? {
        // TODO convert assignment sides to tuple expressions
        /*val assignment = getTargets(caller)
        val targets = assignment.targets
        if (targets.size == 1) {
            return targets.first()
        }
        try {
            val rhs = caller.assignedValue as PySequenceExpression
            if (rhs.elements.size != targets.size) {
                return null
            }
            val assignment = targets.zip(rhs.elements).filter {
                val
            }
        } catch (e: ClassCastException) {
            print("couldn't match targets with values")
            return null
        }*/
        return caller.targetsToValuesMapping
            .firstOrNull {
                val finder = PsiElementFinder(element)
                it.second.accept(finder)
                finder.didFind
            }
            ?.let {
                val assignment = PyElementGenerator.getInstance(element.project)
                    .createFromText(LanguageLevel.PYTHON38, PyAssignmentStatement::class.java, "val = val")

                assignment.leftHandSideExpression!!.replace(it.first)
                assignment.assignedValue!!.replace(it.second)

                println(assignment.text)
                assignment
            }
    }

    private class PsiElementFinder(val elementToFind: PsiElement) : PyRecursiveElementVisitor() {
        var didFind = false
            private set

        override fun visitElement(element: PsiElement) {
            super.visitElement(element)

            if (element.text == elementToFind.text) {
                didFind = true
                return
            }
        }
    }
}

