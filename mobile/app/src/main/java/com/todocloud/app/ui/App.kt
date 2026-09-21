package com.todocloud.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class TabItem(
    val label: String,
    val icon: @Composable () -> Unit,
)

@Composable
fun TodoCloudApp() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        TabItem("任务") { Icon(Icons.Outlined.Checklist, contentDescription = "任务") },
        TabItem("日历") { Icon(Icons.Outlined.CalendarMonth, contentDescription = "日历") },
        TabItem("设置") { Icon(Icons.Outlined.Settings, contentDescription = "设置") },
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = tab.icon,
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        when (selectedTab) {
            0 -> TaskHomeScreen(paddingValues)
            1 -> PlaceholderScreen("日历")
            else -> PlaceholderScreen("设置")
        }
    }
}

@Composable
private fun TaskHomeScreen(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("TodoCloud", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "你的任务会在这里和云端同步",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("开始创建第一个任务", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "后续将支持截图识别、日历和自定义提醒。",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

