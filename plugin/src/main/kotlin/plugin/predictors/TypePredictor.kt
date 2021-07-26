package plugin.predictors

import com.intellij.psi.PsiFile
import com.lordcodes.turtle.ShellLocation
import com.lordcodes.turtle.shellRun
import krangl.DataFrame
import krangl.eq
import krangl.readCSV
import java.nio.file.Paths

class TypePredictor private constructor() {

    companion object {
        private val DEFAULT_PATH =
            this.javaClass.getResource("/models")!!.path
        private const val TYPE4PY_PATH = "type4py"
        private const val DLTPY_PATH = "dltpy"
        private const val DEFAULT_PROJECT_DIRECTORY = "project"
        private const val TMP_AUTHOR = "tmp_author"
        private const val TMP_REPO = "tmp_repo"
        private const val DEFAULT_FILE_NAME = "tmp.py"
        private const val DEFAULT_OUTPUT_DIRECTORY = "preprocessed"
        private const val PREDICTIONS_FILE = "predictions_tmp.csv"

        fun predictType4Py(psiFile: PsiFile, parameters: List<String>): Iterator<String> {
            val text: String = psiFile.text
            val projectFile = Paths.get(
                DEFAULT_PATH,
                TYPE4PY_PATH,
                DEFAULT_PROJECT_DIRECTORY,
                TMP_AUTHOR,
                TMP_REPO,
                DEFAULT_FILE_NAME
            ).toFile()
            projectFile.createNewFile()
            projectFile.writeText(text)

            val outputDirectory = Paths.get(DEFAULT_PATH, TYPE4PY_PATH, DEFAULT_OUTPUT_DIRECTORY).toFile()
            outputDirectory.mkdir()

            val projectPath = Paths.get(DEFAULT_PATH, TYPE4PY_PATH, DEFAULT_PROJECT_DIRECTORY).toString()

            val modelPath = ShellLocation.HOME.resolve(Paths.get(DEFAULT_PATH, TYPE4PY_PATH).toFile().parentFile)
            println("Path is ${modelPath.path}================")

            val output = shellRun(modelPath) {
                command("python3", listOf("-m", "type4py", "extract", "--c", projectPath, "--o", outputDirectory.path))
                command("python3", listOf("-m", "type4py", "preprocess", "--o", outputDirectory.path))
                command("python3", listOf("-m", "type4py", "vectorize", "--o", outputDirectory.path))
                command("python3", listOf("-m", "type4py", "predict", "--o", outputDirectory.path, "--a"))
            }

            println(output)

            val predictions = DataFrame.readCSV(
                Paths.get(DEFAULT_PATH, TYPE4PY_PATH, DEFAULT_OUTPUT_DIRECTORY, PREDICTIONS_FILE).toFile()
            )
            val pred =
                parameters.map { parameter ->
                    predictions.filter { it["name"] eq parameter }["prediction"][0] as String
                }

            return pred.iterator()
        }

        fun predictDLTPy(psiFile: PsiFile, parameters: List<String>): Iterator<String> {
            val text: String = psiFile.text
            val projectFile = Paths.get(
                DEFAULT_PATH,
                DLTPY_PATH,
                DEFAULT_PROJECT_DIRECTORY,
                "${TMP_AUTHOR}__$TMP_REPO",
                DEFAULT_FILE_NAME
            ).toFile()
            projectFile.createNewFile()
            projectFile.writeText(text)

            val outputDirectory = Paths.get(DEFAULT_PATH, DLTPY_PATH, "output").toFile()
            outputDirectory.mkdir()

            val projectPath = Paths.get(DEFAULT_PATH, DLTPY_PATH, DEFAULT_PROJECT_DIRECTORY).toString()

            val modelPath = ShellLocation.HOME.resolve(Paths.get(DEFAULT_PATH, DLTPY_PATH).toFile())
            println("Path is ${modelPath.path}================")

            val output = shellRun(modelPath) {
                command("python3", listOf("-m", "preprocessing.pipeline"))
                command("python3", listOf("-m", "input-preparation.generate_df"))
                command("python3", listOf("-m", "input-preparation.df_to_vec"))
                command("python3", listOf("-m", "learning.learn"))
            }

            println(output)

            val predictions =
                DataFrame.readCSV(Paths.get(DEFAULT_PATH, DLTPY_PATH, "output", PREDICTIONS_FILE).toFile())
            val pred =
                parameters.map { parameter ->
                    predictions.filter { it["name"] eq parameter }["prediction"][0] as String
                }

            projectFile.delete()

            return pred.iterator()
        }

        /*fun predictKInference(psiFile: PsiFile, parameters: List<String>): Iterator<String> {
            return listOf("").iterator()
        }*/

        /*fun loadTypes() {
            val outputDirectory = Paths.get(DEFAULT_PATH, DEFAULT_OUTPUT_DIRECTORY).toFile()
            outputDirectory.mkdir()

            val modelPath = ShellLocation.HOME.resolve(Paths.get(DEFAULT_PATH).toFile().parentFile)
            val output = shellRun(modelPath) {
                command("python3", listOf("-m", "type4py", "load", "--o", outputDirectory.path))
            }

            println(output)
        }*/
    }
}
