package extractor.configurers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.psi.*
import com.intellij.psi.util.QualifiedName
import extractor.utils.traverseProject

class MyPythonSourceRootConfigurer {
    var totalPreResolved: Int = 0
    var totalResolved: Int = 0
    var totalUnresolved: Int = 0

    fun configureProject(project: Project, baseDir: VirtualFile) {
//        val projectManager = ProjectRootManager.getInstance(project)
        val visitor = ImportVisitor(baseDir)

        traverseProject(project) { psi, _ ->
            visitor.curFile = psi.virtualFile
            psi.accept(visitor)
        }

        /*projectManager.contentRoots.forEach { root ->
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
        }*/
        totalPreResolved += visitor.preResolved


        visitor.sourceRoots.filterNotNull().forEach {
            addSourceRoot(project, baseDir, it, false)
        }
    }

    fun countImports(project: Project) {
//        val projectManager = ProjectRootManager.getInstance(project)
        val visitor = CountingVisitor()
        traverseProject(project) { psi, _ ->
            psi.accept(visitor)
        }
        /*projectManager.contentRoots.forEach { root ->
            VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
                if (virtualFile.extension != "py" || virtualFile.canonicalPath == null) {
                    return@iterateChildrenRecursively true
                }
                val psi = PsiManager.getInstance(project)
                    .findFile(virtualFile) ?: return@iterateChildrenRecursively true
                psi.accept(visitor)

                true
            }
        }*/
        totalResolved += visitor.totalResolved
        totalUnresolved += visitor.totalUnresolved
    }

    private class CountingVisitor : PyRecursiveElementVisitor() {
        var totalResolved: Int = 0
        var totalUnresolved: Int = 0

        override fun visitPyImportStatement(node: PyImportStatement) {
            super.visitPyImportStatement(node)
            node.importElements.filterNot { it.importedQName == null }.forEach {
                val resolved = it.multiResolve()
                if (resolved.isEmpty()) {
                    totalUnresolved++
                } else {
                    totalResolved++
                }
            }
        }

        override fun visitPyFromImportStatement(node: PyFromImportStatement) {
            super.visitPyFromImportStatement(node)
            val resolved = node.resolveImportSourceCandidates()
            if (resolved.isNotEmpty()) {
                totalResolved++
            } else {
                totalUnresolved++
            }
        }
    }

    private class ImportVisitor(val root: VirtualFile) : PyRecursiveElementVisitor() {
        lateinit var curFile: VirtualFile
        val sourceRoots: MutableList<VirtualFile?> = mutableListOf()
        var preResolved: Int = 0

        override fun visitPyImportStatement(node: PyImportStatement) {
            node.importElements.filterNot { it.importedQName == null }.forEach {
                val resolved = it.multiResolve()
                if (resolved.isNotEmpty()) {
                    preResolved++
                }

                val module = findImportee(root, curFile, it.importedQName!!)
                sourceRoots.add(module)
            }
        }

        override fun visitPyFromImportStatement(node: PyFromImportStatement) {
            val import = node.importSourceQName ?: return
            val resolved = node.resolveImportSource()
            if (resolved != null) {
                preResolved++
                return
            }

            val module = findImportee(root, curFile, import) ?: findImporteeDfs(root, curFile, import)

            sourceRoots.add(module)
        }

        private fun findImportee(root: VirtualFile, file: VirtualFile, importName: QualifiedName): VirtualFile? {
            var newFile = file
            do {
                val module = tryTraverse(newFile, importName)
                if (module != null && module.parent != root) {
                    return module.parent
                }
                newFile = newFile.parent
            } while (newFile != root)

            return null
        }

        private fun findImporteeDfs(root: VirtualFile, file: VirtualFile, importName: QualifiedName): VirtualFile? {
            for (child in root.children) {
                val module = tryTraverse(child, importName)
                if (module != null && module.parent != root) {
                    return module.parent
                } else {
                    val newModule = findImporteeDfs(child, file, importName)
                    if (newModule != null) {
                        return newModule
                    }
                }
            }
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