package com.example.myapplication

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.arthenica.ffmpegkit.FFmpegKit
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File


class MainActivity : ComponentActivity() {

    private lateinit var libVLC: LibVLC
    private var mediaPlayer: MediaPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var ffmpegSessionId: Long? = null

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
        var rtspUrl by remember { mutableStateOf(TextFieldValue("rtsp://your_rtsp_url_here")) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("RTSP Viewer", color = Color.White) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
                )
            },
            content = { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AndroidView(
                        factory = { ctx -> SurfaceView(ctx).also { surfaceView = it } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = rtspUrl,
                        onValueChange = { rtspUrl = it },
                        label = { Text("RTSP URL") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(20.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(onClick = { startPlayback(rtspUrl.text) }) {
                            Text("Start Playback")
                        }

                        Button(onClick = { stopPlayback() }) {
                            Text("Stop Playback")
                        }

                        Button(onClick = { startRecording(rtspUrl.text) }) {
                            Text("Start Recording")
                        }

                        Button(onClick = { stopRecording() }) {
                            Text("Stop Recording")
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Button(onClick = {
                                val params = PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()
                                enterPictureInPictureMode(params)
                            }) {
                                Text("Enter Picture-in-Picture")
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(
                        "Buttons:\n" +
                                "▶️ Start Playback\n" +
                                "⏹️ Stop Playback\n" +
                                "🔴 Start Recording\n" +
                                "⚪ Stop Recording\n" +
                                "🧿 Enter PiP Mode",
                        color = Color.LightGray,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        )
    }

    private fun startPlayback(url: String) {
        mediaPlayer = MediaPlayer(libVLC).apply {
            val media = Media(libVLC, Uri.parse(url))
            media.setHWDecoderEnabled(true, false)
            setMedia(media)
            attachSurface()
            play()
        }
        Toast.makeText(this, "Playback started", Toast.LENGTH_SHORT).show()
    }

    private fun stopPlayback() {
        stopRecording()
        mediaPlayer?.stop()
        mediaPlayer?.detachViews()
        mediaPlayer?.release()
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
        val outputFile = File(getExternalFilesDir(null), "recorded_${System.currentTimeMillis()}.mp4")
        val command = "-i $url -c copy -f mp4 ${outputFile.absolutePath}"

        FFmpegKit.executeAsync(command) { session ->
            Log.d(TAG, "Recording session ended: ${session.state}, returnCode: ${session.returnCode}")
        }.also { session ->
            ffmpegSessionId = session.sessionId
        }

        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        ffmpegSessionId?.let {
            FFmpegKit.cancel(it)
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
        }
        ffmpegSessionId = null
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
