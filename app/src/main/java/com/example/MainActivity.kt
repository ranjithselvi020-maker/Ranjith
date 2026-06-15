package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.EasyVideoEditorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.VideoEditorViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme(darkTheme = true, dynamicColor = false) {
        val viewModel: VideoEditorViewModel = viewModel()
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          EasyVideoEditorScreen(
            viewModel = viewModel
          )
        }
      }
    }
  }
}
