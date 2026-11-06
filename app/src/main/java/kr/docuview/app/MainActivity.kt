package kr.docuview.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kr.docuview.app.data.DocumentDatabase
import kr.docuview.app.importer.DocumentImporter
import kr.docuview.app.ui.HomeScreen
import kr.docuview.app.ui.ReaderScreen
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = DocumentDatabase.getInstance(applicationContext)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DocumentViewerApp(database)
                }
            }
        }
    }
}

@Composable
private fun DocumentViewerApp(database: DocumentDatabase) {
    val context = LocalContext.current
    val dao = remember(database) { database.documentDao() }
    val importer = remember(context, dao) { DocumentImporter(context.applicationContext, dao) }
    val recentDocuments by dao.observeRecent().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var activeDocument by remember { mutableStateOf<kr.docuview.app.data.DocumentEntity?>(null) }
    var showBrowser by remember { mutableStateOf(false) }
    var scannedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var hasScanned by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                errorMessage = null
                try {
                    activeDocument = importer.import(uri)
                } catch (error: Exception) {
                    errorMessage = error.message ?: "?�일??가?�오지 못했?�니??"
                } finally {
                    importing = false
                }
            }
        }
    }

    if (showBrowser) {
        kr.docuview.app.ui.FileBrowserScreen(
            importing = importing,
            initialFiles = scannedFiles,
            hasScanned = hasScanned,
            onFilesScanned = { scannedFiles = it; hasScanned = true },
            onBack = { showBrowser = false },
            onOpenFile = { file ->
                scope.launch {
                    importing = true
                    errorMessage = null
                    try {
                        activeDocument = importer.import(Uri.fromFile(file))
                        showBrowser = false
                    } catch (error: Exception) {
                        errorMessage = error.message ?: "파일을 가져오지 못했습니다."
                    } finally {
                        importing = false
                    }
                }
            },
        )
    } else if (activeDocument == null) {
        HomeScreen(
            documents = recentDocuments,
            importing = importing,
            errorMessage = errorMessage,
            onOpenPicker = { showBrowser = true },
            onOpenDocument = { activeDocument = it },
            onDismissError = { errorMessage = null },
        )
    } else {
        ReaderScreen(
            document = activeDocument!!,
            dao = dao,
            onBack = { activeDocument = null },
        )
    }
}
