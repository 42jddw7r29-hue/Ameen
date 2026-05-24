package com.example.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ContactRepository
import com.example.data.WhitelistedContact
import com.example.service.CallMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ContactRepository
    val whitelistedContacts: StateFlow<List<WhitelistedContact>>

    // Live permission monitoring states
    private val _contactsPermissionGranted = MutableStateFlow(false)
    val contactsPermissionGranted = _contactsPermissionGranted.asStateFlow()

    private val _phoneStatePermissionGranted = MutableStateFlow(false)
    val phoneStatePermissionGranted = _phoneStatePermissionGranted.asStateFlow()

    private val _callLogPermissionGranted = MutableStateFlow(false)
    val callLogPermissionGranted = _callLogPermissionGranted.asStateFlow()

    private val _notificationPolicyGranted = MutableStateFlow(false)
    val notificationPolicyGranted = _notificationPolicyGranted.asStateFlow()

    private val _canDrawOverlays = MutableStateFlow(false)
    val canDrawOverlays = _canDrawOverlays.asStateFlow()

    private val _postNotificationsGranted = MutableStateFlow(false)
    val postNotificationsGranted = _postNotificationsGranted.asStateFlow()

    // Service state
    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive = _isServiceActive.asStateFlow()

    // Test countdown state
    private val _testCountdown = MutableStateFlow<Int?>(null)
    val testCountdown = _testCountdown.asStateFlow()

    // System contacts loading state
    private val _systemContacts = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val systemContacts = _systemContacts.asStateFlow()

    private val _isContactsLoading = MutableStateFlow(false)
    val isContactsLoading = _isContactsLoading.asStateFlow()

    init {
        val contactDao = AppDatabase.getDatabase(application).contactDao()
        repository = ContactRepository(contactDao)
        whitelistedContacts = repository.allContactsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        checkAllPermissions()
        updateServiceState()
    }

    fun checkAllPermissions() {
        val ctx = getApplication<Application>()
        
        _contactsPermissionGranted.value = ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        _phoneStatePermissionGranted.value = ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        _callLogPermissionGranted.value = ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        _notificationPolicyGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.isNotificationPolicyAccessGranted
        } else {
            true
        }

        _canDrawOverlays.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(ctx)
        } else {
            true
        }

        _postNotificationsGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun updateServiceState() {
        _isServiceActive.value = CallMonitorService.isServiceRunning
    }

    fun toggleService() {
        val ctx = getApplication<Application>()
        if (CallMonitorService.isServiceRunning) {
            CallMonitorService.stopService(ctx)
        } else {
            CallMonitorService.startService(ctx)
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            updateServiceState()
        }
    }

    fun addManualContact(name: String, phoneNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertContact(
                WhitelistedContact(name = name, phoneNumber = phoneNumber)
            )
        }
    }

    fun toggleContactEnabled(contact: WhitelistedContact) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateContact(contact.copy(isEnabled = !contact.isEnabled))
        }
    }

    fun deleteContact(contact: WhitelistedContact) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteContact(contact)
        }
    }

    fun loadSystemContacts() {
        if (!contactsPermissionGranted.value) return
        _isContactsLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val loaded = mutableListOf<Pair<String, String>>()
            try {
                val cursor = ctx.contentResolver.query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                )
                cursor?.use {
                    val nameIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val name = if (nameIdx >= 0) it.getString(nameIdx) else "غير معروف"
                        val number = if (numIdx >= 0) it.getString(numIdx) else ""
                        if (number.isNotBlank()) {
                            // Filter duplicates out
                            if (!loaded.any { it.second == number }) {
                                loaded.add(Pair(name, number))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _systemContacts.value = loaded
            _isContactsLoading.value = false
        }
    }

    fun startTestCountdown() {
        val ctx = getApplication<Application>()
        if (!CallMonitorService.isServiceRunning) {
            CallMonitorService.startService(ctx)
        }
        
        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _testCountdown.value = (millisUntilFinished / 1000).toInt() + 1
            }

            override fun onFinish() {
                _testCountdown.value = null
                CallMonitorService.triggerTestRing(ctx)
            }
        }.start()
    }

    fun stopTestRing() {
        val ctx = getApplication<Application>()
        CallMonitorService.stopTestRing(ctx)
        _testCountdown.value = null
    }

    fun openDndSettings() {
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
        }
    }

    fun openOverlaySettings() {
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
        }
    }
}
