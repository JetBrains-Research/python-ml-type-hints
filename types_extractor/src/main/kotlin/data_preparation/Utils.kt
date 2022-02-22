package data_preparation

import extractor.function.Function

fun createParameterDatapoints(function: Function): List<Datapoint> =
    function.argNames.zip(function.argDescriptions).zip(function.argTypes) { (a, b), c ->
        Triple(a, b, c)
    }.map {
        ParameterDatapoint(
            name = it.first,
            comment = it.second,
            type = TypeEmbedder.encode(it.third) ?: 1000
        )
    }

fun createReturnDatapoint(function: Function): Datapoint =
    ReturnDatapoint(
        function.name,
        function.fnDescription,
        function.returnDescr,
        function.returnExpr,
        function.argNames,
        type = TypeEmbedder.encode(function.returnType) ?: 1000
    )