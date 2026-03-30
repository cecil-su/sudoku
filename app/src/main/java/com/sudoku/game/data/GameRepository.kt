package com.sudoku.game.data

import android.content.Context
import com.sudoku.game.model.Cell
import com.sudoku.game.model.Difficulty
import com.sudoku.game.model.GameState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GameRepository(private val context: Context) {

    private val saveFile get() = File(context.filesDir, "saved_game.json")

    suspend fun saveGame(state: GameState, elapsedSeconds: Long) = withContext(Dispatchers.IO) {
        val json = serializeState(state, elapsedSeconds)
        val tmpFile = File(context.filesDir, "saved_game.tmp")
        tmpFile.writeText(json.toString())
        tmpFile.renameTo(saveFile)
    }

    suspend fun loadGame(): GameState? = withContext(Dispatchers.IO) {
        if (!saveFile.exists()) return@withContext null
        try {
            val json = JSONObject(saveFile.readText())
            deserializeState(json)
        } catch (e: org.json.JSONException) {
            saveFile.delete()
            null
        } catch (e: java.io.IOException) {
            null
        }
    }

    suspend fun hasSavedGame(): Boolean = withContext(Dispatchers.IO) {
        saveFile.exists()
    }

    suspend fun deleteSave() = withContext(Dispatchers.IO) {
        saveFile.delete()
    }

    private fun serializeState(state: GameState, elapsedSeconds: Long): JSONObject {
        return JSONObject().apply {
            put("difficulty", state.difficulty.name)
            put("elapsedSeconds", elapsedSeconds)
            put("hintsUsed", state.hintsUsed)
            put("errorCount", state.errorCount)
            put("isNoteMode", state.isNoteMode)
            put("isCompleted", state.isCompleted)
            put("selectedRow", state.selectedRow)
            put("selectedCol", state.selectedCol)
            put("cells", serializeCells(state.cells))
            put("solution", serializeSolution(state.solution))
        }
    }

    private fun serializeCells(cells: List<List<Cell>>): JSONArray {
        return JSONArray().apply {
            for (row in cells) {
                val rowArr = JSONArray()
                for (cell in row) {
                    rowArr.put(JSONObject().apply {
                        put("v", cell.value)
                        put("g", cell.isGiven)
                        if (cell.notes.isNotEmpty()) {
                            put("n", JSONArray(cell.notes.toList()))
                        }
                    })
                }
                put(rowArr)
            }
        }
    }

    private fun serializeSolution(solution: List<List<Int>>): JSONArray {
        return JSONArray().apply {
            for (row in solution) {
                put(JSONArray(row))
            }
        }
    }

    private fun deserializeState(json: JSONObject): GameState {
        val difficulty = Difficulty.valueOf(json.getString("difficulty"))
        val cells = deserializeCells(json.getJSONArray("cells"))
        val solution = deserializeSolution(json.getJSONArray("solution"))

        return GameState(
            cells = cells,
            solution = solution,
            difficulty = difficulty,
            hintsUsed = json.getInt("hintsUsed"),
            errorCount = json.optInt("errorCount", 0),
            isNoteMode = json.optBoolean("isNoteMode", false),
            isCompleted = json.optBoolean("isCompleted", false),
            selectedRow = json.optInt("selectedRow", -1),
            selectedCol = json.optInt("selectedCol", -1),
            restoredElapsedSeconds = json.getLong("elapsedSeconds")
        )
    }

    private fun deserializeCells(arr: JSONArray): List<List<Cell>> {
        return List(9) { r ->
            val rowArr = arr.getJSONArray(r)
            List(9) { c ->
                val obj = rowArr.getJSONObject(c)
                val notes = if (obj.has("n")) {
                    val notesArr = obj.getJSONArray("n")
                    (0 until notesArr.length()).map { notesArr.getInt(it) }.toSet()
                } else emptySet()
                Cell(
                    row = r,
                    col = c,
                    value = obj.getInt("v"),
                    isGiven = obj.getBoolean("g"),
                    notes = notes
                )
            }
        }
    }

    private fun deserializeSolution(arr: JSONArray): List<List<Int>> {
        return List(9) { r ->
            val rowArr = arr.getJSONArray(r)
            List(9) { c -> rowArr.getInt(c) }
        }
    }
}
