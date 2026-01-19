package com.example.vehicletrackingprototype

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var auth: FirebaseAuth

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Check if already logged in
        if (auth.currentUser != null) {
            Log.d(TAG, "User already logged in: ${auth.currentUser?.email}")
            goToMain()
            return
        }

        edtEmail = findViewById(R.id.edtEmail)
        edtPassword = findViewById(R.id.edtPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)

        Log.d(TAG, "LoginActivity created successfully")

        // REGISTER BUTTON
        btnRegister.setOnClickListener {
            Log.d(TAG, "Register button clicked")
            val email = edtEmail.text.toString().trim()
            val password = edtPassword.text.toString().trim()

            Log.d(TAG, "Email: $email, Password length: ${password.length}")

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Empty fields")
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Invalid email format")
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Password too short")
                return@setOnClickListener
            }

            registerUser(email, password)
        }

        // LOGIN BUTTON
        btnLogin.setOnClickListener {
            Log.d(TAG, "Login button clicked")
            val email = edtEmail.text.toString().trim()
            val password = edtPassword.text.toString().trim()

            Log.d(TAG, "Email: $email, Password length: ${password.length}")

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Empty fields")
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Invalid email format")
                return@setOnClickListener
            }

            loginUser(email, password)
        }
    }

    private fun registerUser(email: String, password: String) {
        btnRegister.isEnabled = false
        btnLogin.isEnabled = false

        Log.d(TAG, "Attempting to register user: $email")

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val userId = result.user?.uid ?: return@addOnSuccessListener
                Log.d(TAG, "User registered successfully: $userId")

                // Save user data to database with explicit URL
                val databaseUrl = "https://vehicletrackingprototype-d8b88-default-rtdb.asia-southeast1.firebasedatabase.app/"
                val userData = mapOf(
                    "email" to email,
                    "createdAt" to System.currentTimeMillis()
                )

                FirebaseDatabase.getInstance(databaseUrl).reference
                    .child("users").child(userId)
                    .setValue(userData)
                    .addOnSuccessListener {
                        Log.d(TAG, "User data saved to database at: $databaseUrl")
                        Toast.makeText(this, "Registered successfully!", Toast.LENGTH_SHORT).show()
                        goToMain()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error saving user data", e)
                        btnRegister.isEnabled = true
                        btnLogin.isEnabled = true
                        Toast.makeText(this, "Error saving user data: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Registration failed", e)
                btnRegister.isEnabled = true
                btnLogin.isEnabled = true

                val errorMessage = when {
                    e is FirebaseAuthException -> {
                        when (e.errorCode) {
                            "ERROR_INVALID_EMAIL" -> "❌ Invalid email format. Please enter a valid email address."
                            "ERROR_EMAIL_ALREADY_IN_USE" -> "⚠️ This email is already registered.\nPlease use the LOGIN button instead."
                            "ERROR_WEAK_PASSWORD" -> "🔒 Password is too weak.\nPlease use at least 6 characters with letters and numbers."
                            "ERROR_NETWORK_REQUEST_FAILED" -> "📡 Network error.\nPlease check your internet connection and try again."
                            else -> "❌ Registration failed: ${e.message}"
                        }
                    }
                    e.message?.contains("CONFIGURATION_NOT_FOUND") == true -> {
                        showFirebaseConfigError()
                        "⚙️ Firebase configuration error. Please check setup."
                    }
                    e.message?.contains("internal error") == true -> {
                        showFirebaseSetupDialog()
                        "⚙️ Firebase Authentication not properly configured"
                    }
                    else -> "❌ Registration failed: ${e.message}"
                }

                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
    }

    private fun loginUser(email: String, password: String) {
        btnLogin.isEnabled = false
        btnRegister.isEnabled = false

        Log.d(TAG, "Attempting to login user: $email")

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                Log.d(TAG, "Login successful")
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                goToMain()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Login failed", e)
                btnLogin.isEnabled = true
                btnRegister.isEnabled = true

                val errorMessage = when {
                    e is FirebaseAuthException -> {
                        when (e.errorCode) {
                            "ERROR_INVALID_EMAIL" -> "❌ Invalid email format.\nPlease check your email address."
                            "ERROR_WRONG_PASSWORD" -> "🔑 Incorrect password.\nPlease check your password and try again."
                            "ERROR_USER_NOT_FOUND" -> "📧 Email not registered.\nNo account found with this email.\nPlease create an account first using the CREATE ACCOUNT button."
                            "ERROR_INVALID_CREDENTIAL" -> "❌ Invalid credentials.\nThe email or password you entered is incorrect."
                            "ERROR_USER_DISABLED" -> "⛔ Account disabled.\nThis account has been disabled. Please contact support."
                            "ERROR_NETWORK_REQUEST_FAILED" -> "📡 Network error.\nPlease check your internet connection and try again."
                            "ERROR_TOO_MANY_REQUESTS" -> "⏱️ Too many attempts.\nPlease wait a few minutes before trying again."
                            else -> "❌ Login failed: ${e.message}"
                        }
                    }
                    e.message?.contains("CONFIGURATION_NOT_FOUND") == true -> {
                        showFirebaseConfigError()
                        "⚙️ Firebase configuration error"
                    }
                    e.message?.contains("internal error") == true -> {
                        showFirebaseSetupDialog()
                        "⚙️ Firebase Authentication not configured"
                    }
                    else -> "❌ Login failed: ${e.message}"
                }

                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
    }

    private fun showFirebaseSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Firebase Setup Required")
            .setMessage(
                "Firebase Authentication is not enabled in your Firebase Console.\n\n" +
                "To fix this:\n" +
                "1. Go to console.firebase.google.com\n" +
                "2. Select your project\n" +
                "3. Go to Authentication → Sign-in method\n" +
                "4. Enable Email/Password authentication\n" +
                "5. Try again"
            )
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNegativeButton("Copy Console Link") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Firebase Console", "https://console.firebase.google.com/project/vehicletrackingprototype-d8b88/authentication/users")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showFirebaseConfigError() {
        Log.e(TAG, "Firebase configuration error detected")
    }

    private fun goToMain() {
        val userId = auth.currentUser?.uid ?: return

        // Save userKey for other activities
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putString("userKey", userId).apply()

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
