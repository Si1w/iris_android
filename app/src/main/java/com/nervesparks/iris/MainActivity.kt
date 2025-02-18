package com.nervesparks.iris

import android.Manifest
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.llama.cpp.LLamaAndroid
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.text.format.Formatter
import android.util.Log
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.getEndpointConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getModelConfig
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.nervesparks.iris.data.UserPreferencesRepository
import com.nervesparks.iris.ui.SettingsBottomSheet
import com.nervesparks.iris.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainViewModelFactory(
    private val llamaAndroid: LLamaAndroid,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(llamaAndroid, userPreferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class MainActivity(
    activityManager: ActivityManager? = null,
    downloadManager: DownloadManager? = null,
    clipboardManager: ClipboardManager? = null,
) : ComponentActivity() {

    private val tag: String? = this::class.simpleName

    private val activityManager by lazy { activityManager ?: getSystemService<ActivityManager>()!! }
    private val downloadManager by lazy { downloadManager ?: getSystemService<DownloadManager>()!! }
    private val clipboardManager by lazy { clipboardManager ?: getSystemService<ClipboardManager>()!! }
    private val REQUEST_RECORD_AUDIO_PERMISSION: Int = 1
    private val permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)


    private lateinit var viewModel: MainViewModel

    private fun availableMemory(): ActivityManager.MemoryInfo {
        return ActivityManager.MemoryInfo().also { memoryInfo ->
            activityManager.getMemoryInfo(memoryInfo)
        }
    }
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(ChatGPTOnBackground, ChatGPTSurface)
    )

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        window.statusBarColor = ChatGPTOnBackground.toArgb()

        StrictMode.setVmPolicy(
            VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )
        val userPrefsRepo = UserPreferencesRepository.getInstance(applicationContext)

        val lLamaAndroid = LLamaAndroid.instance()
        val viewModelFactory = MainViewModelFactory(lLamaAndroid, userPrefsRepo)
        viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        val free = Formatter.formatFileSize(this, availableMemory().availMem)
        val total = Formatter.formatFileSize(this, availableMemory().totalMem)
        val transparentColor = Color.Transparent.toArgb()
        window.decorView.rootView.setBackgroundColor(transparentColor)
        viewModel.log("Current memory: $free / $total")
        viewModel.log("Downloads directory: ${getExternalFilesDir(null)}")

        val extFilesDir = getExternalFilesDir(null)
        val models = listOf(
            Downloadable(
                "Plm-1.8B-F16.gguf",
                Uri.parse("https://huggingface.co/PLM-Team/plm-instruct-dpo-gguf/resolve/main/Plm-1.8B-F16.gguf?download=true"),
                File(extFilesDir, "Plm-1.8B-F16.gguf")
            ),
            Downloadable(
                "plm-1.8B-instruct-dpo-Q4_K_M.gguf",
                Uri.parse("https://huggingface.co/PLM-Team/plm-instruct-dpo-gguf/resolve/main/plm-1.8B-instruct-dpo-Q4_K_M.gguf?download=true"),
                File(extFilesDir, "plm-1.8B-instruct-dpo-Q4_K_M.gguf")
            ),
            Downloadable(
                "plm-1.8B-instruct-dpo-Q8_0.gguf",
                Uri.parse("https://huggingface.co/PLM-Team/plm-instruct-dpo-gguf/resolve/main/plm-1.8B-instruct-dpo-Q8_0.gguf?download=true"),
                File(extFilesDir, "plm-1.8B-instruct-dpo-Q8_0.gguf")
            ),
        )
        if (extFilesDir != null) {
            viewModel.loadExistingModels(extFilesDir)
        }

        setContent {
            var showSettingSheet by remember { mutableStateOf(false) }
            var isBottomSheetVisible by rememberSaveable { mutableStateOf(false) }
            var modelData by rememberSaveable { mutableStateOf<List<Map<String, String>>?>(null) }
            var selectedModel by remember { mutableStateOf<String?>(null) }
            var isLoading by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            val sheetState = rememberModalBottomSheetState()

            var UserGivenModel by remember {
                mutableStateOf(
                    TextFieldValue(
                        text = viewModel.userGivenModel,
                        selection = TextRange(viewModel.userGivenModel.length)
                    )
                )
            }
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight(),
                        drawerContainerColor = ChatGPTOnBackground,
                    ) {
                        /* Drawer content wrapper */
                        Column(
                            modifier = Modifier
                                .padding(5.dp)
                                .fillMaxHeight()
                        ) {
                            // Top section with logo and name
                            Column {
                                Row(
                                    modifier =  Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.logo),
                                        contentDescription = "Centered Background Logo",
                                        modifier = Modifier.size(35.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(Modifier.padding(5.dp))
                                    Text(
                                        text = "EdgeLLM",
                                        fontWeight = FontWeight(500),
                                        color = ChatGPTOnBackground,
                                        fontSize = 30.sp
                                    )
                                    Spacer(Modifier.weight(1f))
                                    if (showSettingSheet) {
                                        SettingsBottomSheet(
                                            viewModel = viewModel,
                                            onDismiss = { showSettingSheet = false }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.padding(start = 45.dp)
                                ) {
                                    Text(
                                        text = "NerveSparks",
                                        color = ChatGPTOnBackground,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text(
                                    text = "Active Model",
                                    fontSize = 16.sp,
                                    color = ChatGPTOnBackground,
                                    modifier = Modifier
                                        .padding(vertical = 4.dp, horizontal = 8.dp)
                                )
                                Text(
                                    text = viewModel.loadedModelName.value,
                                    fontSize = 16.sp,
                                    color = ChatGPTOnBackground,
                                    modifier = Modifier
                                        .padding(vertical = 4.dp, horizontal = 8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))

                            Column(
                                verticalArrangement = Arrangement.Bottom,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .padding(horizontal = 16.dp)
                                        .background(
                                            color = ChatGPTSurface,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = Color.LightGray.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    val context = LocalContext.current
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    data = Uri.parse(
                                                        "https://github.com/nerve-sparks/iris_android"
                                                    )
                                                }
                                                context.startActivity(intent)
                                            }
                                    ) {
                                        Text(
                                            text = "Star us",
                                            color = ChatGPTOnBackground,
                                            fontSize = 14.sp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Image(
                                            modifier = Modifier.size(24.dp),
                                            painter = painterResource(id = R.drawable.github_svgrepo_com),
                                            contentDescription = "Github icon"
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(5.dp))

                                // "NerveSparks.com" button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .padding(horizontal = 16.dp)
                                        .background(
                                            color = ChatGPTSurface,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = Color.LightGray.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    val context = LocalContext.current
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    data = Uri.parse("https://nervesparks.com")
                                                }
                                                context.startActivity(intent)
                                            }
                                    ) {
                                        Text(
                                            text = "NerveSparks.com",
                                            color =ChatGPTOnBackground,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(5.dp))

                                // "powered by llama.cpp"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "powered by",
                                        color = ChatGPTOnBackground,
                                        fontSize = 14.sp
                                    )
                                    val context = LocalContext.current
                                    Text(
                                        modifier = Modifier
                                            .clickable {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    data = Uri.parse("https://github.com/ggerganov/llama.cpp")
                                                }
                                                context.startActivity(intent)
                                            },
                                        text = " llama.cpp",
                                        color = ChatGPTOnBackground,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            ) {
                ChatScreen(
                    viewModel,
                    clipboardManager,
                    downloadManager,
                    models,
                    extFilesDir,
                )
            }
        }


        // Initialize Sherpa-Onnx STT
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
        Log.i("Sherpa", "Start to initialize model")
        initModel()
        Log.i("Sherpa", "Finished initializing model")

        // Initialize Sherpa-Onnx TTS
        Log.i("Sherpa", "Start to initialize TTS")
        initTts()
        Log.i("Sherpa", "Finish initializing TTS")

        Log.i("Sherpa", "Start to initialize AudioTrack")
        initAudioTrack()
        Log.i("Sherpa", "Finish initializing AudioTrack")
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)} passing\n      in a {@link RequestMultiplePermissions} object for the {@link ActivityResultContract} and\n      handling the result in the {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }

        if (!permissionToRecordAccepted) {
            Log.e("Sherpa", "Audio record is disallowed")
            finish()
        }

        Log.i("Sherpa", "Audio record is permitted")
    }

    private fun initModel() {
        // Please change getModelConfig() to add new models
        // See https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html
        // for a list of available models
        val type = 0
        var ruleFsts : String?
        ruleFsts = null

        Log.i("Sherpa", "Select model type $type")
        val config = OnlineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = viewModel.sampleRateInHz, featureDim = 80),
            modelConfig = getModelConfig(type = type)!!,
            // lmConfig = getOnlineLMConfig(type = type),
            endpointConfig = getEndpointConfig(),
            enableEndpoint = true,
        )

        if (ruleFsts != null) {
            config.ruleFsts = ruleFsts
        }

        viewModel.recognizer = OnlineRecognizer(
            assetManager = application.assets,
            config = config,
        )
    }

    private fun initAudioTrack() {
        val sampleRate = viewModel.tts.sampleRate()
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        Log.i("Sherpa", "sampleRate: $sampleRate, buffLength: $bufLength")

        val attr = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        viewModel.track = AudioTrack(
            attr, format, bufLength, AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        viewModel.track.play()
    }

    private fun initTts() {
        var modelDir: String?
        var modelName: String?
        var acousticModelName: String?
        var vocoder: String?
        var voices: String?
        var ruleFsts: String?
        var ruleFars: String?
        var lexicon: String?
        var dataDir: String?
        var dictDir: String?
        var assets: AssetManager? = application.assets

        // The purpose of such a design is to make the CI test easier
        // Please see
        // https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-tts-apk-script.py

        // VITS -- begin
        modelName = null
        // VITS -- end

        // Matcha -- begin
        acousticModelName = null
        vocoder = null
        // Matcha -- end

        // For Kokoro -- begin
        voices = null
        // For Kokoro -- end

        ruleFsts = null
        ruleFars = null
        dataDir = null

        // vits-melo-tts-zh_en
        // https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html#vits-melo-tts-zh-en-chinese-english-1-speaker
        modelDir = "vits-melo-tts-zh_en"
        modelName = "model.onnx"
        lexicon = "lexicon.txt"
        dictDir = "vits-melo-tts-zh_en/dict"

        if (dataDir != null) {
            val newDir = copyDataDir(dataDir!!)
            dataDir = "$newDir/$dataDir"
        }

        if (dictDir != null) {
            val newDir = copyDataDir(dictDir!!)
            dictDir = "$newDir/$dictDir"
            if (ruleFsts == null) {
                ruleFsts = "$modelDir/phone.fst,$modelDir/date.fst,$modelDir/number.fst"
            }
        }

        val config = getOfflineTtsConfig(
            modelDir = modelDir!!,
            modelName = modelName ?: "",
            acousticModelName = acousticModelName ?: "",
            vocoder = vocoder ?: "",
            voices = voices ?: "",
            lexicon = lexicon ?: "",
            dataDir = dataDir ?: "",
            dictDir = dictDir ?: "",
            ruleFsts = ruleFsts ?: "",
            ruleFars = ruleFars ?: "",
        )!!

        viewModel.tts = OfflineTts(assetManager = assets, config = config)
    }

    private fun copyDataDir(dataDir: String): String {
        Log.i("Sherpa", "data dir is $dataDir")
        copyAssets(dataDir)

        val newDataDir = application.getExternalFilesDir(null)!!.absolutePath
        Log.i("Sherpa", "newDataDir: $newDataDir")
        return newDataDir
    }

    private fun copyAssets(path: String) {
        val assets: Array<String>?
        try {
            assets = application.assets.list(path)
            if (assets!!.isEmpty()) {
                copyFile(path)
            } else {
                val fullPath = "${application.getExternalFilesDir(null)}/$path"
                val dir = File(fullPath)
                dir.mkdirs()
                for (asset in assets.iterator()) {
                    val p: String = if (path == "") "" else path + "/"
                    copyAssets(p + asset)
                }
            }
        } catch (ex: IOException) {
            Log.e("Sherpa", "Failed to copy $path. $ex")
        }
    }

    private fun copyFile(filename: String) {
        try {
            val istream = application.assets.open(filename)
            val newFilename = application.getExternalFilesDir(null).toString() + "/" + filename
            val ostream = FileOutputStream(newFilename)
            // Log.i(TAG, "Copying $filename to $newFilename")
            val buffer = ByteArray(1024)
            var read = 0
            while (read != -1) {
                ostream.write(buffer, 0, read)
                read = istream.read(buffer)
            }
            istream.close()
            ostream.flush()
            ostream.close()
        } catch (ex: Exception) {
            Log.e("Sherpa", "Failed to copy $filename, $ex")
        }
    }


}

@Composable
fun LinearGradient() {
    val gradient = Brush.linearGradient(
        colors = listOf(ChatGPTSurface,ChatGPTBackground),
        start = Offset(0f, 300f),
        end = Offset(0f, 1000f)
    )
    Box(
        modifier = Modifier
            .background(gradient)
            .fillMaxSize()
    )
}







// [END android_compose_layout_material_modal_drawer]









