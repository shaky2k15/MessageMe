package com.mt.organizemessages

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.mt.organizemessages.data.MessageRepository
import com.mt.organizemessages.domain.BackupEngine
import com.mt.organizemessages.domain.MetricsEngine
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
                if (tagsStr.isNotBlank()) map[id] = tagsStr.split(",").map { t -> t.trim() }
            }
        }
        return map
    }
    fun setTagsForMessage(messageId: String, tags: List<String>) {
        val values = android.content.ContentValues().apply {
            put("message_id", messageId)
            put("tags", tags.joinToString(","))
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
        val values = android.content.ContentValues().apply { put("address", address) }
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
        val values = android.content.ContentValues().apply { put("thread_id", threadId) }
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
            val values = android.content.ContentValues().apply {
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

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasPermissions = it.values.all { v -> v } }
    val defaultSmsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultSms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            context.getSystemService(android.app.role.RoleManager::class.java).isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
    }

    val requiredPermissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE)

    if (!isDefaultSms) {
        Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Default App Required", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("MessageMe must be the default SMS app to function.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Button(onClick = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val rm = context.getSystemService(android.app.role.RoleManager::class.java)
                    defaultSmsLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS))
                } else {
                    val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                    defaultSmsLauncher.launch(intent)
                }
            }) { Text("Set as Default") }
        }
    } else if (hasPermissions) {
        var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Inbox) }
        val settingsManager = remember { SettingsManager(context) }
        var taggingEnabled by remember { mutableStateOf(settingsManager.isTaggingEnabled) }
        var metricsEnabled by remember { mutableStateOf(settingsManager.isMetricsEnabled) }
        
        when (val screen = currentScreen) {
            is AppScreen.Inbox -> MainSmsScreen(modifier, metricsEnabled, { currentScreen = AppScreen.NewMessage }, { currentScreen = AppScreen.MetricsBrowser }, { currentScreen = AppScreen.SpamFolder }, { currentScreen = AppScreen.Settings }, { currentScreen = AppScreen.About }, { t, a -> currentScreen = AppScreen.Thread(t, a) }, { t -> currentScreen = AppScreen.TagFilter(t) })
            is AppScreen.NewMessage -> NewMessageScreen(modifier) { currentScreen = AppScreen.Inbox }
            is AppScreen.Thread -> ThreadScreen(modifier, screen.threadId, screen.address, taggingEnabled, { currentScreen = AppScreen.Inbox }, { t -> currentScreen = AppScreen.TagFilter(t) })
            is AppScreen.TagFilter -> TagFilterScreen(modifier, screen.tag, { t, a -> currentScreen = AppScreen.Thread(t, a) }, { currentScreen = AppScreen.Inbox })
            is AppScreen.MetricsBrowser -> MetricsBrowserScreen(modifier) { currentScreen = AppScreen.Inbox }
            is AppScreen.SpamFolder -> SpamFolderScreen(modifier, { t, a -> currentScreen = AppScreen.Thread(t, a) }, { currentScreen = AppScreen.Inbox })
            is AppScreen.Settings -> SettingsScreen(modifier, settingsManager, { taggingEnabled = settingsManager.isTaggingEnabled; metricsEnabled = settingsManager.isMetricsEnabled }, { currentScreen = AppScreen.Inbox })
            is AppScreen.About -> AboutScreen(modifier) { currentScreen = AppScreen.Inbox }
        }
    } else {
        Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { permissionLauncher.launch(requiredPermissions) }) { Text("Grant Permissions") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSmsScreen(modifier: Modifier, metricsEnabled: Boolean, onNewMsg: () -> Unit, onMetrics: () -> Unit, onSpam: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit, onThread: (Long, String) -> Unit, onTag: (String) -> Unit) {
    val context = LocalContext.current
    val messageRepo = remember { MessageRepository(context) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var blocked by remember { mutableStateOf(emptySet<String>()) }
    var archived by remember { mutableStateOf(emptySet<Long>()) }
    var searchQuery by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestScopes(Scope(DriveScopes.DRIVE_FILE)).build()
    val googleClient = GoogleSignIn.getClient(context, gso)
    val driveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        GoogleSignIn.getSignedInAccountFromIntent(res.data).result?.let { backupToDrive(context, it, messages) }
    }

    LaunchedEffect(Unit) {
        val db = TagsDbHelper(context)
        blocked = db.getBlockedSenders()
        archived = db.getArchivedThreads()
        messages = messageRepo.fetchAllMessages()
    }

    val allTags = remember(messages) { messages.flatMap { it.tags }.distinct().filter { it.isNotBlank() }.sorted() }
    val visibleMessages = messages.filter { !blocked.contains(it.address) && !archived.contains(it.threadId) && (searchQuery.isBlank() || it.address.contains(searchQuery, true) || it.body.contains(searchQuery, true)) }

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet(Modifier.width(LocalConfiguration.current.screenWidthDp.dp * 0.3f)) {
            Spacer(Modifier.height(16.dp))
            NavigationDrawerItem({ Text("Settings") }, false, { scope.launch { drawerState.close() }; onSettings() })
            NavigationDrawerItem({ Text("About") }, false, { scope.launch { drawerState.close() }; onAbout() })
            if (allTags.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Tags", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 28.dp, bottom = 8.dp, top = 8.dp), color = MaterialTheme.colorScheme.primary)
                allTags.forEach { tag ->
                    NavigationDrawerItem({ Text(tag) }, false, { scope.launch { drawerState.close() }; onTag(tag) }, icon = { Icon(Icons.Filled.LocalOffer, null, Modifier.size(18.dp)) })
                }
            }
        }
    }) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("Inbox") }, navigationIcon = { IconButton({ scope.launch { drawerState.open() } }) { Icon(Icons.Filled.Menu, null) } }, actions = {
                IconButton(onSpam) { Icon(Icons.Filled.Block, null) }
                if (metricsEnabled) IconButton(onMetrics) { Icon(Icons.Filled.Analytics, null) }
                IconButton({ googleClient.signOut().addOnCompleteListener { driveLauncher.launch(googleClient.signInIntent) } }) { Icon(Icons.Filled.CloudUpload, null) }
            })
        }, floatingActionButton = { FloatingActionButton(onNewMsg) { Icon(Icons.Filled.Add, null) } }) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                OutlinedTextField(searchQuery, { searchQuery = it }, placeholder = { Text("Search...") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(24.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(visibleMessages) { msg ->
                        InboxItem(msg, { onThread(msg.threadId, msg.address) }, {
                            val db = TagsDbHelper(context)
                            db.setArchivedThread(msg.threadId)
                            archived = db.getArchivedThreads()
                        }, {
                            val db = TagsDbHelper(context)
                            db.setBlockedSender(msg.address)
                            blocked = db.getBlockedSenders()
                        })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(modifier: Modifier, threadId: Long, address: String, tagEnabled: Boolean, onBack: () -> Unit, onTag: (String) -> Unit) {
    val context = LocalContext.current
    val messageRepo = remember { MessageRepository(context) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var replyText by remember { mutableStateOf("") }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { messageRepo.simulateSendMms(threadId, address, it) } }

    LaunchedEffect(threadId) { messages = messageRepo.fetchChatThread(threadId) }

    Scaffold(topBar = { TopAppBar(title = { Text(address) }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }, bottomBar = {
        Row(Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding().imePadding(), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ photoLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Filled.Add, null) }
            OutlinedTextField(replyText, { replyText = it }, Modifier.weight(1f), placeholder = { Text("Message") }, shape = RoundedCornerShape(24.dp))
            IconButton({ if (replyText.isNotBlank()) { messageRepo.sendSmsMessage(address, replyText); replyText = "" } }, Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.onPrimary) }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp), reverseLayout = true) {
            items(messages) { ChatBubble(it, tagEnabled, onTag) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { MessageRepository(context) }
    var to by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var sims by remember { mutableStateOf<List<SubscriptionInfo>>(emptyList()) }
    var selectedSim by remember { mutableStateOf<SubscriptionInfo?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let { context.contentResolver.query(it, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c -> if (c.moveToFirst()) to = c.getString(0) } }
    }

    LaunchedEffect(Unit) {
        val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        try { sims = sm.activeSubscriptionInfoList ?: emptyList(); if (sims.isNotEmpty()) selectedSim = sims[0] } catch (e: SecurityException) {}
    }

    Scaffold(topBar = { TopAppBar(title = { Text("New Message") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            OutlinedTextField(to, { to = it }, Modifier.fillMaxWidth(), label = { Text("To") }, trailingIcon = { IconButton({ picker.launch(null) }) { Icon(Icons.Filled.Person, null) } }, shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().weight(1f), label = { Text("Message") }, shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (sims.isNotEmpty()) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Filled.SimCard, null, tint = MaterialTheme.colorScheme.primary) }
                        DropdownMenu(menu, { menu = false }) { sims.forEach { sim -> DropdownMenuItem({ Text(sim.displayName.toString()) }, { selectedSim = sim; menu = false }) } }
                    }
                    Text(selectedSim?.displayName?.toString() ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(8.dp))
                Button({ if (to.isNotBlank() && body.isNotBlank()) { repo.sendSmsMessage(to, body, selectedSim?.subscriptionId); onBack() } }, Modifier.height(50.dp), shape = RoundedCornerShape(25.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterScreen(modifier: Modifier, tag: String, onThread: (Long, String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { MessageRepository(context) }
    var msgs by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    LaunchedEffect(tag) { msgs = repo.fetchAllMessages().filter { it.tags.contains(tag) } }
    Scaffold(topBar = { TopAppBar(title = { Text("Tag: $tag") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) { items(msgs) { InboxItem(it, { onThread(it.threadId, it.address) }, {}, {}) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsBrowserScreen(modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { MessageRepository(context) }
    val engine = remember { MetricsEngine() }
    var html by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { html = engine.generateMetricsHtml(repo.fetchAllMessages()) }
    Scaffold(topBar = { TopAppBar(title = { Text("Metrics") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { pad ->
        if (html.isNotEmpty()) AndroidView(factory = { WebView(it) }, Modifier.padding(pad).fillMaxSize()) { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamFolderScreen(modifier: Modifier, onThread: (Long, String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { MessageRepository(context) }
    var msgs by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    LaunchedEffect(Unit) { msgs = repo.fetchAllMessages().filter { TagsDbHelper(context).getBlockedSenders().contains(it.address) } }
    Scaffold(topBar = { TopAppBar(title = { Text("Spam") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) { items(msgs) { InboxItem(it, { onThread(it.threadId, it.address) }, {}, {}) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier, mgr: SettingsManager, onChanged: () -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Enable Tagging"); Switch(mgr.isTaggingEnabled, { mgr.isTaggingEnabled = it; onChanged() }) }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Enable Metrics"); Switch(mgr.isMetricsEnabled, { mgr.isMetricsEnabled = it; onChanged() }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(modifier: Modifier, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("About") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Icon(Icons.Filled.LocalOffer, null, Modifier.size(72.dp), MaterialTheme.colorScheme.primary); Text("MessageMe", style = MaterialTheme.typography.headlineMedium); Text("v1.0.0") }
    }
}

@Composable
fun InboxItem(msg: ChatMessage, onClick: () -> Unit, onArchive: () -> Unit, onBlock: () -> Unit) {
    val bgColor = msg.colorHex?.let { Color(android.graphics.Color.parseColor(it)) } ?: Color.Transparent
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = if (bgColor != Color.Transparent) bgColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) { Text(msg.address.take(1).uppercase()) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { Text(msg.address, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold); Text(msg.body, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
            IconButton(onArchive) { Icon(Icons.Filled.CloudUpload, null, Modifier.size(20.dp)) }
            IconButton(onBlock) { Icon(Icons.Filled.Block, null, Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage, tagEnabled: Boolean, onTag: (String) -> Unit) {
    val align = if (msg.isSent) Alignment.End else Alignment.Start
    val color = if (msg.isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        if (msg.attachmentUri != null) AsyncImage(msg.attachmentUri, null, Modifier.size(200.dp).padding(4.dp))
        Surface(color = color, shape = RoundedCornerShape(12.dp)) { Text(msg.body, Modifier.padding(12.dp)) }
        if (tagEnabled && msg.tags.isNotEmpty()) Row { msg.tags.forEach { Text("#$it", Modifier.clickable { onTag(it) }.padding(4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } }
        Spacer(Modifier.height(8.dp))
    }
}

fun backupToDrive(context: Context, account: GoogleSignInAccount, messages: List<ChatMessage>) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val cred = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE)).apply { selectedAccount = account.account }
            val drive = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), cred).setApplicationName("MessageMe").build()
            val xml = BackupEngine().generateBackupXml(messages)
            val folder = drive.files().create(File().apply { name = "MessageMe_Backups"; mimeType = "application/vnd.google-apps.folder" }).setFields("id").execute()
            drive.files().create(File().apply { name = "Backup_${System.currentTimeMillis()}.xml"; parents = listOf(folder.id) }, ByteArrayContent.fromString("text/xml", xml)).execute()
            withContext(Dispatchers.Main) { Toast.makeText(context, "Backup Success", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(context, "Backup Fail", Toast.LENGTH_SHORT).show() } }
    }
}