package extractor

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
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
import java.nio.file.Paths

class FileTypesExtractor {
    fun extractTypesFromProject(projectPath: String, toInfer: Boolean): Map<String, TypedElements> {
        val types = mutableMapOf<String, TypedElements>()

        println("Extracting projects from $projectPath")
        File(projectPath).list().orEmpty().forEach {
            val project = ProjectUtil.openOrImport(Paths.get(projectPath, it), null, true)
            println("Extracting types from $it")
            if (project != null) {
                val projectManager = ProjectRootManager.getInstance(project)
                // Use the very first suggested conda environment
                val mySdkPath = CondaEnvSdkFlavor.getInstance().suggestHomePaths(project.modules[0], UserDataHolderBase())
                        .filter { sdk -> sdk.contains("diploma") }
                        .take(1)[0]
                val sdkConfigurer = SdkConfigurer(project, projectManager)
//                val sourceRootConfigurator = MyPythonSourceRootConfigurator()
//                sourceRootConfigurator.configureProject(project, VirtualFileManager.getInstance().findFileByNioPath(Paths.get(projectPath, it))!!)
                sdkConfigurer.setProjectSdk(mySdkPath)
                print(mySdkPath)

                // TODO walk each folder as separate project
                projectManager.contentRoots.take(10).forEach { root ->
                    VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
                        if (virtualFile.extension != "py" || virtualFile.canonicalPath == null) {
                            return@iterateChildrenRecursively true
                        }
                        val psi = PsiManager.getInstance(project)
                                .findFile(virtualFile) ?: return@iterateChildrenRecursively true
                        val typedElements = TypedElements()
                        val filePath = virtualFile.path
                        println("file is ${filePath}")

                        psi.accept(TypesExtractorElementVisitor(typedElements, filePath, toInfer))
                        types[filePath] = typedElements
                        true
                    }
                }
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
                                   private val filePath: String,
                                   private val toInfer: Boolean) : PsiRecursiveElementVisitor() {

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)

        if (element is PyAnnotationOwner &&
                //(element is PyTargetExpression ||
                (element is PyFunction || element is PyNamedParameter) &&
                element.parent !is PyImportElement) {
            val context = TypeEvalContext.userInitiated(element.project, element.containingFile)
            val line = StringUtil.offsetToLineNumber(element.containingFile.text, element.textOffset)
            val type = when (element) {
                is PyFunction -> try {
                    element.getReturnStatementType(context)?.name
                } catch(e: Exception) {
                    "None"
                }
                else -> {
                    try {
                        PythonDocumentationProvider.getTypeName(context.getType(element as PyTypedElement), context)
                    } catch (e: Exception) {
                        "None"
                    }
                }
            }
            val annotation = element.annotationValue
            val elementType: ElementType = when (element) {
                is PyParameter -> ElementType.PARAMETER
                is PyFunction -> ElementType.FUNCTION
                is PyTargetExpression -> ElementType.VARIABLE
                else -> ElementType.NONE
            }
            if (toInfer) {
                typedElements.addType(
                        (element as PyTypedElement).name.orEmpty(),
                        type ?: "Any", filePath,
                        line, elementType)
            } else {
                typedElements.addType(
                    (element as PyTypedElement).name.orEmpty(),
                    annotation?:"Any", filePath,
                    line, elementType)
            }

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

