package extractor.function

import com.intellij.find.findUsages.FindUsagesHandlerBase
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.jetbrains.python.findUsages.PyFunctionFindUsagesHandler
import com.jetbrains.python.findUsages.PyPsiFindUsagesHandlerFactory
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyAugAssignmentStatement
import com.jetbrains.python.psi.PyConditionalStatementPart
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyReturnStatement
import com.jetbrains.python.psi.search.PySuperMethodsSearch
import com.jetbrains.python.psi.types.TypeEvalContext

class UsagesProcessor {
    private val logger = thisLogger()

    fun findUsages(function: PyFunction): Collection<PsiElement> {
        return try {
            findUsagesInternal(function).mapNotNull {
                try {
                    getCaller(it)
                } catch (e: NullPointerException) {
                    return@mapNotNull null
                }
            }
        } catch (e: NullPointerException) {
            logger.warn("couldn't find usages for function ${function.name}")
            listOf()
        }
    }

    private fun findUsagesInternal(function: PyFunction): List<UsageInfo?> {
        val usages: MutableList<UsageInfo> = ArrayList()
        val handler = createFindUsagesHandler(function)
        val elementsToProcess: List<PsiElement> = listOf(*handler.primaryElements, *handler.secondaryElements)
        for (e in elementsToProcess) {
            handler.processElementUsages(e, { usageInfo: UsageInfo ->
                try {
                    if (!usageInfo.isNonCodeUsage) {
                        usages.add(usageInfo)
                    }
                    true
                } catch (_: NullPointerException) {
                    false
                }
            }, FindUsagesHandlerBase.createFindUsagesOptions(function.project, null))
        }
        return usages
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
            logger.warn("couldn't find function call for ${element.text}")
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

    private fun createFindUsagesHandler(function: PyFunction): FindUsagesHandlerBase {
        val context = TypeEvalContext.userInitiated(function.project, null)
        val superMethods = PySuperMethodsSearch.search(function, true, context).findAll()
        if (superMethods.isNotEmpty()) {
            val next = superMethods.iterator().next()
            if (next is PyFunction && !PyPsiFindUsagesHandlerFactory.isInObject(next)) {
                val allMethods: MutableList<PsiElement> = ArrayList()
                allMethods.add(function)
                allMethods.addAll(superMethods)
                return PyFunctionFindUsagesHandler(function, allMethods)
            }
        }
        return PyFunctionFindUsagesHandler(function)
    }
}

