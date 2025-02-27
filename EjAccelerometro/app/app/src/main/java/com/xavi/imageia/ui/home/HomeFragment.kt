package com.xavi.imageia.ui.home

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import androidx.core.content.ContextCompat.getSystemService
import com.xavi.imageia.databinding.FragmentHomeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
//ullada
class HomeFragment : Fragment(), OnInitListener,SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometre: Sensor? = null
    private lateinit var textToSpeech: TextToSpeech
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    var LOG_TAG = "PRUEBAS"
    private var lastTime: Long = 0
    private val tiempoEspera = 5000
    private var contGolpes = 0
    private var isUploading = false
    //private var procesando = false

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val permissionGranted = permissions.entries.all { it.value }
            if (!permissionGranted) {
                Toast.makeText(context, "Permissions not granted.", Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        textToSpeech = TextToSpeech(requireContext(), this)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometre = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        if (accelerometre != null) {
            sensorManager.registerListener(this, accelerometre, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            Log.i("Sensor", "El sensor de aceleración lineal no está disponible en este dispositivo.")
        }

        // Permitir operaciones de red en el hilo principal (solo para pruebas)
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Configurar el botón button4
        binding.button4.setOnClickListener {
            takePhoto()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        homeViewModel.text.observe(viewLifecycleOwner) { /* Observa cambios en ViewModel */ }

        return binding.root
    }

    private fun takePhoto() {
        if (isUploading) {
            Log.i(LOG_TAG, "Espera a que termine la subida.")
            return
        }

        isUploading = true // Bloquear la toma de fotos

        val imageCapture = imageCapture ?: run {
            Log.e(LOG_TAG, "imageCapture no está inicializado")
            isUploading = false // En caso de error, desbloquear
            return
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale("es", "ES"))
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(LOG_TAG, "Error al capturar la foto: ${exc.message}", exc)
                    isUploading = false // Desbloquear en caso de error
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri
                    val msg = "Foto capturada y guardada: ${output.savedUri}"
                    Log.d(LOG_TAG, msg)

                    val imageBytes = savedUri?.let { uri ->
                        requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                            inputStream.readBytes()
                        }
                    }

                    if (imageBytes != null) {
                        val postUrl = "https://imagia1.ieti.site/api/analitzar-imatge"

                        // Subir la imagen de manera asíncrona
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val response = uploadImage(postUrl, imageBytes)
                                val jObject = JSONObject(response)
                                val serverResponse = jObject.getString("response")

                                withContext(Dispatchers.Main) {
                                    speakText(serverResponse)
                                    Log.i("subirFoto", "Respuesta: $response")
                                    isUploading = false // Desbloquear cuando reciba la respuesta
                                }
                            } catch (e: Exception) {
                                Log.e("subirFoto", "Error subiendo la imagen", e)
                                isUploading = false // Desbloquear en caso de error
                            }
                        }
                    } else {
                        Log.e(LOG_TAG, "No se pudo leer la imagen.")
                        isUploading = false // Desbloquear en caso de error
                    }
                }
            }
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(LOG_TAG, "Error al vincular casos de uso", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }
    private fun speakText(text: String) {
        // Hacer que el TextToSpeech lea el texto ingresado
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Establecer el idioma como español de España
            val loc = Locale("es","ES")
            val langResult = textToSpeech.setLanguage(loc)

            // Verificar si el idioma está disponible
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(context, "Idioma no soportado o no disponible", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Error al inicializar Text-to-Speech", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        if (this::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val LOG_TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

//    multipart/form-data; boundary=<calculated when request is sent>
    
    private fun uploadImage(postUrl: String, imageBytes: ByteArray): String {
        Log.d("uploadImage", "Iniciando subida de imagen a $postUrl")
        return try {
            val boundary = "Boundary-${System.currentTimeMillis()}"
            val url = URL(postUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val outputStream = DataOutputStream(connection.outputStream)
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            // Parte de inicio del formulario
            outputStream.writeBytes(twoHyphens + boundary + lineEnd)
            outputStream.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"image.png\"$lineEnd")
            outputStream.writeBytes("Content-Type: image/png$lineEnd")
            outputStream.writeBytes(lineEnd)

            outputStream.write(imageBytes)
            outputStream.writeBytes(lineEnd)
            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
            outputStream.flush()
            outputStream.close()

            val responseCode = connection.responseCode
            Log.d("uploadImage", "Código de respuesta: $responseCode")
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                Log.d("uploadImage", "Respuesta del servidor: $response")
                response.toString()

            } else {
                Log.e("uploadImage", "Error del servidor: $responseCode ${connection.responseMessage}")
                "Error del servidor: $responseCode ${connection.responseMessage}"
            }
        } catch (e: Exception) {
            Log.e("uploadImage", "Error: ${e.message}", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val zAxy = event?.values?.get(2)
        if (zAxy != null) {
//            Log.i("SensorJuan", zAxy.toString())
            if(zAxy >= 2 && zAxy < 10){
                val tiempo = System.currentTimeMillis()
                contGolpes += 1
                if ((tiempo - lastTime) < tiempoEspera && contGolpes == 2){
                    Log.i("SensorJuan", "entro a sacar foto: " + contGolpes.toString())
                    takePhoto()
                    contGolpes = 0
                    lastTime = 0
                }else{
                    Log.i("SensorJuan", "entro al else de sacar foto")
                    if (contGolpes > 2){
                        contGolpes = 0;
                    }
                    lastTime = tiempo
                }
                Log.i("Sensor", zAxy.toString())
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //
    }
    override fun onPause(){
        super.onPause()
        sensorManager.unregisterListener(this)

    }
}
