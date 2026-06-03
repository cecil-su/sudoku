package com.sudoku.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.runtime.collectAsState
import com.sudoku.game.model.Difficulty
import com.sudoku.game.ui.screen.GameScreen
import com.sudoku.game.ui.screen.HomeScreen
import com.sudoku.game.ui.screen.ProviderEditScreen
import com.sudoku.game.ui.screen.SettingsScreen
import com.sudoku.game.ui.theme.SudokuTheme
import com.sudoku.game.viewmodel.GameViewModel
import com.sudoku.game.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SudokuTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val gameViewModel: GameViewModel = viewModel()
                    val settingsViewModel: SettingsViewModel = viewModel()

                    // Confirmation dialog state
                    var pendingDifficulty by remember { mutableStateOf<Difficulty?>(null) }

                    pendingDifficulty?.let { difficulty ->
                        AlertDialog(
                            onDismissRequest = { pendingDifficulty = null },
                            title = { Text("开始新游戏") },
                            text = { Text("当前有未完成的游戏，开始新游戏将覆盖存档。") },
                            confirmButton = {
                                TextButton(onClick = {
                                    pendingDifficulty = null
                                    gameViewModel.newGame(difficulty)
                                    navController.navigate("game") { launchSingleTop = true }
                                }) { Text("确认") }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingDifficulty = null }) { Text("取消") }
                            }
                        )
                    }

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            val hasSaved by gameViewModel.hasSavedGame.collectAsState()
                            val stats by gameViewModel.stats.collectAsState()
                            HomeScreen(
                                onStartGame = { difficulty ->
                                    if (hasSaved) {
                                        pendingDifficulty = difficulty
                                    } else {
                                        gameViewModel.newGame(difficulty)
                                        navController.navigate("game") { launchSingleTop = true }
                                    }
                                },
                                onContinueGame = {
                                    gameViewModel.continueGame()
                                    navController.navigate("game") { launchSingleTop = true }
                                },
                                onOpenSettings = { navController.navigate("settings") },
                                hasSavedGame = hasSaved,
                                stats = stats
                            )
                        }
                        composable("game") {
                            GameScreen(
                                viewModel = gameViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            val settings by settingsViewModel.settings.collectAsState()
                            SettingsScreen(
                                settings = settings,
                                onAdd = { navController.navigate("provider_edit") },
                                onEdit = { id -> navController.navigate("provider_edit?providerId=$id") },
                                onSelectActive = { id -> settingsViewModel.selectProvider(id) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "provider_edit?providerId={providerId}",
                            arguments = listOf(navArgument("providerId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            })
                        ) { backStackEntry ->
                            val settings by settingsViewModel.settings.collectAsState()
                            val editingId = backStackEntry.arguments?.getString("providerId")
                            val existing = settings.providers.firstOrNull { it.id == editingId }
                            ProviderEditScreen(
                                existing = existing,
                                onSave = {
                                    settingsViewModel.saveProvider(it)
                                    navController.popBackStack()
                                },
                                onDelete = { id ->
                                    settingsViewModel.deleteProvider(id)
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
