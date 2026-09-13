package dev.arbrajab.lexiconandroid.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.arbrajab.lexiconandroid.data.local.dao.CorpusDao
import dev.arbrajab.lexiconandroid.data.local.dao.DocumentDao
import dev.arbrajab.lexiconandroid.data.local.dao.PendingQueryDao
import dev.arbrajab.lexiconandroid.data.local.dao.QueryResultDao
import dev.arbrajab.lexiconandroid.data.local.entity.CorpusEntity
import dev.arbrajab.lexiconandroid.data.local.entity.DocumentEntity
import dev.arbrajab.lexiconandroid.data.local.entity.PendingQueryEntity
import dev.arbrajab.lexiconandroid.data.local.entity.QueryResultEntity
import dev.arbrajab.lexiconandroid.data.local.entity.QuerySyncState

class Converters {
    @TypeConverter
    fun fromSyncState(value: QuerySyncState): String = value.name

    @TypeConverter
    fun toSyncState(value: String): QuerySyncState = QuerySyncState.valueOf(value)
}

@Database(
    entities = [
        CorpusEntity::class,
        DocumentEntity::class,
        QueryResultEntity::class,
        PendingQueryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun corpusDao(): CorpusDao
    abstract fun documentDao(): DocumentDao
    abstract fun queryResultDao(): QueryResultDao
    abstract fun pendingQueryDao(): PendingQueryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "lexicon.db"
            ).build().also { instance = it }
        }
    }
}
