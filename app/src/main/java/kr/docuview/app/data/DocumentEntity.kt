package kr.docuview.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val extension: String,
    val localPath: String,
    val sizeBytes: Long,
    val lastOpenedAt: Long,
    /** 0-based page index. */
    val lastPage: Int = 0,
    val totalPages: Int = 0,
)

@Entity(
    tableName = "bookmarks",
    primaryKeys = ["documentId", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId"])],
)
data class BookmarkEntity(
    val documentId: String,
    /** 0-based page index. */
    val pageIndex: Int,
    val createdAt: Long,
)
