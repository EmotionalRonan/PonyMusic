package me.wcy.music.service

import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.StateFlow
import me.wcy.music.storage.db.entity.SongEntity

/**
 * Created by wangchenyan.top on 2023/9/18.
 */
interface AudioPlayer {

    val playlist: LiveData<List<SongEntity>>
    val currentSong: LiveData<SongEntity?>
    val playState: StateFlow<PlayState>
    val playProgress: StateFlow<Long>
    val bufferingPercent: StateFlow<Int>
    val playMode: StateFlow<PlayMode>

    @MainThread
    fun addAndPlay(song: SongEntity)

    @MainThread
    fun replaceAll(songList: List<SongEntity>, song: SongEntity)

    @MainThread
    fun play(song: SongEntity?)

    @MainThread
    fun delete(song: SongEntity)

    @MainThread
    fun clearPlaylist()

    @MainThread
    fun playPause()

    @MainThread
    fun startPlayer()

    @MainThread
    fun pausePlayer(abandonAudioFocus: Boolean = true)

    @MainThread
    fun stopPlayer()

    @MainThread
    fun next()

    @MainThread
    fun prev()

    @MainThread
    fun seekTo(msec: Int)

    @MainThread
    fun setVolume(leftVolume: Float, rightVolume: Float)

    @MainThread
    fun getAudioPosition(): Long

    @MainThread
    fun getAudioSessionId(): Int

    fun getMediaSession(): MediaSessionCompat.Token

    @MainThread
    fun setPlayMode(mode: PlayMode)
}