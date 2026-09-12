package com.telestudy.tv.core.player

import androidx.media3.datasource.DataSource
import com.telestudy.tv.core.tdlib.TdlibFileManager

/**
 * Media3 DataSource.Factory implementation that produces TelegramDataSource instances.
 */
class TelegramDataSourceFactory(
    private val tdlibFileManager: TdlibFileManager
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return TelegramDataSource(tdlibFileManager)
    }
}
