# Run the project

Clone the project and [llama.cpp](https://github.com/Si1w/PLM-llama.cpp-stable) in the same directory.

```bash
mkdir EdgePLM-Android
cd EdgePLM-Android
git clone git@github.com:Si1w/PLM-llama.cpp-stable.git
git clone git@github.com:Si1w/iris_android.git
```

<u>**Remainder**</u>: Since the latest version of `llama.cpp` change several api, so we need to use the stable version to run the project.

# Model lists for download

The path of the model lists is in [app/src/main/java/com/nervesparks/iris/MainActivity.kt](https://github.com/Si1w/iris_android/blob/master/app/src/main/java/com/nervesparks/iris/MainActivity.kt)

```kotlin
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
```

To add a new model, you need to add a new `Downloadable` object in the `models` list. The `Downloadable` object contains three parameters: `name`, `uri`, and `file`. The `name` is the name of the model, the `uri` is the download link of the model, and the `file` is the path where the model will be saved. The `uri` can be obtained from [huggingface](https://huggingface.co).

Finally, you need to add the model card into the model choose page [app/src/main/java/com/nervesparks/iris/MainViewModel.kt](https://github.com/Si1w/iris_android/blob/master/app/src/main/java/com/nervesparks/iris/MainViewModel.kt)

```kotlin
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
```

# Key Features

## Chat Screen

The path of the chatscreen is in [app/src/main/java/com/nervesparks/iris/ui/MainChatScreen.kt](https://github.com/Si1w/iris_android/blob/master/app/src/main/java/com/nervesparks/iris/ui/MainChatScreen.kt)

## Model Parameters

The path of the parameter screen is in [app/src/main/java/com/nervesparks/iris/ui/ParametersScreen.kt](https://github.com/Si1w/iris_android/blob/master/app/src/main/java/com/nervesparks/iris/ui/ParametersScreen.kt)

## Sherpa-onnx

These files are for the sherpa-onnx TTS and STT [app/src/main/java/com/k2fsa/sherpa/onnx](https://github.com/Si1w/iris_android/tree/master/app/src/main/java/com/k2fsa/sherpa/onnx)

Also the `jniLibs` is the shared library for the sherpa-onnx

## Llama.cpp

These files are for the llama.cpp kotlin api [./llama](https://github.com/Si1w/iris_android/tree/master/llama)

## Project Variables

Most of the model parameters and variables are all in `MainViewModel` part
[app/src/main/java/com/nervesparks/iris/MainViewModel.kt](https://github.com/Si1w/iris_android/blob/master/app/src/main/java/com/nervesparks/iris/MainViewModel.kt)