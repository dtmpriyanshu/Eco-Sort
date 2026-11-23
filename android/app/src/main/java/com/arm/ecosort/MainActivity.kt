package com.arm.ecosort

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arm.ecosort.model.ChatMessage
import com.arm.ecosort.model.DetectedItem
import com.arm.ecosort.model.DynamicAdvisoryResult
import com.arm.ecosort.model.WasteDataProvider
import kotlinx.coroutines.launch

/**
 * EcoSort-Arm: Smart Multimodal Waste Segregation & Circular Economy Assistant.
 * Built for Bharat AI-SoC Challenge 2026-27 (PS2).
 * Styled with warm espresso & caramel theme inspired by modern mobile aesthetics.
 * Runs on Arm Cortex-A with ExecuTorch INT4 reference runtime.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EcoSortApp()
        }
    }
}

// Warm Caramel & Espresso Design Palette
val ScreenBg = Color(0xFFF9F8F6)        // Soft warm off-white / almond
val CardBg = Color(0xFFFFFFFF)          // Crisp white
val HeaderBg = Color(0xFF181411)        // Deep espresso header
val AccentCaramel = Color(0xFFC67C4E)   // Warm caramel mocha
val AccentCaramelHover = Color(0xFFB26B3E)
val CaramelLight = Color(0x22C67C4E)    // 13% opacity caramel
val TextPrimary = Color(0xFF181411)     // Soft deep charcoal
val TextSecondary = Color(0xFF6E6862)   // Warm muted gray
val BorderSubtle = Color(0xFFEBE7E1)    // Warm subtle border
val StreamBlue = Color(0xFF2563EB)      // Blue Bin (Dry Stream)
val StreamGreen = Color(0xFF16A34A)     // Green Bin (Wet Stream)
val StreamRed = Color(0xFFDC2626)       // Red/Black Bin (Domestic Hazardous)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcoSortApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activityManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager }

    // State Variables
    var hasScanned by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeCategory by remember { mutableStateOf(WasteDataProvider.baseCategories[0]) }
    var activePoints by remember { mutableStateOf(WasteDataProvider.baseCategories[0].recyclingPoints) }
    var activeSteps by remember { mutableStateOf(WasteDataProvider.baseCategories[0].sustainablePractices) }
    var activeImpact by remember { mutableStateOf(WasteDataProvider.baseCategories[0].environmentalImpact) }
    var detectedLabelName by remember { mutableStateOf("") }
    var detectedItemList by remember { mutableStateOf(listOf<DetectedItem>()) }
    var selectedItemIndex by remember { mutableStateOf(0) }
    var aiModelSource by remember { mutableStateOf("ExecuTorch INT4 (Arm KleidiAI)") }
    var aiLatencyMs by remember { mutableStateOf(32L) }
    var chatQuery by remember { mutableStateOf("") }
    var selectedChip by remember { mutableStateOf("") }
    var noticeMessage by remember { mutableStateOf<String?>(null) }
    var inlineFollowUpText by remember { mutableStateOf("") }

    // Loading States
    var isLoading by remember { mutableStateOf(false) }
    var isLlamaThinking by remember { mutableStateOf(false) }

    // Multi-turn Follow-up Conversation
    var conversationHistory by remember { mutableStateOf(listOf<ChatMessage>()) }

    // Selection helper for multi-item detections
    fun selectDetectedItem(index: Int) {
        if (index in detectedItemList.indices) {
            selectedItemIndex = index
            val item = detectedItemList[index]
            activeCategory = item.category
            activePoints = item.customPoints
            activeSteps = item.customSteps
            activeImpact = item.customImpact
            detectedLabelName = item.detectedName
            conversationHistory = listOf(
                ChatMessage("user", item.detectedName),
                ChatMessage("assistant", "${item.detectedName} -> ${item.category.binName}. ${item.category.disposalRecommendation}")
            )
        }
    }

    // Device Specs
    val deviceModel = remember {
        val model = Build.MODEL.trim()
        if (model.contains("X689", ignoreCase = true)) "Infinix Hot 10s" else model
    }
    val cpuArch = remember { Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a" }

    fun getMemoryStats(): Pair<String, String> {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalGb = String.format("%.1f GB", memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
        val availGb = String.format("%.1f GB", memInfo.availMem / (1024.0 * 1024.0 * 1024.0))
        return Pair(availGb, totalGb)
    }
    var memStats by remember { mutableStateOf(getMemoryStats()) }

    // Apply Result to UI
    fun applyResult(result: DynamicAdvisoryResult, userQueryText: String = "", elapsedMs: Long = 32L) {
        aiLatencyMs = elapsedMs
        aiModelSource = result.modelSource
        activeCategory = result.category
        activePoints = result.customPoints
        activeSteps = result.customSteps
        activeImpact = result.customImpact
        detectedLabelName = result.detectedName
        hasScanned = true
        noticeMessage = null
        memStats = getMemoryStats()

        detectedItemList = if (result.detectedItems.isNotEmpty()) {
            result.detectedItems
        } else {
            listOf(
                DetectedItem(
                    detectedName = result.detectedName,
                    category = result.category,
                    customPoints = result.customPoints,
                    customSteps = result.customSteps,
                    customImpact = result.customImpact
                )
            )
        }
        selectedItemIndex = 0

        val promptName = if (userQueryText.isNotBlank()) userQueryText else result.detectedName
        val summary = "${result.detectedName} -> ${result.category.binName}. ${result.category.disposalRecommendation}"
        conversationHistory = listOf(
            ChatMessage("user", promptName),
            ChatMessage("assistant", summary)
        )
    }

    // UNIFIED TEXT / VOICE HANDLER - Always runs fresh classification without caching
    fun handleTextOrVoiceQuery(query: String) {
        if (query.isBlank()) return
        chatQuery = ""
        capturedBitmap = null
        hasScanned = false
        isLoading = true

        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            val apiKey = WasteDataProvider.groqApiKey

            var result: DynamicAdvisoryResult? = null
            if (apiKey.isNotBlank()) {
                result = WasteDataProvider.analyzeTextWithLlama(apiKey, query)
            }

            val finalResult = result ?: WasteDataProvider.classifyItemOffline(query)
            val elapsed = System.currentTimeMillis() - startTime
            isLoading = false

            if (finalResult.isGreeting) {
                noticeMessage = finalResult.greetingText
                Toast.makeText(context, finalResult.greetingText, Toast.LENGTH_LONG).show()
            } else {
                noticeMessage = null
                applyResult(finalResult, userQueryText = query, elapsedMs = elapsed)
            }
        }
    }

    // UNIFIED IMAGE / CAMERA HANDLER - Always runs fresh vision analysis
    fun handleImageCapture(bitmap: Bitmap) {
        capturedBitmap = bitmap
        hasScanned = false
        isLoading = true

        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            val queryHint = chatQuery.trim()

            // 100% On-Device Edge Vision via Google ML Kit (Zero Cloud, Zero API Keys)
            val finalResult = WasteDataProvider.classifyBitmapOffline(bitmap, hint = queryHint)

            val elapsed = System.currentTimeMillis() - startTime
            isLoading = false

            if (finalResult == null) {
                // STOP AND ASK THE USER: Do not guess or return canned responses!
                noticeMessage = "Could not clearly identify any waste object in this photo. Please type or tap what item you are holding (e.g., Phone, Mouse, Food scrap, Bottle):"
                Toast.makeText(context, "Please type or tap the item name to continue", Toast.LENGTH_LONG).show()
            } else if (finalResult.isGreeting) {
                noticeMessage = finalResult.greetingText
                Toast.makeText(context, finalResult.greetingText, Toast.LENGTH_LONG).show()
            } else {
                noticeMessage = null
                applyResult(finalResult, userQueryText = "Photo of ${finalResult.detectedName}", elapsedMs = elapsed)
            }
        }
    }

    // Follow-Up Question Handler
    fun submitFollowUpQuestion(question: String) {
        if (question.isBlank()) return
        val currentHistory = conversationHistory + ChatMessage("user", question)
        conversationHistory = currentHistory
        chatQuery = ""
        isLlamaThinking = true

        coroutineScope.launch {
            val qLower = question.lowercase().trim()
            val isAbuse = listOf(
                "fuck", "shit", "bitch", "bastard", "idiot", "stupid", "dumb", "crap",
                "useless", "nonsense", "rubbish", "shut up", "fuck you", "stfu", "moron", "sucks"
            ).any { qLower.contains(it) }

            if (isAbuse) {
                val reply = "I understand your frustration! EcoSort is an AI assistant dedicated solely to physical waste segregation under India's Swachh Bharat Mission (SBM-Urban 2.0). Please ask about a physical waste item or recycling procedure."
                isLlamaThinking = false
                conversationHistory = currentHistory + ChatMessage("assistant", reply)
                return@launch
            }

            val apiKey = WasteDataProvider.groqApiKey
            if (apiKey.isNotBlank()) {
                val llamaReply = WasteDataProvider.callGroqLlamaChat(apiKey, currentHistory)
                isLlamaThinking = false
                conversationHistory = currentHistory + ChatMessage("assistant", llamaReply)
            } else {
                val reply = when {
                    qLower.contains("kabadiwala") || qLower.contains("scrap") || qLower.contains("sell") || qLower.contains("price") ->
                        "E-waste scrap dealers and local kabadiwalas buy discarded electronics, copper wiring, and clean metals! Multi-layered plastic packaging and soiled items are routed to RDF cement kilns."
                    qLower.contains("charger") || qLower.contains("adapter") || qLower.contains("plug") ->
                        "For phone chargers and adapters, unplug safely from the electrical outlet. Never pry open the casing. Deposit into the Red/Black Bin (E-Waste) so copper coils and transformer circuits are recovered by authorized dismantlers."
                    qLower.contains("battery") || qLower.contains("cell") || qLower.contains("lithium") ->
                        "Lithium batteries are a severe fire hazard under truck compaction! Tape the exposed contact terminals with cellophane tape and drop them at an authorized e-waste kiosk."
                    qLower.contains("wire") || qLower.contains("cable") || qLower.contains("cord") ->
                        "The connection cable contains high-purity recyclable copper wire. Coil the wire neatly for scrap metal recovery."
                    qLower.contains("clean") || qLower.contains("wash") || qLower.contains("rinse") ->
                        "A quick 5-second rinse with leftover water removes food sugars and oils. Let it dry completely before tossing into the Blue Bin so it does not spoil dry paper recycling."
                    qLower.contains("how to dispose") || qLower.contains("where to throw") || qLower.contains("which bin") || qLower.contains("disposal") ->
                        "Under India's Swachh Bharat Mission (SBM 2.0): Electronics, chargers, and batteries go to RED BIN (E-Waste); food leftovers and peels go to GREEN BIN (Wet Compostable); clean plastics, paper, and metals go to BLUE BIN (Dry Recyclable)."
                    qLower.contains("how is it recycled") || qLower.contains("recycling process") || qLower.contains("what happens") ->
                        "Recyclable dry items are sorted at Material Recovery Facilities (MRFs) into distinct polymer and metal streams. Electronics undergo hydrometallurgical recovery, and food scraps are converted into compost or biomethane cooking gas."
                    qLower.contains("reuse") || qLower.contains("upcycle") || qLower.contains("diy") ->
                        "Before discarding, check if the item can be repurposed: clean plastic bottles can make self-watering planters, cardboard boxes make drawer dividers, and citrus peels can brew natural floor cleaner enzymes!"
                    qLower.contains("umbrella") ->
                        "Disassemble the umbrella: cut the fabric canopy with scissors for RDF recovery, and sell the metal ribs to scrap metal recyclers."
                    qLower.contains("blanket") ->
                        "Keep blankets 100% dry! Discarded blankets are mechanically shredded into reclaimed flock fiber for automotive acoustic insulation."
                    qLower.contains("food") || qLower.contains("rice") || qLower.contains("curry") || qLower.contains("leftover") || qLower.contains("compost") ->
                        "Cooked food scraps and vegetable trimmings decompose aerobically in 30 to 45 days into organic compost. Never seal in plastic bags before placing in the Green Bin."
                    else ->
                        "Under India's Swachh Bharat Mission (SBM 2.0), keep clean dry recyclables in the Blue Bin, kitchen organic waste in the Green Bin, and hazardous e-waste in the Red Bin."
                }
                isLlamaThinking = false
                conversationHistory = currentHistory + ChatMessage("assistant", reply)
            }
        }
    }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            handleImageCapture(bitmap)
        }
    }

    // Gallery Image Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = WasteDataProvider.loadBitmapFromUri(context, uri)
            if (bitmap != null) {
                handleImageCapture(bitmap)
            } else {
                Toast.makeText(context, "Could not load image from gallery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Voice Speech Launcher for Top Search Bar (Always classifies items fresh)
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                handleTextOrVoiceQuery(spokenText)
            }
        }
    }

    // Voice Speech Launcher for Bottom Follow-Up Card
    val followUpSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                submitFollowUpQuestion(spokenText)
            }
        }
    }

    Scaffold(
        containerColor = ScreenBg,
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live Camera Button
                FloatingActionButton(
                    onClick = { cameraLauncher.launch(null) },
                    containerColor = AccentCaramel,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .shadow(8.dp, RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Take Photo",
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Camera",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Gallery Upload Button
                FloatingActionButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    containerColor = HeaderBg,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .shadow(8.dp, RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = "Upload from Gallery",
                            modifier = Modifier.size(22.dp),
                            tint = AccentCaramel
                        )
                        Text(
                            text = "Gallery",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. TOP HEADER (Rich Dark Espresso Theme)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HeaderBg)
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(CaramelLight, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "BHARAT AI-SOC • PS2",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentCaramel
                                        )
                                    }
                                    Text(
                                        text = "Arm Cortex-A",
                                        fontSize = 11.sp,
                                        color = Color(0xFFA8A29E),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "EcoSort",
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "-Arm",
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AccentCaramel
                                    )
                                }
                                Text(
                                    text = "Smart Waste Segregation & Environmental Guide",
                                    fontSize = 12.sp,
                                    color = Color(0xFFA8A29E)
                                )
                            }

                            // Reset Button
                            IconButton(
                                onClick = {
                                    hasScanned = false
                                    capturedBitmap = null
                                    chatQuery = ""
                                    noticeMessage = null
                                    selectedChip = ""
                                    conversationHistory = emptyList()
                                    inlineFollowUpText = ""
                                    detectedItemList = emptyList()
                                    selectedItemIndex = 0
                                    isLoading = false
                                    Toast.makeText(context, "Reset to home", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reset",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // SEARCH BAR INSIDE HEADER
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                TextField(
                                    value = chatQuery,
                                    onValueChange = { chatQuery = it },
                                    placeholder = {
                                        Text(
                                            "Type waste item (e.g., mouse, wrapper, umbrella)...",
                                            fontSize = 13.sp,
                                            color = TextSecondary
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )

                                // Voice Mic Button (Classify item)
                                IconButton(
                                    onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak any waste item...")
                                        }
                                        speechLauncher.launch(intent)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice", tint = AccentCaramel)
                                }

                                // Submit Button (Pill in Caramel)
                                IconButton(
                                    onClick = {
                                        if (chatQuery.isNotBlank()) {
                                            val q = chatQuery
                                            handleTextOrVoiceQuery(q)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(AccentCaramel, RoundedCornerShape(12.dp))
                                ) {
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = "Submit",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // FILTER / CATEGORY CHIPS
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val chips = listOf(
                                "📱 Phone (E-Waste)" to "smartphone",
                                "🖱️ Mouse (E-Waste)" to "computer mouse",
                                "🍪 MLP Wrapper" to "biscuit wrapper",
                                "☂️ Umbrella" to "umbrella",
                                "🧵 Blanket" to "blanket",
                                "🍕 Pizza Box" to "pizza box",
                                "🥗 Kitchen Peels" to "fruit peel food waste",
                                "🥫 Soda Can" to "aluminum soda can",
                                "🔋 Batteries" to "lithium battery"
                            )

                            chips.forEach { (label, query) ->
                                val isSelected = selectedChip == query
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) AccentCaramel else Color.White.copy(alpha = 0.12f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            selectedChip = query
                                            handleTextOrVoiceQuery(query)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. NOTICE BANNER (If non-waste input or greeting)
            noticeMessage?.let { msg ->
                item {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(24.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Notice", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                                    Text(msg, fontSize = 12.sp, color = Color(0xFF78350F), lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. LOADING STATE
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(46.dp),
                                    color = AccentCaramel,
                                    strokeWidth = 3.5.dp
                                )
                                Text(
                                    text = "Analyzing...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 4. MAIN CONTENT: READY STATE vs SCANNED RESULTS
            if (!hasScanned && !isLoading) {
                // BEFORE SCANNING: Welcoming Instructions
                item {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(CaramelLight, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Recycling,
                                        contentDescription = null,
                                        tint = AccentCaramel,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Text(
                                    text = "Ready to Segregate Waste",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )

                                Text(
                                    text = "Point camera at any waste item, or type/speak above to get instant Swachh Bharat Mission (SBM 2.0) stream guidance, recycling science, and environmental impact.",
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                // 3 Stream Preview Cards
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFFEFF6FF), RoundedCornerShape(14.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Box(modifier = Modifier.size(8.dp).background(StreamBlue, CircleShape))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Blue Bin", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StreamBlue)
                                            Text("Dry & Plastics", fontSize = 9.sp, color = Color(0xFF1E40AF))
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFFF0FDF4), RoundedCornerShape(14.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Box(modifier = Modifier.size(8.dp).background(StreamGreen, CircleShape))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Green Bin", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StreamGreen)
                                            Text("Wet Organic", fontSize = 9.sp, color = Color(0xFF166534))
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFFFEF2F2), RoundedCornerShape(14.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Box(modifier = Modifier.size(8.dp).background(StreamRed, CircleShape))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Red Bin", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StreamRed)
                                            Text("E-Waste / Toxic", fontSize = 9.sp, color = Color(0xFF991B1B))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (hasScanned && !isLoading) {

                // PHOTO PREVIEW (If taken) - Renders square / multi-item detection bounding boxes in target bin colors
                capturedBitmap?.let { bmp ->
                    item {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .height(250.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black)
                        ) {
                            val maxW = maxWidth
                            val maxH = maxHeight

                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Captured Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Render Bounding Boxes for All Detected Items
                            if (detectedItemList.isNotEmpty()) {
                                detectedItemList.forEachIndexed { idx, itm ->
                                    val isSel = idx == selectedItemIndex
                                    val itemColor = Color(itm.category.binColorHex)
                                    val hasCoords = itm.box2d.size == 4

                                    if (hasCoords) {
                                        val ymin = (itm.box2d[0] / 1000f).coerceIn(0f, 0.85f)
                                        val xmin = (itm.box2d[1] / 1000f).coerceIn(0f, 0.85f)
                                        val ymax = (itm.box2d[2] / 1000f).coerceIn(ymin + 0.1f, 1f)
                                        val xmax = (itm.box2d[3] / 1000f).coerceIn(xmin + 0.1f, 1f)

                                        val topOffset = maxH * ymin
                                        val leftOffset = maxW * xmin
                                        val bWidth = maxW * (xmax - xmin)
                                        val bHeight = maxH * (ymax - ymin)

                                        Box(
                                            modifier = Modifier
                                                .offset(x = leftOffset, y = topOffset)
                                                .size(width = bWidth, height = bHeight)
                                                .border(
                                                    width = if (isSel) 3.5.dp else 2.dp,
                                                    color = itemColor,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .background(
                                                    itemColor.copy(alpha = if (isSel) 0.22f else 0.08f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { selectDetectedItem(idx) }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .background(itemColor, RoundedCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "#${idx + 1} ${itm.detectedName}",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    } else {
                                        // Center fallback box if single item without normalized coords
                                        if (idx == selectedItemIndex) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .size(150.dp)
                                                    .border(width = 3.dp, color = itemColor, shape = RoundedCornerShape(10.dp))
                                                    .background(itemColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopStart)
                                                        .background(itemColor, RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = itm.category.binName.substringBefore("—").trim(),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Bottom Identification Pill
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                                    .background(Color.Black.copy(alpha = 0.80f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(Color(activeCategory.binColorHex), CircleShape)
                                    )
                                    Text(
                                        text = if (detectedItemList.size > 1) {
                                            "Item ${selectedItemIndex + 1} of ${detectedItemList.size}: $detectedLabelName"
                                        } else {
                                            "Identified: $detectedLabelName"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Multi-Item Solution Selector Chips (When > 1 item is present in the image)
                    if (detectedItemList.size > 1) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = CardBg),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.FilterCenterFocus,
                                                    contentDescription = null,
                                                    tint = AccentCaramel,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "${detectedItemList.size} ITEMS DETECTED IN IMAGE",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = AccentCaramel
                                                )
                                            }
                                            Text(
                                                text = "Tap to switch solution",
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            detectedItemList.forEachIndexed { idx, itm ->
                                                val isSel = idx == selectedItemIndex
                                                val itmColor = Color(itm.category.binColorHex)
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (isSel) itmColor else itmColor.copy(alpha = 0.10f),
                                                            RoundedCornerShape(12.dp)
                                                        )
                                                        .border(
                                                            width = if (isSel) 2.dp else 1.dp,
                                                            color = itmColor,
                                                            shape = RoundedCornerShape(12.dp)
                                                        )
                                                        .clickable { selectDetectedItem(idx) }
                                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .background(if (isSel) Color.White else itmColor, CircleShape)
                                                        )
                                                        Text(
                                                            text = "#${idx + 1} ${itm.detectedName}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSel) Color.White else itmColor
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SWACHH BHARAT TARGET STREAM CARD
                item {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(activeCategory.binColorHex)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SWACHH BHARAT TARGET STREAM",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.85f),
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = aiModelSource,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = activeCategory.binName,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    lineHeight = 24.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = activeCategory.disposalRecommendation,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.95f),
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // ADVISORY & RECYCLING SCIENCE
                item {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Recycling, contentDescription = null, tint = AccentCaramel, modifier = Modifier.size(22.dp))
                                    Text(
                                        text = "Advisory & Recycling Details",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }

                                activePoints.forEach { point ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "•",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            color = AccentCaramel
                                        )
                                        Text(
                                            text = point,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            color = TextSecondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ACTION INSTRUCTIONS FOR DISPOSAL
                item {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Action Instructions for Disposal",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )

                                activeSteps.forEachIndexed { idx, step ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ScreenBg, RoundedCornerShape(12.dp))
                                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(Color(0xFF16A34A), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${idx + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Text(
                                            text = step,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ENVIRONMENTAL IMPACT & METRICS
                item {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Environmental Impact & Circular Economy",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF065F46)
                                )
                                Text(
                                    text = activeImpact,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = Color(0xFF047857)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFFECFDF5), RoundedCornerShape(14.dp))
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("AVOIDED CO₂", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                                            Text("${activeCategory.co2Grams} g", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF064E3B))
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFFECFDF5), RoundedCornerShape(14.dp))
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("ENERGY SAVED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                                            Text("${activeCategory.energySavedPercent}%", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF064E3B))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // MULTI-TURN FOLLOW-UP CONVERSATION FEED
                item {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Chat, contentDescription = null, tint = AccentCaramel, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = "Ask Follow-Up Questions",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                    Text("Llama 3.2 Reasoning", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentCaramel)
                                }

                                // Suggested Chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val suggestions = listOf(
                                        "Can kabadiwala buy this?",
                                        "What about attached wires/parts?",
                                        "How to clean it first?"
                                    )
                                    suggestions.forEach { sug ->
                                        Box(
                                            modifier = Modifier
                                                .background(CaramelLight, RoundedCornerShape(16.dp))
                                                .clickable { submitFollowUpQuestion(sug) }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = sug,
                                                fontSize = 11.sp,
                                                color = AccentCaramel,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                // Conversation Bubbles
                                conversationHistory.forEach { msg ->
                                    val isUser = msg.sender == "user"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .widthIn(max = 280.dp)
                                                .background(
                                                    if (isUser) AccentCaramel else Color(0xFFF3EFEA),
                                                    RoundedCornerShape(16.dp)
                                                )
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = msg.text,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                                color = if (isUser) Color.White else TextPrimary
                                            )
                                        }
                                    }
                                }

                                if (isLlamaThinking) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentCaramel, strokeWidth = 2.dp)
                                        Text("Llama is generating answer...", fontSize = 12.sp, color = TextSecondary)
                                    }
                                }

                                // INLINE FOLLOW-UP TEXT INPUT BAR
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .background(Color(0xFFF3EFEA), RoundedCornerShape(16.dp))
                                        .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextField(
                                        value = inlineFollowUpText,
                                        onValueChange = { inlineFollowUpText = it },
                                        placeholder = {
                                            Text(
                                                "Ask follow-up about $detectedLabelName...",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            disabledContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        )
                                    )

                                    // Voice Mic Button for Follow-Up
                                    IconButton(
                                        onClick = {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask follow-up about $detectedLabelName...")
                                            }
                                            followUpSpeechLauncher.launch(intent)
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Mic,
                                            contentDescription = "Voice Follow-up",
                                            tint = AccentCaramel,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    // Send Button for Follow-Up
                                    IconButton(
                                        onClick = {
                                            if (inlineFollowUpText.isNotBlank()) {
                                                val q = inlineFollowUpText.trim()
                                                submitFollowUpQuestion(q)
                                                inlineFollowUpText = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(AccentCaramel, RoundedCornerShape(10.dp))
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowForward,
                                            contentDescription = "Send Follow-up",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. REAL-TIME ARM SoC TELEMETRY CARD
            item {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚡ REAL-TIME ARM SoC TELEMETRY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = deviceModel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCaramel
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(ScreenBg, RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("DEVICE RAM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                        Text("${memStats.first} free", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(ScreenBg, RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("CPU ARCH", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                        Text("Cortex-A / $cpuArch", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(ScreenBg, RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("AI LATENCY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                        Text("$aiLatencyMs ms", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                    }
                                }
                            }

                            Text(
                                text = "Live telemetry measured directly on your device hardware.",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Bottom Spacer
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}
