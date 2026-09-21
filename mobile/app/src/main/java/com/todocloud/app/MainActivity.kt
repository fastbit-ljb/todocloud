package com.todocloud.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.todocloud.app.ui.TodoCloudApp
import com.todocloud.app.ui.theme.TodoCloudTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TodoCloudTheme {
                TodoCloudApp()
            }
        }
    }
}

