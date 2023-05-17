package pl.pollub.s95408.lab4

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class DownloadWorker(private val context: Context, workerParameters: WorkerParameters) : CoroutineWorker(context, workerParameters) {

    companion object {
        const val Progress = "Progress"
        const val Bytes = "Bytes"
    }

    object FileParams{
        const val KEY_FILE_URL = "KEY_FILE_URL"
        const val KEY_FILE_TYPE = "KEY_MIME_TYPE"
        const val KEY_FILE_NAME = "KEY_FILE_NAME"
        const val KEY_FILE_URI = "KEY_FILE_URI"
    }

    object NotificationConstants{
        const val CHANNEL_NAME = "Download_File_Worker_Channel_Name"
        const val CHANNEL_DESCRIPTION = "Download_File_Worker_Channel_Description"
        const val CHANNEL_ID = "Lab4_Download_File_Worker_Demo_Channel"
        const val NOTIFICATION_ID = 1
    }

    override suspend fun doWork(): Result {
        val fileUrl = inputData.getString(FileParams.KEY_FILE_URL) ?: ""
        val fileName = inputData.getString(FileParams.KEY_FILE_NAME) ?: ""
        val fileType = inputData.getString(FileParams.KEY_FILE_TYPE) ?: ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){

            val name = NotificationConstants.CHANNEL_NAME
            val description = NotificationConstants.CHANNEL_DESCRIPTION
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NotificationConstants.CHANNEL_ID,name,importance)
            channel.description = description

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?

            notificationManager?.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context,NotificationConstants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading $fileName")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setProgress(100,0,false)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure()
        }
        NotificationManagerCompat.from(context).notify(NotificationConstants.NOTIFICATION_ID,builder.build())

        if(fileUrl.isEmpty() || fileType.isEmpty() || fileName.isEmpty()) {
            Log.d("DownloadWorker", "URL: $fileUrl")
            Log.d("DownloadWorker", "Type: $fileType")
            Log.d("DownloadWorker", "Name: $fileName")
            Log.e("DownloadWorker","File url, type or name is empty")
            return Result.failure()
        }

        val uri = getSavedFileUri(
            fileName = fileName,
            mimeType = fileType,
            fileUrl = fileUrl,
            context = context,
            notificationBuilder = builder
        )
        NotificationManagerCompat.from(context).cancel(NotificationConstants.NOTIFICATION_ID)
        return if (uri != null){
            Result.success(workDataOf(FileParams.KEY_FILE_URI to uri.toString()))
        }else{
            Result.failure()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getSavedFileUri(
        fileName:String,
        mimeType:String,
        fileUrl:String,
        context: Context,
        notificationBuilder: NotificationCompat.Builder): Uri?{

        if (mimeType.isEmpty()) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Lab4")
            }

            val resolver = context.contentResolver

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            return if (uri!=null){
                withContext(Dispatchers.IO) {
                    val connection = URL(fileUrl).openConnection()
                    connection.connect()
                    val lengthOfFile = connection.contentLength
                    val inputStream = connection.getInputStream()
                    val outputStream = resolver.openOutputStream(uri)
                    val data = ByteArray(1024)
                    var total: Long = 0
                    var count: Int
                    while (inputStream.read(data).also { count = it } != -1) {
                        total += count.toLong()
                        outputStream?.write(data, 0, count)
                        val progress = total.toFloat() / lengthOfFile.toFloat()
                        setProgress(workDataOf(Progress to progress, Bytes to total))
                        notificationBuilder.setProgress(100, (progress * 100).toInt(), false)
                        NotificationManagerCompat.from(context).notify(NotificationConstants.NOTIFICATION_ID, notificationBuilder.build())
                    }
                    outputStream?.flush()
                    outputStream?.close()
                    inputStream.close()
                }
                uri
            }else{
                null
            }
        }else{

            val target = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            URL(fileUrl).openStream().use { input->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            return target.toUri()
        }
    }
}

