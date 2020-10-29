package extractor

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.TypeEvalContext
import java.io.File

class FileTypesExtractor {
    fun extractTypesFromDataset(datasetPath: String): Map<String, TypedElements> {
        val datasetFile = File(datasetPath)
        val holdoutProjects = datasetFile.walk().maxDepth(1).toList()

        var types: Map<String, TypedElements> = HashMap()

        holdoutProjects.forEach { projectPath ->
            println("Extracting types from $projectPath)")
            val project = ProjectUtil.openOrImport(projectPath.path, null, true)
            if (project != null) {
                ProjectRootManager.getInstance(project).contentRoots.forEach { root ->
                    VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
                        if (virtualFile.extension != "py" || virtualFile.canonicalPath == null) {
                            return@iterateChildrenRecursively true
                        }
                        val psi = PsiManager.getInstance(project)
                                .findFile(virtualFile) ?: return@iterateChildrenRecursively true
                        val typedElements = TypedElements()
                        println("project is ${virtualFile.canonicalPath}")
                        psi.accept(TypesExtractorElementVisitor(typedElements))
                        types = types + Pair(projectPath.name, typedElements)
                        true
                    }
                }
            }
        }

        return types
    }
}

class TypedElements {
    private var types: Map<String, String> = HashMap()

    fun addType(name: String, type: String) {
        types = types + Pair(name, type)
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
