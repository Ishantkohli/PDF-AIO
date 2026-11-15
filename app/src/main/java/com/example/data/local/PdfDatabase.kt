package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DocumentEntity::class,
        AnnotationEntity::class,
        SignatureEntity::class,
        BookmarkEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PdfDatabase : RoomDatabase() {
    abstract fun pdfDao(): PdfDao

    companion object {
        @Volatile
        private var INSTANCE: PdfDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN sourceUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documents ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documents ADD COLUMN folderCategory TEXT NOT NULL DEFAULT 'General'")
                db.execSQL("ALTER TABLE documents ADD COLUMN author TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documents ADD COLUMN subject TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documents ADD COLUMN keywords TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE annotations ADD COLUMN opacity REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE annotations ADD COLUMN extraData TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS bookmarks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        documentId INTEGER NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        createdTimestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_bookmarks_documentId ON bookmarks(documentId)"
                )
            }
        }

        fun getInstance(context: Context): PdfDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PdfDatabase::class.java,
                    "pdf_aio.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
