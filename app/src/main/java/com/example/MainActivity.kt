package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.core.model.ProcessedResult
import com.example.core.model.ToolType
import com.example.data.HistoryRepository
import com.example.data.PreferencesRepository
import com.example.ui.screens.*
import com.example.ui.theme.PocketPhotoToolsTheme

enum class AppScreen {
    HOME,
    COMPRESS,
    RESIZE,
    PASSPORT,
    CONVERT,
    BATCH,
    CROP_ROTATE,
    RESULT,
    HISTORY,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val historyRepository = HistoryRepository(applicationContext)
        val preferencesRepository = PreferencesRepository(applicationContext)

        setContent {
            val isDarkThemePref by preferencesRepository.isDarkTheme.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = isDarkThemePref || systemDark

            PocketPhotoToolsTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PocketPhotoApp(
                        historyRepository = historyRepository,
                        preferencesRepository = preferencesRepository
                    )
                }
            }
        }
    }
}

@Composable
fun PocketPhotoApp(
    historyRepository: HistoryRepository,
    preferencesRepository: PreferencesRepository
) {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var currentResult by remember { mutableStateOf<ProcessedResult?>(null) }
    var activeInitialUri by remember { mutableStateOf<Uri?>(null) }

    val historyList by historyRepository.history.collectAsState()

    // Handle back button on sub-screens
    if (currentScreen != AppScreen.HOME) {
        BackHandler {
            currentScreen = AppScreen.HOME
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            AppScreen.HOME -> {
                HomeScreen(
                    onNavigateToTool = { tool ->
                        activeInitialUri = null
                        currentScreen = when (tool) {
                            ToolType.COMPRESS -> AppScreen.COMPRESS
                            ToolType.RESIZE -> AppScreen.RESIZE
                            ToolType.PASSPORT -> AppScreen.PASSPORT
                            ToolType.CONVERT -> AppScreen.CONVERT
                            ToolType.BATCH -> AppScreen.BATCH
                            ToolType.CROP_ROTATE -> AppScreen.CROP_ROTATE
                        }
                    },
                    onNavigateToResult = { result ->
                        currentResult = result
                        currentScreen = AppScreen.RESULT
                    },
                    onNavigateToHistory = {
                        currentScreen = AppScreen.HISTORY
                    },
                    onNavigateToSettings = {
                        currentScreen = AppScreen.SETTINGS
                    },
                    recentHistory = historyList
                )
            }

            AppScreen.COMPRESS -> {
                CompressScreen(
                    initialUri = activeInitialUri,
                    onBack = { currentScreen = AppScreen.HOME },
                    onResultReady = { result ->
                        historyRepository.addResult(result)
                        currentResult = result
                        currentScreen = AppScreen.RESULT
                    }
                )
            }

            AppScreen.RESIZE -> {
                ResizeScreen(
                    initialUri = activeInitialUri,
                    onBack = { currentScreen = AppScreen.HOME },
                    onResultReady = { result ->
                        historyRepository.addResult(result)
                        currentResult = result
                        currentScreen = AppScreen.RESULT
                    }
                )
            }

            AppScreen.PASSPORT -> {
                PassportScreen(
                    initialUri = activeInitialUri,
                    onBack = { currentScreen = AppScreen.HOME },
                    onResultReady = { result ->
                        historyRepository.addResult(result)
                        currentResult = result
                        currentScreen = AppScreen.RESULT
                    }
                )
            }

            AppScreen.CONVERT -> {
                ConvertScreen(
                    initialUri = activeInitialUri,
                    onBack = { currentScreen = AppScreen.HOME },
                    onResultReady = { result ->
                        historyRepository.addResult(result)
                        currentResult = result
                        currentScreen = AppScreen.RESULT
                    }
                )
            }

            AppScreen.BATCH -> {
                BatchScreen(
                    onBack = { currentScreen = AppScreen.HOME }
                )
            }

            AppScreen.CROP_ROTATE -> {
                CropRotateScreen(
                    initialUri = activeInitialUri,
                    onBack = { currentScreen = AppScreen.HOME },
                    onResultReady = { result ->
                        historyRepository.addResult(result)
                        currentResult = result
                        currentScreen = AppScreen.RESULT
                    }
                )
            }

            AppScreen.RESULT -> {
                val res = currentResult
                if (res != null) {
                    ResultScreen(
                        result = res,
                        onBackToHome = { currentScreen = AppScreen.HOME },
                        onEditAgain = {
                            activeInitialUri = res.originalUri
                            currentScreen = when (res.tool) {
                                ToolType.COMPRESS -> AppScreen.COMPRESS
                                ToolType.RESIZE -> AppScreen.RESIZE
                                ToolType.PASSPORT -> AppScreen.PASSPORT
                                ToolType.CONVERT -> AppScreen.CONVERT
                                ToolType.BATCH -> AppScreen.BATCH
                                ToolType.CROP_ROTATE -> AppScreen.CROP_ROTATE
                            }
                        }
                    )
                } else {
                    currentScreen = AppScreen.HOME
                }
            }

            AppScreen.HISTORY -> {
                HistoryScreen(
                    historyList = historyList,
                    onItemClick = { item ->
                        currentResult = item
                        currentScreen = AppScreen.RESULT
                    },
                    onDeleteItem = { item ->
                        historyRepository.deleteItem(item)
                    },
                    onClearAll = {
                        historyRepository.clearHistory()
                    },
                    onBack = { currentScreen = AppScreen.HOME },
                    onNavigateHome = { currentScreen = AppScreen.HOME }
                )
            }

            AppScreen.SETTINGS -> {
                SettingsScreen(
                    prefsRepository = preferencesRepository,
                    onBack = { currentScreen = AppScreen.HOME }
                )
            }
        }
    }
}
