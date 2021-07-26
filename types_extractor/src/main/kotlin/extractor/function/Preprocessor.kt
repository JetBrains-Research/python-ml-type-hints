package extractor.function

import edu.stanford.nlp.pipeline.CoreDocument
import org.apache.commons.lang.StringUtils
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import edu.stanford.nlp.simple.Sentence
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import java.util.*


class Preprocessor {
    fun preprocess(function: Function): Function {
        return Function(
            name = processIdentifier(function.name),
            returnType = function.returnType,
            returnExpr = function.returnExpr.map {
                val str = it.replace("return", "")
                processIdentifier(str)
            },
            returnDescr = processSentence(function.returnDescr),
            lineNumber = function.lineNumber,
            fnDescription = processSentence(function.returnDescr),
            argTypes = function.argTypes,
            argOccurrences = function.argOccurrences.map(this::processSentence),
            argNames = function.argNames.map(this::processIdentifier),
            argDescriptions = function.argDescriptions.map(this::processSentence),
            docstring = processSentence(function.docstring),
            usages = function.usages
        )
    }

    fun processSentence(sentence: String?): String? {
        val reducer = listOf(
            ::replaceDigitsWithSpace,
            ::removePunctuationAndLinebreaks,
            ::tokenize,
            ::lemmatize,
            ::removeStopWords
        )

        return reducer.fold(sentence, { acc, f -> acc?.let { f(it) } })
    }

    fun processIdentifier(sentence: String): String {
        val reducer = listOf(
            ::replaceDigitsWithSpace,
            ::removePunctuationAndLinebreaks,
            ::tokenize,
            ::lemmatize
        )

        return reducer.fold(sentence, { acc, f -> f(acc) })
    }

    companion object {
        //        private val pipeline: StanfordCoreNLP
        private val props = Properties()


        init {
//            props.setProperty("customAnnotatorClass.stopword", "intoxicant.analytics.coreNlp.StopwordAnnotator")
            props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref,kbp,quote,stopword")
//            pipeline = StanfordCoreNLP(props)
        }

        fun replaceDigitsWithSpace(sentence: String) =
            sentence.replace(Regex("[0-9]+"), " ")

        fun removePunctuationAndLinebreaks(sentence: String) =
            sentence
                .replace(Regex("[^A-Za-z0-9]+"), " ")
                .filterNot { it == '\n' || it == '\r' }

        fun tokenize(sentence: String) =
            convertCamelcase(sentence.replace('_', ' '))

        fun lemmatize(sentence: String): String {
//            val doc = CoreDocument(sentence)
//            pipeline.annotate(doc)
            if (sentence.filter { it.isLetterOrDigit() } == "")
                return ""
            try {
                val sent = Sentence(sentence)
                return sent.lemmas(props).joinToString(" ")
            } catch (e: Exception) {
                println("string is $sentence")
                throw e
            }
        }

        fun removeStopWords(sentence: String): String {
            val analyzer = StandardAnalyzer(javaClass.getResourceAsStream("/stopwords").reader())
            val tokens = analyzer.tokenStream("", sentence.reader())
            val termAttribute = tokens.addAttribute(CharTermAttribute::class.java)
            var res = ""
            tokens.reset()
            while (tokens.incrementToken()) {
                res += termAttribute.toString()
                res += " "
            }
            return res.trim()
        }

        /*fun getWordnetPos(tag: String): String {

        }*/

        fun convertCamelcase(sentence: String): String {
            return StringUtils.splitByCharacterTypeCamelCase(sentence).joinToString(" ")
        }
    }
}