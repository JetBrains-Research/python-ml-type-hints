package extractor.configurers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.modules

class SdkConfigurer(private val project: Project, private val projectManager: ProjectRootManager) {
    private fun createSdk(sdkPath: String): Sdk {
        val sdk = PyDetectedSdk(sdkPath)
        println("Created SDK: $sdk")
        return sdk
    }

    private fun connectSdkWithProject(sdk: Sdk) {
        println("Connecting SDK with project files")
        val jdkTable = ProjectJdkTable.getInstance()
        runWriteAction {
            jdkTable.addJdk(sdk)
        }
        ApplicationManager.getApplication().runWriteAction {
            projectManager.projectSdk = sdk
        }
        project.pythonSdk = sdk
        project.modules.forEach { module ->
            module.pythonSdk = sdk
        }
    }

    fun setProjectSdk(sdkPath: String) {
        println("Setting up SDK for project $project")
        val sdk = createSdk(sdkPath)
        connectSdkWithProject(sdk)
        PythonSdkType.getInstance().setupSdkPaths(sdk)
    }
}
