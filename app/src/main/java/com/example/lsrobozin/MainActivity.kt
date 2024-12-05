/*
 * MainActivity.kt
 * Current Date and Time (UTC): 2024-12-05 05:01:23
 * Current User's Login: lefsilva79
 *
 * Parte 1: Imports e Inicialização
 */

package com.example.lsrobozin

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import com.example.lsrobozin.utils.LogHelper

class MainActivity : AppCompatActivity() {
    // Declaração dos elementos de UI
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var valueInput: EditText
    private lateinit var statusText: TextView
    private lateinit var monitorSwitch: Switch
    private lateinit var autoClickSwitch: Switch
    private lateinit var statusIndicator: View
    private lateinit var setupWizardButton: Button
    private lateinit var notificationCheckBox: CheckBox
    private lateinit var batteryCheckBox: CheckBox
    private lateinit var hibernationCheckBox: CheckBox

    // Variáveis de controle
    private var serviceStarted = false
    private var hasShownAccessibilityPrompt = false
    private val handler = Handler(Looper.getMainLooper())

    // Verificação periódica do estado
    private val checkStateRunnable = object : Runnable {
        override fun run() {
            LogHelper.logEvent("Executando verificação periódica de estado")
            checkSearchState()
            handler.postDelayed(this, 5000) // Verifica a cada 5 segundos
        }
    }

    private val searchStateReceiver = SearchStateReceiver()

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 100
        private const val STORAGE_PERMISSION_CODE = 101
        private const val TAG = "MainActivity"
        private const val SEARCH_STATE_ACTION = "com.example.lsrobozin.SEARCH_STATE_CHANGED"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Verifica permissão antes de inicializar o logger
        checkStoragePermission()

        searchStateReceiver.setCallback { isSearching ->
            LogHelper.logEvent("Callback do SearchStateReceiver - isSearching: $isSearching")
            updateUIForSearchState(isSearching)
        }

        initializeViews()
        loadSavedPreferences()
        setupListeners()
        handler.post(checkStateRunnable)

        if (!isTaskRoot) {
            LogHelper.logEvent("Não é task root - redirecionando")
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
            return
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
            } else {
                initializeLogger()
            }
        } else {
            initializeLogger()
        }
    }

    private fun initializeLogger() {
        LogHelper.initialize(this)
        LogHelper.logEvent("MainActivity iniciada")
    }

    /*
 * MainActivity.kt
 * Current Date and Time (UTC): 2024-12-05 05:01:59
 * Current User's Login: lefsilva79
 *
 * Parte 2: Inicialização de Views e Configuração de Listeners
 */

    private fun initializeViews() {
        LogHelper.logEvent("Iniciando inicialização das views")

        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        valueInput = findViewById(R.id.valueInput)
        statusText = findViewById(R.id.statusText)
        monitorSwitch = findViewById(R.id.monitorSwitch)
        autoClickSwitch = findViewById(R.id.autoClickSwitch)
        statusIndicator = findViewById(R.id.statusIndicator)
        setupWizardButton = findViewById(R.id.setupWizardButton)
        notificationCheckBox = findViewById(R.id.notificationCheckBox)
        batteryCheckBox = findViewById(R.id.batteryCheckBox)
        hibernationCheckBox = findViewById(R.id.hibernationCheckBox)

        startButton.isEnabled = false
        stopButton.isEnabled = false
        monitorSwitch.isEnabled = false
        autoClickSwitch.isEnabled = false

        LogHelper.logEvent("Views inicializadas com estado inicial: botões desabilitados")
    }

    private fun loadSavedPreferences() {
        LogHelper.logEvent("Carregando preferências salvas")

        val prefs = getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
        monitorSwitch.isChecked = prefs.getBoolean("monitor_instacart", false)
        autoClickSwitch.isChecked = prefs.getBoolean("auto_click", false)
        valueInput.setText(prefs.getString("last_value", ""))
        serviceStarted = prefs.getBoolean("service_started", false)
        notificationCheckBox.isChecked = prefs.getBoolean("notifications_enabled", false)
        batteryCheckBox.isChecked = prefs.getBoolean("battery_optimization", false)
        hibernationCheckBox.isChecked = prefs.getBoolean("hibernation", false)

        if (prefs.getBoolean("is_searching", false)) {
            valueInput.isEnabled = false
            startButton.isEnabled = false
            stopButton.isEnabled = true
            LogHelper.logEvent("Estado de busca ativo encontrado nas preferências")
        }

        LogHelper.logEvent("""
            Preferências carregadas:
            Monitor Instacart: ${monitorSwitch.isChecked}
            Auto-click: ${autoClickSwitch.isChecked}
            Último valor: ${valueInput.text}
            Serviço ativo: $serviceStarted
        """.trimIndent())
    }

    private fun setupListeners() {
        LogHelper.logEvent("Configurando listeners dos componentes")

        startButton.setOnClickListener {
            handleStartButton()
        }

        stopButton.setOnClickListener {
            stopSearching()
        }

        setupWizardButton.setOnClickListener {
            LogHelper.logEvent("Iniciando SetupWizardActivity")
            startActivity(Intent(this, SetupWizardActivity::class.java))
        }

        monitorSwitch.setOnCheckedChangeListener { _, isChecked ->
            LogHelper.logEvent("Monitor Instacart ${if (isChecked) "ativado" else "desativado"}")
            MyAccessibilityService.getInstance()?.setMonitorInstacart(isChecked)
            savePreference("monitor_instacart", isChecked)
        }

        autoClickSwitch.setOnCheckedChangeListener { _, isChecked ->
            LogHelper.logEvent("Auto-click ${if (isChecked) "ativado" else "desativado"}")
            MyAccessibilityService.getInstance()?.setInstacartAutoClick(isChecked)
            savePreference("auto_click", isChecked)
        }

        notificationCheckBox.setOnCheckedChangeListener { _, isChecked ->
            LogHelper.logEvent("Alteração em notificações: $isChecked")
            if (isChecked) {
                checkNotificationPermission()
            }
            savePreference("notifications_enabled", isChecked)
        }

        batteryCheckBox.setOnCheckedChangeListener { _, isChecked ->
            LogHelper.logEvent("Alteração em otimização de bateria: $isChecked")
            if (isChecked) {
                checkBatteryOptimization()
            }
            savePreference("battery_optimization", isChecked)
        }

        hibernationCheckBox.setOnCheckedChangeListener { _, isChecked ->
            LogHelper.logEvent("Alteração em hibernação: $isChecked")
            if (isChecked) {
                checkHibernation()
            }
            savePreference("hibernation", isChecked)
        }
    }

    private fun handleStartButton() {
        val value = valueInput.text.toString().trim()

        if (value.isEmpty()) {
            LogHelper.logEvent("Tentativa de início sem valor")
            showToast("Digite um valor para buscar")
            return
        }

        try {
            val numericValue = value.toInt()
            if (numericValue > MyAccessibilityService.MAX_VALUE) {
                LogHelper.logEvent("Valor excede máximo permitido: $numericValue")
                showToast("Valor máximo permitido é ${MyAccessibilityService.MAX_VALUE}")
                return
            }

            MyAccessibilityService.getInstance()?.let { service ->
                LogHelper.logEvent("Iniciando busca por valor: $numericValue")
                startButton.isEnabled = false
                stopButton.isEnabled = true
                valueInput.isEnabled = false
                service.startSearching(numericValue)
                service.setServiceStarted(true)
                savePreference("last_value", value)
                savePreference("is_searching", true)
                updateStatus("Buscando valor: $$value")

                handler.removeCallbacks(checkStateRunnable)
                handler.post(checkStateRunnable)
            } ?: run {
                LogHelper.logEvent("Serviço não disponível - mostrando prompt de acessibilidade")
                showAccessibilityPrompt()
            }
        } catch (e: NumberFormatException) {
            LogHelper.logEvent("Erro de formato: valor inválido inserido")
            showToast("Digite apenas números")
        }
    }

    /*
 * MainActivity.kt
 * Current Date and Time (UTC): 2024-12-05 05:03:09
 * Current User's Login: lefsilva79
 *
 * Parte 3: Gerenciamento de Estado e Ciclo de Vida
 */

    private fun stopSearching() {
        MyAccessibilityService.getInstance()?.let { service ->
            LogHelper.logEvent("Parando busca manualmente")
            service.stopSearching()
            service.setServiceStarted(false)
            savePreference("is_searching", false)
            startButton.isEnabled = true
            stopButton.isEnabled = false
            valueInput.isEnabled = true
            updateStatus("Busca interrompida")

            handler.removeCallbacks(checkStateRunnable)
        }
    }

    private fun checkSearchState() {
        LogHelper.logEvent("Verificando estado da busca")
        val prefs = getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
        val isSearching = prefs.getBoolean("is_searching", false)

        if (isSearching) {
            valueInput.isEnabled = false
            startButton.isEnabled = false
            stopButton.isEnabled = true
            val lastValue = prefs.getString("last_value", "")
            updateStatus("Buscando valor: $$lastValue")
            LogHelper.logEvent("Estado de busca ativo - valor: $lastValue")
        }
    }

    private fun updateUIForSearchState(isSearching: Boolean) {
        LogHelper.logEvent("Atualizando UI para estado de busca: $isSearching")
        valueInput.isEnabled = !isSearching
        startButton.isEnabled = !isSearching
        stopButton.isEnabled = isSearching

        if (isSearching) {
            val lastValue = getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
                .getString("last_value", "")
            updateStatus("Buscando valor: $$lastValue")
        } else {
            updateStatus("Busca interrompida")
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        LogHelper.logEvent("Verificando status do serviço de acessibilidade")
        try {
            val accessibilityEnabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )

            if (accessibilityEnabled != 1) return false

            val serviceName = "$packageName/${MyAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServices.split(":")
                .any { service ->
                    service.equals(serviceName, ignoreCase = true)
                }.also { isEnabled ->
                    LogHelper.logEvent("Status do serviço de acessibilidade: ${if (isEnabled) "ativo" else "inativo"}")
                }

        } catch (e: Exception) {
            LogHelper.logError("Verificação do serviço de acessibilidade", e)
            return false
        }
    }

    private fun checkAccessibilityService() {
        val enabled = isAccessibilityServiceEnabled()
        LogHelper.logEvent("Verificando serviço de acessibilidade: $enabled")
        if (!enabled && !hasShownAccessibilityPrompt) {
            hasShownAccessibilityPrompt = true
            updateStatus("Serviço de acessibilidade não está ativo")
            showAccessibilityPrompt()
        } else {
            serviceStarted = true
            savePreference("service_started", true)
        }
        updateControlsState(enabled)
    }

    private fun showAccessibilityPrompt() {
        LogHelper.logEvent("Mostrando prompt de acessibilidade")
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        showToast("Por favor, ative o serviço de acessibilidade")
    }

    private fun updateControlsState(serviceEnabled: Boolean) {
        LogHelper.logEvent("""
        Estado dos controles atualizado:
        Serviço ativo: $serviceEnabled
        Buscando: ${getSharedPreferences("ValorLocator", Context.MODE_PRIVATE).getBoolean("is_searching", false)}
        """.trimIndent())

        val isSearching = getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
            .getBoolean("is_searching", false)

        startButton.isEnabled = serviceEnabled && !isSearching
        stopButton.isEnabled = serviceEnabled && isSearching
        monitorSwitch.isEnabled = serviceEnabled
        autoClickSwitch.isEnabled = serviceEnabled
        valueInput.isEnabled = !isSearching
        setupWizardButton.isEnabled = true

        statusIndicator.setBackgroundColor(
            if (serviceEnabled)
                ContextCompat.getColor(this, android.R.color.holo_green_light)
            else
                ContextCompat.getColor(this, android.R.color.holo_red_light)
        )

        if (!serviceEnabled) {
            updateStatus("Serviço de acessibilidade inativo")
        } else if (isSearching) {
            val lastValue = getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
                .getString("last_value", "")
            updateStatus("Buscando valor: $$lastValue")
        } else {
            updateStatus("Pronto para iniciar")
        }
    }

    private fun updateStatus(message: String) {
        LogHelper.logEvent("Status atualizado: $message")
        statusText.text = message
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun savePreference(key: String, value: Boolean) {
        LogHelper.logEvent("Salvando preferência: $key = $value")
        getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    private fun savePreference(key: String, value: String) {
        LogHelper.logEvent("Salvando preferência: $key = $value")
        getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        LogHelper.logEvent("MainActivity resumida")

        val filter = IntentFilter(SEARCH_STATE_ACTION)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    searchStateReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(searchStateReceiver, filter)
            }

            val prefs = getSharedPreferences("ValorLocator", Context.MODE_PRIVATE)
            val isSearching = prefs.getBoolean("is_searching", false)

            if (isSearching) {
                val lastValue = prefs.getString("last_value", "")
                startButton.isEnabled = false
                stopButton.isEnabled = true
                valueInput.isEnabled = false
                updateStatus("Continuando busca: $lastValue")

                handler.removeCallbacks(checkStateRunnable)
                handler.post(checkStateRunnable)
            }

            val enabled = isAccessibilityServiceEnabled()
            updateControlsState(enabled)
        } catch (e: Exception) {
            LogHelper.logError("Registro do receiver", e)
        }
    }

    override fun onPause() {
        LogHelper.logEvent("MainActivity pausada")
        super.onPause()
        unregisterReceiver(searchStateReceiver)
    }

    override fun onNewIntent(intent: Intent) {
        LogHelper.logEvent("Novo intent recebido")
        setIntent(intent)
        super.onNewIntent(intent)
        if (!isTaskRoot) {
            finish()
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(mainIntent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initializeLogger()
                } else {
                    Toast.makeText(this, "Permissão de armazenamento necessária para logs", Toast.LENGTH_LONG).show()
                }
            }
            NOTIFICATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LogHelper.logEvent("Permissão de notificação concedida")
                    showToast("Permissão de notificação concedida")
                } else {
                    LogHelper.logEvent("Permissão de notificação negada")
                    notificationCheckBox.isChecked = false
                    showToast("Permissão de notificação negada")
                }
            }
        }
    }

    override fun onDestroy() {
        LogHelper.logEvent("MainActivity sendo destruída")
        super.onDestroy()
        handler.removeCallbacks(checkStateRunnable)
        savePreference("service_started", false)
    }

    private fun checkNotificationPermission() {
        LogHelper.logEvent("Verificando permissão de notificação")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
                notificationCheckBox.isChecked = false
                showToast("Por favor, habilite as notificações")
            }
        }
    }

    private fun checkBatteryOptimization() {
        LogHelper.logEvent("Verificando otimização de bateria")
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun checkHibernation() {
        LogHelper.logEvent("Verificando configurações de hibernação")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent().apply {
                    action = "android.settings.APP_HIBERNATION_SETTINGS"
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                LogHelper.logError("Acesso às configurações de hibernação", e)
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
} // Fechamento da classe MainActivity