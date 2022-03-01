package extractor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.python.psi.*
import com.intellij.psi.util.QualifiedName

class MyPythonSourceRootConfigurator {
    public var totalPreResolved: Int = 0
    public var totalResolved: Int = 0
    public var totalUnresolved: Int = 0

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
        totalPreResolved += visitor.preResolved


        visitor.sourceRoots.filterNotNull().forEach {
            addSourceRoot(project, baseDir, it, false)
        }
    }

    fun countImports(project: Project) {
        val projectManager = ProjectRootManager.getInstance(project)
        val visitor = CountingVisitor()
        projectManager.contentRoots.forEach { root ->
            VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
                if (virtualFile.extension != "py" || virtualFile.canonicalPath == null) {
                    return@iterateChildrenRecursively true
                }
                val psi = PsiManager.getInstance(project)
                    .findFile(virtualFile) ?: return@iterateChildrenRecursively true
                psi.accept(visitor)

                true
            }
        }
        totalResolved += visitor.totalResolved
        totalUnresolved += visitor.totalUnresolved
    }

    private class CountingVisitor : PyRecursiveElementVisitor() {
        var totalResolved: Int = 0
        var totalUnresolved: Int = 0

        override fun visitPyImportStatement(node: PyImportStatement) {
            super.visitPyImportStatement(node)
            val import = node.importElements
            for (i in import.indices) {
                if (import[i].importedQName == null) {
                    continue
                }

                try {
                    val resolved = import[i].multiResolve()
                    if (resolved.isEmpty()) {
                        totalUnresolved++
                        println(import[i].importedQName)
                    } else {
                        totalResolved++
                    }
                } catch (e: NullPointerException) {
                }
            }
        }

        override fun visitPyFromImportStatement(node: PyFromImportStatement) {
            super.visitPyFromImportStatement(node)
            try {
                val resolved = node.resolveImportSourceCandidates()
                if (resolved.isNotEmpty()) {
                    totalResolved++
                } else {
                    totalUnresolved++
                    println(node.text)
                }
            } catch (e: NullPointerException) {
            }
        }
    }

    private class ImportVisitor(val root: VirtualFile) : PyRecursiveElementVisitor() {
        lateinit var curFile: VirtualFile
        val sourceRoots: MutableList<VirtualFile?> = mutableListOf()
        var preResolved: Int = 0

        override fun visitPyImportStatement(node: PyImportStatement) {
            val import = node.importElements
            val toResolve = MutableList(import.size) { true }

            for (i in import.indices) {
                if (import[i].importedQName == null) {
                    continue
                }

                try {
                    val resolved = import[i].multiResolve()
                    if (resolved.isNotEmpty()) {
                        preResolved++
                        toResolve[i] = false
                    }
                } catch (e: NullPointerException) {

                }
            }

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
            try {
                val resolved = node.resolveImportSource()
                if (resolved != null) {
                    preResolved++
                    return
                }
            } catch (e: NullPointerException) {
            }

            val module = findImportee(root, curFile, import) ?: findImporteeDfs(root, curFile, import)

            sourceRoots.add(module)
        }

        private fun findImportee(root: VirtualFile, file: VirtualFile, importName: QualifiedName): VirtualFile? {
            var newFile = file
            do {
                val module = tryTraverse(root, newFile, importName)
                if (module != null && module.parent != root) {
                    return module.parent
                }
                newFile = newFile.parent
            } while (newFile != root)

            return null
        }

        private fun findImporteeDfs(root: VirtualFile, file: VirtualFile, importName: QualifiedName): VirtualFile? {
            for (child in root.children) {
                val module = tryTraverse(root, child, importName)
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

        private fun tryTraverse(root: VirtualFile, file: VirtualFile, importName: QualifiedName): VirtualFile? {
            file.toNioPath().toList()
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