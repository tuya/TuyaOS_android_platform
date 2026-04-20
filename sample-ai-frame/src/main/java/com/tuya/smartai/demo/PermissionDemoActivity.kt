package com.tuya.smartai.demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionDemoActivity : AppCompatActivity() {

    private lateinit var tvBluetoothStatus: TextView
    private lateinit var tvNetworkStatus: TextView
    private lateinit var tvAudioStatus: TextView
    private lateinit var tvCameraStatus: TextView
    private lateinit var btnRequestBluetooth: Button
    private lateinit var btnRequestAudio: Button
    private lateinit var btnRequestCamera: Button
    private var btnRequestNetwork: Button? = null
    private lateinit var btnEnterIoTActivity: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_demo)

        tvBluetoothStatus = findViewById(R.id.tvBluetoothStatus)
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus)
        tvAudioStatus = findViewById(R.id.tvAudioStatus)
        tvCameraStatus = findViewById(R.id.tvCameraStatus)

        btnRequestBluetooth = findViewById(R.id.btnRequestBluetooth)
        btnRequestNetwork = findViewById(R.id.btnRequestNetwork)
        btnRequestAudio = findViewById(R.id.btnRequestAudio)
        btnRequestCamera = findViewById(R.id.btnRequestCamera)

        btnRequestBluetooth.setOnClickListener { requestBluetoothPermissions() }
        btnRequestAudio.setOnClickListener { requestAudioPermission() }
        btnRequestCamera.setOnClickListener { requestCameraPermission() }

        btnEnterIoTActivity = findViewById(R.id.btnEnterIoTActivity)
        btnEnterIoTActivity.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        updateAllPermissionStatus()

        if (isAllPermissionsGranted()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun isAllPermissionsGranted(): Boolean {
        val bluetoothGranted = getBluetoothPermissionsToRequest().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        val audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return bluetoothGranted && audioGranted
    }

    private fun updateEnterButtonState() {
        val allGranted = isAllPermissionsGranted()
        btnEnterIoTActivity.isEnabled = allGranted
        btnEnterIoTActivity.text = if (allGranted) "进入主逻辑" else "请先授予权限"
    }

    override fun onResume() {
        super.onResume()
        updateAllPermissionStatus()
    }

    private fun updateAllPermissionStatus() {
        updatePermissionStatusUI(getBluetoothPermissionsToRequest(), tvBluetoothStatus, btnRequestBluetooth)
        updatePermissionStatusUI(arrayOf(Manifest.permission.RECORD_AUDIO), tvAudioStatus, btnRequestAudio)
        updatePermissionStatusUI(arrayOf(Manifest.permission.CAMERA), tvCameraStatus, btnRequestCamera)

        val internetGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
        updateStatusTextView(tvNetworkStatus, internetGranted)
        btnRequestNetwork?.visibility = View.INVISIBLE

        updateEnterButtonState()
    }

    private fun getBluetoothPermissionsToRequest(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.BLUETOOTH_ADVERTISE
        } else {
            permissions += Manifest.permission.BLUETOOTH
            permissions += Manifest.permission.BLUETOOTH_ADMIN
        }
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
        return permissions.toTypedArray()
    }

    private fun requestBluetoothPermissions() {
        requestPermissionsGroup(getBluetoothPermissionsToRequest(), REQUEST_CODE_BLUETOOTH, tvBluetoothStatus, btnRequestBluetooth)
    }

    private fun requestAudioPermission() {
        requestPermissionsGroup(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_AUDIO, tvAudioStatus, btnRequestAudio)
    }

    private fun requestCameraPermission() {
        requestPermissionsGroup(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_CAMERA, tvCameraStatus, btnRequestCamera)
    }

    private fun requestPermissionsGroup(permissions: Array<String>, requestCode: Int, statusView: TextView, buttonView: Button) {
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            updateStatusTextView(statusView, true)
            buttonView.text = "已授予"
            buttonView.isEnabled = false
            Toast.makeText(this, "权限 '${getFriendlyPermissionName(requestCode)}' 已授予", Toast.LENGTH_SHORT).show()
        } else {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), requestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val (originalPermissions, targetStatusView, targetButtonView) = when (requestCode) {
            REQUEST_CODE_BLUETOOTH -> Triple(getBluetoothPermissionsToRequest(), tvBluetoothStatus, btnRequestBluetooth)
            REQUEST_CODE_AUDIO -> Triple(arrayOf(Manifest.permission.RECORD_AUDIO), tvAudioStatus, btnRequestAudio)
            REQUEST_CODE_CAMERA -> Triple(arrayOf(Manifest.permission.CAMERA), tvCameraStatus, btnRequestCamera)
            else -> return
        }

        val allGranted = originalPermissions.all { perm ->
            Log.i(TAG, "onRequestPermissionsResult: perm $perm, isGrant: ${ContextCompat.checkSelfPermission(this, perm)}")
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }

        updateStatusTextView(targetStatusView, allGranted)
        val friendlyName = getFriendlyPermissionName(requestCode)
        if (allGranted) {
            targetButtonView.text = "已授予"
            targetButtonView.isEnabled = false
            Toast.makeText(this, "权限 '$friendlyName' 已成功授予", Toast.LENGTH_SHORT).show()
        } else {
            targetButtonView.text = "授予"
            targetButtonView.isEnabled = true
            Toast.makeText(this, "权限 '$friendlyName' 未完全授予", Toast.LENGTH_SHORT).show()
        }

        updateEnterButtonState()
    }

    private fun updatePermissionStatusUI(permissionsToCheck: Array<String>?, statusView: TextView, buttonView: Button) {
        val allGranted = !permissionsToCheck.isNullOrEmpty() && permissionsToCheck.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        updateStatusTextView(statusView, allGranted)
        if (allGranted) {
            buttonView.text = "已授予"
            buttonView.isEnabled = false
        } else {
            buttonView.text = "授予"
            buttonView.isEnabled = true
        }
    }

    private fun updateStatusTextView(textView: TextView, granted: Boolean) {
        if (granted) {
            textView.text = "已授予"
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            textView.text = "未授予"
            textView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun getFriendlyPermissionName(requestCode: Int): String = when (requestCode) {
        REQUEST_CODE_BLUETOOTH -> "蓝牙"
        REQUEST_CODE_AUDIO -> "录音"
        REQUEST_CODE_CAMERA -> "相机"
        else -> "未知"
    }

    companion object {
        private const val TAG = "PermissionDemoActivity"
        private const val REQUEST_CODE_BLUETOOTH = 101
        private const val REQUEST_CODE_AUDIO = 103
        private const val REQUEST_CODE_CAMERA = 104
    }
}
