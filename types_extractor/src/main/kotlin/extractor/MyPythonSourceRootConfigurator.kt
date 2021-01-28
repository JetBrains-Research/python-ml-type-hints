package extractor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectConfigurator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.*
import com.intellij.psi.util.QualifiedName

class MyPythonSourceRootConfigurator {
    fun configureProject(project: Project, baseDir: VirtualFile) {
        val projectManager = ProjectRootManager.getInstance(project)
        val visitor = ImportVisitor(baseDir)
        projectManager.contentRoots.forEach { root ->
            VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
                if (virtualFile.extension != "py" || virtualFile.canonicalPath == null) {
                    return@iterateChildrenRecursively true
                }
                val psi = PsiManager.getInstance(project)
                        .findFile(virtualFile) ?: return@iterateChildrenRecursively true
                visitor.curFile = virtualFile
                psi.accept(visitor)

                true
            }
        }


        visitor.sourceRoots.filterNotNull().forEach {
            //println(it.canonicalPath)
            addSourceRoot(project, baseDir, it, false)
        }
    }

    private class ImportVisitor(val root: VirtualFile) : PyRecursiveElementVisitor() {
        lateinit var curFile: VirtualFile
        val sourceRoots: MutableList<VirtualFile?> = mutableListOf()

        override fun visitPyImportStatement(node: PyImportStatement) {
            val import = node.importElements
            import.forEach {
                if (it.importedQName == null) {
                    return@forEach
                }
                val module = findImportee(root, curFile, it.importedQName!!)
                sourceRoots.add(module)
            }
        }

        override fun visitPyFromImportStatement(node: PyFromImportStatement) {
            val import = node.importSourceQName ?: return
            //println(import.toString())
            val module = findImportee(root, curFile, import)
            sourceRoots.add(module)
        }

        private fun findImportee(root: VirtualFile, file: VirtualFile, importName: QualifiedName): VirtualFile? {
            var newFile = file
            do  {
                val module = tryTraverse(newFile, importName)
                if (module != null && module.parent != root) {
                    return module.parent
                }
                newFile = newFile.parent
            } while (newFile != root)

            return null
        }

        private fun tryTraverse(file: VirtualFile, importName: QualifiedName): VirtualFile? {
            val moduleDir = file.findFileByRelativePath(importName.join("/"))
            val moduleFile = file.findFileByRelativePath(importName.join("/") + ".py")
            return moduleDir ?: moduleFile
        }
    }

    companion object {
        private fun addSourceRoot(project: Project, baseDir: VirtualFile, root: VirtualFile?, unique: Boolean) {
            val modules = ModuleManager.getInstance(project).modules
            if (modules.size > 0 && root != null) {
                ApplicationManager.getApplication().runWriteAction {
                    val model = ModuleRootManager.getInstance(modules[0]).modifiableModel
                    val contentEntries = model.contentEntries
                    for (contentEntry in contentEntries) {
                        if (Comparing.equal(contentEntry.file, baseDir)) {
                            val sourceFolders = contentEntry.sourceFolders
                            if (unique || sourceFolders.size == 0) {
                                contentEntry.addSourceFolder(root, false)
                            }
                        }
                    }
                    model.commit()
                }
            }
        }
    }
}