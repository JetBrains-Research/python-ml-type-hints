package extractor.utils

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor
import com.jetbrains.python.statistics.modules
import extractor.configurers.MyPythonSourceRootConfigurer
import extractor.configurers.SdkConfigurer
import java.io.File
import java.nio.file.Paths

fun forEachProjectInDir(dirPath: String, doAction: (project: Project, projectDir: String) -> Unit) {
    File(dirPath).list().orEmpty().forEach {
        val project = ProjectUtil.openOrImport(Paths.get(dirPath, it), null, true) ?: return@forEach
        doAction(project, Paths.get(dirPath, it).toString())
    }
}

fun setupProject(project: Project, envName: String, projectPath: String):
        Pair<SdkConfigurer, MyPythonSourceRootConfigurer> {
    val projectManager = ProjectRootManager.getInstance(project)
    val mySdkPaths =
        CondaEnvSdkFlavor.getInstance().suggestHomePaths(project.modules[0], UserDataHolderBase())
            .filter { sdk -> sdk.contains(envName) }
    if (mySdkPaths.isEmpty()) {
        throw NoSuchElementException("no suitable SDK found")
    }
    val sdkConfigurer = SdkConfigurer(project, projectManager)
    sdkConfigurer.setProjectSdk(mySdkPaths[0])
    print(mySdkPaths[0])

    val sourceRootConfigurator = MyPythonSourceRootConfigurer()
    sourceRootConfigurator.configureProject(
        project,
        VirtualFileManager.getInstance().findFileByNioPath(Paths.get(projectPath))!!
    )

    return sdkConfigurer to sourceRootConfigurator
}

fun traverseProject(project: Project, processFile: (psi: PsiFile, filePath: String) -> Unit) {
    val projectManager = ProjectRootManager.getInstance(project)
    projectManager.contentRoots.forEach { root ->
        VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
            val psi = PsiManager.getInstance(project).findFile(file) ?: return@iterateChildrenRecursively true

            if (isPythonFile(file)) {
                processFile(psi, file.path)
            }
            return@iterateChildrenRecursively true
        }
    }
}

fun isPythonFile(virtualFile: VirtualFile): Boolean {
    return virtualFile.extension == "py" && virtualFile.canonicalPath != null
}
