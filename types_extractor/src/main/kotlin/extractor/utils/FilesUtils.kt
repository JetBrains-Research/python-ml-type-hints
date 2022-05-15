package extractor.utils

import com.intellij.util.io.exists
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun createFiles(vararg files: Path) {
    files.forEach { file ->
        Files.createDirectories(file.parent)
        if (!file.exists()) {
            Files.createFile(file)
        }
    }
}

fun getDatasetFrom(filePath: String, dataset: String) =
    Files.readAllLines(Paths.get(filePath)).map { it.split(",") }.filter { it.first() == dataset }.map { it[1] }.toSet()
