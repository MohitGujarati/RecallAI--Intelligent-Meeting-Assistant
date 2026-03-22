package com.example.recall_ai.di

import android.content.Context
import androidx.room.Room
import com.example.recall_ai.data.local.AppDatabase
import com.example.recall_ai.data.local.dao.AudioChunkDao
import com.example.recall_ai.data.local.dao.ChatMessageDao
import com.example.recall_ai.data.local.dao.MeetingDao
import com.example.recall_ai.data.local.dao.SummaryDao
import com.example.recall_ai.data.local.dao.TranscriptDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Room database and all four DAOs as application-scoped singletons.
 *
 * ── Scoping rationale ─────────────────────────────────────────────────────
 * The database and every DAO are @Singleton.
 *
 *   • Room's generated DAO implementations are thread-safe — sharing a single
 *     instance across all injection sites is correct and avoids the overhead
 *     of opening multiple SQLite connections.
 *   • A @Singleton database means the WAL (Write-Ahead Log) journal is opened
 *     once for the process lifetime, giving the best concurrent-read performance
 *     for the Flow-based queries used throughout the app.
 *
 * ── Migration policy ──────────────────────────────────────────────────────
 * Development: fallbackToDestructiveMigration() wipes and recreates the DB on
 *   schema change. Acceptable during active development when test data is disposable.
 *
 * Before first production release:
 *   1. Remove fallbackToDestructiveMigration()
 *   2. Write a Migration object for every version bump, e.g.:
 *        val MIGRATION_1_2 = object : Migration(1, 2) {
 *            override fun migrate(db: SupportSQLiteDatabase) {
 *                db.execSQL("ALTER TABLE meetings ADD COLUMN folderId INTEGER")
 *            }
 *        }
 *   3. Add .addMigrations(MIGRATION_1_2) to the builder below.
 *
 * ── Main-thread access ────────────────────────────────────────────────────
 * allowMainThreadQueries() is intentionally absent. All DAO calls are either
 * suspend functions collected on Dispatchers.IO, or Flow collectors that Room
 * evaluates on its internal query executor — never on the main thread.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            // ── Development safety net ────────────────────────────────────────
            // Wipes and recreates the DB instead of crashing on unhandled migration.
            // REMOVE before shipping — replace with explicit Migration objects.
            .fallbackToDestructiveMigration()
            // ── Future migrations (add before removing fallback above) ─────────
            // .addMigrations(MIGRATION_1_2)
            .build()

    // ── DAO providers ─────────────────────────────────────────────────────────
    //
    // Each DAO is a thin interface over the same db connection — providing them
    // individually means injection sites only declare the DAO they need, rather
    // than depending on the entire AppDatabase object. This keeps constructor
    // parameter lists honest and makes unit tests cheaper (mock one DAO, not all).

    @Provides
    @Singleton
    fun provideMeetingDao(db: AppDatabase): MeetingDao = db.meetingDao()

    @Provides
    @Singleton
    fun provideAudioChunkDao(db: AppDatabase): AudioChunkDao = db.audioChunkDao()

    @Provides
    @Singleton
    fun provideTranscriptDao(db: AppDatabase): TranscriptDao = db.transcriptDao()

    @Provides
    @Singleton
    fun provideSummaryDao(db: AppDatabase): SummaryDao = db.summaryDao()

    @Provides
    @Singleton
    fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()
}