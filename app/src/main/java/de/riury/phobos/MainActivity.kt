package de.riury.phobos

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.snap.creativekit.SnapCreative
import com.snap.creativekit.api.SnapCreativeKitApi
import com.snap.creativekit.media.SnapMediaFactory
import com.snap.creativekit.models.SnapContent
import com.snap.creativekit.models.SnapPhotoContent
import com.snap.creativekit.models.SnapVideoContent
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import kotlin.reflect.KClass


class MainActivity : AppCompatActivity() {

    private lateinit var snapCreativeKitApi: SnapCreativeKitApi
    private lateinit var snapMediaFactory: SnapMediaFactory

    private var mediaPicker = registerForActivityResult(PickVisualMedia()) { uri ->
        if(uri != null) processMediaUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        snapCreativeKitApi = SnapCreative.getApi(applicationContext)
        snapMediaFactory = SnapCreative.getMediaFactory(applicationContext)
    }

    fun onMediaSelectClick(v: View) {
        mediaPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
    }

    private fun processMediaUri(uri: Uri) {
        applicationContext.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE, MediaStore.Video.Media.DURATION), null, null, null)?.use { cursor ->
            val displayNameColumnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeTypeColumnIndex = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
            val durationColumnIndex = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

            cursor.moveToFirst()
            val fileName = cursor.getString(displayNameColumnIndex).substringBeforeLast('.')
            val mimeType = cursor.getString(mimeTypeColumnIndex)

            var mediaFactoryMethod: (File) -> Any = snapMediaFactory::getSnapPhotoFromFile
            var snapContentClass: KClass<*> = SnapPhotoContent::class

            if(mimeType.startsWith("video/")) {
                if(cursor.getInt(durationColumnIndex) > 60000) {
                    Toast.makeText(applicationContext, R.string.error_video_duration, Toast.LENGTH_LONG).show()
                    Log.e("Media Processing", resources.getString(R.string.error_video_duration))
                    return
                }
                mediaFactoryMethod = snapMediaFactory::getSnapVideoFromFile
                snapContentClass = SnapVideoContent::class
            }

            val file = File.createTempFile(fileName, MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)!!, cacheDir)
            Log.d("Media Processing", "Created temporary file: ${file.absolutePath}")
            file.deleteOnExit()

            applicationContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            try {
                val mediaObject = mediaFactoryMethod(file)
                val content = snapContentClass.constructors.first().call(mediaObject)
                snapCreativeKitApi.send(content as SnapContent)
            } catch (e: Exception) {
                Toast.makeText(applicationContext, R.string.error_snap_media_creation, Toast.LENGTH_LONG).show()
                e.message?.let { Log.e("Snapchat SDK", it) }
            }
        }
    }
}