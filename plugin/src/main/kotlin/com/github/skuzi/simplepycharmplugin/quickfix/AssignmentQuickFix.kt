package com.github.skuzi.simplepycharmplugin.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression

/**
 * Quick fix for assignment statements without explicit type annotation of assigned value
 */
class AssignmentQuickFix : LocalQuickFix {
    override fun getFamilyName(): String {
        return name
    }

    override fun getName(): String {
        return "Annotate target variable"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val assignment = descriptor.psiElement

        if (assignment !is PyAssignmentStatement) {
            return
        }

        val newAssignment = PyElementGenerator.getInstance(project)
            .createFromText(LanguageLevel.PYTHON38, PyAssignmentStatement::class.java, "val: str = val")

        assignment.leftHandSideExpression?.let { newAssignment.leftHandSideExpression!!.replace(it) }
        assignment.assignedValue?.let { newAssignment.assignedValue!!.replace(it) }
        if (assignment.lastChild !is PyExpression) {
            newAssignment.add(assignment.lastChild)
        }

        assignment.replace(newAssignment)
    }
}
