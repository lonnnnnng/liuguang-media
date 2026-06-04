package com.liuguang.media.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.liuguang.media.data.local.dao.HistoryDao
import com.liuguang.media.data.local.dao.LiveSourceDao
import com.liuguang.media.data.local.dao.PodcastSubscriptionDao
import com.liuguang.media.data.local.dao.RadioSourceDao
import com.liuguang.media.data.local.dao.VideoSiteDao
import com.liuguang.media.data.local.entity.HistoryEntity
import com.liuguang.media.data.local.entity.LiveSourceEntity
import com.liuguang.media.data.local.entity.PodcastSubscriptionEntity
import com.liuguang.media.data.local.entity.RadioSourceEntity
import com.liuguang.media.data.local.entity.VideoSiteEntity

@Database(
    entities = [
        VideoSiteEntity::class,
        HistoryEntity::class,
        LiveSourceEntity::class,
        RadioSourceEntity::class,
        PodcastSubscriptionEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoSiteDao(): VideoSiteDao
    abstract fun historyDao(): HistoryDao
    abstract fun liveSourceDao(): LiveSourceDao
    abstract fun radioSourceDao(): RadioSourceDao
    abstract fun podcastSubscriptionDao(): PodcastSubscriptionDao

    class Callback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
        }
    }

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    UPDATE video_sites
                    SET sortOrder = sortOrder + 1
                    WHERE apiUrl != 'https://api.wujinapi.me/api.php/provide/vod/'
                      AND sortOrder >= 1
                """)
                db.execSQL("""
                    INSERT INTO video_sites (name, apiUrl, enabled, sortOrder, lastCheckStatus, lastCheckTime)
                    SELECT '无尽资源', 'https://api.wujinapi.me/api.php/provide/vod/', 1, 1, '可播放', 0
                    WHERE NOT EXISTS (
                        SELECT 1 FROM video_sites
                        WHERE apiUrl = 'https://api.wujinapi.me/api.php/provide/vod/'
                    )
                """)
                db.execSQL("""
                    UPDATE video_sites
                    SET enabled = 1, sortOrder = 1, lastCheckStatus = '可播放'
                    WHERE apiUrl = 'https://api.wujinapi.me/api.php/provide/vod/'
                """)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    UPDATE live_sources
                    SET url = '${DefaultSources.DEFAULT_LIVE_SOURCE_URL}', lastCheckStatus = '未检测'
                    WHERE url = '${DefaultSources.LEGACY_IPV6_LIVE_SOURCE_URL}'
                """)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    INSERT INTO live_sources (name, url, enabled, sortOrder, lastCheckStatus, lastCheckTime)
                    SELECT '播放测试源', '${DefaultSources.PLAYBACK_TEST_LIVE_SOURCE_URL}', 1, 2, '可播放', 0
                    WHERE NOT EXISTS (
                        SELECT 1 FROM live_sources
                        WHERE url = '${DefaultSources.PLAYBACK_TEST_LIVE_SOURCE_URL}'
                    )
                """)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    UPDATE live_sources
                    SET url = '${DefaultSources.DEFAULT_LIVE_SOURCE_URL}', lastCheckStatus = '未检测'
                    WHERE name = 'IPTV直播源'
                      AND url = '${DefaultSources.LEGACY_DEFAULT_LIVE_SOURCE_URL}'
                """)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE video_sites ADD COLUMN lastLatencyMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE video_sites ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    UPDATE video_sites
                    SET isDefault = 1
                    WHERE id = (
                        SELECT id
                        FROM video_sites
                        WHERE enabled = 1
                        ORDER BY sortOrder ASC, id ASC
                        LIMIT 1
                    )
                """)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS radio_sources (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        url TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        lastCheckStatus TEXT NOT NULL,
                        lastCheckTime INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    INSERT INTO radio_sources (name, url, enabled, sortOrder, lastCheckStatus, lastCheckTime)
                    SELECT '热门网络电台', '${DefaultSources.DEFAULT_RADIO_SOURCE_URL}', 1, 1, '未检测', 0
                    WHERE NOT EXISTS (
                        SELECT 1 FROM radio_sources
                        WHERE url = '${DefaultSources.DEFAULT_RADIO_SOURCE_URL}'
                    )
                """)
                db.execSQL("""
                    INSERT INTO radio_sources (name, url, enabled, sortOrder, lastCheckStatus, lastCheckTime)
                    SELECT '中文网络电台', '${DefaultSources.RADIO_BROWSER_CHINA_SOURCE_URL}', 1, 2, '未检测', 0
                    WHERE NOT EXISTS (
                        SELECT 1 FROM radio_sources
                        WHERE url = '${DefaultSources.RADIO_BROWSER_CHINA_SOURCE_URL}'
                    )
                """)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS podcast_subscriptions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        url TEXT NOT NULL,
                        description TEXT NOT NULL,
                        imageUrl TEXT NOT NULL,
                        link TEXT NOT NULL,
                        episodeCount INTEGER NOT NULL,
                        lastRefreshTime INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_podcast_subscriptions_url
                    ON podcast_subscriptions(url)
                """)
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcast_subscriptions ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE podcast_subscriptions ADD COLUMN lastCheckStatus TEXT NOT NULL DEFAULT '未检测'")
                db.execSQL("ALTER TABLE podcast_subscriptions ADD COLUMN lastCheckTime INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    DELETE FROM video_sites
                    WHERE apiUrl IN (
                        'https://api.wujinapi.me/api.php/provide/vod/',
                        'https://cj.lziapi.com/api.php/provide/vod/',
                        'https://cj.ffzyapi.com/api.php/provide/vod/'
                    )
                """)
                db.execSQL("""
                    DELETE FROM live_sources
                    WHERE url IN (
                        '${DefaultSources.DEFAULT_LIVE_SOURCE_URL}',
                        '${DefaultSources.PLAYBACK_TEST_LIVE_SOURCE_URL}',
                        '${DefaultSources.LEGACY_DEFAULT_LIVE_SOURCE_URL}',
                        '${DefaultSources.LEGACY_IPV6_LIVE_SOURCE_URL}'
                    )
                """)
                db.execSQL("""
                    DELETE FROM radio_sources
                    WHERE url IN (
                        '${DefaultSources.DEFAULT_RADIO_SOURCE_URL}',
                        '${DefaultSources.RADIO_BROWSER_CHINA_SOURCE_URL}'
                    )
                """)
            }
        }
    }
}
