package extractor.workers

import com.intellij.history.core.Paths
import com.intellij.openapi.diagnostic.thisLogger
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

class ProjectTypeInferrer(val output: String, val neededFiles: Set<String>?) {
    private val logger = thisLogger()

    fun inferTypes(dirPath: String, envName: String) {

        forEachProjectInDir(dirPath) { project, projectDir ->
            val types = mutableListOf<ElementInfo>()
            setupProject(project, envName, projectDir)
            logger.warn(projectDir)
            traverseProject(project) { psi, filePath ->
                val filePathInProject = Paths.appended(project.name, filePath)
                if ((neededFiles != null) && filePathInProject !in neededFiles) {
                    return@traverseProject
                }
                logger.warn("Processing file $filePathInProject")

                val typedElements = mutableListOf<ElementInfo>()
                try {
                    psi.accept(InferringElementVisitor(typedElements, filePathInProject))
                    types.addAll(typedElements)
                } catch (e: Exception) {
                    logger.error("Error during inferring for file $filePathInProject")
                }
            }
            printTypes(types, project.name)
        }
    }

    private fun printTypes(types: List<ElementInfo>, project: String) {
        val typePath = Path.of(output, "inferred_types", "$project-types.csv")
        createFiles(typePath)

        val df = types.toDataFrameByProperties()
        df.writeCSV(typePath.toFile())
    }
}

class InferringElementVisitor(
    private val typedElements: MutableList<ElementInfo>,
    private val filePath: String
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
//                is PyTargetExpression -> ElementType.VARIABLE
                else -> ElementType.NONE
            }

            typedElements.add(
                ElementInfo((element as PyTypedElement).name.orEmpty(), type, annotation, filePath, line + 1, elementType)
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
