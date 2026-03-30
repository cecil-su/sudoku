package com.sudoku.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sudoku.game.ui.screen.GameScreen
import com.sudoku.game.ui.screen.HomeScreen
import com.sudoku.game.ui.theme.SudokuTheme
import com.sudoku.game.viewmodel.GameViewModel

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

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            val hasSaved by gameViewModel.hasSavedGame.collectAsState()
                            val stats by gameViewModel.stats.collectAsState()
                            HomeScreen(
                                onStartGame = { difficulty ->
                                    gameViewModel.newGame(difficulty)
                                    navController.navigate("game")
                                },
                                onContinueGame = {
                                    gameViewModel.continueGame()
                                    navController.navigate("game")
                                },
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
                    }
                }
            }
        }
    }
}
