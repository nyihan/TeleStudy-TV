package com.telestudy.tv

import android.app.Application
import timber.log.Timber

class TeleStudyApp : Application() {

    val clientManager: com.telestudy.tv.core.tdlib.TelegramClientManager by lazy {
        com.telestudy.tv.core.tdlib.TelegramClientManager(this)
    }

    val database: com.telestudy.tv.data.local.TeleStudyDatabase by lazy {
        com.telestudy.tv.data.local.TeleStudyDatabase.getInstance(this)
    }

    val remoteDataSource: com.telestudy.tv.data.remote.TelegramRemoteDataSource by lazy {
        com.telestudy.tv.data.remote.TelegramRemoteDataSource(clientManager)
    }

    val playbackProgressDao: com.telestudy.tv.data.local.dao.PlaybackProgressDao by lazy {
        database.playbackProgressDao()
    }

    val mediaRepository: com.telestudy.tv.data.repository.TelegramMediaRepository by lazy {
        com.telestudy.tv.data.repository.TelegramMediaRepository(
            remoteDataSource = remoteDataSource,
            chatDao = database.chatDao(),
            videoDao = database.videoDao(),
            playbackProgressDao = playbackProgressDao
        )
    }

    val thumbnailManager: com.telestudy.tv.data.thumbnail.TelegramThumbnailManager by lazy {
        com.telestudy.tv.data.thumbnail.TelegramThumbnailManager(
            clientManager = clientManager,
            videoDao = database.videoDao()
        )
    }

    val syncManager: com.telestudy.tv.data.sync.SyncManager by lazy {
        com.telestudy.tv.data.sync.SyncManager(
            context = this,
            clientManager = clientManager,
            remoteDataSource = remoteDataSource,
            chatDao = database.chatDao(),
            videoDao = database.videoDao(),
            thumbnailManager = thumbnailManager
        )
    }

    val updateManager: com.telestudy.tv.core.update.AppUpdateManager by lazy {
        com.telestudy.tv.core.update.AppUpdateManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("TeleStudyApp initialized. Debug mode: %s", BuildConfig.DEBUG)
    }
}
