package plugin.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.PyTupleExpression
import plugin.quickfix.AssignmentQuickFix
import plugin.quickfix.FunctionQuickFix
import plugin.quickfix.ParametersListQuickFix

/**
 * Inspection to detect variable declarations without type annotations
 */
class NoAnnotationInspection : PyInspection() {

    override fun getShortName(): String {
        return "NoAnnotation"
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PyElementVisitor {
        return object : PyElementVisitor() {

            private val DESCRIPTION = "No type annotation"

            override fun visitElement(element: PsiElement) {
                super.visitElement(element)

                if (element is PyAssignmentStatement) {
                    registerForAssignment(element)
                }

                if (element is PyFunction) {
                    registerForParameterList(element.parameterList)
                    registerForFunctionReturn(element)
                }
            }

            private fun registerForAssignment(assignment: PyAssignmentStatement) {
                if (assignment.annotationValue == null &&
                    isSingleVariable(assignment) &&
                    assignment.assignedValue != null
                ) {
                    holder.registerProblem(assignment, DESCRIPTION, AssignmentQuickFix())
                }
            }

            private fun registerForFunctionReturn(function: PyFunction) {
                val nameIdentifier = function.nameIdentifier ?: return
                if (function.annotation == null &&
                    !function.containingClass?.multiFindInitOrNew(false, null).orEmpty().contains(function)
                ) {
                    holder.registerProblem(
                        function,
                        nameIdentifier.textRange.shiftLeft(function.textRange.startOffset),
                        DESCRIPTION,
                        FunctionQuickFix()
                    )
                }
            }

            private fun registerForParameterList(parameterList: PyParameterList) {
                if (parameterList.parameters
                        .filter { !it.isSelf }
                        .filterIsInstance<PyNamedParameter>()
                        .any { it.annotationValue == null }
                ) {
                    holder.registerProblem(parameterList, DESCRIPTION, ParametersListQuickFix())
                }
            }
        }
    }

    private fun isSingleVariable(element: PyAssignmentStatement): Boolean {
        return element.rawTargets.size == 1 && element.leftHandSideExpression !is PyTupleExpression
    }
}
