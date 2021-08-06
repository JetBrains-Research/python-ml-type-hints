package extractor.workers

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext
import extractor.elements.ElementType
import extractor.elements.TypedElements
import extractor.utils.forEachProjectInDir
import extractor.utils.setupProject
import extractor.utils.traverseProject
import java.io.File

class ProjectTypeInferrer(val output: String) {
    fun inferTypes(dirPath: String, toInfer: Boolean, envName: String): Map<String, TypedElements> {
        val types = mutableMapOf<String, TypedElements>()

        forEachProjectInDir(dirPath) { project, projectDir ->
            setupProject(project, envName, projectDir)
            traverseProject(project) { psi, _ ->
                val typedElements = TypedElements()
                psi.accept(InferringElementVisitor(typedElements, project.name, toInfer))
            }
        }

        return types
    }

    fun printTypes(types: Map<String, TypedElements>, output: String) {
        val file = File(output)
        file.createNewFile()

        val writer = file.printWriter()
        writer.println("file;lineno;name;type;element")
        for (entry in types.entries) {
            for (elementInfo in entry.value.types) {
                writer.println(""""${elementInfo.file}";"${elementInfo.line}";"${elementInfo.name}";"${elementInfo.type}";"${elementInfo.elementType}"""")
            }
        }
        writer.flush()
    }
}

class InferringElementVisitor(
    private val typedElements: TypedElements,
    private val filePath: String,
    private val toInfer: Boolean
) : PsiRecursiveElementVisitor() {

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)

        if (element is PyAnnotationOwner &&
            (element is PyFunction || element is PyNamedParameter) &&
            element.parent !is PyImportElement
        ) {
            val context = TypeEvalContext.userInitiated(element.project, element.containingFile)
            val line = StringUtil.offsetToLineNumber(element.containingFile.text, element.textOffset)
            val type = when (element) {
                is PyFunction -> try {
                    element.getReturnStatementType(context)?.name
                } catch (e: Exception) {
                    "Any"
                }
                else -> try {
                    PythonDocumentationProvider.getTypeName(context.getType(element as PyTypedElement), context)
                } catch (e: Exception) {
                    "Any"
                }
            }
            val annotation = element.annotationValue
            val elementType: ElementType = when (element) {
                is PyParameter -> ElementType.PARAMETER
                is PyFunction -> ElementType.FUNCTION
                is PyTargetExpression -> ElementType.VARIABLE
                else -> ElementType.NONE
            }
            typedElements.addType(
                (element as PyTypedElement).name.orEmpty(),
                (if (toInfer) type else annotation) ?: "Any", filePath,
                line, elementType
            )
        }
    }
}