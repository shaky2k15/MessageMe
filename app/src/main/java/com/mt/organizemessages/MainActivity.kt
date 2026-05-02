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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
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
import com.mt.organizemessages.data.MessageRepository
import com.mt.organizemessages.data.TagsDbHelper
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val requiredPermissions = remember {
        val list = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    LaunchedEffect(Unit) {
        hasPermissions = requiredPermissions.all {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

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
    val messageRepo = remember { MessageRepository(context) }
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
                backupToDrive(context, account, messages)
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
                messages = messageRepo.fetchAllMessages()
            }
        }
        val db = TagsDbHelper(context)
        blockedSenders = db.getBlockedSenders()
        archivedThreads = db.getArchivedThreads()
        context.contentResolver.registerContentObserver(Uri.parse("content://mms-sms/conversations"), true, observer)
        messages = messageRepo.fetchAllMessages()
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
    val messageRepo = remember { MessageRepository(context) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var replyText by remember { mutableStateOf("") }
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            messageRepo.simulateSendMms(threadId, address, uri)
        }
    }

    DisposableEffect(context, threadId) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                messages = messageRepo.fetchChatThread(threadId)
            }
        }
        context.contentResolver.registerContentObserver(Uri.parse("content://mms-sms/conversations"), true, observer)
        messages = messageRepo.fetchChatThread(threadId)
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
                TextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Text message") },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            messageRepo.sendSmsMessage(address, replyText)
                            Toast.makeText(context, "Sending message...", Toast.LENGTH_SHORT).show()
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
fun NewMessageScreen(modifier: Modifier = Modifier, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val messageRepo = remember { MessageRepository(context) }
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
            context.contentResolver.query(it, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    searchQuery = cursor.getString(0)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        contacts = messageRepo.fetchContacts()
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
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
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
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
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
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                            messageRepo.sendSmsMessage(searchQuery, messageBody, selectedSim?.subscriptionId)
                            Toast.makeText(context, "Sending message...", Toast.LENGTH_SHORT).show()
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
fun TagFilterScreen(modifier: Modifier = Modifier, tag: String, onNavigateToThread: (Long, String) -> Unit, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val messageRepo = remember { MessageRepository(context) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    
    LaunchedEffect(tag) {
        messages = messageRepo.fetchAllMessages().filter { it.tags.contains(tag) }
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
fun SpamFolderScreen(modifier: Modifier = Modifier, onNavigateToThread: (Long, String) -> Unit, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val messageRepo = remember { MessageRepository(context) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var blockedSenders by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    fun refresh() {
        val db = TagsDbHelper(context)
        blockedSenders = db.getBlockedSenders()
        messages = messageRepo.fetchAllMessages().filter { blockedSenders.contains(it.address) }
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
        if (messages.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No spam messages.")
            }
        } else {
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
                            Toast.makeText(context, "Marked as Not a Spam", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
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

@Composable
fun InboxItem(msg: ChatMessage, onClick: () -> Unit, onArchive: () -> Unit, onBlock: () -> Unit, isSpamView: Boolean = false, onUnblock: () -> Unit = {}, blockIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Block) {
    val context = LocalContext.current
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
                                    text = { Text("Not a Spam") },
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

@OptIn(ExperimentalMaterial3Api::class)
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