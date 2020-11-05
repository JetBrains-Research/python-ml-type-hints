package extractor

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor
import com.jetbrains.python.statistics.modules
import java.io.File
import java.io.FileWriter

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

            projectManager.contentRoots.forEach { root ->
                VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
                    if (virtualFile.extension != "py" || virtualFile.canonicalPath == null) {
                        return@iterateChildrenRecursively true
                    }
                    val psi = PsiManager.getInstance(project)
                            .findFile(virtualFile) ?: return@iterateChildrenRecursively true
                    val typedElements = TypedElements()
                    println("file is ${project.name}/${virtualFile.name}")
                    psi.accept(TypesExtractorElementVisitor(typedElements, project.name, virtualFile.name))
                    types[projectPath] = typedElements
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
        for (entry in types.entries) {
            val path = entry.key
            writer.print(path + "\n\n")
            for (type in entry.value.types) {
                writer.print(type.key + ":" + type.value + "\n")
            }
            writer.print("\n")
        }
        writer.flush()
    }
}

class TypedElements {
    var types: MutableMap<String, String> = mutableMapOf()

    fun addType(name: String, type: String, projectName: String, fileName: String) {
        types["$projectName/$fileName/$name"] = type
    }

    override fun toString(): String {
        return types.toString()
    }
}

class TypesExtractorElementVisitor(private val typedElements: TypedElements,
                                   private val fileName: String,
                                   private val projectName: String) : PsiRecursiveElementVisitor() {

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)

        // TODO PyTypedElement is not only variables and parameters (docstring for example), need to fix it
        if (element is PyTypedElement && (element is PyTargetExpression || element is PyFunction)) {
            val context = TypeEvalContext.userInitiated(element.project, element.containingFile)
            typedElements.addType(
                    element.name.orEmpty(),
                    PythonDocumentationProvider.getTypeName(context.getType(element), context),
                    projectName,
                    fileName
            )
        }
    }
}
