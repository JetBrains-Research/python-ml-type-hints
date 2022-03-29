package extractor.utils

import com.intellij.util.io.exists
import java.nio.file.Files
import java.nio.file.Path

fun createFiles(vararg files: Path) {
    files.forEach { file ->
        Files.createDirectories(file.parent)
        if (!file.exists()) {
            Files.createFile(file)
        }
    }
}
