package br.com.ne3d.spbshellmodern.shell3d.widgets.music

import br.com.ne3d.spbshellmodern.shell3d.widgets.data.LatestWidgetDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import java.util.concurrent.atomic.AtomicLong

data class MusicSnapshot(override val revision: Long, val playing: Boolean, val title: String, val artist: String, val durationMillis: Long, val positionMillis: Long, val packageName: String) : WidgetSnapshot
class MusicDataSource : LatestWidgetDataSource<MusicSnapshot>() {
    private val revision=AtomicLong(); private var last: MusicSnapshot?=null
    fun publishState(playing:Boolean, title:String="", artist:String="", durationMillis:Long=0, positionMillis:Long=0, packageName:String="") : Boolean {
        val candidate=MusicSnapshot(0,playing,title,artist,durationMillis.coerceAtLeast(0),positionMillis.coerceAtLeast(0),packageName)
        if(last?.copy(revision=0)==candidate)return false
        val snapshot=candidate.copy(revision=revision.incrementAndGet()); last=snapshot; publish(snapshot); return true
    }
}
