package kr.docuview.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    fun observeRecent(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocument(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: DocumentEntity)

    @Query("UPDATE documents SET lastOpenedAt = :openedAt WHERE id = :id")
    suspend fun touchDocument(id: String, openedAt: Long)

    @Query(
        "UPDATE documents SET lastPage = :pageIndex, totalPages = :pageCount, lastOpenedAt = :openedAt " +
            "WHERE id = :id",
    )
    suspend fun updateProgress(id: String, pageIndex: Int, pageCount: Int, openedAt: Long)

    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun observeBookmarks(documentId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId AND pageIndex = :pageIndex LIMIT 1")
    suspend fun getBookmark(documentId: String, pageIndex: Int): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE documentId = :documentId AND pageIndex = :pageIndex")
    suspend fun deleteBookmark(documentId: String, pageIndex: Int)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("DELETE FROM documents")
    suspend fun clearRecentDocuments()
}
