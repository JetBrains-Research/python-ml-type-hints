package extractor.workers

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyAnnotationOwner
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.TypeEvalContext
import extractor.elements.ElementInfo
import extractor.elements.ElementType
import extractor.utils.createFiles
import extractor.utils.forEachProjectInDir
import extractor.utils.setupProject
import extractor.utils.traverseProject
import org.jetbrains.dataframe.io.writeCSV
import org.jetbrains.dataframe.toDataFrameByProperties
import java.nio.file.Path

class ProjectTypeInferrer(val output: String) {
    fun inferTypes(dirPath: String, envName: String): List<ElementInfo> {
        val types = mutableListOf<ElementInfo>()

        forEachProjectInDir(dirPath) { project, projectDir ->
            setupProject(project, envName, projectDir)
            traverseProject(project) { psi, _ ->
                val typedElements = mutableListOf<ElementInfo>()
                psi.accept(InferringElementVisitor(typedElements))
                types.addAll(typedElements)
            }
        }

        return types
    }

    fun printTypes(types: List<ElementInfo>, output: String) {
        val typePath = Path.of(output, "types.csv")
        createFiles(typePath)

        val df = types.toDataFrameByProperties()
        df.writeCSV(typePath.toFile())
    }
}

class InferringElementVisitor(
    private val typedElements: MutableList<ElementInfo>
) : PsiRecursiveElementVisitor() {

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)

        if (element is PyAnnotationOwner &&
            (element is PyFunction || element is PyNamedParameter) &&
            element.parent !is PyImportElement
        ) {
            val context = TypeEvalContext.userInitiated(element.project, element.containingFile)
            val line = StringUtil.offsetToLineNumber(element.containingFile.text, element.textOffset)
            val type = getElementType(element, context)
            val annotation = element.annotationValue ?: "Any"

            val elementType: ElementType = when (element) {
                is PyParameter -> ElementType.PARAMETER
                is PyFunction -> ElementType.FUNCTION
                is PyTargetExpression -> ElementType.VARIABLE
                else -> ElementType.NONE
            }

            val path = element.containingFile.virtualFile.path
            typedElements.add(
                ElementInfo((element as PyTypedElement).name.orEmpty(), type, annotation, path, line, elementType)
            )
        }
    }

    private fun getElementType(element: PyAnnotationOwner, context: TypeEvalContext) =
        when (element) {
            is PyFunction -> try {
                element.getReturnStatementType(context)?.name ?: "Any"
            } catch (e: Exception) {
                "Any"
            }
            else -> try {
                PythonDocumentationProvider.getTypeName(context.getType(element as PyTypedElement), context)
            } catch (e: Exception) {
                "Any"
            }
        }
}
