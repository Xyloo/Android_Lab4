package pl.pollub.s95408.lab4

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.pollub.s95408.lab4.ui.theme.Lab4Theme
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MainActivity : ComponentActivity() {
    private var bytesDownloaded = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                        Button(onClick = { /*TODO*/ }) {
                            Text("Pobierz plik")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Pobrano bajtów")
                            }
                            Column {
                                BytesDownloadedText()
                            }
                        }
                        Divider(color = Color.Gray, thickness = 3.dp)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun InfoSection()
    {
        val scope = rememberCoroutineScope()
        var fileType by remember { mutableStateOf("0")}
        var fileSize by remember { mutableStateOf(0)}
        var url by remember {mutableStateOf("https://cdn.kernel.org/pub/linux/kernel/v6.x/linux-6.3.tar.xz")}
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                space = 10.dp,
                alignment = Alignment.CenterHorizontally
            )
        ) {
            Text("Adres")
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Podaj adres") },
            )
        }
        Button(onClick = {
            scope.launch { withContext(Dispatchers.IO)
            {
                val conn = URL(url).openConnection() as HttpsURLConnection
                conn.connect()
                Log.d("CONNECTION", "Connected successfully")
                Log.d("CONNECTION", "Content length: ${conn.contentLength}")
                Log.d("CONNECTION", "Content type: ${conn.contentType}")
                fileSize = conn.contentLength
                fileType = conn.contentType
                conn.disconnect()
                Log.d("CONNECTION", "Disconnected successfully")
            } }
        }) {
            Text("Pobierz informacje")
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
                Text(text = fileType, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
    }

    @Composable
    fun BytesDownloadedText() {
        val value = remember { bytesDownloaded }
        Text(text = value.toString(), modifier = Modifier.padding(bottom = 10.dp))
    }

}

