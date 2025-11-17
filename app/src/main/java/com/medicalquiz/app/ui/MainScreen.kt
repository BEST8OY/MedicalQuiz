package com.medicalquiz.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.DatabaseItem

@Composable
fun MainScreen(
    databases: List<DatabaseItem>,
    statusText: String,
    showManageStoragePrompt: Boolean,
    onGrantStorage: () -> Unit,
    onDatabaseSelected: (DatabaseItem) -> Unit
) {
    Surface(modifier = Modifier
        .fillMaxSize()
        .systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = statusText)

            if (showManageStoragePrompt) {
                Button(onClick = onGrantStorage) {
                    Text(text = "Grant storage access")
                }
            }

            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(databases) { db ->
                    DatabaseItemRow(db, onClick = { onDatabaseSelected(db) })
                }
            }
        }
    }
}

@Composable
fun DatabaseItemRow(item: DatabaseItem, onClick: () -> Unit) {
    Column(modifier = Modifier
        .clickable(onClick = onClick)
        .padding(vertical = 8.dp)
    ) {
        Text(text = item.name, style = MaterialTheme.typography.titleMedium)
        Text(text = item.size, style = MaterialTheme.typography.bodySmall)
        Text(text = item.path, style = MaterialTheme.typography.bodySmall)
    }
}