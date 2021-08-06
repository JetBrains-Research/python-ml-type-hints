package extractor.utils

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

fun createFilesInDir(dirPath: String, vararg files: String) {
    Files.createDirectory(Path.of(dirPath))
    files.forEach { file ->
        Files.createFile(Path.of(dirPath, file))
    }
}