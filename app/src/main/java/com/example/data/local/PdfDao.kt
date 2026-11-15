package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM documents ORDER BY isPinned DESC, lastOpenedTimestamp DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun getDocumentById(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentByIdSync(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE filePath = :filePath LIMIT 1")
    suspend fun findDocumentByPath(filePath: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE LOWER(title) = LOWER(:title) AND id != :excludeId LIMIT 1")
    suspend fun findDocumentByTitleExcluding(title: String, excludeId: Long): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity): Long

    @Update
    suspend fun updateDocument(doc: DocumentEntity)

    @Delete
    suspend fun deleteDocument(doc: DocumentEntity)

    @Query("UPDATE documents SET lastReadPage = :page, lastOpenedTimestamp = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: Long, page: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isFavorite = :isFav WHERE id = :id")
    suspend fun setFavorite(id: Long, isFav: Boolean)

    @Query("UPDATE documents SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean)

    @Query("UPDATE documents SET folderCategory = :category WHERE id = :id")
    suspend fun setFolderCategory(id: Long, category: String)

    @Query("UPDATE documents SET title = :newTitle, filePath = :newFilePath, uriString = :newUriString, sourceUri = :newSourceUri, lastOpenedTimestamp = :timestamp WHERE id = :id")
    suspend fun updateDocumentLocationInternal(
        id: Long,
        newTitle: String,
        newFilePath: String,
        newUriString: String,
        newSourceUri: String,
        timestamp: Long = System.currentTimeMillis()
    )

    @Transaction
    suspend fun renameDocumentTransactionally(
        id: Long,
        newTitle: String,
        newFilePath: String,
        newUriString: String,
        newSourceUri: String
    ): DocumentEntity? {
        updateDocumentLocationInternal(
            id = id,
            newTitle = newTitle,
            newFilePath = newFilePath,
            newUriString = newUriString,
            newSourceUri = newSourceUri
        )
        return getDocumentByIdSync(id)
    }

    @Query("UPDATE documents SET author = :author, subject = :subject, keywords = :keywords, fileSizeBytes = :fileSizeBytes WHERE id = :id")
    suspend fun updateDocumentMetadata(
        id: Long,
        author: String,
        subject: String,
        keywords: String,
        fileSizeBytes: Long
    )

    // --- Annotations ---
    @Query("SELECT * FROM annotations WHERE documentId = :docId AND pageIndex = :pageIndex ORDER BY id ASC")
    fun getAnnotationsForPage(docId: Long, pageIndex: Int): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE documentId = :docId ORDER BY pageIndex ASC, id ASC")
    suspend fun getAllAnnotationsForDoc(docId: Long): List<AnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: Long)

    @Update
    suspend fun updateAnnotation(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE documentId = :docId AND pageIndex = :pageIndex")
    suspend fun clearPageAnnotations(docId: Long, pageIndex: Int)

    @Query("DELETE FROM annotations WHERE documentId = :docId")
    suspend fun clearAllAnnotationsForDoc(docId: Long)

    // --- Bookmarks ---
    @Query("SELECT * FROM bookmarks WHERE documentId = :docId ORDER BY pageIndex ASC, createdTimestamp ASC")
    fun getBookmarksForDoc(docId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE documentId = :docId ORDER BY pageIndex ASC, createdTimestamp ASC")
    suspend fun getBookmarksForDocSync(docId: Long): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    // --- Signatures ---
    @Query("SELECT * FROM signatures ORDER BY createdTimestamp DESC")
    fun getAllSignatures(): Flow<List<SignatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignature(sig: SignatureEntity): Long

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteSignatureById(id: Long)

    @Query("DELETE FROM documents WHERE isSample = 1")
    suspend fun deleteSampleDocuments()
}
