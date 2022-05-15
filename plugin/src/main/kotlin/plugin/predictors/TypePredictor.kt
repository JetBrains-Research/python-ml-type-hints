package plugin.predictors

import com.jetbrains.python.psi.PyFunction

interface TypePredictor {
    fun predictParameters(function: PyFunction, topN: Int = 3): Map<String, List<Pair<String, Double>>>

    fun predictReturnType(function: PyFunction, topN: Int = 3): List<Pair<String, Double>>
}