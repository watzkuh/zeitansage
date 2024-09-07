package io.watzkuh.zeitansage

import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.View.FOCUSABLE
import android.view.View.NOT_FOCUSABLE
import android.widget.EditText
import android.widget.Switch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.watzkuh.zeitansage.R.id.interval
import io.watzkuh.zeitansage.R.id.switchy
import io.watzkuh.zeitansage.ui.theme.ZeitansageTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.CoroutineContext


class MainActivity : ComponentActivity(), CoroutineScope {
    private lateinit var zeitAnsager: ZeitAnsager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        zeitAnsager = ZeitAnsager(this, lifecycle, coroutineContext)
        lifecycle.addObserver(zeitAnsager)
        val switch = findViewById<Switch>(switchy)
        val interval = findViewById<EditText>(interval)
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                interval.disable()
                var intervalInMillis: Long = 10_000

                interval.text.toString().toLongOrNull()?.let {
                    intervalInMillis = it * 60 * 1000
                }
                zeitAnsager.start(intervalInMillis)
            } else {
                interval.enable()
                zeitAnsager.cancel()
            }
        }
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main
}

fun EditText.disable() {
    inputType = InputType.TYPE_NULL
    isCursorVisible = false
    background.alpha = 0
}

fun EditText.enable() {
    inputType = InputType.TYPE_CLASS_NUMBER
    isCursorVisible = true
    background.alpha = 255
}


internal class ZeitAnsager(
    private val context: Context,
    private val lifecycle: Lifecycle,
    override val coroutineContext: CoroutineContext
) : DefaultLifecycleObserver, CoroutineScope {
    private lateinit var tts: TextToSpeech
    private var isActive = false

    override fun onCreate(owner: LifecycleOwner) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            tts = TextToSpeech(context, fun(status) {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.US)
                    tts.setVoice(tts.defaultVoice)
                }
            })
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        tts.shutdown()
    }

    fun start(interval: Long) {
        isActive = true
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        launch(Dispatchers.IO) {
            val attributes =
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            runBlocking {
                while (isActive) {
                    val focusRequest =
                        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(attributes).build()
                    val focus = audioManager.requestAudioFocus(focusRequest)
                    delay(500)
                    if (focus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        sayTime()
                        delay(2500)
                    }
                    audioManager.abandonAudioFocusRequest(focusRequest)
                    delay(interval)
                }
            }
        }
    }

    fun cancel() {
        isActive = false
        tts.stop()
    }

    private fun sayTime() {
        val df: DateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val date: String = df.format(Calendar.getInstance().time)
        tts.speak("It is $date", TextToSpeech.QUEUE_FLUSH, null, null);
    }
}