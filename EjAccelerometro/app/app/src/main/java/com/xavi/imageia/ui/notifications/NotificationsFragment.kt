package com.xavi.imageia.ui.notifications

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.xavi.imageia.databinding.FragmentNotificationsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val editTextUser: EditText = binding.userText
        val editTextPhone: EditText = binding.phoneText
        val editTextMail: EditText = binding.mailText
        val editTextPassword: EditText = binding.passwordText
        val button: Button = binding.confButton

        // Cargar los datos guardados
        loadUserData(editTextUser, editTextPhone, editTextMail, editTextPassword)

        button.setOnClickListener {
            val user = editTextUser.text.toString()
            val phone = editTextPhone.text.toString()
            val mail = editTextMail.text.toString()
            val password = editTextPassword.text.toString()

            // Guardar los datos en SharedPreferences
            saveUserData(user, phone, mail, password)

            // Registrar el usuario en el servidor
            registerUser(phone, user, mail, password)
        }

        return root
    }

    private fun loadUserData(
        editTextUser: EditText, editTextPhone: EditText, editTextMail: EditText, editTextPassword: EditText
    ) {
        val sharedPref = requireContext().getSharedPreferences("UserData", Context.MODE_PRIVATE)
        val user = sharedPref.getString("user", "") ?: ""
        val phone = sharedPref.getString("phone", "") ?: ""
        val mail = sharedPref.getString("mail", "") ?: ""
        val password = sharedPref.getString("password", "") ?: ""

        editTextUser.setText(user)
        editTextPhone.setText(phone)
        editTextMail.setText(mail)
        editTextPassword.setText(password)
    }

    private fun saveUserData(user: String, phone: String, mail: String, password: String) {
        val sharedPref = requireContext().getSharedPreferences("UserData", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("user", user)
            putString("phone", phone)
            putString("mail", mail)
            putString("password", password)
            apply()
        }
    }

    private fun registerUser(phone: String, nickname: String, email: String, password: String) {
        val postUrl = "https://imagia1.ieti.site/api/usuaris/registrar"
        val requestBody = JSONObject().apply {
            put("telefon", phone)
            put("nickname", nickname)
            put("email", email)
            put("password", password)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(postUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")

                val outputStream = OutputStreamWriter(connection.outputStream)
                outputStream.write(requestBody.toString())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                val responseMessage = connection.inputStream.bufferedReader().use { it.readText() }

                withContext(Dispatchers.Main) {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(requireContext(), "Registro exitoso", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Error: $responseMessage", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
                }
                Log.e("registerUser", "Error: ${e.message}", e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
