package plugin.predictors

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.psi.PyFunction
import khttp.post
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import plugin.exceptions.PredictionException

class Type4PyPredictor(local: Boolean = true): TypePredictor {
    private val server: String = "${if (local) "http://localhost:5001" else "https://type4py.com"}/api/predict?tc=0"
    private val logger = thisLogger()

    override fun predictParameters(function: PyFunction, topN: Int): Map<String, List<Pair<String, Double>>> {
        val func = getFunctionJson(function)
        logger.warn(func.toString())
        return func.getJSONObject("params_p").let { params ->
            params.keys().asSequence().associateWith {
                params.getJSONArray(it)
                    .map { (it as JSONArray) }
                    .map {result -> (result[0] as String) to (result[1] as Double) }
                    .take(topN)
            }
        }
    }

    override fun predictReturnType(function: PyFunction, topN: Int): List<Pair<String, Double>> {
        val func = getFunctionJson(function)
        return if (func.has("ret_type_p"))
            func.getJSONArray("ret_type_p")
                .map { (it as JSONArray) }
                .map { (it[0] as String) to (it[1] as Double) }
                .take(topN)
        else listOf()
    }

    private fun typesJson(function: PyFunction): JSONObject {
        val file = function.containingFile.text
//        thisLogger().warn(file)

        val request = post(server, data = file)
        return request.jsonObject.getJSONObject("response")
    }


    private fun getFunctionJson(function: PyFunction): JSONObject {
        try {
            val types = typesJson(function)
            return (types.getJSONArray("classes")
                .flatMap {
                    (it as JSONObject).getJSONArray("funcs").map { it as JSONObject }
                } +
                    types.getJSONArray("funcs").map { it as JSONObject }).first {
                it.get("name") == function.name &&
                        StringUtil.offsetToLineNumber(function.containingFile.text, function.textOffset) + 1 ==
                        it.getJSONArray("fn_lc").getJSONArray(0).get(0)
            }
        } catch(e: JSONException) {
            throw PredictionException(e)
        }
    }
}