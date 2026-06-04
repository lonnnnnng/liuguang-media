package com.liuguang.media.di

import android.content.Context
import androidx.room.Room
import com.liuguang.media.data.local.AppDatabase
import com.liuguang.media.data.local.dao.HistoryDao
import com.liuguang.media.data.local.dao.LiveSourceDao
import com.liuguang.media.data.local.dao.PodcastSubscriptionDao
import com.liuguang.media.data.local.dao.RadioSourceDao
import com.liuguang.media.data.local.dao.VideoSiteDao
import com.liuguang.media.data.remote.VodApiService
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "zy_player.db"
        )
            .addCallback(AppDatabase.Callback())
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideVideoSiteDao(database: AppDatabase): VideoSiteDao {
        return database.videoSiteDao()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(database: AppDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    @Singleton
    fun provideLiveSourceDao(database: AppDatabase): LiveSourceDao {
        return database.liveSourceDao()
    }

    @Provides
    @Singleton
    fun provideRadioSourceDao(database: AppDatabase): RadioSourceDao {
        return database.radioSourceDao()
    }

    @Provides
    @Singleton
    fun providePodcastSubscriptionDao(database: AppDatabase): PodcastSubscriptionDao {
        return database.podcastSubscriptionDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://placeholder.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideVodApiService(retrofit: Retrofit): VodApiService {
        return retrofit.create(VodApiService::class.java)
    }
}
