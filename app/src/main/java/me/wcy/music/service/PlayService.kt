package me.wcy.music.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.blankj.utilcode.util.IntentUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.wcy.music.R
import me.wcy.music.ext.registerReceiverCompat
import me.wcy.music.service.receiver.StatusBarReceiver
import me.wcy.music.storage.db.entity.SongEntity
import me.wcy.music.storage.preference.ConfigPreferences
import me.wcy.music.utils.MusicUtils
import top.wangchenyan.common.CommonApp
import top.wangchenyan.common.permission.Permissioner
import top.wangchenyan.common.utils.image.ImageUtils
import javax.inject.Inject

/**
 * 音乐播放后台服务
 * Created by wcy on 2015/11/27.
 */
@AndroidEntryPoint
class PlayService : Service() {
    private val notificationReceiver by lazy {
        StatusBarReceiver()
    }
    private var loadCoverJob: Job? = null
    private var isChannelCreated = false

    @Inject
    lateinit var audioPlayer: AudioPlayer

    inner class PlayBinder : Binder() {
        val service: PlayService
            get() = this@PlayService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: " + javaClass.simpleName)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return PlayBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY
        if (intent.action != null) {
            when (intent.action) {
                ACTION_SHOW_NOTIFICATION -> {
                    val isPlaying = intent.getBooleanExtra("is_playing", false)
                    val music = intent.getParcelableExtra<SongEntity>("music")
                    if (music != null) {
                        showNotification(isPlaying, music)
                    }
                }

                ACTION_CANCEL_NOTIFICATION -> {
                    cancelNotification()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelNotification()
        unregisterNotificationReceiver()
    }

    private fun showNotification(isPlaying: Boolean, song: SongEntity) {
        if (checkNotificationPermission()) {
            loadCoverJob?.cancel()
            startForeground(
                NOTIFICATION_ID,
                buildNotification(song, null, isPlaying)
            )
            loadCoverJob = CommonApp.appScope.launch {
                val bitmap = ImageUtils.loadBitmap(song.getSmallCover()).data
                if (bitmap != null) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(song, bitmap, isPlaying)
                    )
                }
            }
        }
    }

    private fun cancelNotification() {
        stopForeground(true)
    }

    private fun checkNotificationPermission(): Boolean {
        if (Permissioner.hasNotificationPermission(this)) {
            createNotificationChannel()
            return true
        }
        return false
    }

    private fun createNotificationChannel() {
        if (isChannelCreated.not() && Permissioner.hasNotificationPermission(this)) {
            isChannelCreated = true
            val name = "通知栏控制"
            val descriptionText = "通知栏控制"
            val importance = NotificationManagerCompat.IMPORTANCE_LOW
            val mChannel =
                NotificationChannelCompat.Builder(NOTIFICATION_ID.toString(), importance)
                    .setName(name)
                    .setDescription(descriptionText)
                    .setVibrationEnabled(false)
                    .build()
            NotificationManagerCompat.from(this)
                .createNotificationChannel(mChannel)
            registerNotificationReceiver()
        }
    }

    private fun registerNotificationReceiver() {
        registerReceiverCompat(
            notificationReceiver,
            IntentFilter(StatusBarReceiver.ACTION_STATUS_BAR),
        )
    }

    private fun unregisterNotificationReceiver() {
        if (isChannelCreated) {
            unregisterReceiver(notificationReceiver)
        }
    }

    private fun buildNotification(
        song: SongEntity,
        cover: Bitmap?,
        isPlaying: Boolean
    ): Notification {
        val intent = IntentUtils.getLaunchAppIntent(packageName)
        intent.putExtra(EXTRA_NOTIFICATION, true)
        intent.action = Intent.ACTION_VIEW
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        val builder = NotificationCompat.Builder(this, NOTIFICATION_ID.toString())
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_notification)
        if (ConfigPreferences.useCustomNotification) {
            builder.buildCustomNotification(song, cover, isPlaying)
        } else {
            builder.buildSystemNotification(song, cover, isPlaying)
        }
        return builder.build()
    }

    private fun NotificationCompat.Builder.buildSystemNotification(
        song: SongEntity,
        cover: Bitmap?,
        isPlaying: Boolean
    ) {
        this.setLargeIcon(
            cover ?: BitmapFactory.decodeResource(
                resources,
                R.drawable.ic_default_cover
            )
        )
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSubText(song.album)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
                    .setMediaSession(audioPlayer.getMediaSession())
            )
            .apply {
                if (isPlaying) {
                    addAction(
                        R.drawable.ic_notification_pause,
                        "暂停",
                        getActionIntent(REQUEST_CODE_PAUSE, StatusBarReceiver.EXTRA_PAUSE)
                    )
                } else {
                    addAction(
                        R.drawable.ic_notification_play,
                        "播放",
                        getActionIntent(REQUEST_CODE_PLAY, StatusBarReceiver.EXTRA_PLAY)
                    )
                }
            }
            .addAction(
                R.drawable.ic_notification_next,
                "下一曲",
                getActionIntent(REQUEST_CODE_NEXT, StatusBarReceiver.EXTRA_NEXT)
            )
    }

    private fun NotificationCompat.Builder.buildCustomNotification(
        song: SongEntity,
        cover: Bitmap?,
        isPlaying: Boolean
    ) {
        val title = song.title
        val subtitle = MusicUtils.getArtistAndAlbum(song.artist, song.album)
        val remoteViews = RemoteViews(packageName, R.layout.notification)
        if (cover != null) {
            remoteViews.setImageViewBitmap(R.id.iv_icon, cover)
        } else {
            remoteViews.setImageViewResource(R.id.iv_icon, R.drawable.ic_default_cover)
        }
        remoteViews.setTextViewText(R.id.tv_title, title)
        remoteViews.setTextViewText(R.id.tv_subtitle, subtitle)
        val playIconRes: Int
        val requestCode: Int
        val extra: String
        if (isPlaying) {
            playIconRes = R.drawable.ic_notification_pause
            requestCode = REQUEST_CODE_PAUSE
            extra = StatusBarReceiver.EXTRA_PAUSE
        } else {
            playIconRes = R.drawable.ic_notification_play
            requestCode = REQUEST_CODE_PLAY
            extra = StatusBarReceiver.EXTRA_PLAY
        }
        remoteViews.setImageViewResource(R.id.iv_play_pause, playIconRes)
        remoteViews.setOnClickPendingIntent(R.id.iv_play_pause, getActionIntent(requestCode, extra))
        remoteViews.setImageViewResource(R.id.iv_next, R.drawable.ic_notification_next)
        remoteViews.setOnClickPendingIntent(
            R.id.iv_next,
            getActionIntent(REQUEST_CODE_NEXT, StatusBarReceiver.EXTRA_NEXT)
        )
        this.setCustomContentView(remoteViews)
    }

    private fun getActionIntent(requestCode: Int, extra: String): PendingIntent {
        return PendingIntent.getBroadcast(
            this@PlayService,
            requestCode,
            Intent(StatusBarReceiver.ACTION_STATUS_BAR).apply {
                putExtra(StatusBarReceiver.EXTRA, extra)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_NOTIFICATION = "me.wcy.music.notification"

        private const val TAG = "Service"
        private const val NOTIFICATION_ID = 0x111
        private const val REQUEST_CODE_PLAY = 0
        private const val REQUEST_CODE_PAUSE = 1
        private const val REQUEST_CODE_NEXT = 2
        private const val ACTION_SHOW_NOTIFICATION = "me.wcy.music.ACTION_SHOW_NOTIFICATION"
        private const val ACTION_CANCEL_NOTIFICATION = "me.wcy.music.ACTION_CANCEL_NOTIFICATION"

        fun showNotification(context: Context, isPlaying: Boolean, music: SongEntity) {
            val intent = Intent(context, PlayService::class.java)
            intent.action = ACTION_SHOW_NOTIFICATION
            intent.putExtra("is_playing", isPlaying)
            intent.putExtra("music", music)
            context.startService(intent)
        }

        fun cancelNotification(context: Context) {
            val intent = Intent(context, PlayService::class.java)
            intent.action = ACTION_CANCEL_NOTIFICATION
            context.startService(intent)
        }
    }
}