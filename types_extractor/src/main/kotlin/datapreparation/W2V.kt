package datapreparation

import java.util.*

object W2V {
    val codeModel: Map<String, FloatArray> = loadW2VModel("code.txt")

    val languageModel: Map<String, FloatArray> = loadW2VModel("lang.txt")

    private fun loadW2VModel(dir: String): Map<String, FloatArray> {
        val scanner = Scanner(this::class.java.getResourceAsStream("/w2v_models/$dir"))
        val numberOfWords = scanner.nextInt()
        val vectorLength = scanner.nextInt()
        return mutableMapOf<String, FloatArray>().apply {
            (1..numberOfWords).forEach { _ ->
                this[scanner.next()] = (1..vectorLength).map { scanner.nextFloat() }.toFloatArray()
            }
        }
    }
}
