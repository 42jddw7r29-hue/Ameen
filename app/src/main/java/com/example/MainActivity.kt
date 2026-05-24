package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.WhitelistedContact
import com.example.service.CallMonitorService
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ContactViewModel

// Bento Grid Theme Colors
private val BentoBg = Color(0xFFFDFBFF)
private val BentoTextPrimary = Color(0xFF1A1C1E)
private val BentoTextSecondary = Color(0xFF44474E)
private val BentoNavyBg = Color(0xFFD6E2FF)
private val BentoNavyText = Color(0xFF001A40)
private val BentoVioletBg = Color(0xFFF2E7FF)
private val BentoVioletText = Color(0xFF21005D)
private val BentoRoseBg = Color(0xFFFDE2E1)
private val BentoRoseText = Color(0xFF410002)
private val BentoAlertBg = Color(0xFFBA1A1A)
private val BentoAlertText = Color(0xFFFFFFFF)
private val BentoBorderColor = Color(0xFFC4C6CF)

private val ActiveGreen = Color(0xFF10B981)
private val LightGold = Color(0xFFF59E0B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BentoBg
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ContactViewModel = viewModel()) {
    val context = LocalContext.current
    
    val whitelistedContacts by viewModel.whitelistedContacts.collectAsState()
    val isServiceActive by viewModel.isServiceActive.collectAsState()
    val testCountdown by viewModel.testCountdown.collectAsState()
    
    // Check permission states
    val contactsPermissionGranted by viewModel.contactsPermissionGranted.collectAsState()
    val phoneStatePermissionGranted by viewModel.phoneStatePermissionGranted.collectAsState()
    val callLogPermissionGranted by viewModel.callLogPermissionGranted.collectAsState()
    val notificationPolicyGranted by viewModel.notificationPolicyGranted.collectAsState()
    val canDrawOverlays by viewModel.canDrawOverlays.collectAsState()
    val postNotificationsGranted by viewModel.postNotificationsGranted.collectAsState()
    
    val allPermissionsGranted = contactsPermissionGranted && 
            phoneStatePermissionGranted && 
            callLogPermissionGranted && 
            notificationPolicyGranted && 
            canDrawOverlays

    var showAddContactSheet by remember { mutableStateOf(false) }
    var selectedTabForAdding by remember { mutableStateOf(0) } // 0: Import, 1: Manual
    
    // Dynamic permission request launchers
    val contactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.checkAllPermissions()
        if (granted) Toast.makeText(context, "تم منح إذن جهات الاتصال", Toast.LENGTH_SHORT).show()
    }
    
    val phoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        viewModel.checkAllPermissions()
        val p1 = map[Manifest.permission.READ_PHONE_STATE] == true
        val p2 = map[Manifest.permission.READ_CALL_LOG] == true
        if (p1 && p2) {
            Toast.makeText(context, "تم منح أذونات المكالمات بنجاح", Toast.LENGTH_SHORT).show()
        }
    }
    
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.checkAllPermissions()
    }

    // Refresh permissions on activity resume
    DisposableEffect(Unit) {
        viewModel.checkAllPermissions()
        viewModel.updateServiceState()
        onDispose {}
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    viewModel.checkAllPermissions()
                    showAddContactSheet = true 
                },
                containerColor = BentoNavyText,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("add_contact_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "اضافة مستثنى")
            }
        },
        containerColor = BentoBg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            // 1. Bento Theme Header row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Settings/Status Orb on the Left
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BentoNavyBg)
                            .clickable {
                                Toast.makeText(context, "رنين الصامت: نظام تجاوز الوضع الصامت نشط", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = BentoNavyText,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Title & Description on the Right
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "تجاوز الصامت",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPrimary,
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isServiceActive) "وضع الاستعداد نشط" else "الخدمة متوقفة حالياً",
                            fontSize = 14.sp,
                            color = BentoTextSecondary,
                            textAlign = TextAlign.Right
                        )
                    }
                }
            }

            // 2. Status Alert Card (Full Width System warning if permissions incomplete)
            if (!allPermissionsGranted) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Dynamic toast or scroll hint
                                Toast.makeText(context, "يرجى الموافقة على الأذونات في أسفل الشاشة لتشغيل التطبيق!", Toast.LENGTH_LONG).show()
                            },
                        colors = CardDefaults.cardColors(containerColor = BentoAlertBg),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .clickable {
                                        viewModel.checkAllPermissions()
                                        Toast.makeText(context, "تمت إعادة مراجعة الحالة", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "تحقق الآن",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(1f).padding(end = 12.dp)
                            ) {
                                Text(
                                    "تنبيه النظام",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right
                                )
                                Text(
                                    "صلاحيات الوصول غير مكتملة",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right
                                )
                            }
                        }
                    }
                }
            }

            // 3. Main Active Status Bento (Large 2-column wide block)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BentoNavyBg),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            // Badge indicating mضافين
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.4f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "+${whitelistedContacts.size} جهة",
                                    color = BentoNavyText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            // People Icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BentoNavyText),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBox,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "جهات الاتصال المفعلة",
                                color = BentoNavyText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "سيتم تجاوز الصمت والرنين لهؤلاء الأشخاص فقط لضمان سلامتك وهدوئك.",
                                color = BentoNavyText.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Right
                            )
                        }

                        // Active Call Monitor Service switch built inside this Grid Block
                        HorizontalDivider(color = BentoNavyText.copy(alpha = 0.15f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = isServiceActive,
                                onCheckedChange = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !postNotificationsGranted) {
                                        postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.toggleService()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = BentoNavyText,
                                    uncheckedThumbColor = BentoNavyText.copy(alpha = 0.4f),
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("toggle_service_button")
                            )

                            Text(
                                text = if (isServiceActive) "مراقب المكالمات قيد العمل الآن ✓" else "تشغيل الخدمة ومراقبة المكالمات",
                                color = BentoNavyText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Right
                            )
                        }
                    }
                }
            }

            // 4. Asymmetric Columns: Volume Override & DND Override Bento row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Card: Volume Override Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp),
                        colors = CardDefaults.cardColors(containerColor = BentoVioletBg),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = BentoVioletText,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "مستوى الصوت",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoVioletText,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "85%",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = BentoVioletText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Right Card: DND Mode Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp),
                        colors = CardDefaults.cardColors(containerColor = BentoRoseBg),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = BentoRoseText,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "تجاوز DND",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoRoseText,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (notificationPolicyGranted) "نشط الآن" else "غير مسموح",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoRoseText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // 5. Test Suite Sandbox Bento Box
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, BentoBorderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "منصة تجربة الرنين (المحاكاة)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = LightGold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = LightGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            "ضع هاتفك بالوضع الصامت واقفل الشاشة لتجربته. سيقوم محاكي المكالمات بتجاوز الصمت بعد 5 ثوانٍ.",
                            fontSize = 12.sp,
                            color = BentoTextSecondary,
                            textAlign = TextAlign.End
                        )

                        if (testCountdown != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.stopTestRing() },
                                    colors = ButtonDefaults.buttonColors(containerColor = BentoTextSecondary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("إلغاءوتفادي الرنين", color = Color.White, fontSize = 12.sp)
                                }
                                Text(
                                    "بدء الرنين: $testCountdown ثواني ⏳",
                                    color = BentoAlertBg,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.stopTestRing() },
                                    colors = ButtonDefaults.buttonColors(containerColor = BentoBorderColor.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("إيقاف الرنين 🔇", color = BentoTextPrimary, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        if (isServiceActive) {
                                            viewModel.startTestCountdown()
                                        } else {
                                            Toast.makeText(context, "الرجاء تفعيل الخدمة أولاً من الأعلى 👆", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BentoNavyText),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("start_test_ring_button")
                                ) {
                                    Text("بدء اختبار محاكي الرنين", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 6. Whitelisted Items Layout Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "رؤية الكل",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF005AC1),
                        modifier = Modifier.clickable {
                            Toast.makeText(context, "يتم حالياً تصفية جميع جهات الاتصال المفعلة بالأسفل", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Text(
                        text = "أحدث المضافين لجهات الاتصال",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary
                    )
                }
            }

            // 7. Whitelisted contact lists inside a beautiful container
            if (whitelistedContacts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, BentoBorderColor)
                    ) {
                        EmptyWhitelistView()
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, BentoBorderColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            whitelistedContacts.forEach { contact ->
                                ContactRowItem(
                                    contact = contact,
                                    onToggleEnabled = { viewModel.toggleContactEnabled(contact) },
                                    onDelete = { viewModel.deleteContact(contact) }
                                )
                            }
                        }
                    }
                }
            }

            // 8. Systems Access permissions manager
            item {
                Text(
                    text = "الأمن والتحكم بالنظام للأذونات",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            }

            item {
                PermissionsManagerCard(
                    contactsGranted = contactsPermissionGranted,
                    phoneGranted = phoneStatePermissionGranted && callLogPermissionGranted,
                    dndGranted = notificationPolicyGranted,
                    overlayGranted = canDrawOverlays,
                    onRequestContacts = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
                    onRequestPhone = {
                        phoneLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.READ_CALL_LOG
                            )
                        )
                    },
                    onRequestDnd = { viewModel.openDndSettings() },
                    onRequestOverlay = { viewModel.openOverlaySettings() }
                )
            }
        }
    }

    // Add Contact Sheet/Dialog Box
    if (showAddContactSheet) {
        Dialog(
            onDismissRequest = { showAddContactSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showAddContactSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = BentoTextSecondary)
                        }
                        Text(
                            "إضافة جهة اتصال مستثناة",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPrimary
                        )
                    }

                    // Selection tabs
                    TabRow(
                        selectedTabIndex = selectedTabForAdding,
                        containerColor = BentoBg,
                        contentColor = BentoNavyText,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = selectedTabForAdding == 0,
                            onClick = { selectedTabForAdding = 0 },
                            text = { Text("استيراد من الهاتف", fontSize = 13.sp) }
                        )
                        Tab(
                            selected = selectedTabForAdding == 1,
                            onClick = { selectedTabForAdding = 1 },
                            text = { Text("إدخال يدوي", fontSize = 13.sp) }
                        )
                    }

                    if (selectedTabForAdding == 0) {
                        if (!contactsPermissionGranted) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = LightGold, modifier = Modifier.size(48.dp))
                                Text(
                                    "إذن جهات الاتصال غير متوفر",
                                    color = BentoTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "يرجى توفير إذن قراءة جهات الاتصال لتتمكن من اختيار الأسماء مباشرة من سجل هاتفك.",
                                    color = BentoTextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BentoNavyText)
                                ) {
                                    Text("منح الإذن الآن")
                                }
                            }
                        } else {
                            // Load contacts
                            LaunchedEffect(Unit) {
                                viewModel.loadSystemContacts()
                            }
                            
                            val systemContactsList by viewModel.systemContacts.collectAsState()
                            val isLoadingContacts by viewModel.isContactsLoading.collectAsState()
                            
                            var searchQuery by remember { mutableStateOf("") }
                            val filteredSystemContacts = remember(systemContactsList, searchQuery) {
                                if (searchQuery.isBlank()) systemContactsList
                                else systemContactsList.filter { 
                                    it.first.contains(searchQuery, ignoreCase = true) || 
                                    it.second.contains(searchQuery) 
                                }
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("ابحث بالاسم أو الرقم...", color = BentoTextSecondary) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BentoTextSecondary) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = BentoTextPrimary,
                                        unfocusedTextColor = BentoTextPrimary,
                                        focusedBorderColor = BentoNavyText,
                                        unfocusedBorderColor = BentoBorderColor
                                    ),
                                    singleLine = true
                                )

                                if (isLoadingContacts) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = BentoNavyText)
                                    }
                                } else if (filteredSystemContacts.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("لا توجد جهات اتصال مطابقة", color = BentoTextSecondary)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 250.dp)
                                    ) {
                                        items(filteredSystemContacts) { pair ->
                                            ContactSystemRow(
                                                name = pair.first,
                                                phone = pair.second,
                                                onSelect = {
                                                    viewModel.addManualContact(pair.first, pair.second)
                                                    showAddContactSheet = false
                                                    Toast.makeText(context, "تمت إضافة ${pair.first}", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Manual input
                        var nameInput by remember { mutableStateOf("") }
                        var phoneInput by remember { mutableStateOf("") }
                        
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("الاسم") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = BentoTextPrimary,
                                    unfocusedTextColor = BentoTextPrimary,
                                    focusedLabelColor = BentoNavyText,
                                    unfocusedLabelColor = BentoTextSecondary,
                                    focusedBorderColor = BentoNavyText,
                                    unfocusedBorderColor = BentoBorderColor
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("contact_name_input"),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                label = { Text("رقم الهاتف") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = BentoTextPrimary,
                                    unfocusedTextColor = BentoTextPrimary,
                                    focusedLabelColor = BentoNavyText,
                                    unfocusedLabelColor = BentoTextSecondary,
                                    focusedBorderColor = BentoNavyText,
                                    unfocusedBorderColor = BentoBorderColor
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("contact_phone_input"),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    if (nameInput.isNotBlank() && phoneInput.isNotBlank()) {
                                        viewModel.addManualContact(nameInput.trim(), phoneInput.trim())
                                        showAddContactSheet = false
                                        Toast.makeText(context, "تمت إضافة ${nameInput}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "يرجى ملء جميع الحقول", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .testTag("save_manual_contact_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = BentoNavyText),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("إضافة إلى القائمة", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionsManagerCard(
    contactsGranted: Boolean,
    phoneGranted: Boolean,
    dndGranted: Boolean,
    overlayGranted: Boolean,
    onRequestContacts: () -> Unit,
    onRequestPhone: () -> Unit,
    onRequestDnd: () -> Unit,
    onRequestOverlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, BentoBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "صلاحيات الوصول المطلوبة للنظام",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = BentoTextPrimary,
                modifier = Modifier.align(Alignment.End)
            )

            HorizontalDivider(color = BentoBorderColor.copy(alpha = 0.5f))

            PermissionItemRow(
                title = "قراءة جهات الاتصال للفلترة",
                isGranted = contactsGranted,
                onClick = onRequestContacts
            )

            PermissionItemRow(
                title = "مراقبة المكالمات الهاتفية الواردة",
                isGranted = phoneGranted,
                onClick = onRequestPhone
            )

            PermissionItemRow(
                title = "إدارة التنبيهات واجتياز وضع عدم الإزعاج (DND)",
                isGranted = dndGranted,
                onClick = onRequestDnd
            )

            PermissionItemRow(
                title = "الظهور فوق قفل الشاشة والتطبيقات الأخرى",
                isGranted = overlayGranted,
                onClick = onRequestOverlay
            )
        }
    }
}

@Composable
fun PermissionItemRow(
    title: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BentoBg)
            .clickable { if (!isGranted) onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = BentoTextPrimary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Right
            )
            Text(
                text = if (isGranted) "تمت الموافقة بنجاح ✓" else "انقر هنا لمنح الإذن المطلوب !",
                fontSize = 11.sp,
                color = if (isGranted) ActiveGreen else BentoAlertBg,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) ActiveGreen else BentoAlertBg,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun EmptyWhitelistView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(BentoBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = BentoTextSecondary,
                modifier = Modifier.size(36.dp)
            )
        }

        Text(
            text = "قائمة الاستثناء فارغة !",
            color = BentoTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = "يرجى إضافة جهات الاتصال (مثل العائلة أو الزملاء في العمل) لتتمكن من تجاوز الوضع الصامت عند اتصالهم.",
            color = BentoTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// Generate beautiful pastel background color for contact avatar dynamically
@Composable
fun getAvatarColors(name: String): Pair<Color, Color> {
    val hash = name.hashCode().coerceAtLeast(0)
    return when(hash % 3) {
        0 -> Pair(Color(0xFFFFDBCB), Color(0xFF2F1500))  // Warm peach / dark brown text
        1 -> Pair(Color(0xFFE0E2EC), Color(0xFF191C20))  // Warm grey / deep charcoal text
        else -> Pair(Color(0xFFD6E2FF), Color(0xFF001A40)) // Light blue / navy text
    }
}

@Composable
fun ContactRowItem(
    contact: WhitelistedContact,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit
) {
    val (bgColor, textColor) = getAvatarColors(contact.name)
    val firstChar = contact.name.trim().take(1).uppercase()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BentoBg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        // Delete button on the far left
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .testTag("delete_contact_button_${contact.id}")
                .size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "حذف جهة الاتصال",
                tint = BentoAlertBg,
                modifier = Modifier.size(20.dp)
            )
        }

        // Action switch
        Switch(
            checked = contact.isEnabled,
            onCheckedChange = { onToggleEnabled() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ActiveGreen,
                uncheckedThumbColor = BentoTextSecondary,
                uncheckedTrackColor = BentoBorderColor.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .testTag("toggle_contact_switch_${contact.id}")
                .scale(0.8f)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Details column aligned right
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Text(
                text = contact.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (contact.isEnabled) BentoTextPrimary else BentoTextSecondary,
                textAlign = TextAlign.Right,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = contact.phoneNumber,
                fontSize = 12.sp,
                color = BentoTextSecondary,
                textAlign = TextAlign.Right,
                maxLines = 1
            )
        }

        // Colorful Dynamic Initial Avatar from the design spec on the right
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (contact.isEnabled) bgColor else BentoBorderColor.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstChar,
                color = if (contact.isEnabled) textColor else BentoTextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun ContactSystemRow(
    name: String,
    phone: String,
    onSelect: () -> Unit
) {
    val (bgColor, textColor) = getAvatarColors(name)
    val firstChar = name.trim().take(1).uppercase()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = BentoNavyText,
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
            Text(
                text = name,
                color = BentoTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Right
            )
            Text(
                text = phone,
                color = BentoTextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Right
            )
        }
        
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstChar,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    HorizontalDivider(color = BentoBorderColor.copy(alpha = 0.2f))
}
