package com.freetune.app.playback

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.freetune.app.FreeTuneApp
import com.freetune.app.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var likedIds: Set<String> = emptySet()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(FreeTuneApp.CHANNEL_PLAYBACK)
                .build()
        )
        val app = application as FreeTuneApp
        val manager = app.container.playerManager
        val repository = app.container.repository

        val session = MediaSession.Builder(this, manager.player)
            .setCallback(object : MediaSession.Callback {

                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    // Keep every default command and authorize the like button on top,
                    // so playback controls keep working for this controller.
                    val base = super.onConnect(session, controller)
                    val commands = base.availableSessionCommands
                        .buildUpon()
                        .add(SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY))
                        .build()
                    // The player holds one media item at a time, so Media3 would
                    // consider skip-to-next unavailable. The real queue lives in
                    // PlayerManager: advertise the commands and route them below.
                    val playerCommands = base.availablePlayerCommands
                        .buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .setAvailablePlayerCommands(playerCommands)
                        .setCustomLayout(notificationControls())
                        .build()
                }

                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    when (playerCommand) {
                        Player.COMMAND_SEEK_TO_NEXT -> {
                            manager.next()
                            return SessionResult.RESULT_SUCCESS
                        }
                        Player.COMMAND_SEEK_TO_PREVIOUS -> {
                            manager.previous()
                            return SessionResult.RESULT_SUCCESS
                        }
                    }
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == ACTION_TOGGLE_LIKE) {
                        val track = manager.state.value.current
                        if (track != null) {
                            scope.launch { repository.toggleLike(track) }
                        }
                        return Futures.immediateFuture(
                            SessionResult(SessionResult.RESULT_SUCCESS)
                        )
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()

        mediaSession = session
        // Registering the session also connects Media3's internal notification controller.
        // Without this the service never posts the media notification or enters the foreground.
        addSession(session)

        scope.launch {
            repository.liked.collect { tracks ->
                likedIds = tracks.mapTo(mutableSetOf()) { it.id }
                refreshControls()
            }
        }
        scope.launch {
            manager.state
                .map { it.current?.id }
                .distinctUntilChanged()
                .collect { refreshControls() }
        }
    }

    private fun refreshControls() {
        mediaSession?.setCustomLayout(notificationControls())
    }

    /**
     * Previous / next / like. Media3 keeps play-pause itself; the custom layout
     * previously replaced the whole row with the like button, which is why the
     * notification lost its skip controls.
     */
    private fun notificationControls(): List<CommandButton> = listOf(
        CommandButton.Builder()
            .setDisplayName("Previous")
            .setIconResId(R.drawable.ic_skip_previous)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .build(),
        CommandButton.Builder()
            .setDisplayName("Next")
            .setIconResId(R.drawable.ic_skip_next)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .build(),
        likeButton()
    )

    private fun likeButton(): CommandButton {
        val current = (application as FreeTuneApp)
            .container
            .playerManager
            .state
            .value
            .current
        val liked = current != null && likedIds.contains(current.id)
        return CommandButton.Builder()
            .setDisplayName(if (liked) "Unlike" else "Like")
            .setIconResId(if (liked) R.drawable.ic_like_filled else R.drawable.ic_like)
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null ||
            !player.playWhenReady ||
            player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val ACTION_TOGGLE_LIKE = "com.freetune.app.action.TOGGLE_LIKE"
    }
}
