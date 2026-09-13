package br.com.ne3d.spbshellmodern.shell3d.widgets.music

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import br.com.ne3d.spbshellmodern.shell3d.widgets.system.ShellNotificationListener

class MusicSessionRepository(context: Context) {
 private val manager=context.getSystemService(MediaSessionManager::class.java); private val component=ComponentName(context,ShellNotificationListener::class.java); private var source:MusicDataSource?=null; private var activeController:MediaController?=null; private var started=false
 private val callback=object:MediaController.Callback(){override fun onMetadataChanged(metadata:MediaMetadata?){publish()};override fun onPlaybackStateChanged(state:PlaybackState?){publish()};override fun onSessionDestroyed(){setController(null)}}
 private val sessions=MediaSessionManager.OnActiveSessionsChangedListener{controllers->setController(controllers?.firstOrNull())}
 fun start(dataSource:MusicDataSource){source=dataSource;if(started)return;started=true;try{manager.addOnActiveSessionsChangedListener(sessions,component);setController(manager.getActiveSessions(component).firstOrNull())}catch(_:SecurityException){dataSource.publishState(false)}}
 fun stop(){if(!started)return;started=false;runCatching{manager.removeOnActiveSessionsChangedListener(sessions)};activeController?.unregisterCallback(callback);activeController=null;source=null}
 fun refresh(){if(started)try{setController(manager.getActiveSessions(component).firstOrNull())}catch(_:SecurityException){source?.publishState(false)}}
 private fun setController(controller:MediaController?){if(activeController?.sessionToken==controller?.sessionToken){publish();return};activeController?.unregisterCallback(callback);activeController=controller;controller?.registerCallback(callback);publish()}
 private fun publish(){val c=activeController;val metadata=c?.metadata;val state=c?.playbackState;source?.publishState(state?.state==PlaybackState.STATE_PLAYING,metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?:0,state?.position?:0,c?.packageName.orEmpty())}
 fun previous(){activeController?.playbackState?.takeIf{it.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS !=0L}?.let{activeController?.transportControls?.skipToPrevious()}}
 fun next(){activeController?.playbackState?.takeIf{it.actions and PlaybackState.ACTION_SKIP_TO_NEXT !=0L}?.let{activeController?.transportControls?.skipToNext()}}
 fun togglePlayPause(){val state=activeController?.playbackState?:return;val controls=activeController?.transportControls?:return;if(state.state==PlaybackState.STATE_PLAYING&&state.actions and PlaybackState.ACTION_PAUSE!=0L)controls.pause() else if(state.actions and PlaybackState.ACTION_PLAY!=0L)controls.play()}
}
