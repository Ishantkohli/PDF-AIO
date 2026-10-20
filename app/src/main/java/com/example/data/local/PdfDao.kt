package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM documents ORDER BY lastOpenedTimestamp DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun getDocumentById(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentByIdSync(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE filePath = :filePath LIMIT 1")
    suspend fun findDocumentByPath(filePath: String): DocumentEntity?

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

    @Query("SELECT * FROM signatures ORDER BY createdTimestamp DESC")
    fun getAllSignatures(): Flow<List<SignatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignature(sig: SignatureEntity): Long

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteSignatureById(id: Long)

    @Query("DELETE FROM documents WHERE isSample = 1")
    suspend fun deleteSampleDocuments()
}
