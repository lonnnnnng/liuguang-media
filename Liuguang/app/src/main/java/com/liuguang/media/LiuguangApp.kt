package com.liuguang.media

import android.app.Application
import com.liuguang.media.data.repository.AutoSourceCheckScheduler
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltAndroidApp
class LiuguangApp : Application() {
    @Inject
    lateinit var autoSourceCheckScheduler: Lazy<AutoSourceCheckScheduler>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            delay(AUTO_SOURCE_CHECK_START_DELAY_MS)
            autoSourceCheckScheduler.get().start()
        }
    }

    private companion object {
        const val AUTO_SOURCE_CHECK_START_DELAY_MS = 5_000L
    }
}
