package com.nervesparks.iris

import android.app.Application
import android.content.Context
import android.llama.cpp.LLamaAndroid
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nervesparks.iris.data.UserPreferencesRepository
import com.nervesparks.iris.media.decodeWaveFile
import com.nervesparks.iris.recorder.Recorder
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
//import org.json.JSONObject
//import org.vosk.Model
//import org.vosk.Recognizer
//import org.vosk.android.RecognitionListener
//import org.vosk.android.SpeechService
//import org.vosk.android.SpeechStreamService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.UUID


class MainViewModel(
    private val llamaAndroid: LLamaAndroid = LLamaAndroid.instance(),
    private val userPreferencesRepository: UserPreferencesRepository,
    private val application: Application
): ViewModel() {
    companion object {
//        @JvmStatic
//        private val NanosPerSecond = 1_000_000_000.0
    }


    private val _defaultModelName = mutableStateOf("")
    val defaultModelName: State<String> = _defaultModelName

    init {
        loadDefaultModelName()
        viewModelScope.launch {
            loadData()
            Log.i("whisper", "Loaded")
        }
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

//    private var Vosk: Model? = null
//    private var speechService: SpeechService? = null

    var canTranscribe by mutableStateOf(false)
        private set
    var dataLog by mutableStateOf("")
        private set
    var isRecording by mutableStateOf(false)
        private set

    private var lastTranscribedText by mutableStateOf("")
    private val modelsPath by lazy { File(application.filesDir, "models") }
    private var recorder: Recorder = Recorder()
    private var whisperContext: WhisperContext? = null
    private var recordedFile: File? = null
    private var mediaPlayer: MediaPlayer? = null

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

    fun textToSpeech(context: Context) {
        if (!getIsSending()) {
            // If TTS is already initialized, stop it first
            textToSpeech?.stop()

            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.let { txtToSpeech ->
                        txtToSpeech.language = Locale.US
                        txtToSpeech.setSpeechRate(1.0f)

                        // Add a unique utterance ID for tracking
                        val utteranceId = UUID.randomUUID().toString()

                        txtToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onDone(utteranceId: String?) {
                                // Reset state when speech is complete
                                CoroutineScope(Dispatchers.Main).launch {
                                    stateForTextToSpeech = true
                                }
                            }

                            override fun onError(utteranceId: String?) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    stateForTextToSpeech = true
                                }
                            }

                            override fun onStart(utteranceId: String?) {
                                // Update state to indicate speech is playing
                                CoroutineScope(Dispatchers.Main).launch {
                                    stateForTextToSpeech = false
                                }
                            }
                        })

                        txtToSpeech.speak(
                            textForTextToSpeech,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            utteranceId
                        )
                    }
                }
            }
        }
    }

    fun stopTextToSpeech() {
        textToSpeech?.apply {
            stop()  // Stops current speech
            shutdown()  // Releases the resources
        }
        textToSpeech = null

        // Reset state to allow restarting
        stateForTextToSpeech = true
    }

    private suspend fun loadData() {
        try {
            copyAssets()
            loadBaseModel()
            canTranscribe = true
            Log.i("whisper", "Loaded")
        } catch (e: Exception) {
            Log.w("whisper", e)
        }
    }

    private suspend fun copyAssets() = withContext(Dispatchers.IO) {
        try{ modelsPath.mkdirs() } catch (e: Exception) {Log.w("whisper", "Failed to create directory")}
    }

    private suspend fun loadBaseModel() = withContext(Dispatchers.IO) {
        val models = application.assets.list("models/")
        if (models != null) {
            whisperContext = WhisperContext.createContextFromAsset(
                application.assets,
                "models/" + models[0]
            )
        }
        Log.i("whisper", "model Loaded successfully")
    }

    private suspend fun startPlayback(file: File) = withContext(Dispatchers.Main) {
        mediaPlayer = MediaPlayer.create(application, file.absolutePath.toUri())
        mediaPlayer?.start()
    }

    private suspend fun stopPlayback() = withContext(Dispatchers.Main) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private suspend fun readAudioSamples(file: File): FloatArray = withContext(Dispatchers.IO) {
        stopPlayback()
        startPlayback(file)
        return@withContext decodeWaveFile(file)
    }

    private suspend fun transcribeAudio(file: File) {
        if (!canTranscribe) {
            return
        }
        canTranscribe = false
        try {
            val data = readAudioSamples(file)
            val text = whisperContext?.transcribeData(data) ?: ""
            val contentOnly = text.replace(Regex("\\[\\d{2}:\\d{2}:\\d{2}\\.\\d{3} --> \\d{2}:\\d{2}:\\d{2}\\.\\d{3}]:\\s*"), "").trim()
            Log.i("whisper", "Transcription: $text")
            updateMessage(contentOnly)
        } catch (e: Exception) {
            Log.w("whisper", e)
        }

        canTranscribe = true
    }

    fun toggleRecord() = viewModelScope.launch {
        try {
            if (isRecording) {
                recorder.stopRecording()
                isRecording = false
                recordedFile?.let { transcribeAudio(it) }
                Log.i("whisper", "Translating the speech")
            } else {
                stopPlayback()
                val file = getTempFileForRecording()
                recorder.startRecording(file) { e ->
                    viewModelScope.launch {
                        withContext(Dispatchers.Main) {
                            isRecording = false
                        }
                    }
                }
                isRecording = true
                recordedFile = file
            }
        } catch (e: Exception) {
            Log.w("whisper", e)
            isRecording = false
        }
    }

    private suspend fun getTempFileForRecording() = withContext(Dispatchers.IO) {
        File.createTempFile("recording", "wav")
    }

//    fun loadVoskModel(context: Context) {
//        viewModelScope.launch {
//            try {
//                val modelPath = copyAssetFolder(context, "model-en-us")
//                Vosk = Model(modelPath)
//                Log.i("Vosk", "Model loaded from $modelPath")
//            } catch (e: Exception) {
//                Log.e("Vosk", "Unexpected error: ${e.message}")
//            }
//        }
//    }
//
//    fun SpeechToText() {
//        try {
//            val sample_rate = 16000f
//            val recognizer = Recognizer(Vosk, sample_rate)
//            speechService = SpeechService(recognizer, sample_rate).apply {
//                startListening(object : RecognitionListener {
//                    override fun onPartialResult(result: String?) {
//                        Log.i("SST", "Partial Result: $result")
//                        result?.let {
//                            try {
//                                val cleanedResult = it.trim()
//                                val jsonObj = JSONObject(cleanedResult)
//                                val text = jsonObj.optString("text", "")
//                                Log.d("SST", "Extracted Text: $text")
//                                if (text != "") {
//                                    updateMessage(text)
//                                } else {}
//                            } catch (e: Exception) {
//                                Log.e("SST", "JSON Parsing Error: ${e.message}")
//                            }
//                        }
//                    }
//
//                    override fun onResult(result: String?) {
//                        Log.d("SST", "Mid Result：$result")
//                        result?.let {
//                            try {
//                                val cleanedResult = it.trim()
//                                val jsonObj = JSONObject(cleanedResult)
//                                val text = jsonObj.optString("text", "")
//                                Log.d("SST", "Extracted Text: $text")
//                                if (text != "") {
//                                    updateMessage(text)
//                                } else {}
//                            } catch (e: Exception) {
//                                Log.e("SST", "JSON Parsing Error: ${e.message}")
//                            }
//                        }
//                    }
//
//                    override fun onFinalResult(result: String?) {
//                        Log.d("SST", "Final Result JSON: $result")
//                        result?.let {
//                            try {
//                                val cleanedResult = it.trim()
//                                val jsonObj = JSONObject(cleanedResult)
//                                val text = jsonObj.optString("text", "")
//                                Log.d("SST", "Extracted Text: $text")
//                                if (text != "") {
//                                    updateMessage(text)
//                                } else {}
//                            } catch (e: Exception) {
//                                Log.e("SST", "JSON Parsing Error: ${e.message}")
//                            }
//                        }
//                    }
//
//                    override fun onError(e: Exception?) {
//                        Log.e("SST", "Error Result：${e?.message}")
//                    }
//
//                    override fun onTimeout() {
//                        Log.d("SST", "Overtime")
//                    }
//                })
//            }
//        } catch (e: Exception) {
//            Log.e("SST", "Error on Vosk: ${e.message}")
//        }
//    }
//
//    fun stopSpeechToText() {
//        try {
//            speechService?.stop()
//            Log.d("SST", "Speech recognition stopped.")
//        } catch (e: Exception) {
//            Log.e("SST", "Error stopping STT: ${e.message}")
//        }
//    }

    var toggler by mutableStateOf(false)
    var showModal by  mutableStateOf(true)
    var showAlert by mutableStateOf(false)
    var switchModal by mutableStateOf(false)
    var currentDownloadable: Downloadable? by mutableStateOf(null)

    override fun onCleared() {
        textToSpeech?.shutdown()
        super.onCleared()

        viewModelScope.launch {
            try {
                llamaAndroid.unload()
                runBlocking {
                    whisperContext?.release()
                    whisperContext = null
                    stopPlayback()
                }
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
            } catch (exc: TimeoutCancellationException) {
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

    var loadedModelName = mutableStateOf("");

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

//    private fun copyAssetFolder(context: Context, assetFolder: String): String {
//        val outputDir = File(context.filesDir, assetFolder)
//        if (!outputDir.exists()) outputDir.mkdirs()
//
//        try {
//            val assetManager = context.assets
//            val files = assetManager.list(assetFolder) ?: return outputDir.absolutePath
//
//            for (file in files) {
//                val assetPath = "$assetFolder/$file"
//                val outFile = File(outputDir, file)
//                if (assetManager.list(assetPath)?.isNotEmpty() == true) {
//                    copyAssetFolder(context, assetPath)
//                } else {
//                    outFile.parentFile?.takeIf { !it.exists() }?.mkdirs()
//                    assetManager.open(assetPath).use { inputStream ->
//                        FileOutputStream(outFile).use { outputStream ->
//                            inputStream.copyTo(outputStream)
//                        }
//                    }
//                }
//            }
//        } catch (e: IOException) {
//            Log.e("Vosk", "Failed to copy assets: ${e.message}")
//        }
//        return outputDir.absolutePath
//    }

}