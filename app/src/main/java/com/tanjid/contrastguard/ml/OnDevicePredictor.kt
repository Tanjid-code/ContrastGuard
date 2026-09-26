package com.tanjid.contrastguard.ml

import android.content.Context
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import kotlin.math.exp
import kotlin.math.max

class OnDevicePredictor(private val context: Context) {

    private var module: Module? = null
    private var isInitialized = false
    private var loadError: String? = null

    fun initialize(): Boolean {
        if (isInitialized) return true
        return try {
            val modelPath = assetFilePath(context, "contrastguard_model.ptl")
            module = LiteModuleLoader.load(modelPath)
            isInitialized = true
            loadError = null
            true
        } catch (e: Exception) {
            loadError = e.localizedMessage ?: e.message ?: "Unknown error"
            e.printStackTrace()
            false
        }
    }

    fun getError(): String? = loadError

    data class PredictionOutput(
        val isMalware: Boolean,
        val label: String,
        val confidence: Float,
        val benignProbability: Float,
        val malwareProbability: Float,
        val modelScore: Float,
        val ruleScore: Float,
        val hybridScore: Float,
        val verdictSource: String
    )

    fun predict(rawFeatures: FloatArray, ruleScore: Float = 0f, criticalPermCount: Int = 0): PredictionOutput {
        if (!isInitialized || module == null) {
            if (!initialize()) {
                return PredictionOutput(false, "ERROR", 0f, 0f, 0f, 0f, ruleScore, ruleScore, "Init Failed")
            }
        }

        return try {
            val scaledFeatures = Scaler.transform(rawFeatures)
            val inputTensor = Tensor.fromBlob(scaledFeatures, longArrayOf(1, 299))
            val outputTensor = module!!.forward(IValue.from(inputTensor)).toTensor()
            val logits = outputTensor.dataAsFloatArray

            val maxLogit = max(logits[0], logits[1])
            val exp0 = exp((logits[0] - maxLogit).toDouble()).toFloat()
            val exp1 = exp((logits[1] - maxLogit).toDouble()).toFloat()
            val sumExp = exp0 + exp1

            val probBenign = (exp0 / sumExp) * 100f
            val probMalware = (exp1 / sumExp) * 100f

            val modelScore = probMalware
            val rs = ruleScore.coerceIn(0f, 100f)

            // Advanced multi-layer decision engine
            val (isMalware, verdictSource) = decideVerdict(modelScore, rs, criticalPermCount)

            val hybridScore = (modelScore * 0.5f) + (rs * 0.5f)
            val confidence = if (isMalware) {
                hybridScore.coerceIn(60f, 99.5f)
            } else {
                (100f - hybridScore).coerceIn(60f, 99.5f)
            }

            PredictionOutput(
                isMalware = isMalware,
                label = if (isMalware) "MALWARE" else "SAFE",
                confidence = confidence,
                benignProbability = probBenign,
                malwareProbability = probMalware,
                modelScore = modelScore,
                ruleScore = rs,
                hybridScore = hybridScore,
                verdictSource = verdictSource
            )
        } catch (e: Exception) {
            PredictionOutput(false, "ERROR", 0f, 0f, 0f, 0f, ruleScore, ruleScore, "Engine Error")
        }
    }

    private fun decideVerdict(modelScore: Float, ruleScore: Float, criticalPerms: Int): Pair<Boolean, String> {
        // Tier 1: If critical dangerous permissions found AND model agrees
        if (criticalPerms >= 2 && modelScore > 55f) {
            return true to "Critical Permission + AI Consensus"
        }

        // Tier 2: Strong consensus — both model and rules flag it
        if (modelScore > 75f && ruleScore > 50f) {
            return true to "Strong Consensus (AI + Rules)"
        }

        // Tier 3: Rule override — extremely high rule score with critical perms
        if (ruleScore > 70f && criticalPerms >= 1) {
            return true to "Rule Override (Dangerous Patterns)"
        }

        // Tier 4: Model says malware but rules disagree — suppress false positive
        if (modelScore > 75f && ruleScore < 30f) {
            return false to "AI Flagged but Rules Cleared (Safe)"
        }

        // Tier 5: Model uncertain, rules uncertain — default safe
        if (modelScore < 70f && ruleScore < 45f) {
            return false to "Consensus Engine (Low Risk)"
        }

        // Tier 6: Moderate suspicion from both
        if (modelScore > 60f && ruleScore > 35f && criticalPerms >= 1) {
            return true to "Moderate Consensus (Review Recommended)"
        }

        return false to "Consensus Engine (Cleared)"
    }

    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) return file.absolutePath
        context.assets.open(assetName).use { input ->
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
        return file.absolutePath
    }
}