package com.example.demo

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Service
import java.nio.LongBuffer
import java.nio.file.Paths

@Service
class BertNerService {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val tokenizer: HuggingFaceTokenizer
    
    // Mapping based on your DistilBERT-NER config
    private val id2label = mapOf(
        0 to "O",
        1 to "B-PER", 2 to "I-PER",
        3 to "B-ORG", 4 to "I-ORG",
        5 to "B-LOC", 6 to "I-LOC",
        7 to "B-MISC", 8 to "I-MISC"
    )

    init {
        val modelDir = "/Users/samuel/Downloads/bertNER"
        session = env.createSession("$modelDir/model.onnx")
        tokenizer = HuggingFaceTokenizer.newInstance(Paths.get("$modelDir/tokenizer.json"))
    }

    /**
     * Extracts named entities from text using BERT.
     * Returns a map where keys are entity types (PER, LOC, etc.) 
     * and values are lists of extracted strings.
     */
    fun extractEntities(text: String): Map<String, List<String>> {
        val encoding = tokenizer.encode(text)
        val inputIds = encoding.ids
        val attentionMask = encoding.attentionMask
        val tokens = encoding.tokens

        val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), longArrayOf(1, inputIds.size.toLong()))
        val attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), longArrayOf(1, attentionMask.size.toLong()))

        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor
        )

        return session.run(inputs).use { result ->
            @Suppress("UNCHECKED_CAST")
            val logits = result.get(0).value as Array<Array<FloatArray>>
            val tokenLogits = logits[0] // [seq_len, num_labels]

            val entities = mutableMapOf<String, MutableList<String>>()
            var currentEntity = StringBuilder()
            var currentType = ""

            for (i in tokenLogits.indices) {
                val labelId = argMax(tokenLogits[i])
                val label = id2label[labelId] ?: "O"
                val token = tokens[i]

                if (label.startsWith("B-")) {
                    if (currentEntity.isNotEmpty()) {
                        entities.getOrPut(currentType) { mutableListOf() }.add(cleanToken(currentEntity.toString()))
                    }
                    currentEntity = StringBuilder(token)
                    currentType = label.substring(2)
                } else if (label.startsWith("I-") && currentType == label.substring(2)) {
                    // Handle WordPiece tokens (##) or simple spaces
                    if (token.startsWith("##")) {
                        currentEntity.append(token.substring(2))
                    } else {
                        currentEntity.append(" ").append(token)
                    }
                } else {
                    if (currentEntity.isNotEmpty()) {
                        entities.getOrPut(currentType) { mutableListOf() }.add(cleanToken(currentEntity.toString()))
                        currentEntity = StringBuilder()
                        currentType = ""
                    }
                }
            }
            
            if (currentEntity.isNotEmpty()) {
                entities.getOrPut(currentType) { mutableListOf() }.add(cleanToken(currentEntity.toString()))
            }

            entities
        }
    }

    private fun argMax(array: FloatArray): Int {
        var maxIdx = 0
        for (i in 1 until array.size) {
            if (array[i] > array[maxIdx]) maxIdx = i
        }
        return maxIdx
    }

    private fun cleanToken(token: String): String {
        return token.replace(" ##", "").replace("##", "").replace("[CLS]", "").replace("[SEP]", "").trim()
    }

    @PreDestroy
    fun cleanup() {
        session.close()
        tokenizer.close()
        env.close()
    }
}
