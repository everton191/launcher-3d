package br.com.ne3d.spbshellmodern.shell3d.widgets.music

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager

/** Reads the system's active media session outside the GL runtime. */
class MusicSessionRepository(context: Context) {
    private val manager=context.getSystemService(MediaSessionManager::class.java)
    fun publishActive(source: MusicDataSource): Boolean {
        val controller=try { manager.getActiveSessions(null).firstOrNull() } catch (_: SecurityException) { null }
        val metadata=controller?.metadata; val state=controller?.playbackState
        return source.publishState(
            playing=state?.state==android.media.session.PlaybackState.STATE_PLAYING,
            title=metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist=metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            durationMillis=metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)?:0L,
            positionMillis=state?.position?:0L,
            packageName=controller?.packageName.orEmpty()
        )
    }
    fun transport(): MediaController.TransportControls? = try { manager.getActiveSessions(null).firstOrNull()?.transportControls } catch (_: SecurityException) { null }
}
