package plugin.utils

import com.intellij.openapi.diagnostic.Logger

fun <T> timed(logger: Logger, task: String, block: () -> T): T {
    val time = System.currentTimeMillis()
    val result = block()
    logger.warn("$task took ${System.currentTimeMillis() - time} ms")
    return result
}