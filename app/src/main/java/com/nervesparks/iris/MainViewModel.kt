package com.nervesparks.iris

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.llama.cpp.LLamaAndroid
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.EditText
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.nervesparks.iris.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID


class MainViewModel(private val llamaAndroid: LLamaAndroid = LLamaAndroid.instance(), private val userPreferencesRepository: UserPreferencesRepository): ViewModel() {
    companion object {
//        @JvmStatic
//        private val NanosPerSecond = 1_000_000_000.0
    }


    private val _defaultModelName = mutableStateOf("")
    val defaultModelName: State<String> = _defaultModelName

    init {
        loadDefaultModelName()
    }
    private fun loadDefaultModelName(){
        _defaultModelName.value = userPreferencesRepository.getDefaultModelName()
    }

    fun setDefaultModelName(modelName: String){
        userPreferencesRepository.setDefaultModelName(modelName)
        _defaultModelName.value = modelName
    }

    lateinit var selectedModel: String
    private val tag: String? = this::class.simpleName

    var messages by mutableStateOf(

        listOf<Map<String, String>>(),
    )
        private set
    var newShowModal by mutableStateOf(false)
    var showDownloadInfoModal by mutableStateOf(false)
    var user_thread by mutableStateOf(0f)
    var topP by mutableStateOf(0f)
    var topK by mutableStateOf(0)
    var temp by mutableStateOf(0f)

    var allModels by mutableStateOf(
        listOf(
            mapOf(
                "name" to "Plm-1.8B-F16.gguf",
                "source" to "https://huggingface.co/PLM-Team/plm-instruct-dpo-gguf/resolve/main/Plm-1.8B-F16.gguf?download=true",
                "destination" to "Plm-1.8B-F16.gguf"
            ),
            mapOf(
                "name" to "plm-1.8B-instruct-dpo-Q4_K_M.gguf",
                "source" to "https://huggingface.co/PLM-Team/plm-instruct-dpo-gguf/resolve/main/plm-1.8B-instruct-dpo-Q4_K_M.gguf?download=true",
                "destination" to "plm-1.8B-instruct-dpo-Q4_K_M.gguf"
            ),
            mapOf(
                "name" to "plm-1.8B-instruct-dpo-Q8_0.gguf",
                "source" to "https://huggingface.co/PLM-Team/plm-instruct-dpo-gguf/resolve/main/plm-1.8B-instruct-dpo-Q8_0.gguf?download=true",
                "destination" to "plm-1.8B-instruct-dpo-Q8_0.gguf"
            ),
        )
    )

    private var first by mutableStateOf(
        true
    )
    var userSpecifiedThreads by mutableIntStateOf(2)
    var message by mutableStateOf("")
        private set

    var userGivenModel by mutableStateOf("")
    var SearchedName by mutableStateOf("")

    private var textToSpeech:TextToSpeech? = null

    var textForTextToSpeech = ""
    var stateForTextToSpeech by mutableStateOf(true)
        private set

    var eot_str = ""
    var refresh by mutableStateOf(false)

    // Sherpa-onnx SST
    lateinit var recognizer: OnlineRecognizer
    var audioRecord: AudioRecord? = null
    var recordingThread: Thread? = null

    val audioSource = MediaRecorder.AudioSource.MIC
    val sampleRateInHz = 16000
    val channelConfig = AudioFormat.CHANNEL_IN_MONO

    // Note: We don't use AudioFormat.ENCODING_PCM_FLOAT
    // since the AudioRecord.read(float[]) needs API level >= 23
    // but we are targeting API level >= 21
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    var idx: Int = 0
    var lastText: String = ""

    @Volatile
    var isRecording: Boolean = false

    // Sherpa-onnx TTS

    lateinit var tts: OfflineTts
    var sid by mutableStateOf(0)
    var speed by mutableStateOf(1.0f)
    var stopped: Boolean = false
    var mediaPlayer: MediaPlayer? = null
    lateinit var track: AudioTrack
    var generate by mutableStateOf(false)
    var play by mutableStateOf(false)
    var stop by mutableStateOf(false)

    fun loadExistingModels(directory: File) {
        // List models in the directory that end with .gguf
        directory.listFiles { file -> file.extension == "gguf" }?.forEach { file ->
            val modelName = file.name
            Log.i("This is the modelname", modelName)
            if (!allModels.any { it["name"] == modelName }) {
                allModels += mapOf(
                    "name" to modelName,
                    "source" to "local",
                    "destination" to file.name
                )
            }
        }

        if (defaultModelName.value.isNotEmpty()) {
            val loadedDefaultModel = allModels.find { model -> model["name"] == defaultModelName.value }

            if (loadedDefaultModel != null) {
                val destinationPath = File(directory, loadedDefaultModel["destination"].toString())
                if(loadedModelName.value == "") {
                    load(destinationPath.path, userThreads = user_thread.toInt())
                }
                currentDownloadable = Downloadable(
                    loadedDefaultModel["name"].toString(),
                    Uri.parse(loadedDefaultModel["source"].toString()),
                    destinationPath
                )
            } else {
                // Handle case where the model is not found
                allModels.find { model ->
                    val destinationPath = File(directory, model["destination"].toString())
                    destinationPath.exists()
                }?.let { model ->
                    val destinationPath = File(directory, model["destination"].toString())
                    if(loadedModelName.value == "") {
                        load(destinationPath.path, userThreads = user_thread.toInt())
                    }
                    currentDownloadable = Downloadable(
                        model["name"].toString(),
                        Uri.parse(model["source"].toString()),
                        destinationPath
                    )
                }
            }
        } else{
            allModels.find { model ->
                val destinationPath = File(directory, model["destination"].toString())
                destinationPath.exists()
            }?.let { model ->
                val destinationPath = File(directory, model["destination"].toString())
                if(loadedModelName.value == "") {
                    load(destinationPath.path, userThreads = user_thread.toInt())
                }
                currentDownloadable = Downloadable(
                    model["name"].toString(),
                    Uri.parse(model["source"].toString()),
                    destinationPath
                )
            }
            // Attempt to find and load the first model that exists in the combined logic

        }
    }

    // Sherpa-onnx SST

    fun initMicrophone(context: Context): Boolean {
        val numBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        Log.i(
            "Sherpa", "buffer size in milliseconds: ${numBytes * 1000.0f / sampleRateInHz}"
        )

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        audioRecord = AudioRecord(
            audioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            numBytes * 2 // a sample has two bytes as we are using 16-bit PCM
        )
        return true
    }

    fun processSamples() {
        Log.i("Sherpa", "processing samples")
        val stream = recognizer.createStream()

        val interval = 0.1 // i.e., 100 ms
        val bufferSize = (interval * sampleRateInHz).toInt() // in samples
        val buffer = ShortArray(bufferSize)

        while (isRecording) {
            val ret = audioRecord?.read(buffer, 0, buffer.size)
            if (ret != null && ret > 0) {
                val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                stream.acceptWaveform(samples, sampleRate = sampleRateInHz)
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }

                val isEndpoint = recognizer.isEndpoint(stream)
                var text = recognizer.getResult(stream).text

                // For streaming parformer, we need to manually add some
                // paddings so that it has enough right context to
                // recognize the last word of this segment
                if (isEndpoint && recognizer.config.modelConfig.paraformer.encoder.isNotBlank()) {
                    val tailPaddings = FloatArray((0.8 * sampleRateInHz).toInt())
                    stream.acceptWaveform(tailPaddings, sampleRate = sampleRateInHz)
                    while (recognizer.isReady(stream)) {
                        recognizer.decode(stream)
                    }
                    text = recognizer.getResult(stream).text
                }

                var textToDisplay = lastText

                if (text.isNotBlank()) {
                    textToDisplay = if (lastText.isBlank()) {
                        text
                    } else {
                        "${lastText}\n: $text"
                    }
                }

                if (isEndpoint) {
                    recognizer.reset(stream)
                    if (text.isNotBlank()) {
                        lastText = text
                        textToDisplay = lastText
                        idx += 1
                    }
                }
                updateMessage(textToDisplay)
            }
        }
        stream.release()
    }

    // Sherpa-onnx TTS

    private fun callback(samples: FloatArray): Int {
        if (!stopped) {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            return 1
        } else {
            track.stop()
            return 0
        }
    }

    fun onClickGenerate(context: Context) {
        track.pause()
        track.flush()
        track.play()

        val dir = context.filesDir
        dir.listFiles()?.forEach {
            if (it.name.endsWith(".wav")) {
                it.delete()
            }
        }

        stopped = false
        Thread {
            val audio = tts.generateWithCallback(
                text = textForTextToSpeech,
                sid = sid,
                speed = speed,
                callback = this::callback
            )

            val filename = context.filesDir.absolutePath + "/generated.wav"
            val ok = audio.samples.size > 0 && audio.save(filename)
            if (ok) {
                track.stop()
            }
        }.start()
    }

//    fun onClickPlay(context: Context) {
//        val filename = context.filesDir.absolutePath + "/generated.wav"
//        mediaPlayer?.stop()
//        mediaPlayer = MediaPlayer.create(
//            context,
//            Uri.fromFile(File(filename))
//        )
//        mediaPlayer?.start()
//        mediaPlayer = null
//    }
//
//    fun onClickStop() {
//        track.pause()
//        track.flush()
//        mediaPlayer?.stop()
//        mediaPlayer = null
//    }


    var toggler by mutableStateOf(false)
    var showModal by  mutableStateOf(true)
    var showAlert by mutableStateOf(false)
    var currentDownloadable: Downloadable? by mutableStateOf(null)

    override fun onCleared() {
        textToSpeech?.shutdown()
        super.onCleared()

        viewModelScope.launch {
            try {

                llamaAndroid.unload()

            } catch (exc: IllegalStateException) {
                addMessage("error", exc.message ?: "")
            }
        }
    }

    fun send() {
        val userMessage = removeExtraWhiteSpaces(message)
        message = ""

        // Add to messages console.
        if (userMessage != "" && userMessage != " ") {
            if(first){
                addMessage("system", "I am EdgePLM,  a helpful assistant")
                addMessage("user", "Hi")
                addMessage("assistant", "How may I help You?")
                first = false
            }

            addMessage("user", userMessage)


            viewModelScope.launch {
                try {
                    llamaAndroid.send(llamaAndroid.getTemplate(messages))
                        .catch {
                            Log.e(tag, "send() failed", it)
                            addMessage("error", it.message ?: "")
                        }
                        .collect { response ->
                            // Create a new assistant message with the response
                            if (getIsMarked()) {
                                addMessage("codeBlock", response)

                            } else {
                                addMessage("assistant", response)
                            }
                        }
                }
                finally {
                    if (!getIsCompleteEOT()) {
                        trimEOT()
                    }
                }



            }
        }
    }

    suspend fun unload(){
        llamaAndroid.unload()
    }

    var tokensList = mutableListOf<String>() // Store emitted tokens
    var benchmarkStartTime: Long = 0L // Track the benchmark start time
    var tokensPerSecondsFinal: Double by mutableStateOf(0.0) // Track tokens per second and trigger UI updates
    var isBenchmarkingComplete by mutableStateOf(false) // Flag to track if benchmarking is complete

    fun myCustomBenchmark() {
        viewModelScope.launch {
            try {
                tokensList.clear() // Reset the token list before benchmarking
                benchmarkStartTime = System.currentTimeMillis() // Record the start time
                isBenchmarkingComplete = false // Reset benchmarking flag

                // Launch a coroutine to update the tokens per second every second
                launch {
                    while (!isBenchmarkingComplete) {
                        delay(1000L) // Delay 1 second
                        val elapsedTime = System.currentTimeMillis() - benchmarkStartTime
                        if (elapsedTime > 0) {
                            tokensPerSecondsFinal = tokensList.size.toDouble() / (elapsedTime / 1000.0)
                        }
                    }
                }

                llamaAndroid.myCustomBenchmark()
                    .collect { emittedString ->
                        if (emittedString != null) {
                            tokensList.add(emittedString) // Add each token to the list
                            Log.d(tag, "Token collected: $emittedString")
                        }
                    }
            } catch (exc: IllegalStateException) {
                Log.e(tag, "myCustomBenchmark() failed", exc)
            } catch (exc: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e(tag, "myCustomBenchmark() timed out", exc)
            } catch (exc: Exception) {
                Log.e(tag, "Unexpected error during myCustomBenchmark()", exc)
            } finally {
                // Benchmark complete, log the final tokens per second value
                val elapsedTime = System.currentTimeMillis() - benchmarkStartTime
                val finalTokensPerSecond = if (elapsedTime > 0) {
                    tokensList.size.toDouble() / (elapsedTime / 1000.0)
                } else {
                    0.0
                }
                Log.d(tag, "Benchmark complete. Tokens/sec: $finalTokensPerSecond")

                // Update the final tokens per second and stop updating the value
                tokensPerSecondsFinal = finalTokensPerSecond
                isBenchmarkingComplete = true // Mark benchmarking as complete
            }
        }
    }





    var loadedModelName = mutableStateOf("")

    fun load(pathToModel: String, userThreads: Int)  {
        viewModelScope.launch {
            try{
                llamaAndroid.unload()
            } catch (exc: IllegalStateException){
                Log.e(tag, "load() failed", exc)
            }
            try {
                var modelName = pathToModel.split("/")
                loadedModelName.value = modelName.last()
                newShowModal = false
                showModal= false
                showAlert = true
                llamaAndroid.load(pathToModel, userThreads = userThreads, topK = topK, topP = topP, temp = temp)
                showAlert = false

            } catch (exc: IllegalStateException) {
                Log.e(tag, "load() failed", exc)
//                addMessage("error", exc.message ?: "")
            }
            showModal = false
            showAlert = false
            eot_str = llamaAndroid.send_eot_str()
        }
    }
    private fun addMessage(role: String, content: String) {
        val newMessage = mapOf("role" to role, "content" to content)

        messages = if (messages.isNotEmpty() && messages.last()["role"] == role) {
            val lastMessageContent = messages.last()["content"] ?: ""
            val updatedContent = "$lastMessageContent$content"
            val updatedLastMessage = messages.last() + ("content" to updatedContent)
            messages.toMutableList().apply {
                set(messages.lastIndex, updatedLastMessage)
            }
        } else {
            messages + listOf(newMessage)
        }
    }

    private fun trimEOT() {
        if (messages.isEmpty()) return
        val lastMessageContent = messages.last()["content"] ?: ""
        // Only slice if the content is longer than the EOT string
        if (lastMessageContent.length < eot_str.length) return

        val updatedContent = lastMessageContent.slice(0..(lastMessageContent.length-eot_str.length))
        val updatedLastMessage = messages.last() + ("content" to updatedContent)
        messages = messages.toMutableList().apply {
            set(messages.lastIndex, updatedLastMessage)
        }
        messages.last()["content"]?.let { Log.e(tag, it) }
    }

    private fun removeExtraWhiteSpaces(input: String): String {
        // Replace multiple white spaces with a single space
        return input.replace("\\s+".toRegex(), " ")
    }

    private fun parseTemplateJson(chatData: List<Map<String, String>> ):String{
        var chatStr = ""
        for (data in chatData){
            val role = data["role"]
            val content = data["content"]
            if (role != "log"){
                chatStr += "$role \n$content \n"
            }

        }
        return chatStr
    }
    fun updateMessage(newMessage: String) {
        message = newMessage
    }

    fun clear() {
        messages = listOf(

        )
        first = true
    }

    fun log(message: String) {
//        addMessage("log", message)
    }

    fun getIsSending(): Boolean {
        return llamaAndroid.getIsSending()
    }

    private fun getIsMarked(): Boolean {
        return llamaAndroid.getIsMarked()
    }

    fun getIsCompleteEOT(): Boolean{
        return llamaAndroid.getIsCompleteEOT()
    }

    fun stop() {
        llamaAndroid.stopTextGeneration()
    }

}