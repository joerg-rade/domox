package domox

import edu.stanford.nlp.pipeline.CoreDocument
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import java.util.*

class StanfordNLP {
    var pipeline:StanfordCoreNLP
    init {
        try {
            val props = Properties()
            //            props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref,kbp,quote");
            props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref")
            props.setProperty("coref.algorithm", "neural")
            pipeline = StanfordCoreNLP(props)
        } catch (e: Exception) {
            throw RuntimeException("Exception occurred in creating NLP pipeline")
        }
    }

    fun annotate(rawText: String?): CoreDocument {
        val document = CoreDocument(rawText)
        pipeline.annotate(document)
        return document
    }

}