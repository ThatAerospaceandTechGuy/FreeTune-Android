package com.freetune.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.freetune.app.FreeTuneApp
import com.freetune.app.ui.components.AddToPlaylistDialog
import com.freetune.app.ui.components.MiniPlayer
import com.freetune.app.ui.components.PlaylistDownloadDialog
import com.freetune.app.ui.screens.HomeScreen
import com.freetune.app.ui.screens.LibraryScreen
import com.freetune.app.ui.screens.PlayerScreen
import com.freetune.app.ui.screens.PlaylistScreen
import com.freetune.app.ui.screens.SearchScreen
import com.freetune.app.ui.screens.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val PLAYLIST = "playlist/{playlistId}"

    fun playlist(id: Long) = "playlist/$id"
}

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun FreeTuneRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as FreeTuneApp
    val factory = remember(app.container) { MainViewModelFactory(app.container) }
    val vm: MainViewModel = viewModel(factory = factory)
    val navController = rememberNavController()

    val playerState by vm.playerState.collectAsStateWithLifecycle()
    val pickerTrack by vm.playlistPicker.collectAsStateWithLifecycle()
    val downloadRequest by vm.playlistDownload.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val quality by vm.quality.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showChrome = route != Routes.PLAYER

    val tabs = remember {
        listOf(
            TabItem(Routes.HOME, "Home", Icons.Rounded.Home),
            TabItem(Routes.SEARCH, "Search", Icons.Rounded.Search),
            TabItem(Routes.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
            TabItem(Routes.SETTINGS, "Settings", Icons.Rounded.Settings)
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showChrome) {
                Column {
                    MiniPlayer(
                        state = playerState,
                        onOpen = {
                            navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                        },
                        onTogglePlay = vm::togglePlayPause,
                        onNext = vm::next
                    )
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route ||
                                    (tab.route == Routes.LIBRARY &&
                                        route?.startsWith("playlist/") == true),
                                onClick = {
                                    if (tab.route == Routes.LIBRARY &&
                                        route?.startsWith("playlist/") == true
                                    ) {
                                        navController.popBackStack(
                                            Routes.LIBRARY,
                                            inclusive = false
                                        )
                                        return@NavigationBarItem
                                    }
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm = vm,
                    onOpenSearch = {
                        navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                    }
                )
            }
            composable(Routes.SEARCH) { SearchScreen(vm) }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    vm = vm,
                    onOpenPlaylist = { id ->
                        navController.navigate(Routes.playlist(id)) { launchSingleTop = true }
                    }
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen(vm) }
            composable(
                route = Routes.PLAYLIST,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) { entry ->
                val playlistId = entry.arguments?.getLong("playlistId") ?: -1L
                PlaylistScreen(
                    vm = vm,
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.PLAYER) {
                PlayerScreen(vm = vm, onBack = { navController.popBackStack() })
            }
        }
    }

    pickerTrack?.let { track ->
        AddToPlaylistDialog(
            track = track,
            playlists = playlists,
            onDismiss = vm::dismissPlaylistPicker,
            onPick = { playlist ->
                vm.addToPlaylist(track, playlist.id, playlist.name)
            },
            onCreateAndAdd = { name ->
                vm.createPlaylistAndAdd(name, track)
            }
        )
    }

    downloadRequest?.let { request ->
        PlaylistDownloadDialog(
            playlistName = request.playlist.name,
            trackCount = request.tracks.size,
            alreadyDownloaded = request.tracks.count { it.isDownloaded },
            initialQuality = quality,
            onDismiss = vm::dismissPlaylistDownload,
            onDownload = vm::confirmPlaylistDownload
        )
    }
}
