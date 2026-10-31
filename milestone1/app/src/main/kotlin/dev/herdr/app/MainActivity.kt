package dev.herdr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.herdr.feature.board.AppRoot
import dev.herdr.feature.board.BoardViewModel
import dev.herdr.feature.board.HerdrTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HerdrApplication).container
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                BoardViewModel(container.settings, container.repository, container.connector) as T
        }
        setContent {
            HerdrTheme {
                val vm: BoardViewModel = viewModel(factory = factory)
                AppRoot(vm)
            }
        }
    }
}
