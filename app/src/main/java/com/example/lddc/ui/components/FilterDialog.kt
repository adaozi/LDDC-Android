package com.example.lddc.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lddc.model.SearchFilters

/**
 * 通用筛选对话框
 *
 * 用于搜索结果页面的筛选功能
 *
 * @param searchFilters 当前筛选条件
 * @param onFiltersChanged 筛选条件变化回调
 * @param onDismiss 关闭对话框回调
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    searchFilters: SearchFilters,
    onFiltersChanged: (SearchFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var tempFilters by remember { mutableStateOf(searchFilters) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筛选条件") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // 歌曲名筛选
                OutlinedTextField(
                    value = tempFilters.songName,
                    onValueChange = { tempFilters = tempFilters.copy(songName = it) },
                    label = { Text("歌曲名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 作者筛选
                OutlinedTextField(
                    value = tempFilters.artist,
                    onValueChange = { tempFilters = tempFilters.copy(artist = it) },
                    label = { Text("作者") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 专辑筛选
                OutlinedTextField(
                    value = tempFilters.album,
                    onValueChange = { tempFilters = tempFilters.copy(album = it) },
                    label = { Text("专辑") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 平台筛选（下拉菜单）
                var platformExpanded by remember { mutableStateOf(false) }
                val platformOptions = listOf(
                    "全部平台" to emptySet(),
                    "QQ音乐" to setOf("QM"),
                    "网易云音乐" to setOf("NE"),
                    "酷狗音乐" to setOf("KG")
                )
                val selectedPlatformLabel =
                    platformOptions.find { it.second == tempFilters.platforms }?.first ?: "全部平台"

                ExposedDropdownMenuBox(
                    expanded = platformExpanded,
                    onExpandedChange = { platformExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedPlatformLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("平台筛选") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = platformExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = platformExpanded,
                        onDismissRequest = { platformExpanded = false }
                    ) {
                        platformOptions.forEach { (label, platforms) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    tempFilters = tempFilters.copy(platforms = platforms)
                                    platformExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onFiltersChanged(tempFilters)
                    onDismiss()
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
