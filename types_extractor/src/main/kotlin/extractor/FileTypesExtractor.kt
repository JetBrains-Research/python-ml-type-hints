package extractor

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor
import com.jetbrains.python.statistics.modules
import java.io.File

class FileTypesExtractor {
    fun extractTypesFromProject(projectPath: String): Map<String, TypedElements> {
        val types = mutableMapOf<String, TypedElements>()

        println("Extracting types from $projectPath")
        val project = ProjectUtil.openOrImport(projectPath, null, true)
        if (project != null) {
            val projectManager = ProjectRootManager.getInstance(project)
            // Use the very first suggested conda environment
            val mySdkPath = CondaEnvSdkFlavor.getInstance().suggestHomePaths(project.modules[0], UserDataHolderBase()).take(1)[0]
            val sdkConfigurer = SdkConfigurer(project, projectManager)
            sdkConfigurer.setProjectSdk(mySdkPath)

            // TODO walk each folder as separate project
            projectManager.contentRoots.forEach { root ->
                VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
                    if (virtualFile.extension != "py" || virtualFile.canonicalPath == null) {
                        return@iterateChildrenRecursively true
                    }
                    val psi = PsiManager.getInstance(project)
                            .findFile(virtualFile) ?: return@iterateChildrenRecursively true
                    val typedElements = TypedElements()
                    val filePath = virtualFile.path
                    println("file is ${filePath}")

                    psi.accept(TypesExtractorElementVisitor(typedElements, filePath))
                    types[filePath] = typedElements
                    true
                }
            }
        }

        return types
    }

    fun printTypes(types: Map<String, TypedElements>, output: String) {
        val file = File(output)
        file.createNewFile()

        val writer = file.printWriter()
        writer.println("file,lineno,name,type")
        for (entry in types.entries) {
            for (elementInfo in entry.value.types) {
                writer.println("\"" + elementInfo.file + "\";\"" + elementInfo.line + "\";\"" + elementInfo.name + "\";\"" + elementInfo.type + "\"")
            }
        }
        writer.flush()
    }
}

class TypedElements {
    var types: ArrayList<ElementInfo> = arrayListOf()

    fun addType(name: String, type: String, filePath: String, line: Int, elementType: ElementType) {
        types.add(ElementInfo(name, type, filePath, line, elementType))
    }

    override fun toString(): String {
        return types.toString()
    }
}

class ElementInfo(val name: String, val type: String, val file: String, val line: Int, val elementType: ElementType)

enum class ElementType(val type: Int) {
    FUNCTION(0),
    PARAMETER(1),
    VARIABLE(2),
    NONE(3),
}

class TypesExtractorElementVisitor(private val typedElements: TypedElements,
                                   private val filePath: String) : PsiRecursiveElementVisitor() {

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)

        if (element is PyAnnotationOwner &&
                //(element is PyTargetExpression ||
                (element is PyFunction || element is PyNamedParameter) &&
                element.parent !is PyImportElement) {
            val context = TypeEvalContext.userInitiated(element.project, element.containingFile)
            val line = StringUtil.offsetToLineNumber(element.containingFile.text, element.textOffset)
            val type = when (element) {
                is PyFunction -> element.getReturnStatementType(context)?.name
                else -> PythonDocumentationProvider.getTypeName(context.getType(element as PyTypedElement), context)
            }
            val annotation = element.annotationValue
            val elementType: ElementType = when (element) {
                is PyParameter -> ElementType.PARAMETER
                is PyFunction -> ElementType.FUNCTION
                is PyTargetExpression -> ElementType.VARIABLE
                else -> ElementType.NONE
            }
            // uncomment if evaluating types
            /*typedElements.addType(
                    (element as PyTypedElement).name.orEmpty(),
                    type?:"Any", filePath,
                    line, elementType)*/

            // uncomment if retrieving annotations
            typedElements.addType(
                    (element as PyTypedElement).name.orEmpty(),
                    annotation?:"Any", filePath,
                    line, elementType)


            // TODO extract
            /*val annotation = (element as PyAnnotationOwner).annotationValue
            if (annotation != null) {
                when (element) {
                    is PyTargetExpression -> {

                        val assignmentStatement = element.parent as PyAssignmentStatement

                        val newAssignmentStatement = PyElementGenerator.getInstance(element.project)
                                .createFromText(LanguageLevel.PYTHON36,
                                        PyAssignmentStatement::class.java,
                                        element.text + " = " + assignmentStatement.assignedValue!!.text)
                        assignmentStatement.replace(newAssignmentStatement)
                    }
                    is PyFunction -> {

                    }
                    is PyNamedParameter -> {

                    }
                    else -> {}
                }
            }*/
        }
    }
}

