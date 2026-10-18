package com.afterglow.music.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.afterglow.music.data.Track
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

private val Night = Color(0xFF090B10)
private val DeepSpace = Color(0xFF070B14)
private val Panel = Color(0xD9131B2A)

@Composable
fun AfterglowApp(viewModel: MusicViewModel, onRequestMusicAccess: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduceUiMotion = remember(context) { reducedMotionEnabled(context) }
    val accent = when (state.accentTheme) {
        AccentTheme.AURORA -> if (state.darkTheme) Color(0xFF8DFFD7) else Color(0xFF126B5A)
        AccentTheme.ION -> if (state.darkTheme) Color(0xFF7BE5FF) else Color(0xFF176B91)
        AccentTheme.PULSAR -> if (state.darkTheme) Color(0xFFC4A8FF) else Color(0xFF6845A9)
    }
    val secondary = when (state.accentTheme) {
        AccentTheme.AURORA -> Color(0xFFFFBF78)
        AccentTheme.ION -> Color(0xFFFFD37B)
        AccentTheme.PULSAR -> Color(0xFF78E0D0)
    }
    val colors = if (state.darkTheme) darkColorScheme(
        primary = accent, secondary = secondary, background = DeepSpace, surface = Panel,
        onPrimary = Color(0xFF07100E), onBackground = Color(0xFFF3F5F7), onSurface = Color(0xFFF3F5F7),
        surfaceVariant = Color(0xE01B2432), onSurfaceVariant = Color(0xFFB9C4D5), outline = Color(0xFF48566A),
    ) else lightColorScheme(
        primary = accent, secondary = secondary, background = Color(0xFFF1F4FA), surface = Color(0xECFFFFFF),
        onPrimary = Color.White, surfaceVariant = Color(0xE8E7EDF5), onSurfaceVariant = Color(0xFF4C596B), outline = Color(0xFFB9C5D4),
    )

    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(SpaceDesign.compactRadius),
            medium = RoundedCornerShape(SpaceDesign.controlRadius),
            large = RoundedCornerShape(SpaceDesign.cardRadius),
            extraLarge = RoundedCornerShape(SpaceDesign.sheetRadius),
        ),
    ) {
        val currentTrack = state.tracks.firstOrNull { it.key == state.currentKey }
        var playerOpen by remember { mutableStateOf(false) }
        var createDialog by remember { mutableStateOf(false) }
        var addTrack by remember { mutableStateOf<Track?>(null) }
        var pendingAddTrack by remember { mutableStateOf<Track?>(null) }
        var deletePlaylist by remember { mutableStateOf<String?>(null) }
        var message by remember { mutableStateOf<String?>(null) }

        BackHandler(enabled = state.selectedPlaylist != null || playerOpen) {
            if (playerOpen) playerOpen = false else viewModel.openPlaylist(null)
        }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SpaceBackdrop(state.darkTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    SpaceDock(state.screen, viewModel::navigate)
                }
            },
        ) { inset ->
            Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = SpaceDesign.contentMaxWidth).fillMaxWidth().fillMaxHeight()
                    .padding(horizontal = SpaceDesign.screenGutter),
            ) {
                TopHeader(state, viewModel)
                message?.let { text ->
                    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(SpaceDesign.compactRadius), contentPadding = PaddingValues(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            IconButton(onClick = { message = null }) { Icon(Icons.Rounded.Close, "Dismiss", tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
                state.error?.let { ErrorBanner(it) }
                AnimatedContent(
                    state.screen,
                    modifier = Modifier.weight(1f),
                    label = "screen-content",
                    transitionSpec = {
                        if (reduceUiMotion) EnterTransition.None togetherWith ExitTransition.None
                        else fadeIn(tween(180)) togetherWith fadeOut(tween(130))
                    },
                ) { screen ->
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(state, viewModel, onRequestMusicAccess, onOpenPlayer = { playerOpen = true })
                        AppScreen.LIBRARY -> LibraryScreen(state, viewModel, onRequestMusicAccess, onOpenPlayer = { playerOpen = true }, onAdd = { addTrack = it })
                        AppScreen.SEARCH -> SearchScreen(state, viewModel, onRequestMusicAccess, onOpenPlayer = { playerOpen = true }, onAdd = { addTrack = it })
                        AppScreen.PLAYLISTS -> PlaylistScreen(
                            state, viewModel, onCreate = { createDialog = true }, onOpenPlayer = { playerOpen = true }, onAdd = { addTrack = it },
                            onDelete = { name -> deletePlaylist = name },
                        )
                        AppScreen.SETTINGS -> SettingsScreen(state, viewModel, onRequestMusicAccess)
                    }
                }
                if (currentTrack != null && state.screen != AppScreen.HOME) {
                    MiniPlayer(currentTrack, state, onClick = { playerOpen = true }, onPlayPause = viewModel::togglePlayPause, onNext = viewModel::next)
                }
            }
            }
        }

        if (playerOpen && currentTrack != null) {
            PlayerDialog(currentTrack, state, viewModel, onClose = { playerOpen = false })
        }
        if (createDialog) {
            CreatePlaylistDialog(onDismiss = { createDialog = false }, onCreate = { name ->
                val created = viewModel.createPlaylist(name)
                if (!created) {
                    message = "Choose a new playlist name"
                } else {
                    pendingAddTrack?.let { track -> viewModel.addToPlaylist(name.trim(), track); message = "Created playlist and added ${track.safeTitle}" }
                    pendingAddTrack = null
                    createDialog = false
                }
            }, onCancel = { pendingAddTrack = null })
        }
        addTrack?.let { track ->
            AddToPlaylistDialog(
                track = track, playlists = state.playlists.keys.toList(), onDismiss = { addTrack = null },
                onChoose = { name -> viewModel.addToPlaylist(name, track); addTrack = null; message = "Added to $name" },
                onCreate = { pendingAddTrack = track; addTrack = null; createDialog = true },
            )
        }
        deletePlaylist?.let { name ->
            AlertDialog(
                onDismissRequest = { deletePlaylist = null },
                title = { Text("Delete playlist?") },
                text = { Text("Remove “$name” from your playlists? The songs on your device will stay untouched.") },
                confirmButton = { TextButton(onClick = { viewModel.deletePlaylist(name); deletePlaylist = null; message = "Playlist removed" }) { Text("Delete", color = Color(0xFFE8968F)) } },
                dismissButton = { TextButton(onClick = { deletePlaylist = null }) { Text("Keep playlist") } },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        }
    }
}

@Composable
private fun SpaceBackdrop(dark: Boolean, primary: Color, secondary: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val minDimension = size.minDimension
        val base = if (dark) DeepSpace else Color(0xFFF1F4FA)
        drawRect(Brush.linearGradient(if (dark) listOf(Color(0xFF080D19), base, Color(0xFF0B101A)) else listOf(Color(0xFFF7F8FC), base, Color(0xFFE8EEF7))))
        val center = Offset(size.width * .83f, size.height * .16f)
        drawCircle(
            Brush.radialGradient(listOf(primary.copy(alpha = if (dark) .14f else .085f), Color.Transparent), center, minDimension * .58f),
            radius = minDimension * .58f,
            center = center,
        )
        drawCircle(
            Brush.radialGradient(listOf(secondary.copy(alpha = if (dark) .09f else .055f), Color.Transparent), Offset(size.width * .08f, size.height * .78f), minDimension * .48f),
            radius = minDimension * .48f,
            center = Offset(size.width * .08f, size.height * .78f),
        )
        if (!dark) return@Canvas
        drawCircle(primary.copy(alpha = .12f), radius = minDimension * .31f, center = center, style = Stroke(1.dp.toPx()))
        drawCircle(secondary.copy(alpha = .07f), radius = minDimension * .39f, center = center, style = Stroke(1.dp.toPx()))
        repeat(56) { index ->
            val x = ((index * 67 + 29) % 997) / 997f * size.width
            val y = ((index * 193 + 71) % 991) / 991f * size.height
            val radius = if (index % 9 == 0) 1.35f else .7f
            drawCircle(Color.White.copy(alpha = if (index % 7 == 0) .4f else .17f), radius, Offset(x, y))
        }
    }
}

@Composable
private fun SpaceDock(screen: AppScreen, onNavigate: (AppScreen) -> Unit) {
    val destinations = listOf(
        Triple(AppScreen.HOME, "Orbit", Icons.Rounded.Public),
        Triple(AppScreen.LIBRARY, "Library", Icons.Rounded.GraphicEq),
        Triple(AppScreen.SEARCH, "Search", Icons.Rounded.Search),
        Triple(AppScreen.PLAYLISTS, "Collections", Icons.Rounded.AutoAwesomeMosaic),
    )
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
        GlassPanel(
            Modifier.widthIn(max = SpaceDesign.dockMaxWidth).fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(25.dp),
            contentPadding = PaddingValues(5.dp),
        ) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                destinations.forEach { (destination, label, icon) ->
                    val selected = screen == destination
                    var focused by remember(destination) { mutableStateOf(false) }
                    val destinationShape = RoundedCornerShape(19.dp)
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .onFocusChanged { focused = it.isFocused }
                            .clip(destinationShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else if (focused) MaterialTheme.colorScheme.primary.copy(alpha = .10f) else Color.Transparent)
                            .border(if (focused) 1.5.dp else 1.dp, if (focused) MaterialTheme.colorScheme.secondary.copy(alpha = .9f) else Color.Transparent, destinationShape)
                            .clickable { onNavigate(destination) }.padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(icon, contentDescription = label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
                        Text(label.uppercase(), fontSize = 9.sp, letterSpacing = .8.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopHeader(state: MusicUiState, viewModel: MusicViewModel) {
    Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .14f), modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.GraphicEq, "Afterglow", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("AFTERGLOW", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.1.sp, color = MaterialTheme.colorScheme.onBackground)
            Text("A little space for sound", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { viewModel.navigate(AppScreen.SETTINGS) }) {
            Icon(Icons.Rounded.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeScreen(
    state: MusicUiState,
    vm: MusicViewModel,
    onRequestMusicAccess: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val selectedTrack = remember(state.tracks, state.currentKey, state.recentKeys) {
        state.tracks.firstOrNull { it.key == state.currentKey }
            ?: state.recentKeys.firstNotNullOfOrNull { key -> state.tracks.firstOrNull { it.key == key } }
            ?: state.tracks.firstOrNull()
    }
    val playOrResume: () -> Unit = {
        selectedTrack?.let { track ->
            if (track.key == state.currentKey) vm.togglePlayPause() else vm.play(track, state.tracks)
        }
    }
    val playNext: () -> Unit = {
        if (state.currentKey == null && selectedTrack != null && state.tracks.size > 1) {
            val index = state.tracks.indexOfFirst { it.key == selectedTrack.key }.coerceAtLeast(0)
            vm.play(state.tracks[(index + 1) % state.tracks.size], state.tracks)
        } else if (selectedTrack == null && state.tracks.isNotEmpty()) {
            vm.play(state.tracks.first(), state.tracks)
        } else {
            vm.next()
        }
    }
    val playPrevious: () -> Unit = {
        if (state.currentKey == null && selectedTrack != null && state.tracks.size > 1) {
            val index = state.tracks.indexOfFirst { it.key == selectedTrack.key }.coerceAtLeast(0)
            val previousIndex = (index - 1 + state.tracks.size) % state.tracks.size
            vm.play(state.tracks[previousIndex], state.tracks)
        } else if (selectedTrack == null && state.tracks.isNotEmpty()) {
            vm.play(state.tracks.last(), state.tracks)
        } else {
            vm.previous()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text("HOME SYSTEM  /  01", fontSize = 10.sp, letterSpacing = 2.3.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Your sound, in orbit.", fontSize = 24.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (state.tracks.isEmpty()) "A whole world waiting for its first signal." else "${state.tracks.size} local tracks · orbiting only here", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (state.loading) CircularProgressIndicator(Modifier.padding(start = 8.dp, bottom = 2.dp).size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        }

        PlanetaryMusicOrbit(
            tracks = state.tracks,
            selectedTrack = selectedTrack,
            isPlaying = state.isPlaying,
            onSelectTrack = { vm.play(it, state.tracks) },
            onPlanetTap = {
                when {
                    selectedTrack != null -> playOrResume()
                    !state.hasPermission -> onRequestMusicAccess()
                    else -> vm.refreshLibrary()
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 2.dp, bottom = 1.dp),
        )

        if (selectedTrack != null) {
            HomePlaybackConsole(
                track = selectedTrack,
                state = state,
                onOpenPlayer = onOpenPlayer,
                onPrevious = playPrevious,
                onPlayPause = playOrResume,
                onNext = playNext,
            )
        } else {
            HomeLibraryPrompt(
                hasPermission = state.hasPermission,
                loading = state.loading,
                onClick = { if (state.hasPermission) vm.refreshLibrary() else onRequestMusicAccess() },
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("PRIVATE AUDIO  ·  STORED ON THIS DEVICE", fontSize = 8.sp, letterSpacing = .9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.SatelliteAlt, "Music satellites in orbit", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PlanetaryMusicOrbit(
    tracks: List<Track>,
    selectedTrack: Track?,
    isPlaying: Boolean,
    onSelectTrack: (Track) -> Unit,
    onPlanetTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = remember(context) { context.findLifecycleOwner() }
    var reducedMotion by remember(context) { mutableStateOf(reducedMotionEnabled(context)) }
    val latestOnTrackTap by rememberUpdatedState(onSelectTrack)
    val latestOnPlanetTap by rememberUpdatedState(onPlanetTap)
    val currentIndex = remember(tracks, selectedTrack?.key) {
        tracks.indexOfFirst { it.key == selectedTrack?.key }.takeIf { it >= 0 } ?: 0
    }
    var useFallback by remember(context) { mutableStateOf(!supportsOpenGlEs2(context)) }
    var orbitView by remember { mutableStateOf<PlanetaryOrbitView?>(null) }
    var orbitReady by remember(context) { mutableStateOf(false) }

    if (useFallback) {
        Box(modifier) {
            FallbackPlanetaryMusicOrbit(tracks, selectedTrack, isPlaying, onSelectTrack, onPlanetTap, reducedMotion, Modifier.fillMaxSize())
            Text(
                "LITE ORBIT  ·  3D RENDERING UNAVAILABLE",
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 4.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xCC09121D)).padding(horizontal = 10.dp, vertical = 7.dp),
                color = Color(0xFFB7CDE0), fontSize = 8.sp, letterSpacing = .65.sp,
            )
        }
    } else {
        Box(modifier.semantics {
            contentDescription = buildString {
                append("3D music universe. ")
                append(selectedTrack?.let { "Selected ${it.safeTitle} by ${it.safeArtist}. " } ?: "No track selected. ")
                append("Drag to rotate the camera, pinch to zoom, and tap a satellite to play it.")
            }
        }) {
            AndroidView(
                factory = { viewContext ->
                    PlanetaryOrbitView(
                        viewContext,
                        onTrackTap = { latestOnTrackTap(it) },
                        onPlanetTap = { latestOnPlanetTap() },
                        onUnavailable = { useFallback = true },
                        onSceneReady = { orbitReady = true },
                    ).also { view ->
                        orbitView = view
                        view.updateScene(tracks, selectedTrack?.key, isPlaying, reducedMotion)
                    }
                },
                update = { view -> view.updateScene(tracks, selectedTrack?.key, isPlaying, reducedMotion) },
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                if (tracks.isEmpty()) "NO LOCAL SIGNALS" else "SECTOR  ${(currentIndex + 1).toString().padStart(2, '0')} / ${tracks.size}  ·  UP TO 24 SATELLITES",
                Modifier.align(Alignment.TopStart).padding(top = 11.dp, start = 5.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xCC09121D))
                    .border(1.dp, Color(0x337BE5FF), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                color = Color(0xFFB7CDE0), fontSize = 8.sp, letterSpacing = .75.sp, maxLines = 1,
            )
            if (tracks.isNotEmpty() && !orbitReady) {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 54.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color(0xCC09121D)).padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.5.dp, color = Color(0xFF7BE5FF))
                    Text("MAPPING MUSIC INTO ORBIT", Modifier.padding(start = 7.dp), color = Color(0xFFD9F4FF), fontSize = 8.sp, letterSpacing = .55.sp)
                }
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 3.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xCC09121D))
                    .border(1.dp, Color(0x447BE5FF), RoundedCornerShape(20.dp)).padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbitCameraButton("Zoom out", Icons.Rounded.ZoomOut) { orbitView?.zoomBy(1.2f) }
                OrbitCameraButton("Return to Earth", Icons.Rounded.MyLocation) { orbitView?.resetCamera() }
                OrbitCameraButton("Zoom in", Icons.Rounded.ZoomIn) { orbitView?.zoomBy(.83f) }
            }
            Text(
                "DRAG TO ROTATE  ·  PINCH TO ZOOM  ·  TAP A SATELLITE",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xB909121D))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                color = Color(0xFFB7CDE0), fontSize = 8.sp, letterSpacing = .8.sp, maxLines = 1,
            )
        }
        val liveView = orbitView
        if (liveView != null) {
            DisposableEffect(liveView, owner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            reducedMotion = reducedMotionEnabled(context)
                            liveView.onResume()
                        }
                        Lifecycle.Event.ON_PAUSE -> liveView.onPause()
                        else -> Unit
                    }
                }
                owner?.lifecycle?.addObserver(observer)
                if (owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) liveView.onResume()
                onDispose {
                    owner?.lifecycle?.removeObserver(observer)
                    liveView.onPause()
                    orbitView = null
                }
            }
        }
    }
}

@Composable
private fun OrbitCameraButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, tint = Color(0xFFD9F4FF), modifier = Modifier.size(19.dp))
    }
}

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

@Composable
private fun FallbackPlanetaryMusicOrbit(
    tracks: List<Track>,
    selectedTrack: Track?,
    isPlaying: Boolean,
    onSelectTrack: (Track) -> Unit,
    onPlanetTap: () -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.secondary
        val focusIndex = remember(tracks, selectedTrack?.key) {
            tracks.indexOfFirst { it.key == selectedTrack?.key }.takeIf { it >= 0 } ?: 0
        }
        val offsets = remember(tracks.size) {
            when (tracks.size) {
                0, 1 -> listOf(0)
                2 -> listOf(0, 1)
                3 -> listOf(-1, 0, 1)
                4 -> listOf(-2, -1, 0, 1)
                else -> (-2..2).toList()
            }
        }
        val visibleTracks = remember(tracks, offsets, focusIndex) {
            if (tracks.isEmpty()) emptyList() else offsets.mapNotNull { offset ->
                tracks.getOrNull((focusIndex + offset + tracks.size * 2) % tracks.size)?.let { track -> offset to track }
            }.distinctBy { it.second.key }
        }

        val orbitMotion = rememberInfiniteTransition(label = "song-orbit-motion")
        val phase by orbitMotion.animateFloat(
            initialValue = 0f,
            targetValue = if (reducedMotion) 0f else 360f,
            animationSpec = infiniteRepeatable(tween(88_000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "song-orbit-phase",
        )

        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            listOf(Triple(-27f, .425f, .40f), Triple(-15f, .335f, .355f), Triple(66f, .425f, .30f)).forEachIndexed { index, orbit ->
                val (tilt, widthFactor, heightFactor) = orbit
                val rx = size.width * widthFactor
                val ry = size.height * heightFactor
                rotate(tilt, center) {
                    drawOval(
                        color = if (index == 1) primary.copy(alpha = .40f) else secondary.copy(alpha = .20f),
                        topLeft = Offset(center.x - rx, center.y - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = if (index == 1) 1.25.dp.toPx() else .8.dp.toPx()),
                    )
                }
            }
            repeat(18) { index ->
                val angle = Math.toRadians((index * 41 + 13).toDouble())
                val x = center.x + cos(angle).toFloat() * size.width * .47f
                val y = center.y + sin(angle).toFloat() * size.height * .43f
                drawCircle(Color.White.copy(alpha = if (index % 4 == 0) .52f else .25f), if (index % 4 == 0) 1.5.dp.toPx() else .8.dp.toPx(), Offset(x, y))
            }
        }

        val planetSize = minOf(maxWidth * .43f, 164.dp)
        Box(
            Modifier.align(Alignment.Center).size(planetSize)
                .semantics {
                    contentDescription = when {
                        selectedTrack != null && isPlaying -> "Pause ${selectedTrack.safeTitle}"
                        selectedTrack != null -> "Play ${selectedTrack.safeTitle}"
                        else -> "Open local music library"
                    }
                    role = Role.Button
                }
                .clickable(onClick = onPlanetTap),
            contentAlignment = Alignment.Center,
        ) {
            EarthArtwork(Modifier.fillMaxSize())
            Icon(
                imageVector = if (selectedTrack != null && isPlaying) Icons.Rounded.Pause else if (selectedTrack != null) Icons.Rounded.PlayArrow else Icons.Rounded.Public,
                contentDescription = null,
                tint = Color.White.copy(alpha = .9f),
                modifier = Modifier.size(if (selectedTrack == null) 25.dp else 30.dp),
            )
        }

        val nodeWidth = 112.dp
        val nodeHeight = 50.dp
        visibleTracks.forEach { (slot, track) ->
            key(track.key) {
                val animatedSlot by animateFloatAsState(slot.toFloat(), tween(520), label = "satellite-slot-${track.id}")
                val angle = Math.toRadians((-90f + animatedSlot * 72f + phase).toDouble())
                val tilt = Math.toRadians(-15.0)
                val orbitX = maxWidth.value * .335f
                val orbitY = maxHeight.value * .355f
                val ellipseX = orbitX * cos(angle).toFloat()
                val ellipseY = orbitY * sin(angle).toFloat()
                val rotatedX = ellipseX * cos(tilt).toFloat() - ellipseY * sin(tilt).toFloat()
                val rotatedY = ellipseX * sin(tilt).toFloat() + ellipseY * cos(tilt).toFloat()
                val centerX = maxWidth.value / 2f + rotatedX
                val centerY = maxHeight.value / 2f + rotatedY
                val x = (centerX.dp - nodeWidth / 2).coerceIn(0.dp, (maxWidth - nodeWidth).coerceAtLeast(0.dp))
                val y = (centerY.dp - nodeHeight / 2).coerceIn(0.dp, (maxHeight - nodeHeight).coerceAtLeast(0.dp))
                OrbitSongSatellite(
                    track = track,
                    selected = track.key == selectedTrack?.key,
                    onClick = { onSelectTrack(track) },
                    modifier = Modifier.offset(x = x, y = y).zIndex(if (track.key == selectedTrack?.key) 1f else 0f),
                )
            }
        }

        if (tracks.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.Center).offset(y = (planetSize * .59f)).padding(horizontal = 12.dp),
            ) {
                Text(
                    if (isPlaying) "SIGNAL ACTIVE" else "${tracks.size.toString().padStart(2, '0')} SONGS DETECTED",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = primary,
                    fontSize = 8.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun EarthArtwork(modifier: Modifier = Modifier) {
    val glow = MaterialTheme.colorScheme.primary
    Canvas(modifier.clip(CircleShape)) {
        val radius = size.minDimension * .47f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(glow.copy(alpha = .13f), radius * 1.24f, center)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF79DDF4), Color(0xFF267BA5), Color(0xFF102B4C), Color(0xFF050B19)),
                center = Offset(center.x - radius * .28f, center.y - radius * .38f),
                radius = radius * 1.65f,
            ),
            radius = radius,
            center = center,
        )

        val globe = Path().apply { addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)) }
        clipPath(globe) {
            val land = Color(0xFF75B7A0)
            val northAmerica = Path().apply {
                moveTo(center.x - radius * .82f, center.y - radius * .24f)
                cubicTo(center.x - radius * .76f, center.y - radius * .56f, center.x - radius * .48f, center.y - radius * .69f, center.x - radius * .28f, center.y - radius * .43f)
                cubicTo(center.x - radius * .12f, center.y - radius * .30f, center.x - radius * .33f, center.y - radius * .15f, center.x - radius * .37f, center.y + radius * .03f)
                cubicTo(center.x - radius * .42f, center.y + radius * .22f, center.x - radius * .58f, center.y + radius * .11f, center.x - radius * .66f, center.y - radius * .02f)
                close()
            }
            val europeAfrica = Path().apply {
                moveTo(center.x - radius * .08f, center.y - radius * .52f)
                cubicTo(center.x + radius * .13f, center.y - radius * .65f, center.x + radius * .56f, center.y - radius * .52f, center.x + radius * .70f, center.y - radius * .29f)
                cubicTo(center.x + radius * .82f, center.y - radius * .08f, center.x + radius * .43f, center.y - radius * .08f, center.x + radius * .31f, center.y + radius * .06f)
                cubicTo(center.x + radius * .18f, center.y + radius * .20f, center.x + radius * .27f, center.y + radius * .40f, center.x + radius * .08f, center.y + radius * .61f)
                cubicTo(center.x - radius * .08f, center.y + radius * .51f, center.x - radius * .19f, center.y + radius * .27f, center.x - radius * .14f, center.y + radius * .07f)
                cubicTo(center.x - radius * .22f, center.y - radius * .08f, center.x - radius * .24f, center.y - radius * .31f, center.x - radius * .08f, center.y - radius * .52f)
                close()
            }
            val asia = Path().apply {
                moveTo(center.x + radius * .12f, center.y - radius * .49f)
                cubicTo(center.x + radius * .39f, center.y - radius * .76f, center.x + radius * .76f, center.y - radius * .55f, center.x + radius * .82f, center.y - radius * .34f)
                cubicTo(center.x + radius * .65f, center.y - radius * .20f, center.x + radius * .49f, center.y - radius * .33f, center.x + radius * .34f, center.y - radius * .24f)
                close()
            }
            drawPath(northAmerica, land.copy(alpha = .82f))
            drawPath(europeAfrica, Color(0xFF8BC1A4).copy(alpha = .8f))
            drawPath(asia, Color(0xFF69A995).copy(alpha = .72f))
            drawOval(Color(0xFFDAF8F1).copy(alpha = .18f), Offset(center.x - radius * .7f, center.y - radius * .67f), Size(radius * 1.0f, radius * .22f))
            val cloud = Path().apply {
                moveTo(center.x - radius * .68f, center.y + radius * .38f)
                cubicTo(center.x - radius * .37f, center.y + radius * .18f, center.x - radius * .08f, center.y + radius * .53f, center.x + radius * .38f, center.y + radius * .37f)
            }
            drawPath(cloud, Color.White.copy(alpha = .28f), style = Stroke(width = 1.dp.toPx()))
            rotate(-21f, center) {
                drawOval(Color.White.copy(alpha = .10f), Offset(center.x - radius * .52f, center.y - radius), Size(radius * 1.04f, radius * 2f), style = Stroke(width = .8.dp.toPx()))
                drawOval(Color.White.copy(alpha = .09f), Offset(center.x - radius, center.y - radius * .30f), Size(radius * 2f, radius * .60f), style = Stroke(width = .8.dp.toPx()))
            }
            drawCircle(
                Brush.linearGradient(listOf(Color(0xDD050916), Color.Transparent, Color(0x667CEEFF)), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y)),
                radius,
                center,
            )
        }
        drawCircle(Color(0xFF9DEAFF).copy(alpha = .65f), radius, center, style = Stroke(width = 1.dp.toPx()))
    }
}

@Composable
private fun OrbitSongSatellite(track: Track, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        color = if (selected) MaterialTheme.colorScheme.surface.copy(alpha = .98f) else MaterialTheme.colorScheme.surface.copy(alpha = .88f),
        shape = shape,
        modifier = modifier.width(112.dp).height(50.dp)
            .border(if (selected) 1.4.dp else .8.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .86f) else MaterialTheme.colorScheme.outline.copy(alpha = .72f), shape)
            .semantics {
                contentDescription = "${track.safeTitle} by ${track.safeArtist}${if (selected) ", current track" else ""}"
                role = Role.Button
            }
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumCover(track, 34.dp, Modifier.clip(CircleShape))
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(track.safeTitle, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.safeArtist, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HomePlaybackConsole(
    track: Track,
    state: MusicUiState,
    onOpenPlayer: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val progress = if (state.currentKey == track.key && state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(23.dp)) {
        Column {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
            Row(Modifier.height(76.dp).padding(start = 10.dp, end = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumCover(track, 48.dp, Modifier.clickable(onClick = onOpenPlayer))
                Column(Modifier.weight(1f).padding(start = 9.dp).clickable(onClick = onOpenPlayer)) {
                    Text(if (state.currentKey == track.key && state.isPlaying) "NOW IN ORBIT" else "SELECTED SIGNAL", fontSize = 8.sp, letterSpacing = 1.25.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    Text(track.safeTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.safeArtist, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onPrevious) { Icon(Icons.Rounded.SkipPrevious, "Previous track", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp)) }
                IconButton(onClick = onPlayPause) {
                    Icon(if (state.currentKey == track.key && state.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle, if (state.currentKey == track.key && state.isPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(31.dp))
                }
                IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Next track", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp)) }
            }
        }
    }
}

@Composable
private fun HomeLibraryPrompt(hasPermission: Boolean, loading: Boolean, onClick: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth().height(76.dp).clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(horizontal = 15.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (hasPermission) Icons.Rounded.Refresh else Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(if (loading) "Tuning the local frequencies…" else if (hasPermission) "Scan this world" else "Connect your music world", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(if (hasPermission) "Find audio already stored on your phone" else "Allow audio access to place songs in orbit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Rounded.ArrowForward, "Open", tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun LibraryScreen(state: MusicUiState, vm: MusicViewModel, onRequestMusicAccess: () -> Unit, onOpenPlayer: () -> Unit, onAdd: (Track) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Signal atlas", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("${state.tracks.size} tracks held locally", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SortMenu(state.librarySort, vm::setLibrarySort)
            IconButton(onClick = { vm.setLibraryPresentation(if (state.libraryPresentation == LibraryPresentation.ORBIT) LibraryPresentation.LIST else LibraryPresentation.ORBIT) }) {
                Icon(if (state.libraryPresentation == LibraryPresentation.ORBIT) Icons.Rounded.ViewList else Icons.Rounded.Dashboard, if (state.libraryPresentation == LibraryPresentation.ORBIT) "Switch to list layout" else "Switch to orbit layout", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            LibraryTab.entries.forEach { tab ->
                val selected = state.libraryTab == tab
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else MaterialTheme.colorScheme.surface.copy(alpha = .65f),
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).border(if (selected) 1.dp else .5.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .42f) else MaterialTheme.colorScheme.outline.copy(alpha = .5f), RoundedCornerShape(50.dp)).clickable { vm.selectLibraryTab(tab) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 10.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
        if (!state.hasPermission) {
            AccessCard(onRequestMusicAccess)
            Spacer(Modifier.weight(1f))
            return@Column
        }
        if (state.loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            return@Column
        }
        if (state.libraryTab == LibraryTab.ALBUMS || state.libraryTab == LibraryTab.ARTISTS) {
            val groups = state.tracks.groupBy { if (state.libraryTab == LibraryTab.ALBUMS) it.safeAlbum else it.safeArtist }.toList()
            val sortedGroups = when (state.librarySort) {
                LibrarySort.TITLE -> groups.sortedBy { it.first.lowercase() }
                LibrarySort.ARTIST -> groups.sortedBy { it.second.firstOrNull()?.safeArtist?.lowercase().orEmpty() }
                LibrarySort.RECENT -> groups.sortedByDescending { group -> group.second.maxOfOrNull { it.dateAddedSeconds } ?: 0L }
            }
            if (sortedGroups.isEmpty()) {
                EmptyHint("No signals mapped", "Albums and artists appear after music access is granted.", Modifier.weight(1f))
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val cardWidth = maxWidth * .78f
                    LazyRow(
                        Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(sortedGroups, key = { it.first }) { (name, tracks) ->
                            val first = tracks.first()
                            GlassPanel(Modifier.width(cardWidth).fillMaxHeight().padding(vertical = 5.dp), shape = RoundedCornerShape(30.dp), contentPadding = PaddingValues(18.dp)) {
                                Column(Modifier.fillMaxSize()) {
                                    Text(if (state.libraryTab == LibraryTab.ALBUMS) "ALBUM SIGNAL" else "ARTIST SIGNAL", fontSize = 9.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { AlbumCover(first, minOf(cardWidth - 46.dp, 210.dp)) }
                                    Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${tracks.size} tracks · ${first.safeArtist}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    FilledTonalButton(onClick = { vm.play(first, tracks); onOpenPlayer() }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Enter this orbit")
                                    }
                                }
                            }
                        }
                    }
                }
                Text("SWIPE TO TRAVEL  ·  ${sortedGroups.size} ${if (state.libraryTab == LibraryTab.ALBUMS) "ALBUMS" else "ARTISTS"}", fontSize = 9.sp, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp))
            }
        } else {
            val filtered = if (state.libraryTab == LibraryTab.FAVORITES) state.tracks.filter { it.key in state.favorites } else state.tracks
            val songs = when (state.librarySort) {
                LibrarySort.TITLE -> filtered.sortedBy { it.safeTitle.lowercase() }
                LibrarySort.ARTIST -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { track: Track -> track.safeArtist }.thenBy { it.safeTitle })
                LibrarySort.RECENT -> filtered.sortedByDescending { it.dateAddedSeconds }
            }
            if (state.libraryPresentation == LibraryPresentation.ORBIT) {
                if (songs.isEmpty()) EmptyHint("No signals in this sector", "Scan for music or switch to another constellation.", Modifier.weight(1f))
                else OrbitTrackDeck(songs, state, vm, onOpenPlayer, onAdd, Modifier.weight(1f))
            } else {
                SongList(songs, state, vm, onOpenPlayer, onAdd, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OrbitTrackDeck(tracks: List<Track>, state: MusicUiState, vm: MusicViewModel, onOpenPlayer: () -> Unit, onAdd: (Track) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cardWidth = maxWidth * .82f
            LazyRow(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(tracks, key = { _, track -> track.key }) { index, track ->
                    GlassPanel(
                        Modifier.width(cardWidth).fillMaxHeight().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(30.dp),
                    ) {
                        BoxWithConstraints(Modifier.fillMaxSize().padding(17.dp)) {
                        val coverSize = minOf(maxWidth - 38.dp, 210.dp)
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("TRACK  /  ${(index + 1).toString().padStart(3, '0')}", Modifier.weight(1f), fontSize = 9.sp, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { vm.toggleFavorite(track) }) {
                                    Icon(if (track.key in state.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (track.key in state.favorites) "Remove from favorites" else "Add to favorites", tint = if (track.key in state.favorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                AlbumCover(track, coverSize, Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .2f), RoundedCornerShape(26.dp)))
                            }
                            Text(track.safeTitle, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.safeArtist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(onClick = { vm.play(track, tracks); onOpenPlayer() }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Tune in")
                                }
                                IconButton(onClick = { onAdd(track) }) { Icon(Icons.Rounded.AddToQueue, "Add ${track.safeTitle} to a playlist", tint = MaterialTheme.colorScheme.secondary) }
                            }
                        }
                    }
                }
            }
            }
        }
        Text("SWIPE HORIZONTALLY TO TRAVEL  ·  ${tracks.size} TRACKS", fontSize = 9.sp, letterSpacing = 1.15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 5.dp))
    }
}

@Composable
private fun SearchScreen(state: MusicUiState, vm: MusicViewModel, onRequestMusicAccess: () -> Unit, onOpenPlayer: () -> Unit, onAdd: (Track) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Find your sound", fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 14.dp))
        OutlinedTextField(
            value = state.query, onValueChange = vm::setQuery, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Songs, artists, albums") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") } },
            shape = RoundedCornerShape(SpaceDesign.controlRadius), colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .75f),
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
            ),
        )
        Spacer(Modifier.height(10.dp))
        val query = state.query.trim()
        val results = if (query.isBlank()) emptyList() else state.tracks.filter {
            it.safeTitle.contains(query, true) || it.safeArtist.contains(query, true) || it.safeAlbum.contains(query, true)
        }
        if (!state.hasPermission) AccessCard(onRequestMusicAccess)
        else if (query.isBlank()) EmptyHint("Search your library", "Try a song, album or artist name.", Modifier.weight(1f))
        else if (results.isEmpty()) EmptyHint("No matches", "Try another spelling or search term.", Modifier.weight(1f))
        else SongList(results, state, vm, onOpenPlayer, onAdd, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PlaylistScreen(state: MusicUiState, vm: MusicViewModel, onCreate: () -> Unit, onOpenPlayer: () -> Unit, onAdd: (Track) -> Unit, onDelete: (String) -> Unit) {
    val selected = state.selectedPlaylist
    if (selected == null) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Your playlists", fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("A soundtrack for every side of you", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FilledIconButton(onClick = onCreate, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Icon(Icons.Rounded.Add, "Create playlist") }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
                if (state.playlists.isEmpty()) item { EmptyHint("Start a collection", "Create a playlist, then add songs from your library.") }
                items(state.playlists.keys.toList(), key = { it }) { name ->
                    val keys = state.playlists[name].orEmpty()
                    GlassPanel(Modifier.fillMaxWidth().clickable { vm.openPlaylist(name) }, shape = RoundedCornerShape(SpaceDesign.controlRadius), contentPadding = PaddingValues(15.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .14f), shape = RoundedCornerShape(13.dp), modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary) } }
                            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) { Text(name, fontWeight = FontWeight.SemiBold); Text("${keys.size} songs", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                            Icon(Icons.Rounded.ChevronRight, "Open $name", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.openPlaylist(null) }) { Icon(Icons.Rounded.ArrowBack, "Back to playlists") }
                Column(Modifier.weight(1f)) { Text(selected, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${state.playlists[selected].orEmpty().size} songs", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                IconButton(onClick = { onDelete(selected) }) { Icon(Icons.Rounded.DeleteOutline, "Delete playlist", tint = Color(0xFFE8968F)) }
            }
            val playlistTracks = state.playlists[selected].orEmpty().mapNotNull { key -> state.tracks.firstOrNull { it.key == key } }
            if (playlistTracks.isEmpty()) EmptyHint("This playlist is waiting", "Add songs from your library using the more menu.", Modifier.weight(1f))
            else {
                FilledTonalButton(onClick = { vm.play(playlistTracks.first(), playlistTracks); onOpenPlayer() }, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .14f), contentColor = MaterialTheme.colorScheme.primary)) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play playlist")
                }
                SongList(playlistTracks, state, vm, onOpenPlayer, onAdd, modifier = Modifier.weight(1f), playlistName = selected)
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: MusicUiState, vm: MusicViewModel, onRequestMusicAccess: () -> Unit) {
    var timerMenu by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { Column(Modifier.padding(top = 7.dp, bottom = 4.dp)) { Text("Ship systems", fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("Tune the way Afterglow moves with you.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item {
            SettingRow(Icons.Rounded.Refresh, "Refresh music library", "Scan audio files on this device", trailing = {
                IconButton(onClick = { if (state.hasPermission) vm.refreshLibrary() else onRequestMusicAccess() }) { Icon(Icons.Rounded.Refresh, "Refresh library", tint = MaterialTheme.colorScheme.primary) }
            })
        }
        item {
            SettingRow(if (state.darkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, "Deep-space appearance", if (state.darkTheme) "Dark surfaces are active" else "Light surfaces are active", trailing = {
                Switch(checked = state.darkTheme, onCheckedChange = { vm.toggleTheme() }, colors = SwitchDefaults.colors(checkedThumbColor = Night, checkedTrackColor = MaterialTheme.colorScheme.primary))
            })
        }
        item {
            SettingsPanel("Signal color", "Choose the light emitted by the interface.") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    AccentTheme.entries.forEach { accent ->
                        val selected = state.accentTheme == accent
                        val swatch = when (accent) { AccentTheme.AURORA -> Color(0xFF8DFFD7); AccentTheme.ION -> Color(0xFF7BE5FF); AccentTheme.PULSAR -> Color(0xFFC4A8FF) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(color = swatch, shape = CircleShape, modifier = Modifier.size(48.dp).border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else swatch.copy(alpha = .35f), CircleShape).clickable { vm.setAccentTheme(accent) }) {
                                Box(contentAlignment = Alignment.Center) { if (selected) Icon(Icons.Rounded.Check, "Selected", tint = Color(0xFF10151A), modifier = Modifier.size(18.dp)) }
                            }
                            Text(accent.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 10.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }
        }
        item {
            SettingsPanel("Library navigation", "Choose a browsing motion and ordering.") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LayoutChoice("Orbit deck", Icons.Rounded.ViewCarousel, state.libraryPresentation == LibraryPresentation.ORBIT, Modifier.weight(1f)) { vm.setLibraryPresentation(LibraryPresentation.ORBIT) }
                    LayoutChoice("Track list", Icons.Rounded.ViewList, state.libraryPresentation == LibraryPresentation.LIST, Modifier.weight(1f)) { vm.setLibraryPresentation(LibraryPresentation.LIST) }
                }
                Spacer(Modifier.height(12.dp))
                Text("SORT SIGNALS", fontSize = 9.sp, letterSpacing = 1.3.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LibrarySort.entries.forEach { sort ->
                        val selected = state.librarySort == sort
                        FilterChip(selected = selected, onClick = { vm.setLibrarySort(sort) }, label = { Text(when (sort) { LibrarySort.TITLE -> "Title"; LibrarySort.ARTIST -> "Artist"; LibrarySort.RECENT -> "New" }, fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            SettingsPanel("Sleep trajectory", "Pause audio after a set interval.") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.sleepTimerSeconds?.let { "${formatDuration(it * 1_000)} remaining" } ?: "Timer is dormant", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Works with playback in the background", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        OutlinedButton(onClick = { timerMenu = true }) { Text("Set timer") }
                        DropdownMenu(expanded = timerMenu, onDismissRequest = { timerMenu = false }) {
                            listOf(15, 30, 45, 60, 90).forEach { minutes -> DropdownMenuItem(text = { Text("$minutes minutes") }, onClick = { vm.setSleepTimer(minutes); timerMenu = false }) }
                            if (state.sleepTimerSeconds != null) DropdownMenuItem(text = { Text("Cancel timer") }, onClick = { vm.setSleepTimer(null); timerMenu = false })
                        }
                    }
                }
            }
        }
        item {
            SettingsPanel("Listening history", "Your recently played row stays on this device.") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.recentKeys.size} recent tracks", Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { confirmClearHistory = true }, enabled = state.recentKeys.isNotEmpty()) { Text("Clear history") }
                }
            }
        }
        item {
            SettingsPanel("Private by design", null) {
                Text("Afterglow plays audio already on your device. It has no account, streaming service, analytics or network permission. Your favorites and playlists stay in this app's private storage.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        item { Text("PROTOTYPE 01  ·  LOCAL MUSIC PLAYER", fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp)) }
    }
    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear recent listening?") },
            text = { Text("This removes Afterglow's local recently played list. Your music files, favorites, and playlists will stay untouched.") },
            confirmButton = { TextButton(onClick = { vm.clearRecentlyPlayed(); confirmClearHistory = false }) { Text("Clear history") } },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("Keep history") } },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun SettingsPanel(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(SpaceDesign.cardRadius), contentPadding = PaddingValues(SpaceDesign.panelPadding)) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
            else Spacer(Modifier.height(9.dp))
            content()
        }
    }
}

@Composable
private fun LayoutChoice(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(58.dp).border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .6f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(label, fontSize = 11.sp, modifier = Modifier.padding(start = 7.dp))
        }
    }
}

@Composable
private fun SortMenu(sort: LibrarySort, onSelect: (LibrarySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.Sort, "Sort library", tint = MaterialTheme.colorScheme.secondary) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(when (option) { LibrarySort.TITLE -> "Title"; LibrarySort.ARTIST -> "Artist"; LibrarySort.RECENT -> "Recently added" }) },
                    leadingIcon = { if (sort == option) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SongList(tracks: List<Track>, state: MusicUiState, vm: MusicViewModel, onOpenPlayer: () -> Unit, onAdd: (Track) -> Unit, modifier: Modifier = Modifier, playlistName: String? = null) {
    if (tracks.isEmpty()) {
        EmptyHint(if (playlistName != null) "No songs in this playlist" else "No songs to show", if (playlistName != null) "Add tracks from your library with the more menu." else "Scan your device for music, or change this filter.", modifier)
    } else {
        LazyColumn(modifier, contentPadding = PaddingValues(top = 5.dp, bottom = 13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(tracks, key = { it.key }) { track -> SongRow(track, state, vm, onOpenPlayer, onAdd, playlistName) }
        }
    }
}

@Composable
private fun SongRow(track: Track, state: MusicUiState, vm: MusicViewModel, onOpenPlayer: () -> Unit, onAdd: (Track) -> Unit, playlistName: String? = null) {
    val isCurrent = state.currentKey == track.key
    var focused by remember(track.key) { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(SpaceDesign.controlRadius)
    Row(
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.clip(rowShape)
            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = .10f) else if (focused) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else MaterialTheme.colorScheme.surface.copy(alpha = .32f))
            .border(if (focused) 1.5.dp else 1.dp, if (focused) MaterialTheme.colorScheme.secondary.copy(alpha = .9f) else if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = .35f) else MaterialTheme.colorScheme.outline.copy(alpha = .12f), rowShape)
            .clickable {
            val queue = if (playlistName != null) state.playlists[playlistName].orEmpty().mapNotNull { key -> state.tracks.firstOrNull { it.key == key } } else state.tracks
            vm.play(track, queue); onOpenPlayer()
        }.padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumCover(track, 48.dp)
        Column(Modifier.weight(1f).padding(start = 11.dp, end = 4.dp)) {
            Text(track.safeTitle, fontSize = 14.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.safeArtist}  ·  ${formatDuration(track.durationMs)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { vm.toggleFavorite(track) }) {
            Icon(if (track.key in state.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (track.key in state.favorites) "Remove from favorites" else "Add to favorites", tint = if (track.key in state.favorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More for ${track.safeTitle}", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (playlistName != null) {
                    DropdownMenuItem(text = { Text("Remove from playlist") }, leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) }, onClick = { vm.removeFromPlaylist(playlistName, track); menu = false })
                } else {
                    DropdownMenuItem(text = { Text("Add to playlist") }, leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) }, onClick = { onAdd(track); menu = false })
                }
            }
        }
    }
}

@Composable
private fun AlbumCover(track: Track, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val artworkUri = remember(track.albumId) { Uri.parse("content://media/external/audio/albumart/${track.albumId}") }
    val bitmap by produceState<Bitmap?>(null, artworkUri) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(artworkUri)?.use(BitmapFactory::decodeStream) }.getOrNull()
        }
    }
    Surface(shape = RoundedCornerShape(SpaceDesign.compactRadius), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.size(size)) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = "Album artwork for ${track.safeAlbum}", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .44f), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.secondary.copy(alpha = .18f)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, "Album artwork", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(size * .4f))
        }
    }
}

@Composable
private fun MiniPlayer(track: Track, state: MusicUiState, onClick: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 8.dp).clickable(onClick = onClick), shape = RoundedCornerShape(SpaceDesign.controlRadius)) {
        Column(Modifier.fillMaxWidth()) {
            val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
            Row(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumCover(track, 42.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(track.safeTitle, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.safeArtist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onPlayPause) { Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Next track", tint = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}

@Composable
private fun PlayerDialog(track: Track, state: MusicUiState, vm: MusicViewModel, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion = remember(context) { reducedMotionEnabled(context) }
    val coverScale by animateFloatAsState(
        targetValue = if (state.isPlaying && !reduceMotion) 1f else .97f,
        animationSpec = tween(if (reduceMotion) 0 else 420),
        label = "cover-breathe",
    )
    Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SpaceBackdrop(state.darkTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
            val coverSize = minOf(maxWidth - 64.dp, maxHeight * .38f, 310.dp).coerceAtLeast(120.dp)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NOW PLAYING", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Text(track.safeAlbum, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { vm.toggleFavorite(track) }) { Icon(if (track.key in state.favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Toggle favorite", tint = if (track.key in state.favorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Box(Modifier.fillMaxWidth().height(coverSize + 34.dp).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(coverSize + 42.dp).clip(CircleShape).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .22f), MaterialTheme.colorScheme.secondary.copy(alpha = .08f), Color.Transparent))))
                    AlbumCover(
                        track,
                        coverSize,
                        Modifier.scale(coverScale)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .38f), RoundedCornerShape(SpaceDesign.cardRadius)),
                    )
                }
                GlassPanel(Modifier.fillMaxWidth().padding(bottom = 4.dp), shape = RoundedCornerShape(SpaceDesign.sheetRadius), contentPadding = PaddingValues(horizontal = 17.dp, vertical = 10.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(track.safeTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.safeArtist, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Slider(
                            value = if (state.durationMs > 0) state.positionMs.coerceIn(0, state.durationMs).toFloat() else 0f,
                            onValueChange = { vm.seekTo(it.toLong()) }, valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDuration(state.positionMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("-${formatDuration((state.durationMs - state.positionMs).coerceAtLeast(0))}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Rounded.Shuffle, "Toggle shuffle", tint = if (state.shuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                            IconButton(onClick = vm::previous) { Icon(Icons.Rounded.SkipPrevious, "Previous track", modifier = Modifier.size(30.dp)) }
                            FilledIconButton(onClick = vm::togglePlayPause, modifier = Modifier.size(58.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play", modifier = Modifier.size(32.dp))
                            }
                            IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, "Next track", modifier = Modifier.size(30.dp)) }
                            IconButton(onClick = vm::cycleRepeat) { Icon(if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Cycle repeat", tint = if (state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDismiss(); onCancel() },
        title = { Text("Create a playlist") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(48) }, label = { Text("Playlist name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = { onDismiss(); onCancel() }) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
private fun AddToPlaylistDialog(track: Track, playlists: List<String>, onDismiss: () -> Unit, onChoose: (String) -> Unit, onCreate: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                Text(track.safeTitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                if (playlists.isEmpty()) Text("Create a playlist first, then come back to add this song.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                playlists.forEach { name -> TextButton(onClick = { onChoose(name) }, modifier = Modifier.fillMaxWidth()) { Text(name, Modifier.weight(1f)); Icon(Icons.Rounded.ChevronRight, null) } }
            }
        },
        confirmButton = { TextButton(onClick = onCreate) { Text("New playlist") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
private fun AccessCard(onRequest: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(SpaceDesign.cardRadius), contentPadding = PaddingValues(18.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Headphones, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(9.dp))
            Text("Let your music in", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Allow audio access to find songs stored on your phone. Afterglow does not upload or share your library.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(13.dp))
            Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Allow music access") }
        }
    }
}

@Composable
private fun EmptyLibrary(onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyHint("Your library is quiet", "Add audio files to your device, then scan again.")
        TextButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(5.dp)); Text("Scan again") }
    }
}

@Composable
private fun EmptyHint(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape, modifier = Modifier.size(64.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) } }
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ErrorBanner(text: String) {
    Surface(color = Color(0xFF35262D), shape = RoundedCornerShape(SpaceDesign.compactRadius), modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp).border(1.dp, Color(0x66FF9B9B), RoundedCornerShape(SpaceDesign.compactRadius))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, tint = Color(0xFFF0AAA3), modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp)); Text(text, Modifier.weight(1f), fontSize = 12.sp, color = Color(0xFFFFDAD6), lineHeight = 17.sp)
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(trailing, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, trailing: @Composable () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(SpaceDesign.controlRadius), contentPadding = PaddingValues(start = 14.dp, end = 7.dp, top = 8.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
            trailing()
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000).coerceAtLeast(0)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
