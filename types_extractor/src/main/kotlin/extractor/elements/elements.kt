package extractor.elements

class TypedElements {
    var types: MutableList<ElementInfo> = mutableListOf()

    fun addType(name: String, type: String, annotation: String, filePath: String, line: Int, elementType: ElementType) {
        types.add(ElementInfo(name, type, annotation, filePath, line, elementType))
    }

    override fun toString(): String {
        return types.toString()
    }
}

class ElementInfo(
    val name: String,
    val type: String,
    val annotation: String,
    val file: String,
    val line: Int,
    val elementType: ElementType
)

enum class ElementType(val type: Int) {
    FUNCTION(0),
    PARAMETER(1),
    VARIABLE(2),
    NONE(3),
}