package com.mt.organizemessages

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import coil.compose.AsyncImage
import com.mt.organizemessages.ui.theme.MessageMeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Google Drive Imports
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data classes
data class ChatMessage(val id: String, val threadId: Long, val address: String, val body: String, val date: Long, val isSent: Boolean, val attachmentUri: String? = null, var tags: List<String> = emptyList(), var colorHex: String? = null)
data class ContactInfo(val name: String, val phoneNumber: String)

sealed class AppScreen {
    object Inbox : AppScreen()
    object NewMessage : AppScreen()
    data class Thread(val threadId: Long, val address: String) : AppScreen()
    data class TagFilter(val tag: String) : AppScreen()
    object MetricsBrowser : AppScreen()
    object SpamFolder : AppScreen()
    object Settings : AppScreen()
    object About : AppScreen()
}

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    var isTaggingEnabled: Boolean
        get() = prefs.getBoolean("enable_tagging", true)
        set(value) = prefs.edit().putBoolean("enable_tagging", value).apply()
        
    var isMetricsEnabled: Boolean
        get() = prefs.getBoolean("enable_metrics", true)
        set(value) = prefs.edit().putBoolean("enable_metrics", value).apply()
}

// Parallel SQLite Database to store Tags, Blocks, Archives, and Colors
class TagsDbHelper(context: Context) : SQLiteOpenHelper(context, "tags.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE message_tags (message_id TEXT PRIMARY KEY, tags TEXT)")
        db.execSQL("CREATE TABLE blocked_senders (address TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE archived_threads (thread_id INTEGER PRIMARY KEY)")
        db.execSQL("CREATE TABLE message_colors (message_id TEXT PRIMARY KEY, color_hex TEXT)")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE blocked_senders (address TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE archived_threads (thread_id INTEGER PRIMARY KEY)")
            db.execSQL("CREATE TABLE message_colors (message_id TEXT PRIMARY KEY, color_hex TEXT)")
        }
    }

    fun getAllTagsMap(): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        val cursor = readableDatabase.query("message_tags", arrayOf("message_id", "tags"), null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val tagsStr = it.getString(1) ?: ""
                if (tagsStr.isNotBlank()) {
                    map[id] = tagsStr.split(",").map { t -> t.trim() }
                }
            }
        }
        return map
    }

    fun setTagsForMessage(messageId: String, tags: List<String>) {
        val tagsStr = tags.joinToString(",")
        val values = ContentValues().apply {
            put("message_id", messageId)
            put("tags", tagsStr)
        }
        writableDatabase.insertWithOnConflict("message_tags", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getBlockedSenders(): Set<String> {
        val set = mutableSetOf<String>()
        val cursor = readableDatabase.query("blocked_senders", arrayOf("address"), null, null, null, null, null)
        cursor.use { while (it.moveToNext()) set.add(it.getString(0)) }
        return set
    }

    fun setBlockedSender(address: String) {
        val values = ContentValues().apply { put("address", address) }
        writableDatabase.insertWithOnConflict("blocked_senders", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun removeBlockedSender(address: String) {
        writableDatabase.delete("blocked_senders", "address = ?", arrayOf(address))
    }

    fun getArchivedThreads(): Set<Long> {
        val set = mutableSetOf<Long>()
        val cursor = readableDatabase.query("archived_threads", arrayOf("thread_id"), null, null, null, null, null)
        cursor.use { while (it.moveToNext()) set.add(it.getLong(0)) }
        return set
    }

    fun setArchivedThread(threadId: Long) {
        val values = ContentValues().apply { put("thread_id", threadId) }
        writableDatabase.insertWithOnConflict("archived_threads", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun getAllMessageColorsMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val cursor = readableDatabase.query("message_colors", arrayOf("message_id", "color_hex"), null, null, null, null, null)
        cursor.use { while (it.moveToNext()) map[it.getString(0)] = it.getString(1) }
        return map
    }

    fun setMessageColor(messageId: String, colorHex: String?) {
        if (colorHex == null) {
            writableDatabase.delete("message_colors", "message_id = ?", arrayOf(messageId))
        } else {
            val values = ContentValues().apply {
                put("message_id", messageId)
                put("color_hex", colorHex)
            }
            writableDatabase.insertWithOnConflict("message_colors", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MessageMeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SmsAppContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SmsAppContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(false) }
    var isDefaultSms by remember { 
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
            } else {
                Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            isDefaultSms = roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
        } else {
            isDefaultSms = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }

    val requiredPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE
    )

    if (!isDefaultSms) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Default App Required",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "To send and receive MMS (pictures and group chats), MessageMe must be set as your default SMS app.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Button(onClick = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                    if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                        defaultSmsLauncher.launch(roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS))
                    }
                } else {
                    val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                    defaultSmsLauncher.launch(intent)
                }
            }) {
                Text("Set as Default SMS App")
            }
        }
    } else if (hasPermissions) {
        var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Inbox) }
        val settingsManager = remember { SettingsManager(context) }
        var isTaggingEnabled by remember { mutableStateOf(settingsManager.isTaggingEnabled) }
        var isMetricsEnabled by remember { mutableStateOf(settingsManager.isMetricsEnabled) }
        
        when (val screen = currentScreen) {
            is AppScreen.Inbox -> MainSmsScreen(
                modifier = modifier,
                isMetricsEnabled = isMetricsEnabled,
                onNavigateToNewMessage = { currentScreen = AppScreen.NewMessage },
                onNavigateToMetrics = { currentScreen = AppScreen.MetricsBrowser },
                onNavigateToSpam = { currentScreen = AppScreen.SpamFolder },
                onNavigateToSettings = { currentScreen = AppScreen.Settings },
                onNavigateToAbout = { currentScreen = AppScreen.About },
                onNavigateToThread = { threadId, address -> currentScreen = AppScreen.Thread(threadId, address) },
                onNavigateToTagFilter = { tag -> currentScreen = AppScreen.TagFilter(tag) }
            )
            is AppScreen.NewMessage -> NewMessageScreen(
                modifier = modifier,
                onNavigateBack = { currentScreen = AppScreen.Inbox }
            )
            is AppScreen.Thread -> ThreadScreen(
                modifier = modifier,
                threadId = screen.threadId,
                address = screen.address,
                isTaggingEnabled = isTaggingEnabled,
                onNavigateBack = { currentScreen = AppScreen.Inbox },
                onTagClick = { tag -> currentScreen = AppScreen.TagFilter(tag) }
            )
            is AppScreen.TagFilter -> TagFilterScreen(
                modifier = modifier,
                tag = screen.tag,
                onNavigateToThread = { threadId, address -> currentScreen = AppScreen.Thread(threadId, address) },
                onNavigateBack = { currentScreen = AppScreen.Inbox }
            )
            is AppScreen.MetricsBrowser -> MetricsBrowserScreen(
                modifier = modifier,
                onNavigateBack = { currentScreen = AppScreen.Inbox }
            )
            is AppScreen.SpamFolder -> SpamFolderScreen(
                modifier = modifier,
                onNavigateToThread = { threadId, address -> currentScreen = AppScreen.Thread(threadId, address) },
                onNavigateBack = { currentScreen = AppScreen.Inbox }
            )
            is AppScreen.Settings -> SettingsScreen(
                modifier = modifier,
                settingsManager = settingsManager,
                onSettingsChanged = {
                    isTaggingEnabled = settingsManager.isTaggingEnabled
                    isMetricsEnabled = settingsManager.isMetricsEnabled
                },
                onNavigateBack = { currentScreen = AppScreen.Inbox }
            )
            is AppScreen.About -> AboutScreen(
                modifier = modifier,
                onNavigateBack = { currentScreen = AppScreen.Inbox }
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to MessageMe!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "To read, send messages, and fetch contacts, this app requires permissions. Please grant them to continue.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                Text("Grant Permissions")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSmsScreen(
    modifier: Modifier = Modifier, 
    isMetricsEnabled: Boolean,
    onNavigateToNewMessage: () -> Unit,
    onNavigateToMetrics: () -> Unit,
    onNavigateToSpam: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToThread: (Long, String) -> Unit,
    onNavigateToTagFilter: (String) -> Unit
) {
    val context = LocalContext.current
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var blockedSenders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var archivedThreads by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val drawerWidth = configuration.screenWidthDp.dp * 0.3f

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val driveAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) {
                backupToDrive(context, account)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Google Sign-In Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(context) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val db = TagsDbHelper(context)
                blockedSenders = db.getBlockedSenders()
                archivedThreads = db.getArchivedThreads()
                messages = fetchAllMessages(context).distinctBy { it.threadId }
            }
        }
        val db = TagsDbHelper(context)
        blockedSenders = db.getBlockedSenders()
        archivedThreads = db.getArchivedThreads()
        context.contentResolver.registerContentObserver(Uri.parse("content://mms-sms/conversations"), true, observer)
        messages = fetchAllMessages(context).distinctBy { it.threadId }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val allTags = remember(messages) {
        val db = TagsDbHelper(context)
        db.getAllTagsMap().values.flatten().distinct().filter { it.isNotBlank() }.sorted()
    }

    val visibleMessages = messages.filter { 
        !blockedSenders.contains(it.address) && 
        !archivedThreads.contains(it.threadId) &&
        (searchQuery.isBlank() || 
         it.address.contains(searchQuery, ignoreCase = true) || 
         it.body.contains(searchQuery, ignoreCase = true))
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(drawerWidth)
            ) {
                Spacer(Modifier.height(16.dp))
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigateToSettings() }
                )
                NavigationDrawerItem(
                    label = { Text("About") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onNavigateToAbout() }
                )
                if (allTags.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Tags",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 28.dp, bottom = 8.dp, top = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    allTags.forEach { tag ->
                        NavigationDrawerItem(
                            label = { Text(tag) },
                            icon = { Icon(Icons.Filled.LocalOffer, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            selected = false,
                            onClick = { 
                                scope.launch { drawerState.close() }
                                onNavigateToTagFilter(tag)
                            }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = { 
                TopAppBar(
                    title = { Text("Inbox") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSpam) {
                        Icon(Icons.Filled.Block, contentDescription = "Spam Folder")
                    }
                    if (isMetricsEnabled) {
                        IconButton(onClick = onNavigateToMetrics) {
                            Icon(Icons.Filled.Analytics, contentDescription = "Metrics")
                        }
                    }
                    IconButton(onClick = { 
                        googleSignInClient.signOut().addOnCompleteListener {
                            driveAuthLauncher.launch(googleSignInClient.signInIntent)
                        }
                    }) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = "Backup to Google Drive")
                    }
                }
            ) 
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToNewMessage) {
                Icon(Icons.Filled.Add, contentDescription = "New Message")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search messages...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp)
            )

            if (visibleMessages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Your inbox is empty.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                    items(visibleMessages) { msg ->
                        InboxItem(
                            msg = msg, 
                            onClick = { onNavigateToThread(msg.threadId, msg.address) },
                            onArchive = {
                                val db = TagsDbHelper(context)
                                db.setArchivedThread(msg.threadId)
                                archivedThreads = db.getArchivedThreads()
                                Toast.makeText(context, "Thread Archived", Toast.LENGTH_SHORT).show()
                            },
                            onBlock = {
                                val db = TagsDbHelper(context)
                                db.setBlockedSender(msg.address)
                                blockedSenders = db.getBlockedSenders()
                                Toast.makeText(context, "Sender Blocked & Reported", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(modifier: Modifier = Modifier, threadId: Long, address: String, isTaggingEnabled: Boolean, onNavigateBack: () -> Unit, onTagClick: (String) -> Unit) {
    val context = LocalContext.current
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var replyText by remember { mutableStateOf("") }
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            simulateSendMms(context, threadId, address, uri)
        }
    }

    DisposableEffect(context, threadId) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                messages = fetchChatThread(context, threadId)
            }
        }
        context.contentResolver.registerContentObserver(Uri.parse("content://mms-sms/conversations"), true, observer)
        messages = fetchChatThread(context, threadId)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(address) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    photoPickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Attach File")
                }
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Text message") },
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            sendSmsMessage(context, address, replyText)
                            replyText = ""
                        }
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            reverseLayout = true
        ) {
            items(messages) { msg ->
                ChatBubble(msg, isTaggingEnabled, onTagClick)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, settingsManager: SettingsManager, onSettingsChanged: () -> Unit, onNavigateBack: () -> Unit) {
    var isTaggingEnabled by remember { mutableStateOf(settingsManager.isTaggingEnabled) }
    var isMetricsEnabled by remember { mutableStateOf(settingsManager.isMetricsEnabled) }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Tagging", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = isTaggingEnabled,
                    onCheckedChange = { 
                        isTaggingEnabled = it
                        settingsManager.isTaggingEnabled = it
                        onSettingsChanged()
                    }
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Metrics", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = isMetricsEnabled,
                    onCheckedChange = { 
                        isMetricsEnabled = it
                        settingsManager.isMetricsEnabled = it
                        onSettingsChanged()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(modifier: Modifier = Modifier, onNavigateBack: () -> Unit) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.LocalOffer, contentDescription = "Logo", modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("MessageMe", style = MaterialTheme.typography.headlineMedium)
            Text("Version 1.0.0", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Text("Created for Android", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterScreen(modifier: Modifier = Modifier, tag: String, onNavigateToThread: (Long, String) -> Unit, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    
    LaunchedEffect(tag) {
        val allMsgs = fetchAllMessages(context)
        messages = allMsgs.filter { it.tags.contains(tag) }
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Tag: $tag") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(messages) { msg ->
                InboxItem(msg, onClick = { onNavigateToThread(msg.threadId, msg.address) }, {}, {})
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(modifier: Modifier = Modifier, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<ContactInfo>>(emptyList()) }
    var availableSims by remember { mutableStateOf<List<SubscriptionInfo>>(emptyList()) }
    var selectedSim by remember { mutableStateOf<SubscriptionInfo?>(null) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numberIdx != -1) searchQuery = cursor.getString(numberIdx)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        contacts = fetchContacts(context)
        try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            availableSims = sm.activeSubscriptionInfoList ?: emptyList()
            if (availableSims.isNotEmpty()) selectedSim = availableSims[0]
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    val filteredContacts = remember(searchQuery, contacts) {
        if (searchQuery.isBlank()) emptyList()
        else contacts.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || it.phoneNumber.contains(searchQuery)
        }.take(5)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Message") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("To: Name or Number") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { contactPickerLauncher.launch(null) }) {
                        Icon(Icons.Filled.Person, contentDescription = "Pick Contact")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color(0xFFF5F5F5),
                    unfocusedContainerColor = Color(0xFFF5F5F5)
                )
            )

            if (filteredContacts.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column {
                        filteredContacts.forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { searchQuery = contact.phoneNumber }
                                    .padding(16.dp)
                            ) {
                                Text(text = "${contact.name} (${contact.phoneNumber})")
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = messageBody,
                onValueChange = { messageBody = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color(0xFFF5F5F5),
                    unfocusedContainerColor = Color(0xFFF5F5F5)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (availableSims.isNotEmpty()) {
                    var showSimMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showSimMenu = true }) {
                            Icon(
                                Icons.Filled.SimCard, 
                                contentDescription = "Select SIM",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(expanded = showSimMenu, onDismissRequest = { showSimMenu = false }) {
                            availableSims.forEach { sim ->
                                DropdownMenuItem(
                                    text = { Text(sim.displayName.toString()) },
                                    onClick = { selectedSim = sim; showSimMenu = false }
                                )
                            }
                        }
                    }
                    if (selectedSim != null) {
                        Text(
                            text = selectedSim?.displayName?.toString() ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                
                Button(
                    onClick = {
                        if (searchQuery.isNotBlank() && messageBody.isNotBlank()) {
                            sendSmsMessage(context, searchQuery, messageBody, selectedSim?.subscriptionId)
                            onNavigateBack()
                        } else {
                            Toast.makeText(context, "Please enter a recipient and a message", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsBrowserScreen(modifier: Modifier = Modifier, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var htmlContent by remember { mutableStateOf("<html><body><h2>Loading metrics...</h2></body></html>") }
    
    LaunchedEffect(Unit) {
        htmlContent = generateMetricsHtml(context)
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Metrics Browser") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamFolderScreen(modifier: Modifier = Modifier, onNavigateToThread: (Long, String) -> Unit, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var blockedSenders by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    fun refresh() {
        val db = TagsDbHelper(context)
        blockedSenders = db.getBlockedSenders()
        messages = fetchAllMessages(context).distinctBy { it.threadId }.filter { blockedSenders.contains(it.address) }
    }

    LaunchedEffect(Unit) { refresh() }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Spam & Blocked") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(messages) { msg ->
                InboxItem(
                    msg = msg, 
                    onClick = { onNavigateToThread(msg.threadId, msg.address) }, 
                    onArchive = {}, 
                    onBlock = {},
                    isSpamView = true,
                    onUnblock = {
                        TagsDbHelper(context).removeBlockedSender(msg.address)
                        refresh()
                        Toast.makeText(context, "Sender Unblocked", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun InboxItem(msg: ChatMessage, onClick: () -> Unit, onArchive: () -> Unit, onBlock: () -> Unit, isSpamView: Boolean = false, onUnblock: () -> Unit = {}) {
    val formatter = SimpleDateFormat("MMM dd", Locale.getDefault())
    val dateString = formatter.format(Date(msg.date))
    val previewText = if (msg.attachmentUri != null) "🖼️ Picture message" else msg.body
    var menuExpanded by remember { mutableStateOf(false) }

    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val cardColor = msg.colorHex?.let { Color(android.graphics.Color.parseColor(it)) } ?: baseColor
    val titleColor = if (msg.colorHex != null) Color.White else MaterialTheme.colorScheme.primary
    val subTextColor = if (msg.colorHex != null) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = msg.address,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.labelSmall,
                        color = subTextColor
                    )
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More Options", tint = subTextColor)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            if (isSpamView) {
                                DropdownMenuItem(
                                    text = { Text("Unblock Sender") },
                                    onClick = { menuExpanded = false; onUnblock() }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Archive Thread") },
                                    onClick = { menuExpanded = false; onArchive() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Block & Report Spam") },
                                    onClick = { menuExpanded = false; onBlock() }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodyMedium,
                color = subTextColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (msg.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    msg.tags.forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage, isTaggingEnabled: Boolean, onTagClick: (String) -> Unit) {
    val context = LocalContext.current
    val alignment = if (msg.isSent) Alignment.CenterEnd else Alignment.CenterStart
    
    var localTags by remember(msg) { mutableStateOf(msg.tags) }
    var localColorHex by remember(msg) { mutableStateOf(msg.colorHex) }
    var showDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

    val baseColor = if (msg.isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val bubbleColor = localColorHex?.let { Color(android.graphics.Color.parseColor(it)) } ?: baseColor
    val textColor = if (localColorHex != null) Color.White else if (msg.isSent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    if (showDialog) {
        var tagInput by remember { mutableStateOf(localTags.joinToString(",")) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Edit Tags") },
            text = { 
                OutlinedTextField(
                    value = tagInput, 
                    onValueChange = { tagInput = it },
                    label = { Text("Tags (comma separated)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newTags = tagInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val db = TagsDbHelper(context)
                    db.setTagsForMessage(msg.id, newTags)
                    localTags = newTags
                    showDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showColorDialog) {
        val presets = listOf(null, "#D32F2F", "#388E3C", "#1976D2", "#FBC02D")
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("Bubble Color") },
            text = {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    presets.forEach { hex ->
                        val btnColor = hex?.let { Color(android.graphics.Color.parseColor(it)) } ?: MaterialTheme.colorScheme.surfaceVariant
                        Box(modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(btnColor)
                            .clickable {
                                TagsDbHelper(context).setMessageColor(msg.id, hex)
                                localColorHex = hex
                                showColorDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showColorDialog = false }) { Text("Close") } }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (msg.attachmentUri != null) {
                    AsyncImage(
                        model = Uri.parse(msg.attachmentUri),
                        contentDescription = "MMS Attachment",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    if (msg.body.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
                }
                if (msg.body.isNotBlank()) {
                    Text(
                        text = msg.body,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                // Features row: Tags + Color
                Row(
                    modifier = Modifier.padding(top = 6.dp), 
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    if (isTaggingEnabled) {
                        localTags.forEach { tag ->
                            AssistChip(
                                onClick = { onTagClick(tag) },
                                label = { Text(tag, fontSize = 10.sp) },
                                modifier = Modifier.padding(end = 4.dp).height(24.dp)
                            )
                        }
                        IconButton(
                            onClick = { showDialog = true }, 
                            modifier = Modifier.size(24.dp).padding(start = 2.dp)
                        ) {
                            Icon(Icons.Filled.LocalOffer, contentDescription = "Edit Tags", modifier = Modifier.size(16.dp), tint = textColor)
                        }
                    }
                    IconButton(
                        onClick = { showColorDialog = true }, 
                        modifier = Modifier.size(24.dp).padding(start = 2.dp)
                    ) {
                        Icon(Icons.Filled.ColorLens, contentDescription = "Color", modifier = Modifier.size(16.dp), tint = textColor)
                    }
                }
            }
        }
    }
}

// Data fetching helpers
fun fetchSms(context: Context, targetThreadId: Long?): List<ChatMessage> {
    val messages = mutableListOf<ChatMessage>()
    try {
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
        val selection = if (targetThreadId != null) "${Telephony.Sms.THREAD_ID} = ?" else null
        val selectionArgs = if (targetThreadId != null) arrayOf(targetThreadId.toString()) else null
        
        val cursor = context.contentResolver.query(Telephony.Sms.CONTENT_URI, projection, selection, selectionArgs, null)
        cursor?.use {
            val idxId = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val idxThread = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val idxAddress = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val idxBody = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val idxDate = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val idxType = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)

            while (it.moveToNext()) {
                val idStr = "sms_${it.getLong(idxId)}"
                val threadId = it.getLong(idxThread)
                val address = it.getString(idxAddress) ?: "Unknown"
                val body = it.getString(idxBody) ?: ""
                val date = it.getLong(idxDate)
                val isSent = it.getInt(idxType) == Telephony.Sms.MESSAGE_TYPE_SENT
                messages.add(ChatMessage(idStr, threadId, address, body, date, isSent, null))
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return messages
}

fun fetchMms(context: Context, targetThreadId: Long?): List<ChatMessage> {
    val messages = mutableListOf<ChatMessage>()
    try {
        val uri = Telephony.Mms.CONTENT_URI
        val projection = arrayOf("_id", "thread_id", "date", "msg_box")
        val selection = if (targetThreadId != null) "thread_id = ?" else null
        val selectionArgs = if (targetThreadId != null) arrayOf(targetThreadId.toString()) else null
        
        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            val idIdx = it.getColumnIndexOrThrow("_id")
            val threadIdx = it.getColumnIndexOrThrow("thread_id")
            val dateIdx = it.getColumnIndexOrThrow("date")
            val msgBoxIdx = it.getColumnIndexOrThrow("msg_box")
            
            while (it.moveToNext()) {
                val mmsId = it.getString(idIdx)
                val idStr = "mms_$mmsId"
                val threadId = it.getLong(threadIdx)
                val date = it.getLong(dateIdx) * 1000L // MMS dates are in seconds
                val isSent = it.getInt(msgBoxIdx) == Telephony.Mms.MESSAGE_BOX_SENT
                
                var body = ""
                var attachmentUri: String? = null
                
                val partUri = Uri.parse("content://mms/part")
                val partSelection = "mid = ?"
                val partArgs = arrayOf(mmsId)
                val partCursor = context.contentResolver.query(partUri, null, partSelection, partArgs, null)
                partCursor?.use { pc ->
                    val ctIdx = pc.getColumnIndexOrThrow("ct")
                    val textIdx = pc.getColumnIndexOrThrow("text")
                    val partIdIdx = pc.getColumnIndexOrThrow("_id")
                    
                    while (pc.moveToNext()) {
                        val contentType = pc.getString(ctIdx) ?: ""
                        if ("text/plain" == contentType) {
                            body = pc.getString(textIdx) ?: ""
                        } else if (contentType.startsWith("image/")) {
                            val partId = pc.getString(partIdIdx)
                            attachmentUri = "content://mms/part/$partId"
                        }
                    }
                }
                
                var address = "Unknown"
                val addrUri = Uri.parse("content://mms/$mmsId/addr")
                val addrCursor = context.contentResolver.query(addrUri, null, null, null, null)
                addrCursor?.use { ac ->
                    val typeIdx = ac.getColumnIndexOrThrow("type")
                    val addressIdx = ac.getColumnIndexOrThrow("address")
                    while (ac.moveToNext()) {
                        val type = ac.getInt(typeIdx)
                        if (isSent && type == 151) {
                            address = ac.getString(addressIdx) ?: "Unknown"
                            break
                        } else if (!isSent && type == 137) {
                            val rawAddr = ac.getString(addressIdx) ?: "Unknown"
                            if (rawAddr != "insert-address-token") {
                                address = rawAddr
                                break
                            }
                        }
                    }
                }
                
                messages.add(ChatMessage(idStr, threadId, address, body, date, isSent, attachmentUri))
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return messages
}

fun fetchAllMessages(context: Context): List<ChatMessage> {
    val sms = fetchSms(context, null)
    val mms = fetchMms(context, null)
    val allMsgs = (sms + mms).sortedByDescending { it.date }
    
    val tagsDb = TagsDbHelper(context)
    val tagsMap = tagsDb.getAllTagsMap()
    val colorsMap = tagsDb.getAllMessageColorsMap()
    
    allMsgs.forEach { 
        it.tags = tagsMap[it.id] ?: emptyList() 
        it.colorHex = colorsMap[it.id]
    }
    
    return allMsgs
}

fun fetchChatThread(context: Context, threadId: Long): List<ChatMessage> {
    val sms = fetchSms(context, threadId)
    val mms = fetchMms(context, threadId)
    val allMsgs = (sms + mms).sortedByDescending { it.date }
    
    val tagsDb = TagsDbHelper(context)
    val tagsMap = tagsDb.getAllTagsMap()
    val colorsMap = tagsDb.getAllMessageColorsMap()
    
    allMsgs.forEach { 
        it.tags = tagsMap[it.id] ?: emptyList() 
        it.colorHex = colorsMap[it.id]
    }
    
    return allMsgs
}

fun fetchContacts(context: Context): List<ContactInfo> {
    val contactsList = mutableListOf<ContactInfo>()
    try {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            
            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: "Unknown"
                val number = it.getString(numberIndex) ?: ""
                val cleanNumber = number.replace(Regex("[^0-9+]"), "")
                if (cleanNumber.isNotEmpty()) contactsList.add(ContactInfo(name, cleanNumber))
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return contactsList.distinctBy { it.phoneNumber }
}

fun sendSmsMessage(context: Context, phoneNumber: String, message: String, subId: Int? = null) {
    try {
        val smsManager = if (subId != null && subId != -1) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
        } else {
            context.getSystemService(SmsManager::class.java)
        }
        smsManager.sendTextMessage(phoneNumber, null, message, null, null)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun simulateSendMms(context: Context, threadId: Long, address: String, imageUri: Uri) {
    try {
        val values = ContentValues().apply {
            put("thread_id", threadId)
            put("date", System.currentTimeMillis() / 1000L)
            put("msg_box", Telephony.Mms.MESSAGE_BOX_SENT)
            put("read", 1)
            put("m_type", 128) // PduHeaders.MESSAGE_TYPE_SEND_REQ
            put("v", 18)       // PduHeaders.CURRENT_MMS_VERSION
        }
        val mmsUri = context.contentResolver.insert(Telephony.Mms.CONTENT_URI, values)
        val messageId = mmsUri?.lastPathSegment
        
        if (messageId != null) {
            val partValues = ContentValues().apply {
                put("mid", messageId)
                put("ct", "image/jpeg") 
                put("chset", 106)
            }
            val partUri = context.contentResolver.insert(Uri.parse("content://mms/$messageId/part"), partValues)
            
            if (partUri != null) {
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    context.contentResolver.openOutputStream(partUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            val addrValues = ContentValues().apply {
                put("msg_id", messageId)
                put("type", 151) // PduHeaders.TO
                put("address", address)
                put("charset", 106)
            }
            context.contentResolver.insert(Uri.parse("content://mms/$messageId/addr"), addrValues)
            Toast.makeText(context, "MMS Simulated Locally!", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Simulation Failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// ---------------- Metrics HTML Generator ----------------

fun generateMetricsHtml(context: Context): String {
    val allMessages = fetchAllMessages(context)
    val totalMessages = allMessages.size
    
    val db = TagsDbHelper(context)
    val tagsMap = db.getAllTagsMap()
    val flatTags = tagsMap.values.flatten().map { it.trim().lowercase() }
    val totalUniqueTags = flatTags.distinct().size
    val messagesPerTag = flatTags.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    
    val mostFrequentSender = allMessages.filter { !it.isSent }
        .groupingBy { it.address }
        .eachCount()
        .maxByOrNull { it.value }
    
    val mostFrequentSenderName = mostFrequentSender?.key ?: "None"
    val mostFrequentSenderCount = mostFrequentSender?.value ?: 0
    
    val tagRows = messagesPerTag.joinToString("\n") { (tag, count) ->
        "<tr><td>#$tag</td><td>$count</td></tr>"
    }
    
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; padding: 20px; line-height: 1.6; color: #333; }
                h1 { color: #1a73e8; border-bottom: 2px solid #eee; padding-bottom: 10px; }
                .metric-card { background: #f8f9fa; border-radius: 8px; padding: 15px; margin-bottom: 15px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
                .metric-value { font-size: 24px; font-weight: bold; color: #1a73e8; }
                table { width: 100%; border-collapse: collapse; margin-top: 20px; }
                th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }
                th { background-color: #f1f3f4; font-weight: bold; }
            </style>
        </head>
        <body>
            <h1>MessageMe Metrics</h1>
            
            <div class="metric-card">
                <div>Total Messages</div>
                <div class="metric-value">$totalMessages</div>
            </div>
            
            <div class="metric-card">
                <div>Total Unique Tags</div>
                <div class="metric-value">$totalUniqueTags</div>
            </div>
            
            <div class="metric-card">
                <div>Most Frequent Sender</div>
                <div class="metric-value">$mostFrequentSenderName <span style="font-size: 16px; font-weight: normal; color: #666;">($mostFrequentSenderCount messages)</span></div>
            </div>
            
            <h2>Tags Breakdown</h2>
            <table>
                <tr><th>Tag Name</th><th>Message Count</th></tr>
                ${if (tagRows.isEmpty()) "<tr><td colspan='2'>No tags yet</td></tr>" else tagRows}
            </table>
        </body>
        </html>
    """.trimIndent()
}

// ---------------- Google Drive Backup Engine ----------------

fun backupToDrive(context: Context, account: GoogleSignInAccount) {
    Toast.makeText(context, "Starting Drive Backup. Please wait...", Toast.LENGTH_LONG).show()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            credential.selectedAccount = account.account
            
            val driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("MessageMe").build()

            // 1. Compile all messages into Standard XML format
            val messages = fetchAllMessages(context)
            val xmlBuilder = StringBuilder()
            xmlBuilder.append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n")
            xmlBuilder.append("<smses count=\"${messages.size}\">\n")
            
            for (msg in messages) {
                val type = if (msg.isSent) "2" else "1"
                val escapedAddress = android.text.TextUtils.htmlEncode(msg.address)
                val escapedBody = android.text.TextUtils.htmlEncode(msg.body)
                
                val tagsStr = if (msg.tags.isNotEmpty()) msg.tags.joinToString(",") else ""
                val escapedTags = android.text.TextUtils.htmlEncode(tagsStr)
                
                xmlBuilder.append("  <sms address=\"$escapedAddress\" date=\"${msg.date}\" type=\"$type\" body=\"$escapedBody\" read=\"1\"")
                if (escapedTags.isNotBlank()) {
                    xmlBuilder.append(" tags=\"$escapedTags\"")
                }
                xmlBuilder.append(" />\n")
            }
            xmlBuilder.append("</smses>\n")
            val xmlString = xmlBuilder.toString()

            // 2. Create "MessageMe_Backups" folder
            val folderMetadata = File().apply {
                name = "MessageMe_Backups"
                mimeType = "application/vnd.google-apps.folder"
            }
            val folder = driveService.files().create(folderMetadata).setFields("id").execute()
            val folderId = folder.id

            // 3. Upload XML data file
            val fileMetadata = File().apply {
                name = "MessageMe_Backup_${System.currentTimeMillis()}.xml"
                parents = listOf(folderId)
            }
            val content = ByteArrayContent.fromString("text/xml", xmlString)
            driveService.files().create(fileMetadata, content).execute()

            // 4. Upload MMS attachments (images)
            messages.filter { it.attachmentUri != null }.forEach { msg ->
                val uri = Uri.parse(msg.attachmentUri)
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        val imgMetadata = File().apply {
                            name = "mms_${msg.date}.jpg"
                            parents = listOf(folderId)
                        }
                        val imgContent = ByteArrayContent("image/jpeg", bytes)
                        driveService.files().create(imgMetadata, imgContent).execute()
                    }
                } catch (e: Exception) {
                    e.printStackTrace() // Skip failed images silently to prevent full backup failure
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Backup Completed Successfully!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Backup Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}