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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
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
import kr.docuview.app.R
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
    scanning: Boolean,
    onScan: () -> Unit,
    onBack: () -> Unit,
    onOpenFile: (File) -> Unit,
    onFileDeleted: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(checkStoragePermission(context)) }
    var files by remember { mutableStateOf(initialFiles) }

    LaunchedEffect(initialFiles) {
        files = initialFiles
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var deleteTargetFile by remember { mutableStateOf<File?>(null) }
    var menuAnchorOffset by remember { mutableStateOf(IntOffset.Zero) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val needsAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val permissionName = if (needsAllFilesAccess) "전체 파일 접근 권한" else "저장소 읽기 권한"
    val permissionReason = if (needsAllFilesAccess) {
        "• 권한이 필요한 이유:\n" +
            "안드로이드 시스템 정책상, 일반 미디어 권한으로는 다운로드·Documents·메신저 폴더 등 기기 곳곳에 저장된 PDF·HWP·HWPX 문서 파일에 접근할 수 없습니다.\n\n" +
            "• 주요 용도:\n" +
            "내 폰 안의 문서 파일을 자동으로 찾아 한곳에서 목록으로 확인하고, 원하는 문서를 빠르게 열람하기 위해 필요합니다.\n\n" +
            "• 안심하고 사용하세요:\n" +
            "문서 파일(.pdf, .hwp, .hwpx) 검색 및 읽기 목적으로만 작동하며 기존 파일을 변경·삭제하지 않습니다. 앱에 인터넷 권한이 전혀 없으므로 외부에 파일 데이터가 유출될 위험이 없습니다."
    } else {
        "• 권한이 필요한 이유:\n" +
            "기기 내부 저장소에 위치한 PDF·HWP·HWPX 문서 파일을 찾아 목록으로 제공하고 뷰어로 열람하기 위해 권한이 필요합니다.\n\n" +
            "• 안심하고 사용하세요:\n" +
            "오직 문서 읽기 용도로만 작동하며, 개인정보 수집이나 파일 변경을 하지 않습니다."
    }

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
    ) { granted ->
        hasPermission = granted || checkStoragePermission(context)
        permissionDenied = !hasPermission
    }

    LaunchedEffect(hasPermission, hasScanned, scanning) {
        if (hasPermission && !hasScanned && !scanning) {
            onScan()
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
                                text = "내 폰에 저장된 문서 찾기",
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
                            onClick = { if (hasPermission && !scanning) onScan() },
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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
            ) {
                val isLargeScreen = maxHeight >= 700.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (!isLargeScreen) Modifier.verticalScroll(rememberScrollState())
                            else Modifier
                        ),
                    verticalArrangement = if (isLargeScreen) Arrangement.Center else Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!isLargeScreen) {
                        Spacer(Modifier.height(24.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = "DocuView 아이콘",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "파일 접근 권한 안내",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "요청 권한: $permissionName",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(16.dp))

                    // 1) 버튼을 안내 문구보다 위로 올려서 먼저 보여주기
                    Button(
                        onClick = { showPermissionRationale = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text(
                            text = if (needsAllFilesAccess) "권한 설정하러 가기" else "권한 요청하기",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    if (permissionDenied) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "권한이 허용되지 않아 문서를 찾아볼 수 없습니다. 위 버튼을 눌러 설정에서 허용해 주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // 2) 안내 문구 영역 (대화면에서는 한눈에, 일반 폰에서는 내부 스크롤)
                    Text(
                        text = "권한이 필요한 이유 및 안내",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start),
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (isLargeScreen) 280.dp else 220.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            Text(
                                text = permissionReason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
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
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "DocuView 아이콘",
                        modifier = Modifier.size(48.dp),
                        contentScale = ContentScale.Crop,
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

        if (showPermissionRationale) {
            AlertDialog(
                onDismissRequest = { showPermissionRationale = false },
                title = { Text("$permissionName 안내") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = permissionReason,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (needsAllFilesAccess) {
                                "💡 이동하는 설정 화면의 목록에서 'DocuView'를 찾은 뒤 '모든 파일 관리 허용' 스위치를 켜주시면 됩니다."
                            } else {
                                "💡 권한 허용 창에서 '허용'을 선택해 주시면 됩니다."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPermissionRationale = false
                            if (needsAllFilesAccess) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } else {
                                permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        },
                    ) {
                        Text(if (needsAllFilesAccess) "설정으로 이동" else "권한 허용")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionRationale = false }) { Text("취소") }
                },
            )
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
                                            onFileDeleted(target)
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

