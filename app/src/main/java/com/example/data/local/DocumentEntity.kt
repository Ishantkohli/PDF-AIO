package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uriString: String,
    val title: String,
    val filePath: String,
    val pageCount: Int,
    val lastReadPage: Int = 0,
    val readingMode: String = "VERTICAL", // VERTICAL or HORIZONTAL
    val colorFilter: String = "NORMAL", // NORMAL, NIGHT, SEPIA
    val isFavorite: Boolean = false,
    val isSample: Boolean = false,
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val fileSizeBytes: Long = 0L,
    @ColumnInfo(defaultValue = "")
    val sourceUri: String = "",
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "General")
    val folderCategory: String = "General",
    @ColumnInfo(defaultValue = "")
    val author: String = "",
    @ColumnInfo(defaultValue = "")
    val subject: String = "",
    @ColumnInfo(defaultValue = "")
    val keywords: String = ""
)
