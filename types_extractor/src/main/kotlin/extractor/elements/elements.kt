package extractor.elements

class TypedElements {
    var types: MutableList<ElementInfo> = arrayListOf()

    fun addType(name: String, type: String, filePath: String, line: Int, elementType: ElementType) {
        types.add(ElementInfo(name, type, filePath, line, elementType))
    }

    override fun toString(): String {
        return types.toString()
    }
}

class ElementInfo(val name: String, val type: String, val file: String, val line: Int, val elementType: ElementType)

enum class ElementType(val type: Int) {
    FUNCTION(0),
    PARAMETER(1),
    VARIABLE(2),
    NONE(3),
}