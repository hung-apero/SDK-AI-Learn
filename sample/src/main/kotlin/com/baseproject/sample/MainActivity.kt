package com.baseproject.sample

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.apero.tutor.sdk.TutorCharacter
import com.apero.tutor.sdk.TutorCharacterConfig
import com.apero.tutor.sdk.subtitle.KaraokeSubtitleRenderer
import com.apero.tutor.sdk.tts.AndroidTtsProvider
import com.apero.tutor.sdk.tts.AzureTtsProvider
import com.apero.tutor.sdk.tts.ElevenLabsTtsProvider
import com.apero.tutor.sdk.tts.TtsEvent
import com.apero.tutor.sdk.tts.TtsProvider
import com.baseproject.sample.stt.SttProviderFactory
import com.baseproject.sample.stt.SttProviderKind
import com.baseproject.sample.stt.toDisplayText
import com.baseproject.stt.SpeechToTextProvider
import com.baseproject.stt.SttEvent
import com.baseproject.stt.SttResult
import com.baseproject.stt.android.AndroidSttResult
import com.baseproject.stt.gemini.GeminiSttResult
import com.esotericsoftware.spine.android.SpineView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

/**
 * Demo activity that proves the SDK works end-to-end.
 *
 * Provides a 3-button picker (Android / Azure / ElevenLabs) so the provider
 * can be hot-swapped at runtime via [TutorCharacter.setTtsProvider].
 *
 * Model files are copied from /assets to filesDir on first launch to mimic
 * the "downloaded from server" workflow expected in production.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var sdk: TutorCharacter
    private lateinit var status: TextView
    private lateinit var btnAndroid: Button
    private lateinit var btnAzure: Button
    private lateinit var btnEleven: Button
    private lateinit var btnRateSlow: Button
    private lateinit var btnRateNormal: Button
    private lateinit var btnRateFast: Button

    private var providerLabel: String = "?"
    private var currentKind: ProviderKind = ProviderKind.ANDROID
    private var currentRate: Float = 1.0f
    private var eventsSub: Job? = null

    // STT
    private lateinit var sttOutput: TextView
    private lateinit var btnSttAndroid: Button
    private lateinit var btnSttGemini: Button
    private lateinit var btnSttRecord: Button
    private var sttProvider: SpeechToTextProvider? = null
    private var sttKind: SttProviderKind = SttProviderKind.ANDROID
    private var sttSub: Job? = null
    private var isRecording: Boolean = false

    private val recordPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startStt() else Toast.makeText(this, "Mic permission required", Toast.LENGTH_SHORT).show()
    }

    enum class ProviderKind { ANDROID, AZURE, ELEVENLABS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val spineView    = findViewById<SpineView>(R.id.spineView)
        val subtitleText = findViewById<TextView>(R.id.subtitleText)
        status           = findViewById(R.id.status)
        btnAndroid       = findViewById(R.id.btn_provider_android)
        btnAzure         = findViewById(R.id.btn_provider_azure)
        btnEleven        = findViewById(R.id.btn_provider_elevenlabs)
        btnRateSlow      = findViewById(R.id.btn_rate_slow)
        btnRateNormal    = findViewById(R.id.btn_rate_normal)
        btnRateFast      = findViewById(R.id.btn_rate_fast)

        // 1. Materialise model files into filesDir (mimics a downloader).
        val modelDir = File(filesDir, "models/aria").apply { mkdirs() }
        val atlas = copyAssetIfMissing("spine/tutor_character.atlas", File(modelDir, "tutor_character.atlas"))
        val json  = copyAssetIfMissing("spine/tutor_character.json",  File(modelDir, "tutor_character.json"))
        val png   = copyAssetIfMissing("spine/tutor_character.png",   File(modelDir, "tutor_character.png"))

        // 2. Resolve initial provider from BuildConfig.TTS_PROVIDER.
        val initialKind = resolveInitialKind()
        currentKind = initialKind
        val (initialProvider, initialLabel) = buildProvider(initialKind)
        providerLabel = initialLabel

        // 3. Construct SDK + subscribe to its events.
        sdk = TutorCharacter(
            spineView          = spineView,
            config             = TutorCharacterConfig(atlas, json, png),
            initialTtsProvider = initialProvider,
            subtitleRenderer   = KaraokeSubtitleRenderer(subtitleText),
            scope              = lifecycleScope
        )
        subscribeToEvents(initialProvider)
        updatePickerVisuals()
        updateRateVisuals()

        // 4. Load + wire buttons.
        lifecycleScope.launch {
            status.text = "Loading… [$providerLabel]"
            try {
                sdk.load(this@MainActivity)
                status.text = "Ready · $providerLabel"
            } catch (t: Throwable) {
                status.text = "Load failed: ${t.message}"
            }
        }
        wireButtons()
        setupStt()
    }

    private fun setupStt() {
        sttOutput     = findViewById(R.id.stt_output)
        btnSttAndroid = findViewById(R.id.btn_stt_android)
        btnSttGemini  = findViewById(R.id.btn_stt_gemini)
        btnSttRecord  = findViewById(R.id.btn_stt_record)

        sttKind = SttProviderFactory.defaultKind()
        switchSttProvider(sttKind)

        btnSttAndroid.setOnClickListener { switchSttProvider(SttProviderKind.ANDROID) }
        btnSttGemini.setOnClickListener {
            if (BuildConfig.GEMINI_API_KEY.isBlank()) {
                Toast.makeText(this, "GEMINI_API_KEY not set in local.properties", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            switchSttProvider(SttProviderKind.GEMINI)
        }
        btnSttRecord.setOnClickListener { toggleRecording() }
    }

    private fun switchSttProvider(kind: SttProviderKind) {
        sttSub?.cancel(); sttSub = null
        sttProvider?.destroy()
        sttKind = kind
        val p = SttProviderFactory.create(this, kind)
        sttProvider = p
        lifecycleScope.launch {
            try { p.prepare(this@MainActivity) } catch (t: Throwable) {
                sttOutput.text = "Prepare failed: ${t.message}"
            }
        }
        sttSub = lifecycleScope.launch {
            p.events.collect { ev ->
                when (ev) {
                    is SttEvent.ReadyForSpeech    -> sttOutput.text = "Listening…"
                    is SttEvent.BeginningOfSpeech -> Unit
                    is SttEvent.EndOfSpeech       -> {
                        isRecording = false
                        btnSttRecord.text = "Hold to Record"
                        if (kind == SttProviderKind.GEMINI) sttOutput.text = "Analyzing…"
                    }
                    is SttEvent.PartialResult -> sttOutput.text = "… ${ev.result.text}"
                    is SttEvent.Result        -> sttOutput.text = renderResult(ev.result)
                    is SttEvent.Error         -> {
                        isRecording = false
                        btnSttRecord.text = "Hold to Record"
                        sttOutput.text = "ERROR ${ev.code}: ${ev.message}"
                    }
                }
            }
        }
        updateSttPickerVisuals()
        sttOutput.text = "Ready · ${kind.name.lowercase()}"
    }

    private fun renderResult(r: SttResult): String = when (r) {
        is GeminiSttResult  -> r.toDisplayText()
        is AndroidSttResult -> r.toDisplayText()
        else                -> "Transcript: ${r.text}"
    }

    private fun toggleRecording() {
        if (isRecording) {
            sttProvider?.stopListening()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startStt()
        } else {
            recordPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startStt() {
        val p = sttProvider ?: return
        p.startListening()
        isRecording = true
        btnSttRecord.text = "Stop"
    }

    private fun updateSttPickerVisuals() {
        val onColor  = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
        val offColor = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
        for ((btn, kind) in listOf(
            btnSttAndroid to SttProviderKind.ANDROID,
            btnSttGemini  to SttProviderKind.GEMINI,
        )) {
            val selected = kind == sttKind
            btn.backgroundTintList = if (selected) onColor else offColor
            btn.setTextColor(if (selected) Color.WHITE else Color.parseColor("#333333"))
        }
    }

    private fun wireButtons() {
        val editTts = findViewById<EditText>(R.id.edit_tts_text)
        findViewById<Button>(R.id.btn_speak).setOnClickListener {
            val text = editTts.text.toString().trim()
            if (text.isNotEmpty()) lifecycleScope.launch { sdk.speak(text) }
        }
        findViewById<Button>(R.id.btn_demo_sse).setOnClickListener { simulateSseStream() }
        findViewById<Button>(R.id.btn_happy_on).setOnClickListener  { sdk.setEmotion("happy") }
        findViewById<Button>(R.id.btn_happy_off).setOnClickListener { sdk.setEmotion(null) }
        findViewById<Button>(R.id.btn_blink).setOnClickListener     { sdk.triggerBlink() }
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            sdk.stop()
            status.text = "Stopped · $providerLabel"
        }

        // Provider picker
        btnAndroid.setOnClickListener { switchTo(ProviderKind.ANDROID) }
        btnAzure  .setOnClickListener { switchTo(ProviderKind.AZURE) }
        btnEleven .setOnClickListener { switchTo(ProviderKind.ELEVENLABS) }

        // Speech rate picker
        btnRateSlow  .setOnClickListener { setRate(0.75f) }
        btnRateNormal.setOnClickListener { setRate(1.0f) }
        btnRateFast  .setOnClickListener { setRate(1.5f) }
    }

    private fun setRate(rate: Float) {
        currentRate = rate
        sdk.speechRate = rate
        updateRateVisuals()
    }

    private fun updateRateVisuals() {
        val onColor  = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
        val offColor = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
        for ((btn, value) in listOf(
            btnRateSlow   to 0.75f,
            btnRateNormal to 1.0f,
            btnRateFast   to 1.5f
        )) {
            val selected = value == currentRate
            btn.backgroundTintList = if (selected) onColor else offColor
            btn.setTextColor(if (selected) Color.WHITE else Color.parseColor("#333333"))
        }
    }

    /** Tear down current TTS, swap to new provider, re-subscribe to events. */
    private fun switchTo(kind: ProviderKind) {
        if (kind == currentKind) return
        val (provider, label) = buildProvider(kind)
        currentKind   = kind
        providerLabel = label
        updatePickerVisuals()
        status.text = "Switching to $label…"

        lifecycleScope.launch {
            try {
                sdk.setTtsProvider(this@MainActivity, provider)
                subscribeToEvents(provider)
                status.text = "Ready · $label"
            } catch (t: Throwable) {
                status.text = "Switch failed: ${t.message}"
            }
        }
    }

    private fun subscribeToEvents(provider: TtsProvider) {
        eventsSub?.cancel()
        eventsSub = lifecycleScope.launch {
            provider.events.collect { ev ->
                when (ev) {
                    is TtsEvent.Started -> status.text = "Speaking · $providerLabel"
                    is TtsEvent.Done    -> status.text = "Ready · $providerLabel"
                    is TtsEvent.Error   -> status.text = "ERROR: ${ev.message}"
                    else -> Unit
                }
            }
        }
    }

    private fun updatePickerVisuals() {
        val onColor  = ColorStateList.valueOf(Color.parseColor("#4CAF50"))   // green
        val offColor = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))   // gray
        for ((btn, kind) in listOf(
            btnAndroid to ProviderKind.ANDROID,
            btnAzure   to ProviderKind.AZURE,
            btnEleven  to ProviderKind.ELEVENLABS
        )) {
            val selected = kind == currentKind
            btn.backgroundTintList = if (selected) onColor else offColor
            btn.setTextColor(if (selected) Color.WHITE else Color.parseColor("#333333"))
        }
    }

    private fun buildProvider(kind: ProviderKind): Pair<TtsProvider, String> = when (kind) {
        ProviderKind.ANDROID -> {
            val voice = BuildConfig.ANDROID_TTS_VOICE.takeIf { it.isNotBlank() }
            val label = if (voice != null) "Android TTS ($voice)" else "Android TTS"
            AndroidTtsProvider(initialVoiceName = voice) to label
        }
        ProviderKind.AZURE -> AzureTtsProvider(
            speechKey = BuildConfig.AZURE_SPEECH_KEY,
            region    = BuildConfig.AZURE_SPEECH_REGION,
            voiceName = BuildConfig.AZURE_SPEECH_VOICE
        ) to "Azure (${BuildConfig.AZURE_SPEECH_VOICE})"
        ProviderKind.ELEVENLABS -> ElevenLabsTtsProvider(
            apiKey  = BuildConfig.ELEVENLABS_API_KEY,
            voiceId = BuildConfig.ELEVENLABS_VOICE_ID,
            modelId = BuildConfig.ELEVENLABS_MODEL_ID
        ) to "ElevenLabs (${BuildConfig.ELEVENLABS_VOICE_ID})"
    }

    /**
     * Resolve initial provider kind from `TTS_PROVIDER` build config:
     *   "elevenlabs" | "azure" | "android" → forced choice
     *   "auto" (default)                   → ElevenLabs > Azure > Android,
     *                                        based on which keys are filled
     */
    private fun resolveInitialKind(): ProviderKind {
        val pick = BuildConfig.TTS_PROVIDER.lowercase()
        val elevenAvail = BuildConfig.ELEVENLABS_API_KEY.isNotBlank()
        val azureAvail  = BuildConfig.AZURE_SPEECH_KEY.isNotBlank() &&
                          BuildConfig.AZURE_SPEECH_REGION.isNotBlank()
        return when (pick) {
            "elevenlabs", "eleven" -> ProviderKind.ELEVENLABS
            "azure"                -> ProviderKind.AZURE
            "android", "system"    -> ProviderKind.ANDROID
            else -> when {
                elevenAvail -> ProviderKind.ELEVENLABS
                azureAvail  -> ProviderKind.AZURE
                else        -> ProviderKind.ANDROID
            }
        }
    }

    /** Emit a passage as if streamed token-by-token from an LLM endpoint. */
    private fun simulateSseStream() {
        val passage = """
            Hello! Welcome to your English lesson today.
            My name is Aria, and I will be your tutor.
            Let us start with pronunciation practice.
            I hope you are ready to learn!
        """.trimIndent().replace("\n", " ").replace(Regex(" +"), " ").trim()

        val tokens = passage.split(Regex("(?<=\\s)|(?=\\s)")).filter { it.isNotEmpty() }
        val chunkFlow = flow {
            for (t in tokens) {
                emit(t)
                delay(55L + Random.nextLong(0, 30))
            }
        }
        lifecycleScope.launch { sdk.speakFromFlow(chunkFlow) }
    }

    override fun onDestroy() {
        sttSub?.cancel(); sttSub = null
        sttProvider?.destroy(); sttProvider = null
        sdk.release()
        super.onDestroy()
    }

    private fun copyAssetIfMissing(assetPath: String, destFile: File): File {
        if (destFile.exists() && destFile.length() > 0) return destFile
        assets.open(assetPath).use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        return destFile
    }
}
