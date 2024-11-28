package com.example.magneticlocalization

import android.content.ContentValues
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var position = Pair(7, -13)
    private var doAutoPin = false
    private var isObstacle = false

    private lateinit var graphView: GraphView

    private lateinit var sensorManager: SensorManager
    private var magnetometer: Sensor? = null
    private var uncalibratedMagnetometer: Sensor? = null

    private var magnetometerValues = FloatArray(3) { 0f }
    private var uncalibratedMagnetometerValues = FloatArray(3) { 0f }

    private val recordedData = mutableMapOf<Pair<Int, Int>, String>() // position별 데이터 저장

    private lateinit var positionTextView: TextView
    private lateinit var magnetometerMagnitudeTextView: TextView
    private lateinit var uncalibratedMagnetometerMagnitudeTextView: TextView
    private lateinit var magnetometerXYZTextView: TextView
    private lateinit var uncalibratedMagnetometerXYZTextView: TextView

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            importDataFromUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        graphView = findViewById(R.id.graphView)

        // 상단 정보 텍스트뷰
        positionTextView = findViewById(R.id.positionTextView)
        magnetometerMagnitudeTextView = findViewById(R.id.magnetometerMagnitudeTextView)
        uncalibratedMagnetometerMagnitudeTextView = findViewById(R.id.uncalibratedMagnetometerMagnitudeTextView)
//        magnetometerXYZTextView = findViewById(R.id.magnetometerXYZTextView)
//        uncalibratedMagnetometerXYZTextView = findViewById(R.id.uncalibratedMagnetometerXYZTextView)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        uncalibratedMagnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)

        findViewById<CheckBox>(R.id.autoPinCheckBox).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                doAutoPin = true
                Toast.makeText(this, "자동 Pin 모드", Toast.LENGTH_SHORT).show()
            } else {
                doAutoPin = false
                Toast.makeText(this, "자동 Pin 모드가 해제됨", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<CheckBox>(R.id.obstacleCheckBox).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                isObstacle = true
                Toast.makeText(this, "장애물 기록 모드", Toast.LENGTH_SHORT).show()
            } else {
                isObstacle = false
                Toast.makeText(this, "장애물 기록 모드가 해제됨", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.buttonLeft).setOnClickListener { movePosition(-1, 0) }
        findViewById<Button>(R.id.buttonRight).setOnClickListener { movePosition(1, 0) }
        findViewById<Button>(R.id.buttonUp).setOnClickListener { movePosition(0, 1) }
        findViewById<Button>(R.id.buttonDown).setOnClickListener { movePosition(0, -1) }

        findViewById<Button>(R.id.buttonPin).setOnClickListener { pinNode() }
        findViewById<Button>(R.id.buttonImport).setOnClickListener { importDataFromCsv() }
        findViewById<Button>(R.id.buttonExport).setOnClickListener { exportDataToCsv() }
        findViewById<Button>(R.id.buttonDelete).setOnClickListener { showDeleteConfirmationDialog() }

//        findViewById<Button>(R.id.buttonSettings).setOnClickListener { showSettingsDialog() }

        positionTextView.setOnClickListener { showPositionDialog() }

        graphView.updateCursor(position)
    }

    private fun showSettingsDialog() {
        // 현재 x, y 값으로 다이얼로그를 표시
        val (gapLength, correctionLength) = graphView.getSettings()

        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)

        // EditText를 다이얼로그에 추가
        val gapEditText = dialogView.findViewById<EditText>(R.id.gapEditText)
        val corrEditText = dialogView.findViewById<EditText>(R.id.correctionEditText)

        // 초기값 설정
        gapEditText.setText(gapLength.toString())
        corrEditText.setText(correctionLength.toString())

        builder.setView(dialogView)
            .setTitle("Settings")
            .setPositiveButton("OK") { _, _ ->
                // OK 클릭 시 x, y 값 업데이트
                val newGap = gapEditText.text.toString().toFloatOrNull()
                val newCorr = corrEditText.text.toString().toFloatOrNull()

                if (newGap != null && newCorr != null) {
                    graphView.editSettings(newGap, newCorr)
                } else {
                    Toast.makeText(this, "유효한 값을 입력하세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss() // 취소 시 다이얼로그 닫기
            }

        builder.create().show()
    }

    private fun showPositionDialog() {
        // 현재 x, y 값으로 다이얼로그를 표시
        val x = position.first
        val y = position.second

        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_position, null)

        // EditText를 다이얼로그에 추가
        val xEditText = dialogView.findViewById<EditText>(R.id.xEditText)
        val yEditText = dialogView.findViewById<EditText>(R.id.yEditText)

        // 초기값 설정
        xEditText.setText(x.toString())
        yEditText.setText(y.toString())

        builder.setView(dialogView)
            .setTitle("position 이동")
            .setPositiveButton("OK") { _, _ ->
                // OK 클릭 시 x, y 값 업데이트
                val newX = xEditText.text.toString().toIntOrNull()
                val newY = yEditText.text.toString().toIntOrNull()

                if (newX != null && newY != null) {
                    position = Pair(newX, newY) // Pair 값 수정
                    updatePositionTextView() // TextView 업데이트
                    graphView.updateCursor(position)
                } else {
                    Toast.makeText(this, "유효한 값을 입력하세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss() // 취소 시 다이얼로그 닫기
            }

        builder.create().show()
    }

    private fun movePosition(dx: Int, dy: Int, allowAutoPin: Boolean = true) {
        position = Pair(position.first + dx, position.second + dy)
        updatePositionTextView()
        graphView.updateCursor(position)

        if(allowAutoPin && doAutoPin) {
            pinNode()
        }
    }

    private fun pinNode() {
        if (!isObstacle) {
            val magnitude = calculateMagnitude(uncalibratedMagnetometerValues).toInt()
            graphView.addNode(position, magnitude)

            val currentData = "${magnetometerValues.joinToString(",")},${uncalibratedMagnetometerValues.joinToString(",")}"
            recordedData[position] = currentData
        } else {
            graphView.addObstacleNode(position)
            recordedData[position] = "9999.9,9999.9,9999.9,9999.9,9999.9,9999.9,9999.9,9999.9,9999.9"
        }
    }

    private fun showDeleteConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("삭제 확인")
            .setMessage("${position} 데이터를 정말 삭제하시겠습니까?")
            .setPositiveButton("예") { dialog, _ ->
                deleteData()
                dialog.dismiss()
            }
            .setNegativeButton("아니오") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun deleteData() {
        recordedData.remove(position)
        graphView.removeNode(position)
        showToast("삭제되었습니다.")
    }

    private fun exportDataToCsv() {
        val fileName = "position_data.csv"
//        val file = File(getExternalFilesDir(null), fileName)
        val csvData = buildString {
            append("X,Y,MagX,MagY,MagZ,UncalMagX,UncalMagY,UncalMagZ,BiasX,BiasY,BiasZ\n")
            recordedData.forEach { (pos, data) ->
                append("${pos.first},${pos.second},$data\n")
            }
        }
//        FileOutputStream(file).use { it.write(csvContent.toByteArray()) }
        try {
            // Android 10(API 29) 이상에서는 MediaStore를 사용하여 저장
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = applicationContext.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it).use { outputStream ->
                        outputStream?.write(csvData.toString().toByteArray())
                        showToast("File saved to Downloads folder.")
                    }
                }
            } else {
                // Android 9(API 28) 이하에서는 앱 전용 디렉토리에 저장
                val file = File(
                    getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
                file.writeText(csvData.toString())
                showToast("File saved: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Failed to export file: ${e.message}")
        }
    }

    private fun importDataFromCsv() {
        filePickerLauncher.launch(arrayOf("text/csv", "application/octet-stream", "*/*"))
    }

    private fun importDataFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val lines = reader.readLines()
                if (lines.isNotEmpty()) {
                    val dataLines = lines.drop(1) // 첫 줄은 헤더

                    // recordedData 초기화 및 CSV 데이터 로드
                    recordedData.clear()
                    graphView.clearNodes()

                    for (line in dataLines) {
                        val values = line.split(",")
                        if (values.size >= 11) {
                            val x = values[0].toIntOrNull()
                            val y = values[1].toIntOrNull()
                            val magData = values.subList(2, values.size).joinToString(",")

                            if (x != null && y != null) {
                                recordedData[Pair(x, y)] = magData
                                if ((values[5] != "9999.9") && (values[6] != "9999.9") && (values[7] != "9999.9")) {
                                    val magnitude = calculateMagnitude(values.subList(5,8).map { it.toFloat() }.toFloatArray()).toInt()
                                    graphView.addNode(Pair(x, y), magnitude)
                                } else {
                                    graphView.addObstacleNode(Pair(x, y))
                                }
                            }
                        }
                    }
                    showToast("Data imported successfully!")
                } else {
                    showToast("The file is empty.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Failed to import file: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun calculateMagnitude(values: FloatArray): Float {
        return Math.sqrt(
            (values[0] * values[0] + values[1] * values[1] + values[2] * values[2]).toDouble()
        ).toFloat()
    }

    private fun updatePositionTextView() {
        val magnetometerMagnitude = calculateMagnitude(magnetometerValues)
        val uncalibratedMagnitude = calculateMagnitude(uncalibratedMagnetometerValues)

        positionTextView.text = "Position: (${position.first}, ${position.second})"
        magnetometerMagnitudeTextView.text = "Mag: ${magnetometerMagnitude.roundToInt()} µT"
        uncalibratedMagnetometerMagnitudeTextView.text = "Uncali: ${uncalibratedMagnitude.roundToInt()} µT"

//        magnetometerXYZTextView.text = "Magnetometer: x=${magnetometerValues[0].roundToInt()}, y=${magnetometerValues[1].roundToInt()}, z=${magnetometerValues[2].roundToInt()}"
//        uncalibratedMagnetometerXYZTextView.text = "Uncalibrated: x=${uncalibratedMagnetometerValues[0].roundToInt()}, y=${uncalibratedMagnetometerValues[1].roundToInt()}, z=${uncalibratedMagnetometerValues[2].roundToInt()}"
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetometerValues = event.values
            }
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> {
                uncalibratedMagnetometerValues = event.values
            }
        }
        updatePositionTextView()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        uncalibratedMagnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}
