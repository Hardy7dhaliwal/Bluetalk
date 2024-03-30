package com.example.bluetalk.ui

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
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
import com.example.bluetalk.model.UUIDManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private lateinit var appUUID: UUID
    private lateinit var adapter: BluetoothAdapter

    private val sosObserver = Observer<String>{
        showSOSAlert(it)
    }

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
        BluetalkServer.sosRequest.observe(this,sosObserver)
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
            val name = binding.editTextUsername.text.toString().trim()
            with(sharedPreferences.edit()) {
                putString("username", name)
                apply()
            }
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).
            hideSoftInputFromWindow(binding.root.windowToken,0)
            if(hasRequiredRuntimePermissions()) {
                BluetalkServer.startServer(this.application, lifecycleScope)
            }else{
                checkAndRequestPermissions()
            }
        }
        bottomNavigationView.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.ChatFragment -> bottomNavigationView.visibility =
                    View.GONE // Hide on ChatFragment
                else -> bottomNavigationView.visibility =
                    View.VISIBLE // Show on other Fragments
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.S)
    fun hasRequiredRuntimePermissions(): Boolean {
        val requiredPermissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.RECORD_AUDIO
        )

        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
     fun checkAndRequestPermissions() {
        val permissionsToRequest = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
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
            var allPermissionsGranted = true
            if (grantResults.isNotEmpty()) {
                for (result in grantResults) {
                    if (result == PackageManager.PERMISSION_DENIED) {
                        allPermissionsGranted = false
                        // If user didn't explicitly tap 'Don't Allow', you might want to show your rationale again
                        val shouldProvideRationale =
                            ActivityCompat.shouldShowRequestPermissionRationale(
                                this,
                                permissions[0]
                            )
                        if (shouldProvideRationale) {
                            println("Main activity: Denied")
                            Toast.makeText(this,"Permissions Denied.",Toast.LENGTH_LONG).show()
                            showPermissionDialog()
                        } else {
                            Toast.makeText(this,"Need Permissions.",Toast.LENGTH_LONG).show()
                            // User tapped 'Don't Allow'
                            handleDeniedPermission()
                        }
                        break
                    }
                }
            }
            if (allPermissionsGranted) {
                println("Main activity: Accepted")
                // All permissions have been granted
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                val username = sharedPreferences.getString("username", "Not set")
                if(username != "Not set") {
                    BluetalkServer.startServer(this.application, lifecycleScope)
                }
            }
        }
    }

    private fun handleDeniedPermission() {
        // Explain to the user that the permission is necessary
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Need Permissions")
            .setMessage("Without this permission the app is unable to function properly. Would you like to open settings and grant the permission?")
            .setPositiveButton(
                "Open Settings"
            ) { _: DialogInterface?, _: Int -> openAppSettings() }
            .setNegativeButton("Cancel") { dialog: DialogInterface, _: Int -> dialog.cancel() }
            .create()
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts(
                "package",
                packageName, null
            )
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onStop() {
        super.onStop()
        BluetalkServer.stopServer()
    }

    private fun showSOSAlert(sosMsg: String) {
        val senderName = sosMsg.split("\n")[0].split(" ")[2]
        val builder = AlertDialog.Builder(this) // Use Activity context
        builder.setTitle("SOS Alert Received")
        builder.setMessage("SOS alert received from $senderName. Do you want to forward it?")

        builder.setPositiveButton("Yes") { dialog, _ ->
            lifecycleScope.launch(Dispatchers.IO) {
                BluetalkServer.broadcastSOS(sosMsg)
            }
            Toast.makeText(this,"SOS Forwarded.!!",Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showPermissionDialog(){
        val builder = AlertDialog.Builder(this) // Use Activity context
        builder.setTitle("Permission Denied")
        builder.setMessage("BlueTalkie needs Bluetooth permissions. " +
                "Do you want to give necessary permissions to BlueTalkie to access " +
                "Bluetooth features.")

        builder.setPositiveButton("Yes") { dialog, _ ->
            checkAndRequestPermissions()
            dialog.dismiss()
        }

        builder.setNegativeButton("No") { dialog, _ ->
            // Optionally handle the "No" case. Maybe log the decision or dismiss the alert without action.
            Toast.makeText(this,"Permissions will not be enabled",Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

}