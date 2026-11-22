package kr.docuview.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "page_annotations",
    primaryKeys = ["documentId", "strokeId"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId", "pageIndex"])],
)
data class PageAnnotationEntity(
    val documentId: String,
    val strokeId: String,
    /** 0-based page index. */
    val pageIndex: Int,
    val tool: String,
    val color: String,
    /** Normalized points encoded as "x,y;x,y;...". */
    val points: String,
    val text: String? = null,
    val createdAt: Long,
)
