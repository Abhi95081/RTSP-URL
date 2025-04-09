package com.example.myapplication

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var libVLC: LibVLC
    private var mediaPlayer: MediaPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var ffmpegSession: FFmpegSession? = null
    private lateinit var coroutineScope: CoroutineScope

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
        private const val TAG = "RTSPComposePlayer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val options = arrayListOf("--network-caching=300", "-vvv")
        libVLC = LibVLC(this, options)
        checkAndRequestPermissions()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF6200EE),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1F1F1F)
                )
            ) {
                MainScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val context = LocalContext.current
        var rtspUrl by remember { mutableStateOf(TextFieldValue("rtsp://35.171.173.241:65311/remmiedd")) }
        var isRecording by remember { mutableStateOf(false) }
        var scale by remember { mutableFloatStateOf(1f) }
        var isMaximized by remember { mutableStateOf(false) }
        coroutineScope = rememberCoroutineScope()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("RTSP Viewer", color = Color.White) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1F1F1F), Color(0xFF121212))
                        )
                    )
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            surfaceView = this
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isMaximized) 300.dp else (16 * 24).dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                surfaceView?.scaleX = scale
                                surfaceView?.scaleY = scale
                            }
                        }
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = rtspUrl,
                            onValueChange = { rtspUrl = it },
                            label = { Text("RTSP URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(onClick = {
                                startPlayback(rtspUrl.text)
                                coroutineScope.launch {
                                    delay(1000)
                                    attachSurface()
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Start Playback")
                            }

                            Button(onClick = { stopPlayback() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Close, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Stop Playback")
                            }

                            Button(onClick = {
                                if (!isRecording) {
                                    startRecording(rtspUrl.text)
                                    isRecording = true
                                } else {
                                    stopRecording()
                                    isRecording = false
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    if (isRecording) Icons.Default.Star else Icons.Default.AddCircle,
                                    contentDescription = null,
                                    tint = if (isRecording) Color.White else Color.Red
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (isRecording) "Stop Recording" else "Start Recording")
                            }

                            Button(
                                onClick = { isMaximized = !isMaximized },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    if (isMaximized) Icons.Default.ArrowForward else Icons.Default.ArrowBack,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (isMaximized) "Minimize Video" else "Maximize Video")
                            }

                            Button(
                                onClick = {
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                    (context as? ComponentActivity)?.enterPictureInPictureMode(params)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Enter PiP Mode")
                            }

                            Button(
                                onClick = { openVideoFolder() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Open Recording Folder")
                            }

                            Button(
                                onClick = { playSavedVideo() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ThumbUp, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Play Last Saved Video")
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Buttons:\n▶️ Start Playback\n⏹️ Stop Playback\n🔴 Start Recording\n⚪ Stop Recording\n🧿 Enter PiP Mode\n📂 Open Recording Folder\n🎞️ Play Last Saved Video",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    private fun startPlayback(url: String) {
        mediaPlayer = MediaPlayer(libVLC).apply {
            val media = Media(libVLC, Uri.parse(url))
            media.setHWDecoderEnabled(true, false)
            setMedia(media)
            play()
        }
        Toast.makeText(this, "Playback started", Toast.LENGTH_SHORT).show()
    }

    private fun stopPlayback() {
        stopRecording()
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            val vout = player.vlcVout
            if (vout.areViewsAttached()) {
                vout.detachViews()
            }
            player.release()
        }
        mediaPlayer = null
        Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show()
    }

    private fun attachSurface() {
        surfaceView?.holder?.let { holder ->
            mediaPlayer?.vlcVout?.setVideoSurface(holder.surface, holder)
            mediaPlayer?.vlcVout?.attachViews()
        }
    }

    private fun startRecording(url: String) {
        val outputFile = File(getExternalFilesDir("recordings"), "recorded_${System.currentTimeMillis()}.mp4")
        val command = "-rtsp_transport tcp -i $url -c copy -f mp4 ${outputFile.absolutePath}"

        ffmpegSession = FFmpegKit.executeAsync(command) { session ->
            Log.d(TAG, "Recording session ended: ${session.state}, returnCode: ${session.returnCode}")
        }

        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        ffmpegSession?.cancel()
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
        ffmpegSession = null
    }

    private fun openVideoFolder() {
        val folder = getExternalFilesDir("recordings") ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", folder)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "resource/folder")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "No app found to open folder", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playSavedVideo() {
        val recordingsDir = getExternalFilesDir("recordings") ?: return
        val videos = recordingsDir.listFiles()?.sortedByDescending { it.lastModified() }
        val latestVideo = videos?.firstOrNull()

        if (latestVideo != null) {
            stopPlayback()
            val uri = Uri.fromFile(latestVideo)
            mediaPlayer = MediaPlayer(libVLC).apply {
                val media = Media(libVLC, uri)
                media.setHWDecoderEnabled(true, false)
                setMedia(media)
                play()
            }
            coroutineScope.launch {
                delay(1000)
                attachSurface()
            }
            Toast.makeText(this, "Playing saved video", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No saved video found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
        libVLC.release()
    }
}
