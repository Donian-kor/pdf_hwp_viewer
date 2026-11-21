package kr.docuview.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kr.docuview.app.data.DocumentDatabase
import kr.docuview.app.data.DocumentScanCache
import kr.docuview.app.data.SettingsManager
import kr.docuview.app.importer.DocumentImporter
import kr.docuview.app.importer.DocumentScanner
import kr.docuview.app.ui.FileBrowserScreen
import kr.docuview.app.ui.HomeScreen
import kr.docuview.app.ui.PrivacyPolicyScreen
import kr.docuview.app.ui.ReaderScreen
import kr.docuview.app.ui.SettingsScreen
import kr.docuview.app.ui.theme.DocuViewTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val pendingOpenUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureSystemBars()
        pendingOpenUri.value = resolveIncomingUri(intent)

        val database = DocumentDatabase.getInstance(applicationContext)
        val settingsManager = SettingsManager(applicationContext)

        setContent {
            val settings by settingsManager.settings.collectAsState()
            val incomingUri by pendingOpenUri

            DocuViewTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DocumentViewerApp(
                        database = database,
                        settingsManager = settingsManager,
                        initialDocumentUri = incomingUri,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenUri.value = resolveIncomingUri(intent)
    }

    private fun resolveIncomingUri(intent: Intent?): Uri? {
        if (intent == null) return null

        val action = intent.action ?: return null
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND && action != Intent.ACTION_OPEN_DOCUMENT) {
            return null
        }

        return intent.data
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    }

    override fun onResume() {
        super.onResume()
        configureSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureSystemBars()
        }
    }

    private fun configureSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            show(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
private fun DocumentViewerApp(
    database: DocumentDatabase,
    settingsManager: SettingsManager,
    initialDocumentUri: Uri? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val dao = remember(database) { database.documentDao() }
    val importer = remember(context, dao) { DocumentImporter(context.applicationContext, dao) }
    val recentDocuments by dao.observeRecent().collectAsState(initial = emptyList())
    val settings by settingsManager.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var activeDocument by remember { mutableStateOf<kr.docuview.app.data.DocumentEntity?>(null) }
    var showBrowser by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    val scanCache = remember(context) { DocumentScanCache(context.applicationContext) }
    var scannedFiles by remember { mutableStateOf(scanCache.loadFiles()) }
    // 캐시된 목록은 즉시 보여주되, 프로세스 시작 후 첫 문서찾기 진입 시 한 번 재스캔한다.
    var hasScanned by remember { mutableStateOf(false) }
    var scanningFiles by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

    // 뒤로 가기 및 앱 완전 종료 처리
    BackHandler {
        when {
            showPrivacyPolicy -> {
                showPrivacyPolicy = false
            }
            activeDocument != null -> {
                activeDocument = null
            }
            showSettings || selectedTab == 2 -> {
                showSettings = false
                selectedTab = 0
            }
            showBrowser || selectedTab == 1 -> {
                showBrowser = false
                selectedTab = 0
            }
            else -> {
                // 더 이상 뒤로 갈 화면이 없으면 최근 앱 목록에도 안 남게 완벽 종료
                activity?.finishAndRemoveTask()
            }
        }
    }

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
                    errorMessage = error.message ?: "파일을 가져오지 못했습니다."
                } finally {
                    importing = false
                }
            }
        }
    }

    val openHome: () -> Unit = {
        selectedTab = 0
        showBrowser = false
        showSettings = false
        activeDocument = null
    }

    val openBrowser: () -> Unit = {
        selectedTab = 1
        showBrowser = true
        showSettings = false
        activeDocument = null
    }

    val openSettingsScreen: () -> Unit = {
        selectedTab = 2
        showSettings = true
        showBrowser = false
        activeDocument = null
    }

    val startScan: () -> Unit = {
        if (!scanningFiles) {
            scope.launch {
                scanningFiles = true
                val result = withContext(Dispatchers.IO) { DocumentScanner.scan() }
                scannedFiles = result
                scanCache.save(result)
                hasScanned = true
                scanningFiles = false
            }
        }
    }

    // 초기 문서 URI가 있으면 해당 문서를 바로 엽니다.
    LaunchedEffect(initialDocumentUri) {
        if (initialDocumentUri != null) {
            importing = true
            errorMessage = null
            try {
                activeDocument = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    importer.import(initialDocumentUri)
                }
            } catch (error: Exception) {
                errorMessage = error.message ?: "파일을 가져오지 못했습니다."
            } finally {
                importing = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .heightIn(min = 56.dp),
                ) {
                    val navItems = listOf(
                        Triple("홈", Icons.Default.Home, 0),
                        Triple("문서 찾기", Icons.Default.Add, 1),
                        Triple("설정", Icons.Default.Settings, 2),
                    )

                    navItems.forEach { item ->
                        val currentActiveTab = when {
                            activeDocument != null -> -1
                            showSettings || selectedTab == 2 -> 2
                            showBrowser || selectedTab == 1 -> 1
                            else -> 0
                        }
                        val isSelected = currentActiveTab == item.third
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                when (item.third) {
                                    0 -> openHome()
                                    1 -> openBrowser()
                                    2 -> openSettingsScreen()
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.second,
                                    contentDescription = item.first,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text(item.first, style = MaterialTheme.typography.labelSmall) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemColors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            ),
                            modifier = Modifier.height(48.dp),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (showPrivacyPolicy) {
                PrivacyPolicyScreen(onBack = { showPrivacyPolicy = false })
            } else if (activeDocument != null) {
                ReaderScreen(
                    document = activeDocument!!,
                    dao = dao,
                    onBack = { activeDocument = null },
                    restoreLastPage = settings.restoreLastPage,
                    compactMode = settings.compactMode,
                    keepScreenOn = settings.keepScreenOn,
                )
            } else if (showSettings || selectedTab == 2) {
                SettingsScreen(
                    onBack = { openHome() },
                    restoreLastPage = settings.restoreLastPage,
                    onRestoreLastPageChange = { settingsManager.setRestoreLastPage(it) },
                    compactMode = settings.compactMode,
                    onCompactModeChange = { settingsManager.setCompactMode(it) },
                    keepScreenOn = settings.keepScreenOn,
                    onKeepScreenOnChange = { settingsManager.setKeepScreenOn(it) },
                    themeMode = settings.themeMode,
                    onThemeModeChange = { settingsManager.setThemeMode(it) },
                    onClearRecentDocuments = {
                        scope.launch {
                            dao.clearRecentDocuments()
                        }
                    },
                    onPrivacyPolicyClick = { showPrivacyPolicy = true },
                )
            } else if (showBrowser || selectedTab == 1) {
                FileBrowserScreen(
                    importing = importing,
                    initialFiles = scannedFiles,
                    hasScanned = hasScanned,
                    scanning = scanningFiles,
                    onScan = startScan,
                    onBack = { openHome() },
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
                    onFileDeleted = { file ->
                        scope.launch {
                            val deletedDocuments = dao.getDocumentsByLocalPath(file.absolutePath)
                                .ifEmpty { dao.getDocumentsByName(file.name) }
                            deletedDocuments.forEach { dao.deleteDocument(it.id) }
                            if (activeDocument?.localPath == file.absolutePath) {
                                activeDocument = null
                            }
                            scannedFiles = scannedFiles.filter { it.absolutePath != file.absolutePath }
                            scanCache.save(scannedFiles)
                        }
                    },
                )
            } else {
                HomeScreen(
                    documents = recentDocuments,
                    importing = importing,
                    errorMessage = errorMessage,
                    onOpenPicker = { openBrowser() },
                    onOpenDocument = { activeDocument = it },
                    onDismissError = { errorMessage = null },
                    compactMode = settings.compactMode,
                )
            }
        }
    }
}