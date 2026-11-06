package kr.docuview.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.docuview.app.data.DocumentEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    documents: List<DocumentEntity>,
    importing: Boolean,
    errorMessage: String?,
    onOpenPicker: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
    onDismissError: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("문서뷰어") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenPicker,
                icon = { Icon(Icons.Default.Add, contentDescription = "문서 가져오기") },
                text = { Text(if (importing) "가져오는 중..." else "문서 가져오기") },
            )
        },
    ) { innerPadding ->
        if (documents.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(64.dp))
                Spacer(Modifier.width(8.dp))
                Text("가져온 문서가 없습니다.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Text(
                    "최근 열어본 문서",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(12.dp),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(documents, key = { it.id }) { document ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenDocument(document) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (document.extension == "pdf") Icons.Default.PictureAsPdf else Icons.Default.Description,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        document.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        "${document.extension.uppercase()} · ${document.totalPages.coerceAtLeast(0)}페이지",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("오류") },
            text = { Text(errorMessage) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("확인") } },
        )
    }
}
