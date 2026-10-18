package com.afterglow.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.util.UnstableApi
import com.afterglow.music.playback.PlaybackService
import com.afterglow.music.ui.AfterglowApp
import com.afterglow.music.ui.MusicViewModel
import com.google.common.util.concurrent.ListenableFuture
import androidx.activity.ComponentActivity

@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {
    private val viewModel: MusicViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setPermission(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AfterglowApp(viewModel = viewModel, onRequestMusicAccess = ::requestMusicAccess) }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setPermission(ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess(viewModel::bindPlayer)
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        viewModel.bindPlayer(null)
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onStop()
    }

    private fun requestMusicAccess() {
        if (ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.setPermission(true)
        } else {
            permissionLauncher.launch(audioPermission)
        }
    }
}
