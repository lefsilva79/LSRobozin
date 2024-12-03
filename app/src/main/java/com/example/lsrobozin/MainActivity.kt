package com.example.lsrobozin

import android.accessibilityservice.AccessibilityServiceInfo
import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.lsrobozin.com.example.lsrobozin.AccessibilityInstructionsDialog
import java.util.*
import android.graphics.Color

class MainActivity : AppCompatActivity(), AccessibilityInstructionsDialog.InstructionsDialogListener {

    private lateinit var valueInput: EditText
    private lateinit var startButton: Button
    private lateinit var enableServiceButton: Button
    private lateinit var statusText: TextView
    private lateinit var checkBoxMonitorInstacart: CheckBox
    private lateinit var checkBoxAutoClick: CheckBox
    private lateinit var checkBoxBackground: CheckBox
    private lateinit var checkBoxBattery: CheckBox
    private lateinit var checkBoxNotifications: CheckBox
    private lateinit var checkBoxHibernation: CheckBox

    private var isSearching = false
    private var showSuccessDialog = false
    private var targetValue: Int = 0
    private var allowDuplicates: Boolean = false
    private var timer: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupControls()
        loadSavedState()
    }

    private fun initializeViews() {
        valueInput = findViewById(R.id.valueInput)
        startButton = findViewById(R.id.startButton)
        enableServiceButton = findViewById(R.id.enableServiceButton)
        statusText = findViewById(R.id.statusText)
        checkBoxMonitorInstacart = findViewById(R.id.checkBoxMonitorInstacart)
        checkBoxAutoClick = findViewById(R.id.checkBoxAutoClick)
        checkBoxBackground = findViewById(R.id.checkBoxBackground)
        checkBoxBattery = findViewById(R.id.checkBoxBattery)
        checkBoxNotifications = findViewById(R.id.checkBoxNotifications)
        checkBoxHibernation = findViewById(R.id.checkBoxHibernation)
    }

    private fun setupControls() {
        setupEnableServiceButton()
        setupStartButton()
        setupMonitorInstacartCheckbox()
        setupAutoClickCheckbox()
        setupSystemPermissions()
        setupTutorialButton()
    }

    private fun setupTutorialButton() {
        findViewById<Button>(R.id.btnShowTutorial).setOnClickListener {
            startActivity(Intent(this, SetupWizardActivity::class.java))
        }
    }



    private fun loadSavedState() {
        val prefs = getSharedPreferences("ValorLocator", MODE_PRIVATE)
        isSearching = prefs.getBoolean("isSearching", false)
        allowDuplicates = prefs.getBoolean("allow_duplicates", false)
        targetValue = prefs.getInt("targetValue", 0)
        updateUIState(isSearching)
    }

    private fun setupEnableServiceButton() {
        enableServiceButton.setOnClickListener {
            showAccessibilityInstructions()
        }
    }

    private fun showAccessibilityInstructions() {
        AccessibilityInstructionsDialog().show(supportFragmentManager, "instructions")
    }

    override fun onInstructionsUnderstood() {
        showSuccessDialog = true
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun setupStartButton() {
        startButton.setOnClickListener {
            handleStartButtonClick()
        }
    }

    private fun handleStartButtonClick() {
        val valueStr = valueInput.text.toString()
        if (valueStr.isEmpty()) {
            valueInput.error = "Enter a value"
            return
        }

        val value = valueStr.toIntOrNull()
        if (value == null || value <= 0) {
            valueInput.error = "Invalid value"
            return
        }

        if (!isSearching) {
            MyAccessibilityService.getInstance()?.startSearching(value)
            isSearching = true
        } else {
            MyAccessibilityService.getInstance()?.stopSearching()
            isSearching = false
        }

        updateUIState(isSearching)
        saveSearchState()
    }

    private fun saveSearchState() {
        getSharedPreferences("ValorLocator", MODE_PRIVATE)
            .edit()
            .putBoolean("isSearching", isSearching)
            .putInt("targetValue", valueInput.text.toString().toIntOrNull() ?: 0)
            .apply()
    }

    private fun setupMonitorInstacartCheckbox() {
        checkBoxMonitorInstacart.isChecked = getSharedPreferences("ValorLocator", MODE_PRIVATE)
            .getBoolean("monitor_instacart", false)

        checkBoxMonitorInstacart.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("ValorLocator", MODE_PRIVATE)
                .edit()
                .putBoolean("monitor_instacart", isChecked)
                .apply()
            MyAccessibilityService.getInstance()?.setMonitorInstacart(isChecked)
        }
    }

    private fun setupAutoClickCheckbox() {
        checkBoxAutoClick.isChecked = getSharedPreferences("ValorLocator", MODE_PRIVATE)
            .getBoolean("auto_click", false)

        checkBoxAutoClick.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("ValorLocator", MODE_PRIVATE)
                .edit()
                .putBoolean("auto_click", isChecked)
                .apply()
            MyAccessibilityService.getInstance()?.setInstacartAutoClick(isChecked)
        }
    }

    private fun setupSystemPermissions() {
        setupBackgroundPermission()
        setupBatteryOptimization()
        setupNotifications()
        setupHibernationPrevention()
    }

    private fun setupBackgroundPermission() {
        checkBoxBackground.isChecked = isPowerOptimizationIgnored()
        checkBoxBackground.setOnClickListener {
            if (checkBoxBackground.isChecked && !isPowerOptimizationIgnored()) {
                requestBackgroundPermission()
            }
        }
    }

    private fun requestBackgroundPermission() {
        val intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun setupBatteryOptimization() {
        checkBoxBattery.isChecked = isPowerOptimizationIgnored()
        checkBoxBattery.setOnClickListener {
            if (checkBoxBattery.isChecked && !isPowerOptimizationIgnored()) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun setupNotifications() {
        checkBoxNotifications.isChecked = checkNotificationPermission()
        checkBoxNotifications.setOnClickListener {
            if (checkBoxNotifications.isChecked && !checkNotificationPermission() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission()
            }
        }
    }

    private fun setupHibernationPrevention() {
        checkBoxHibernation.isChecked = !isAppHibernated()
        checkBoxHibernation.setOnClickListener {
            if (checkBoxHibernation.isChecked && isAppHibernated()) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }
    }

    private fun isPowerOptimizationIgnored(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isAppHibernated(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            return am.isBackgroundRestricted
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), NOTIFICATION_PERMISSION_CODE)
        }
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_CODE -> {
                checkBoxNotifications.isChecked = grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        updateCheckboxStates()
        loadPreferences()
    }

    private fun updateCheckboxStates() {
        checkBoxBackground.isChecked = isPowerOptimizationIgnored()
        checkBoxBattery.isChecked = isPowerOptimizationIgnored()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkBoxNotifications.isChecked = checkNotificationPermission()
        }
        checkBoxHibernation.isChecked = !isAppHibernated()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("ValorLocator", MODE_PRIVATE)
        allowDuplicates = prefs.getBoolean("allow_duplicates", false)
        isSearching = prefs.getBoolean("isSearching", false)
        targetValue = prefs.getInt("targetValue", 0)

        if (isSearching && targetValue > 0) {
            valueInput.setText(targetValue.toString())
        }

        updateUIState(isSearching)
    }

    private fun updateUIState(isSearching: Boolean) {
        startButton.text = if (isSearching) "Stop" else "Start"
        valueInput.isEnabled = !isSearching
        val controlsEnabled = true
        checkBoxMonitorInstacart.isEnabled = controlsEnabled
        checkBoxAutoClick.isEnabled = controlsEnabled
        checkBoxBackground.isEnabled = controlsEnabled
        checkBoxBattery.isEnabled = controlsEnabled
        checkBoxNotifications.isEnabled = controlsEnabled
        checkBoxHibernation.isEnabled = controlsEnabled
    }

    private fun updateServiceStatus() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val serviceEnabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.id.contains("com.example.lsrobozin/.MyAccessibilityService") }

        if (serviceEnabled) {
            enableServiceEnabled()
        } else {
            disableServiceEnabled()
        }
    }

    private fun enableServiceEnabled() {
        val indicator = findViewById<View>(R.id.statusIndicator)
        indicator.setBackgroundResource(R.drawable.status_indicator)
        indicator.background.setTint(Color.GREEN) // ou use resources.getColor(android.R.color.holo_green_light)
        statusText.text = "Status: Service enabled"
        startButton.isEnabled = true
        enableServiceButton.visibility = View.GONE
        valueInput.isEnabled = true
        setControlsEnabled(true)
    }

    private fun disableServiceEnabled() {
        val indicator = findViewById<View>(R.id.statusIndicator)
        indicator.setBackgroundResource(R.drawable.status_indicator)
        indicator.background.setTint(Color.RED) // ou use resources.getColor(android.R.color.holo_red_light)
        statusText.text = "Status: Click on the ENABLE SERVICE button"
        startButton.isEnabled = false
        enableServiceButton.visibility = View.VISIBLE
        valueInput.isEnabled = false
        setControlsEnabled(false)
    }

    private fun setControlsEnabled(enabled: Boolean) {
        checkBoxMonitorInstacart.isEnabled = enabled
        checkBoxAutoClick.isEnabled = enabled
        checkBoxBackground.isEnabled = enabled
        checkBoxBattery.isEnabled = enabled
        checkBoxNotifications.isEnabled = enabled
        checkBoxHibernation.isEnabled = enabled
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Se estiver procurando, mostre um diálogo de confirmação
        if (isSearching) {
            AlertDialog.Builder(this)
                .setTitle("Warning")
                .setMessage("Do you want to stop searching and exit the app?")
                .setPositiveButton("Yes") { _, _ ->
                    MyAccessibilityService.getInstance()?.stopSearching()
                    super.onBackPressed()
                }
                .setNegativeButton("No", null)
                .show()
        } else {
            // Se não estiver procurando, mostre um diálogo simples
            AlertDialog.Builder(this)
                .setTitle("Exit")
                .setMessage("Do you want to exit the app?")
                .setPositiveButton("Yes") { _, _ ->
                    super.onBackPressed()
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancela o timer quando a activity é destruída
        Timer().cancel()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 123
    }
}