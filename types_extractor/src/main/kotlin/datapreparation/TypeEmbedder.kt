package datapreparation

import com.intellij.util.castSafelyTo
import javax.json.Json
import javax.json.JsonString

object TypeEmbedder {
    private fun loadTypesFrom(path: String) =
        Json.createReader(this::class.java.getResourceAsStream("/$path")).readArray().map {
            it.castSafelyTo<JsonString>().toString()
        }

    private val indToType = loadTypesFrom("typeEmbeddings.json")
        .map { it.substring(1 until it.length - 1) }
    private val typeToInd = indToType.mapIndexed { index, type -> type to index }.toMap()

    fun decode(type: Int) = indToType[type]

    fun encode(type: String) = typeToInd[type]
}
