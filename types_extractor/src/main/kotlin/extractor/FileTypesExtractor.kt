package extractor

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyTypedElement
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

            projectManager.contentRoots.forEach { root ->
                VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
                    if (virtualFile.extension != "py" || virtualFile.canonicalPath == null) {
                        return@iterateChildrenRecursively true
                    }
                    val psi = PsiManager.getInstance(project)
                            .findFile(virtualFile) ?: return@iterateChildrenRecursively true
                    val typedElements = TypedElements()
                    println("project is ${virtualFile.canonicalPath}")
                    psi.accept(TypesExtractorElementVisitor(typedElements))
                    types[projectPath] = typedElements
                    true
                }
            }
        }

        return types
    }
}

class TypedElements {
    private var types: MutableMap<String, String> = mutableMapOf()

    fun addType(name: String, type: String) {
        types[name] = type
    }

    override fun toString(): String {
        return types.toString()
    }
}

class TypesExtractorElementVisitor(private val typedElements: TypedElements) : PsiRecursiveElementVisitor() {

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)

        // TODO PyTypedElement is not only variables and parameters (docstring for example), need to fix it
        if (element is PyTypedElement) {
            val context = TypeEvalContext.userInitiated(element.project, element.containingFile)
            typedElements.addType(element.name.orEmpty(),
                    PythonDocumentationProvider.getTypeName(context.getType(element), context))
        }
    }
}
