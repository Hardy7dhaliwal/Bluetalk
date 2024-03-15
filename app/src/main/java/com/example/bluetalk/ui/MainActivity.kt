package com.example.bluetalk.ui

import ECDHCryptoManager
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.example.bluetalk.R
import com.example.bluetalk.bluetooth.BluetalkServer
import com.example.bluetalk.database.ChatDao
import com.example.bluetalk.database.ChatDatabase
import com.example.bluetalk.databinding.ActivityMainBinding
import com.example.bluetalk.model.ProxyPacket
import com.example.bluetalk.model.UUIDManager
import com.example.bluetalk.security.ECDHCryptoManager
import com.example.bluetalk.viewModel.SharedViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import timber.log.Timber
import java.security.Security
import java.util.UUID

private const val TAG = "MainActivity"
class MainActivity : AppCompatActivity() {
    private  val RUNTIME_PERMISSION_REQUEST_CODE = 2

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var database: ChatDatabase? = null
    private var chatDao: ChatDao? = null
    private lateinit var model: SharedViewModel
    private lateinit var appUUID: UUID
    private lateinit var adapter: BluetoothAdapter
    private val processedRReq = mutableListOf<ProxyPacket>()



    @RequiresApi(Build.VERSION_CODES.S)
    override fun onStart(){
        super.onStart()
        Security.addProvider(org.spongycastle.jce.provider.BouncyCastleProvider())
        if(!hasRequiredRuntimePermissions()){
            checkAndRequestPermissions()
        }else{
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val username = sharedPreferences.getString("username", "Not set")
            if(username != "Not set") {
                BluetalkServer.startServer(this.application, lifecycleScope)
            }
        }
        database = ChatDatabase.getDatabase(this)
        chatDao = database!!.chatDao()
        appUUID =  UUIDManager.getStoredUUID(this.applicationContext)
        adapter = (this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val username = sharedPreferences.getString("username", "Not set")
        Timber.tag(TAG).d("Username: $username")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navController = findNavController(R.id.nav_host_fragment)
        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        if(username=="Not set"){
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            navHostFragment?.view?.visibility = View.GONE
            bottomNavigationView.visibility=View.GONE
        }else{
            binding.loginContainer.visibility=View.GONE
        }
        binding.buttonCreateAccount.setOnClickListener{
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            navHostFragment?.view?.visibility = View.VISIBLE
            bottomNavigationView.visibility=View.VISIBLE
            binding.loginContainer.visibility=View.GONE
            val name = binding.editTextUsername.text.toString()
            with(sharedPreferences.edit()) {
                putString("username", name)
                apply()
            }
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).
            hideSoftInputFromWindow(binding.root.windowToken,0)
            BluetalkServer.startServer(this.application, lifecycleScope)
        }

        bottomNavigationView.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.ChatFragment -> bottomNavigationView.visibility = View.GONE // Hide on ChatFragment
                else -> bottomNavigationView.visibility = View.VISIBLE // Show on other Fragments
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.S)
    fun hasRequiredRuntimePermissions(): Boolean {
        val requiredPermissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            // Note: For devices targeting API 31 and above, FINE and COARSE location permissions
            // are not required for Bluetooth scanning if you add neverForLocation to manifest.
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.RECORD_AUDIO
        )

        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.RECORD_AUDIO
        ).filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, RUNTIME_PERMISSION_REQUEST_CODE)
        } else {
            // All permissions are granted
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val username = sharedPreferences.getString("username", "Not set")
            if(username != "Not set") {
                BluetalkServer.startServer(this.application, lifecycleScope)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RUNTIME_PERMISSION_REQUEST_CODE) {
            if (hasRequiredRuntimePermissions()) {
                println("Mainactivity: Accepted")
                // All permissions have been granted
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                val username = sharedPreferences.getString("username", "Not set")
                if(username != "Not set") {
                    BluetalkServer.startServer(this.application, lifecycleScope)
                }
            } else {
                println("Mainactivity: Denied again")
                // Handle the case where permissions are denied
                // This could be a simple denial or permanent denial
            }
        }
    }

    override fun onStop() {
        super.onStop()
        BluetalkServer.stopServer()
    }


}