package pl.pollub.s95408.lab4

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.pollub.s95408.lab4.DownloadWorker.Companion.Bytes
import pl.pollub.s95408.lab4.DownloadWorker.Companion.Progress
import pl.pollub.s95408.lab4.ui.theme.Lab4Theme
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
        setContent {
            Lab4Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.padding(all = 15.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {

                        InfoSection()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun InfoSection()
    {
        val data = remember { mutableStateOf(File(id = "10", name = "Sample name", type="0", url = "https://cdn.kernel.org/pub/linux/kernel/v6.x/linux-6.3.tar.xz", downloadedUri = null)) }
        val url = rememberSaveable { mutableStateOf("https://cdn.kernel.org/pub/linux/kernel/v6.x/linux-6.3.tar.xz") }
        val scope = rememberCoroutineScope()
        var fileSize by rememberSaveable { mutableStateOf(0)}
        var bytesDownloaded by rememberSaveable { mutableStateOf(0L)}
        var progress by rememberSaveable { mutableStateOf(0f)}
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                space = 10.dp,
                alignment = Alignment.CenterHorizontally
            )
        ) {
            Text("Adres")
            OutlinedTextField(
                value = url.value,
                onValueChange = { url.value = it },
                label = { Text("Podaj adres") },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround)
        {
            Button(onClick = {
                data.value.url = url.value
                scope.launch { withContext(Dispatchers.IO)
                {
                    val conn = URL(data.value.url).openConnection() as HttpsURLConnection
                    conn.connect()
                    Log.d("CONNECTION", "Connected successfully")
                    Log.d("CONNECTION", "Content length: ${conn.contentLength}")
                    Log.d("CONNECTION", "Content type: ${conn.contentType}")
                    fileSize = conn.contentLength
                    data.value.type = conn.contentType
                    conn.disconnect()
                    Log.d("CONNECTION", "Disconnected successfully")
                } }

            }) {
                Text("Pobierz informacje")
            }
            Button(onClick = {
                data.value.url = url.value
                if (data.value.url.isEmpty())
                    return@Button
                if(data.value.type == "0" || data.value.type.isEmpty()) {
                    scope.launch {
                        withContext(Dispatchers.IO)
                        {
                            Log.d("CONNECTION", "Need file mime type")
                            val conn = URL(data.value.url).openConnection() as HttpsURLConnection
                            conn.connect()
                            data.value.type = conn.contentType
                            fileSize = conn.contentLength
                            conn.disconnect()
                            Log.d("CONNECTION", "Disconnected successfully")
                        }
                    }
                }
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .setRequiresBatteryNotLow(true)
                    .build()
                val workData = Data.Builder()
                workData.apply {
                    putString("KEY_FILE_URL", data.value.url)
                    putString("KEY_MIME_TYPE", data.value.type)
                    putString("KEY_FILE_NAME", data.value.url.substringAfterLast("/"))
                }
                Log.d("DATA", "URL: ${data.value.url}")
                Log.d("DATA", "MIME: ${data.value.type}")
                Log.d("DATA", "NAME: ${data.value.url.substringAfterLast("/")}")
                val oneTimeWorkRequest = OneTimeWorkRequest.Builder(DownloadWorker::class.java)
                    .setConstraints(constraints)
                    .setInputData(workData.build())
                    .build()
                val workManager = WorkManager.getInstance(applicationContext)
                workManager.enqueueUniqueWork("DownloadWorker_${System.currentTimeMillis()}", ExistingWorkPolicy.KEEP, oneTimeWorkRequest)
                workManager.getWorkInfoByIdLiveData(oneTimeWorkRequest.id).observe(this@MainActivity) {
                        info ->
                    info?.let {
                        bytesDownloaded = it.progress.getLong(Bytes, 0L)
                        progress = it.progress.getFloat(Progress, 0f)
                        when (it.state) {
                            WorkInfo.State.FAILED ->
                            {
                                Log.d("WORKER", "Failed")
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                Log.d("WORKER", "Succeeded")
                                data.value.downloadedUri = it.outputData.getString("KEY_FILE_URI")
                            }
                            WorkInfo.State.RUNNING -> {
                                Log.d("WORKER", "Running")
                            }
                            WorkInfo.State.ENQUEUED -> {
                                Log.d("WORKER", "Enqueued")
                            }
                            WorkInfo.State.BLOCKED ->
                            {
                                Log.d("WORKER", "Blocked")
                            }
                            WorkInfo.State.CANCELLED -> {
                                Log.d("WORKER", "Cancelled")
                            }
                        }
                    }

                }}) {
                Text("Pobierz plik")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Rozmiar pliku", modifier = Modifier.padding(bottom = 10.dp))
                Text("Typ pliku")
            }
            Column {
                Text(text = fileSize.toString(), modifier = Modifier.padding(bottom = 10.dp))
                Text(text = data.value.type, modifier = Modifier.padding(bottom = 10.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Pobrano bajtów")
            }
            Column {
                Text(text = bytesDownloaded.toString(), modifier = Modifier.padding(bottom = 10.dp))
            }
        }
        LinearProgressIndicator(progress = progress)
    }

}

