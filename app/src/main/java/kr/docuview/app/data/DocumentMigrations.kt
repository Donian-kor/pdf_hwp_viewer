package kr.docuview.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `page_annotations` (
                `documentId` TEXT NOT NULL,
                `strokeId` TEXT NOT NULL,
                `pageIndex` INTEGER NOT NULL,
                `tool` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `points` TEXT NOT NULL,
                `text` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`documentId`, `strokeId`),
                FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_page_annotations_documentId_pageIndex` " +
                "ON `page_annotations` (`documentId`, `pageIndex`)",
        )
    }
}
