package com.nervesparks.iris.ui.components

import android.app.DownloadManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nervesparks.iris.Downloadable
import com.nervesparks.iris.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ModelCard(
    modelName: String,
    viewModel: MainViewModel,
    dm: DownloadManager,
    extFilesDir: File,
    downloadLink: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = modelName,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                val coroutineScope = rememberCoroutineScope()
                val context = LocalContext.current
                var fullUrl = ""

                fullUrl = if(downloadLink != ""){
                    downloadLink
                }else{
                    "https://huggingface.co/${viewModel.userGivenModel}/resolve/main/${modelName}?download=true"
                }

                Downloadable.Button(viewModel, dm, Downloadable(modelName,source = Uri.parse(fullUrl), destination =  File(extFilesDir, modelName)))
                viewModel.currentDownloadable?.let { downloadable ->
                    if (downloadable.destination.exists()) {
                        Spacer(modifier = Modifier.height(25.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch { viewModel.unload() }
                                // Delete the model file
                                downloadable.destination.delete()
                                // Reset dialog visibility and update UI
                                viewModel.showModal = false
                                viewModel.currentDownloadable = null

                                Toast.makeText(context, "Restarting App!!.", Toast.LENGTH_SHORT).show()
                                val packageManager: PackageManager = context.packageManager
                                val intent: Intent = packageManager.getLaunchIntentForPackage(context.packageName)!!
                                val componentName: ComponentName = intent.component!!
                                val restartIntent: Intent = Intent.makeRestartActivityTask(componentName)
                                context.startActivity(restartIntent)
                                Runtime.getRuntime().exit(0)
                            },
                        ) {
                            Text(text = "Delete Model")
                        }
                    }
                }
            }

        }
    }
}