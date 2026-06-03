package com.sudoku.game.ui.component

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sudoku.game.model.DemoChallenge
import com.sudoku.game.model.DemoController
import java.util.Locale

/**
 * The offline demo player panel shown below the board: a subtitle (technique +
 * narration + step indicator), playback controls, the optional "your turn"
 * challenge, and lifecycle-bound TTS (default muted). Pure UI — all moves are
 * delegated up to the ViewModel.
 */
@Composable
fun DemoPlayer(
    controller: DemoController,
    challenge: DemoChallenge?,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplay: () -> Unit,
    onExit: () -> Unit,
    onTry: () -> Unit,
    onSubmit: (Int) -> Unit,
    onCancelChallenge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val step = controller.current
    val awaiting = challenge != null && challenge.result == null

    // TTS shown muted by default; speak the current narration only when unmuted.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var muted by remember { mutableStateOf(true) }
    var ttsReady by remember { mutableStateOf(false) }
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = engine?.setLanguage(Locale.CHINESE) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = res >= TextToSpeech.LANG_AVAILABLE
            }
        }
        ttsRef.value = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
            ttsRef.value = null
        }
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) ttsRef.value?.stop()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    // Don't speak during an awaiting challenge — it would read out the answer.
    val spoken = if (awaiting) null else step?.narration
    LaunchedEffect(spoken, muted, ttsReady) {
        val t = ttsRef.value
        if (muted || !ttsReady || t == null) {
            t?.stop()
        } else if (spoken != null) {
            t.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "demo")
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (awaiting) "轮到你 ✋" else (step?.techniqueName ?: ""),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${controller.stepNumber} / ${controller.totalSteps}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            when {
                awaiting -> Text(
                    text = "看高亮想一想：第${challenge!!.row + 1}行第${challenge.col + 1}列应该填什么？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                challenge?.result == DemoChallenge.Result.CORRECT -> {
                    Text(
                        text = "✅ 答对了！",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = step?.narration ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                challenge?.result == DemoChallenge.Result.WRONG -> {
                    Text(
                        text = "❌ 正确答案是 ${challenge.answer}。",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = step?.narration ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> Text(
                    text = step?.narration ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (awaiting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (n in 1..9) {
                        OutlinedButton(
                            onClick = { onSubmit(n) },
                            modifier = Modifier.size(32.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(text = n.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancelChallenge) { Text("取消") }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onPrev, enabled = controller.hasPrev) { Text("◀ 上一步") }
                    TextButton(onClick = onNext, enabled = controller.hasNext) { Text("下一步 ▶") }
                    TextButton(onClick = onReplay, enabled = !controller.isAtStart) { Text("⟲ 重播") }
                    if (ttsReady) {
                        TextButton(onClick = { muted = !muted }) { Text(if (muted) "🔇" else "🔊") }
                    }
                    TextButton(onClick = onExit) { Text("✕ 退出") }
                }
                if (step?.placement != null && challenge == null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Button(onClick = onTry) { Text("✋ 我来填") }
                    }
                }
            }
        }
    }
}
