package kr.docuview.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    importing: Boolean,
    initialFiles: List<File>,
    hasScanned: Boolean,
    onFilesScanned: (List<File>) -> Unit,
    onBack: () -> Unit,
    onOpenFile: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(checkStoragePermission(context)) }
    var files by remember { mutableStateOf(initialFiles) }
    var scanning by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = checkStoragePermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted || checkStoragePermission(context) }

    fun doScan() {
        scope.launch {
            scanning = true
            val result = withContext(Dispatchers.IO) { scanDocuments() }
            files = result
            onFilesScanned(result)
            scanning = false
        }
    }

    LaunchedEffect(hasPermission, hasScanned) {
        if (hasPermission && !hasScanned && files.isEmpty() && !scanning) {
            doScan()
        }
    }

    val pdfFiles = files.filter { it.name.lowercase().endsWith(".pdf") }
    val hwpFiles = files.filter { it.name.lowercase().endsWith(".hwp") || it.name.lowercase().endsWith(".hwpx") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("문서 찾기") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "뒤로") }
                },
                actions = {
                    IconButton(onClick = { if (hasPermission && !scanning) doScan() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침", modifier = Modifier.size(32.dp))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("저장소 접근 권한이 필요합니다.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(12.dp))
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } else {
                        permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }) { Text("권한 허용") }
            }
        } else if (scanning) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("PDF/HWP 문서를 찾지 못했습니다.")
            }
        } else {
            Row(Modifier.fillMaxSize().padding(innerPadding)) {
                Column(Modifier.weight(1f).padding(8.dp)) {
                    Text("PDF 문서", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(4.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(pdfFiles, key = { it.absolutePath }) { file ->
                            Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !importing) { onOpenFile(file) }) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text(java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).padding(8.dp)) {
                    Text("HWP 문서", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(4.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(hwpFiles, key = { it.absolutePath }) { file ->
                            Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !importing) { onOpenFile(file) }) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text(java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun checkStoragePermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

private fun scanDocuments(): List<File> {
    val root = Environment.getExternalStorageDirectory()
    val result = mutableListOf<File>()
    val stack = ArrayDeque<File>()
    stack.add(root)
    var visited = 0
    while (stack.isNotEmpty() && result.size < 500 && visited < 200000) {
        val dir = stack.removeLast()
        visited++
        val children = dir.listFiles() ?: continue
        for (child in children) {
            if (child.isDirectory) {
                if (child.name.startsWith(".")) continue
                stack.add(child)
            } else {
                val name = child.name.lowercase()
                if (name.endsWith(".pdf") || name.endsWith(".hwp") || name.endsWith(".hwpx")) {
                    result.add(child)
                }
            }
        }
    }
    return result.sortedByDescending { it.lastModified() }
}
