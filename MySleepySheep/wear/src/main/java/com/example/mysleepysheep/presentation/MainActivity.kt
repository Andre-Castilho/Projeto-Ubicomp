package com.example.mysleepysheep.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.mysleepysheep.R
import androidx.compose.ui.tooling.preview.Preview
import com.example.mysleepysheep.presentation.theme.MySleepySheepTheme
import android.content.pm.PackageManager
import android.os.CountDownTimer
import android.view.WindowManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Scaffold
import java.sql.Array
import kotlin.math.abs

// Classe responsável pelo timer que avalia os dados dos sensores durante o tempo definido
class HeartRateTimer(
    millisInFuture: Long,
    countDownInterval: Long,
    val getBpm: () -> Float,
    val getLx: () -> Float,
    val getAccelerometer: () -> Triple<Float, Float, Float>,
    val updateTime: (Int) -> Unit,
    val onResult: (String) -> Unit
) : CountDownTimer(millisInFuture, countDownInterval) {

    // Variáveis para armazenar valores do acelerômetro em segundos pares e ímpares
    var accelerometer_even_second_value = 0f
    var accelerometer_odd_second_value = 0f
    var movementCount = 0 // Contador de movimentos detectados
    var sleepCycles = mutableListOf<String>() // Lista para armazenar os ciclos de sono detectados

    // Método chamado a cada tick do timer
    override fun onTick(millisUntilFinished: Long) {
        val seconds = (millisUntilFinished / 1000).toInt() // Calcula o tempo restante em segundos
        updateTime(seconds) // Atualiza o tempo na UI

        val bpm = getBpm() // Obtém o batimento cardíaco atual
        val accelerometer = getAccelerometer() // Obtém os valores do acelerômetro
        val totalAcceleration = abs(accelerometer.first + accelerometer.second + accelerometer.third) // Soma total das acelerações

        // Se o BPM estiver muito alto, cancela o timer
        if (bpm > 120f) {
            Log.d("TIMER", "High BPM detected! Cancelling timer.")
            cancel()
        }

        // Armazena valores do acelerômetro em segundos pares e ímpares
        if (seconds % 2 == 0) {
            accelerometer_even_second_value = totalAcceleration
        } else {
            accelerometer_odd_second_value = totalAcceleration
        }

        // Se a diferença entre os valores for grande, conta como movimento
        if(abs(accelerometer_even_second_value - accelerometer_odd_second_value) >= 2.5f) {
            movementCount ++
        }
    }

    // Método chamado quando o timer termina
    override fun onFinish() {
        updateTime(0) // Atualiza o tempo para zero na UI
        val lx = getLx() // Obtém o valor do sensor de luz
        val bpm = getBpm() // Obtém o batimento cardíaco
        val sleepMap = mutableMapOf("Acordado" to 0, "REM" to 0, "Sono Profundo" to 0, "Sono Leve" to 0) // Mapa para contar tipos de sono

        Log.d("TIMER", "Done")
        Log.d("TIMER", "current lx: $lx")
        val sleepType : String // Variável para armazenar o tipo de sono detectado

        // Lógica para determinar o tipo de sono baseado nos sensores
        if (lx >= 150) {
            Log.d("Acordado", "Muito Claro")
            sleepType = "Acordado"
        } else {
            Log.d("OK", "Escuro")
            if(movementCount >= 5) {
                Log.d("Acordado", "Muito Movimento")
                sleepType = "Acordado"
            } else {
                Log.d("OK", "Pouco Movimento")
                if(bpm >= 100) {
                    Log.d("Acordado", "BPM Muito Alto")
                    sleepType = "Acordado"
                } else {
                    Log.d("OK", "BPM Abaixo de 100")
                    if(bpm <= 60) {
                        if(movementCount <= 2) {
                            Log.d("SONO", "REM")
                            sleepType = "REM"
                        } else {
                            Log.d("SONO", "Sono Profundo")
                            sleepType = "Sono Profundo"
                        }
                    } else if(bpm <= 80) {
                        if(movementCount <= 2) {
                            Log.d("SONO", "Sono Profundo")
                            sleepType = "Sono Profundo"
                        } else {
                            Log.d("SONO", "Sono Leve")
                            sleepType = "Sono Leve"
                        }
                    } else {
                        Log.d("SONO", "Sono Leve")
                        sleepType = "Sono Leve"
                    }
                }
            }
        }

        sleepCycles.add(sleepType) // Adiciona o tipo de sono detectado à lista

        movementCount = 0 // Reseta o contador de movimentos
        // Se já foram detectados 9 ciclos de sono, faz a análise final
        if(sleepCycles.size >= 9) {
            Log.d("TIMER", "Nono ciclo de sono detectado, encerrando Timer")
            for((index, sleepCycle) in sleepCycles.withIndex()) {
                Log.d("TIMER", "Tipo de sono: $sleepCycle no ciclo de sono: ${index + 1}")
                sleepMap[sleepCycle] = sleepMap[sleepCycle]!! + 1
            }
            if (sleepMap["Acordado"]!! >= 5) {
                Log.d("RESULTADO", "Pouco Descanso")
                onResult("Pouco Descanso")
            } else {
                if (sleepMap["REM"]!! >= 1 && sleepMap["Sono Profundo"]!! + sleepMap["Sono Leve"]!! >= 4) {
                    Log.d("RESULTADO", "Boa Noite de Sono")
                    onResult("Boa Noite de Sono")
                } else {
                    Log.d("RESULTADO", "Sono não muito bom")
                    onResult("Sono não muito bom")
                }
            }
            cancel() // Para o timer aqui
        } else {
            start() // reinicia, se quiser comportamento cíclico e não atingiu 9 ciclos
        }
    }
}

// Classe principal da Activity do app
class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: SensorManager // Gerenciador de sensores
    private var accelerometer: Sensor? = null // Sensor de acelerômetro
    private var heartRateSensor: Sensor? = null // Sensor de batimento cardíaco
    private var lightSensorSensor: Sensor? = null // Sensor de luz

    // Variáveis de estado para armazenar valores dos sensores e tempo
    private var _sensorValues = mutableStateOf(Triple(0f, 0f, 0f))
    private var _heartRate = mutableStateOf(0f)
    private var _lightSensor = mutableStateOf(0f)
    private var _timeLeft = mutableStateOf(30) // em segundos
    val timeLeft: State<Int> get() = _timeLeft

    val sensorValues: State<Triple<Float, Float, Float>> get() = _sensorValues
    val heartRate: State<Float> get() = _heartRate
    val lightSensor: State<Float> get() = _lightSensor

    private var _sleepResult = mutableStateOf<String?>(null)
    val sleepResult: State<String?> get() = _sleepResult

    private lateinit var heartRateTimer: HeartRateTimer // Instância do timer

    // Listener para receber atualizações dos sensores
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    _sensorValues.value = Triple(x, y, z)
                }

                Sensor.TYPE_HEART_RATE -> {
                    val bpm = event.values[0]
                    _heartRate.value = bpm
                }

                Sensor.TYPE_LIGHT -> {
                    val lx = event.values[0]
                    _lightSensor.value = lx
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    //Metodo chamado ao criar a Activity
    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // Mantém a tela ligada

        super.onCreate(savedInstanceState)

        // Solicita permissão para acessar sensores corporais, se necessário
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.BODY_SENSORS),
                1001 // requestCode
            )
        }

        // Inicializa os sensores
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        lightSensorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        // Cria o timer com as funções para obter os valores dos sensores
        heartRateTimer = HeartRateTimer(
            30000,
            1000,
            getBpm = { _heartRate.value },
            getLx = { _lightSensor.value },
            getAccelerometer = { _sensorValues.value },
            updateTime = { seconds -> _timeLeft.value = seconds },
            onResult = { result -> _sleepResult.value = result }
        )

        heartRateTimer.start() // Inicia o timer

        // Define o conteúdo da tela usando Compose
        setContent {
            WearApp(
                sensorValues = sensorValues.value,
                bpm = heartRate.value,
                lx = lightSensor.value,
                timeLeft = timeLeft.value,
                sleepResult = sleepResult.value
            )
        }
    }

    // Registra os listeners dos sensores ao retomar a Activity
    override fun onResume() {
        super.onResume()
        accelerometer?.also {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        heartRateSensor?.also {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        lightSensorSensor?.also {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    // Remove os listeners dos sensores ao pausar a Activity
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorListener)
    }
}

// Função principal da interface do usuário usando Compose
@Composable
fun WearApp(
    sensorValues: Triple<Float, Float, Float>,
    bpm: Float,
    lx: Float,
    timeLeft: Int,
    sleepResult: String? // <-- novo parâmetro
) {
    MySleepySheepTheme {
        Scaffold(
            timeText = { TimeText() }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Imagem de fundo do carneirinho
                Image(
                    painter = painterResource(id = R.drawable.sheep),
                    contentDescription = "Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Conteúdo rolável para evitar corte em telas pequenas
                ScalingLazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (sleepResult == null) {
                        item {
                            TimerDisplay(timeLeft)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        /*
                    item {
                        SensorDisplay(sensorValues)
                        Spacer(modifier = Modifier.height(8.dp))
                    }*/
                        item {
                            HeartRateDisplay(bpm)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        item {
                            LightSensorDisplay(lx)
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "${getEmojiForSleepResult(sleepResult)} \n $sleepResult ",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                               /* Text(
                                    text = "Resultado: $sleepResult",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )*/

                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun getEmojiForSleepResult(sleepResult: String?): String {
    return when (sleepResult) {
        "Boa Noite de Sono" -> "😊" // Happy emoji
        "Sono não muito bom" -> "😔" // Sad emoji
        "Pouco Descanso" -> "😠" // Awake/angry emoji - you might want to adjust this
        else -> "" // No emoji if result is null or unknown
    }
}

// Componente para exibir os valores do acelerômetro
@Composable
fun SensorDisplay(sensorValues: Triple<Float, Float, Float>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "📐 Acelerômetro",
            color = Color.Cyan,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "X: ${sensorValues.first.format(2)}, Y: ${sensorValues.second.format(2)}, Z: ${sensorValues.third.format(2)}",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

// Componente para exibir o batimento cardíaco
@Composable
fun HeartRateDisplay(bpm: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "❤️ Batimentos Cardíacos",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (bpm > 0) "${bpm.toInt()} BPM" else "Sem leitura...",
            color = if (bpm > 0) Color.Red else Color.Gray,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// Componente para exibir o valor do sensor de luz
@Composable
fun LightSensorDisplay(lx: Float) {
    val isBright = lx > 150
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "💡 Luz Ambiente",
            color = Color(0xFFBBDEFB), // Azul claro
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isBright) "Muito Claro" else "Boa noite",
            color = if (isBright) Color.Yellow else Color.Cyan,
            fontSize = 14.sp
        )
    }
}

// Componente para exibir o tempo restante do timer
@Composable
fun TimerDisplay(timeLeft: Int) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$timeLeft s",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

// Função de extensão para formatar floats com casas decimais
fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
//var i = 2f;
