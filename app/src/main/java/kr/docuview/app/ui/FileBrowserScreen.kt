package kr.docuview.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.platform.LocalDensity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var deleteTargetFile by remember { mutableStateOf<File?>(null) }
    var menuAnchorOffset by remember { mutableStateOf(IntOffset.Zero) }

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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "문서 찾기",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "PDF · HWP · HWPX",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surface, shape = CircleShape),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "뒤로",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                    },
                    actions = {
                        IconButton(
                            onClick = { if (hasPermission && !scanning) doScan() },
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surface, shape = CircleShape),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        if (!hasPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "저장소 접근 권한이 필요합니다",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "문서를 찾고 불러오기 위해 권한을 허용해 주세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } else {
                            permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("권한 허용")
                }
            }
        } else if (scanning) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("문서 찾는 중…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("PDF/HWP 문서를 찾지 못했습니다.", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                if (pdfFiles.isNotEmpty()) {
                    SectionHeader(title = "PDF 문서", count = pdfFiles.size)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(pdfFiles, key = { it.absolutePath }) { file ->
                            DocumentPickerCard(
                                file = file,
                                importing = importing,
                                onOpenFile = onOpenFile,
                                onMoreActions = { offset ->
                                    selectedFile = file
                                    menuAnchorOffset = offset
                                },
                            )
                        }
                    }
                }

                if (hwpFiles.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    SectionHeader(title = "HWP 문서", count = hwpFiles.size)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(hwpFiles, key = { it.absolutePath }) { file ->
                            DocumentPickerCard(
                                file = file,
                                importing = importing,
                                onOpenFile = onOpenFile,
                                onMoreActions = { offset ->
                                    selectedFile = file
                                    menuAnchorOffset = offset
                                },
                            )
                        }
                    }
                }
            }
        }

        selectedFile?.let { file ->
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
                        val x = menuAnchorOffset.x.coerceIn(0, maxX)
                        val y = menuAnchorOffset.y.coerceIn(0, maxY)
                        return IntOffset(x, y)
                    }
                },
                onDismissRequest = { selectedFile = null },
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.widthIn(min = 180.dp, max = 220.dp),
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        DropdownMenuItem(
                            text = { Text("열기") },
                            onClick = {
                                onOpenFile(file)
                                selectedFile = null
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("삭제") },
                            onClick = {
                                deleteTargetFile = file
                                showDeleteConfirm = true
                                selectedFile = null
                            },
                        )
                    }
                }
            }
        }

        if (showDeleteConfirm && deleteTargetFile != null) {
            val target = deleteTargetFile!!
            Dialog(
                onDismissRequest = {
                    showDeleteConfirm = false
                    deleteTargetFile = null
                },
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp),
                    ) {
                        Text(
                            text = "파일 삭제",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "선택한 파일을 정말 삭제할까요?\n삭제된 파일은 휴지통으로 이동하지 않고 바로 삭제됩니다.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "복구가 불가능합니다.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val deleted = withContext(Dispatchers.IO) {
                                            target.delete()
                                        }
                                        if (deleted) {
                                            Toast.makeText(context, "파일이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                            files = files.filter { it.absolutePath != target.absolutePath }
                                        } else {
                                            Toast.makeText(context, "파일을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                                        }
                                        showDeleteConfirm = false
                                        deleteTargetFile = null
                                    }
                                },
                            ) {
                                Text("삭제")
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    showDeleteConfirm = false
                                    deleteTargetFile = null
                                },
                            ) {
                                Text("취소")
                            }
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DocumentPickerCard(
    file: File,
    importing: Boolean,
    onOpenFile: (File) -> Unit,
    onMoreActions: (IntOffset) -> Unit,
) {
    val ext = file.extension.lowercase()
    val iconRes = getFileIconRes(ext)
    val itemPos = remember { mutableStateOf(Offset.Zero) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                itemPos.value = coords.positionInWindow()
            }
            .pointerInput(file) {
                detectTapGestures(
                    onTap = { if (!importing) onOpenFile(file) },
                    onLongPress = { offset ->
                        val absoluteX = (itemPos.value.x + offset.x).roundToInt()
                        val absoluteY = (itemPos.value.y + offset.y).roundToInt()
                        onMoreActions(IntOffset(absoluteX, absoluteY))
                    },
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
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