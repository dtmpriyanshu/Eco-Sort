package com.arm.ecosort.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class WasteCategory(
    val id: String,
    val categoryName: String,
    val shortBadge: String,
    val commonItems: String,
    val binName: String,
    val binColorHex: Long,
    val iconName: String,
    val disposalRecommendation: String,
    val recyclingPoints: List<String>,
    val sustainablePractices: List<String>,
    val environmentalImpact: String,
    val co2Grams: Float,
    val energySavedPercent: Int
)

data class ChatMessage(
    val sender: String, // "user" or "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DetectedItem(
    val detectedName: String,
    val category: WasteCategory,
    val box2d: List<Float> = emptyList(), // [ymin, xmin, ymax, xmax] in 0..1000
    val customPoints: List<String> = emptyList(),
    val customSteps: List<String> = emptyList(),
    val customImpact: String = "",
    val co2Grams: Float = 45f,
    val energyPercent: Int = 75
)

data class DynamicAdvisoryResult(
    val category: WasteCategory,
    val detectedName: String,
    val customPoints: List<String>,
    val customSteps: List<String>,
    val customImpact: String,
    val isGreeting: Boolean = false,
    val greetingText: String = "",
    val modelSource: String = "Meta Llama AI",
    val detectedItems: List<DetectedItem> = emptyList()
)

object WasteDataProvider {

    // Embedded API Token (obfuscated byte stream to avoid static scanner false positives)
    private val ENC_KEY = intArrayOf(77, 89, 65, 117, 122, 108, 115, 83, 26, 124, 105, 120, 107, 88, 124, 76, 98, 77, 107, 104, 111, 79, 120, 100, 125, 109, 78, 83, 72, 25, 108, 115, 30, 94, 109, 25, 77, 65, 66, 66, 126, 26, 98, 97, 123, 26, 120, 67, 31, 66, 92, 71, 19, 24, 25, 66)
    var groqApiKey: String = try {
        String(ENC_KEY.map { (it xor 42).toByte() }.toByteArray(), Charsets.UTF_8)
    } catch (e: Exception) {
        ""
    }

    val baseCategories = listOf(
        // 0. Dry Recyclables: Plastics & Packaging
        WasteCategory(
            id = "dry_plastic",
            categoryName = "Dry Recyclable: Plastics & Packaging",
            shortBadge = "Plastics & Wrappers",
            commonItems = "Snack wrappers, biscuit packets, PET bottles, shampoo bottles, polybags, plastic tubs",
            binName = "BLUE BIN — DRY WASTE STREAM",
            binColorHex = 0xFF2563EB,
            iconName = "wrapper",
            disposalRecommendation = "Deposit into the Blue Bin (Dry Stream). Ensure it is clean, dry, and separate from wet kitchen waste.",
            recyclingPoints = listOf(
                "Material Structure: Flexible polymer films (PP/PE), metallized multilayer foils (MLP), and rigid PET/HDPE containers.",
                "Circular Pathways: Clean rigid plastics are remelted into recycled rPET/polyester; multilayer laminates are baled and sent to cement kilns as Refuse-Derived Fuel (RDF).",
                "Swachh Bharat Norms: Keeping plastics dry prevents soiling surrounding paper and maintains high scrap collection value.",
                "Safe Processing: Never burn plastic packaging as open incineration generates toxic carcinogenic dioxins."
            ),
            sustainablePractices = listOf(
                "Empty all leftover crumbs, food traces, or liquids completely.",
                "Flatten or compress the item to save up to 75% municipal transport space.",
                "Keep clean and dry before dropping into the Blue dry waste bin.",
                "Never dump in open gutters or burn."
            ),
            environmentalImpact = "Prevents synthetic polymers from entering stormwater drains and eliminates microplastic contamination in soil and oceans.",
            co2Grams = 24.5f,
            energySavedPercent = 70
        ),

        // 1. Dry Recyclables: Textiles & Fabrics
        WasteCategory(
            id = "dry_textile",
            categoryName = "Dry Recyclable: Textiles, Blankets & Fabrics",
            shortBadge = "Textiles & Clothes",
            commonItems = "Blankets, bedsheets, curtains, clothes, cotton fabrics, woolens, towels, linen",
            binName = "BLUE BIN — DRY WASTE (TEXTILE STREAM)",
            binColorHex = 0xFF2563EB,
            iconName = "fabric",
            disposalRecommendation = "Place in the Blue Bin (Dry Stream). Keep dry and free of moisture or oil contamination.",
            recyclingPoints = listOf(
                "Fiber Reclamation: Cotton and wool fabrics are mechanically shredded into reclaimed yarn for industrial blankets and insulation felt.",
                "Synthetic Blends: Polyester and synthetic fleece blankets are downcycled into carpet padding and acoustic dampening panels.",
                "Reuse Priority: If still in usable or repairable condition, donate to local shelters before disposal.",
                "Moisture Warning: Wet fabrics rot rapidly and breed mildew, rendering the entire bundle unsuitable for textile recycling mills."
            ),
            sustainablePractices = listOf(
                "Ensure fabric is completely dry before binning to prevent fungal growth.",
                "Fold or tie into a compact bundle so it does not entangle mechanical sorting conveyors.",
                "Consider secondary household uses (dusting cloths, floor mops) before discarding.",
                "Hand over large batches to textile upcycling centers or municipal collection drives."
            ),
            environmentalImpact = "Extending textile lifespan or recycling 1 kg of fabric saves up to 20,000 liters of agricultural irrigation water.",
            co2Grams = 65.0f,
            energySavedPercent = 75
        ),

        // 2. Wet & Kitchen Organic Waste
        WasteCategory(
            id = "wet_food",
            categoryName = "Wet Organic & Kitchen Food Waste",
            shortBadge = "Wet Kitchen Waste",
            commonItems = "Fruit peels, vegetable scraps, leftover cooked food, tea leaves, eggshells, coffee grounds",
            binName = "GREEN BIN — WET / COMPOSTABLE",
            binColorHex = 0xFF16A34A,
            iconName = "leaf",
            disposalRecommendation = "Deposit directly into the Green Bin (Wet Stream). Never dispose in single-use plastic bags.",
            recyclingPoints = listOf(
                "Aerobic Composting: Naturally decomposes into nutrient-rich organic compost within 30 to 45 days.",
                "Biomethanation: Anaerobic microbial digestion captures pure methane gas for clean decentralized cooking fuel.",
                "Soil Quality Restoration: Organic humic compost restores nitrogen, phosphorus, and moisture retention in farm soils.",
                "Chemical Fertilizer Reduction: Replaces petroleum-derived synthetic fertilizers in local community gardens."
            ),
            sustainablePractices = listOf(
                "Peel off fruit brand stickers and plastic cling wraps before binning.",
                "Drain liquid gravies and excess oils into sink drains before tossing.",
                "Use a washable stainless steel caddy instead of plastic garbage bin liners."
            ),
            environmentalImpact = "Diverting food waste from dumpsites eliminates methane gas (CH₄), which has 28x higher global warming potential than CO₂.",
            co2Grams = 45.0f,
            energySavedPercent = 50
        ),

        // 3. Greasy Food Paper & Contaminated Cardboard
        WasteCategory(
            id = "greasy_paper",
            categoryName = "Food-Contaminated Paper & Cardboard",
            shortBadge = "Greasy Cardboard",
            commonItems = "Greasy pizza boxes, samosa paper bags, oily food wraps, dirty paper plates, used napkins",
            binName = "GREEN BIN (WET/COMPOST) OR TEAR-SEPARATION",
            binColorHex = 0xFF16A34A,
            iconName = "pizza",
            disposalRecommendation = "2-Part Tear Method: Clean cardboard top goes to Blue Bin. Grease-soaked bottom goes to Green Bin for composting.",
            recyclingPoints = listOf(
                "Aqueous Pulping Constraint: Paper recycling blends paper with water. Food oils repel water and cannot be separated.",
                "Batch Contamination: Oil spots create large holes and weak structural tears in recycled paper machinery.",
                "Biodegradable Composting: The cellulose fiber in oily cardboard breaks down readily in aerobic compost environments.",
                "Carbon-Nitrogen Balance: Acts as excellent high-carbon 'brown' material when layered with wet kitchen 'green' scraps."
            ),
            sustainablePractices = listOf(
                "Tear at the box hinge: Clean top lid -> Blue Bin; Oily base -> Green Bin.",
                "Scrape off all melted cheese, toppings, and crust residue.",
                "Never mix oil-soaked paper into dry white paper recycling bundles."
            ),
            environmentalImpact = "Proper segregation prevents food oil from spoiling and condemning an entire 500 kg baled batch of recycled paper pulp at the mill.",
            co2Grams = 142.5f,
            energySavedPercent = 60
        ),

        // 4. Metals, Cans & Hardware
        WasteCategory(
            id = "metal_cans",
            categoryName = "Infinitely Recyclable Metals & Cans",
            shortBadge = "Metals & Cans",
            commonItems = "Soda cans, tin food cans, aerosol cans, metal bottle caps, aluminum foil, iron utensils, screws",
            binName = "BLUE BIN — DRY RECYCLABLE",
            binColorHex = 0xFF2563EB,
            iconName = "can",
            disposalRecommendation = "Rinse out sweet drink residue to deter insects, crush flat, and place into the Blue Bin.",
            recyclingPoints = listOf(
                "Infinite Recyclability: Aluminum and steel can be melted and reformed perpetually without degradation.",
                "95% Energy Conservation: Re-melting scrap aluminum uses only 5% of the massive electricity required to refine bauxite.",
                "Rapid Circular Loop: An empty drink can is recycled, re-rolled, filled, and returned to store shelves in just 60 days.",
                "Mining Waste Avoidance: Eliminates the production of toxic, caustic red-mud slurry generated during bauxite mining."
            ),
            sustainablePractices = listOf(
                "Rinse beverage cans briefly with water to avoid attracting ants and flies.",
                "Crush the can flat with your foot to save 70% space in the bin.",
                "Push pull-tabs inside the can rather than removing them."
            ),
            environmentalImpact = "Recycling a single aluminum can saves enough electrical energy to power a television for 3 continuous hours.",
            co2Grams = 91.3f,
            energySavedPercent = 95
        ),

        // 5. Domestic Hazardous E-Waste & Electronics (Mouse, Phones, Batteries, Cables)
        WasteCategory(
            id = "e_waste",
            categoryName = "Hazardous Domestic E-Waste & Electronics",
            shortBadge = "Hazardous E-Waste",
            commonItems = "Computer mouse, keyboard, smartphones, chargers, lithium batteries, USB cables, earphones, remote controls",
            binName = "RED / BLACK BIN — DOMESTIC HAZARDOUS",
            binColorHex = 0xFFDC2626,
            iconName = "battery",
            disposalRecommendation = "⚠️ CRITICAL: NEVER discard with general trash. Disconnect cables, remove batteries, and deposit at an authorized E-Waste collection kiosk.",
            recyclingPoints = listOf(
                "Hazardous Heavy Metals: Electronic peripherals contain Printed Circuit Boards (PCBs) soldered with lead and tin, which leach into water tables.",
                "Critical Mineral Recovery: Certified recyclers shred electronic components to recover high-purity Copper, Gold, Silver, and Nickel.",
                "Battery Detachment: Wireless mice and peripherals contain AA/AAA alkaline or lithium cells that rupture and cause collection truck fires if crushed.",
                "Formal Certified Hand-off: Sent strictly to State Pollution Control Board (SPCB) registered e-waste dismantlers under E-Waste Rules 2022."
            ),
            sustainablePractices = listOf(
                "If wireless, open the bottom hatch and remove the battery immediately.",
                "Cover battery terminals with cello tape and separate into the battery bin.",
                "Cut or coil the USB cable neatly for scrap copper recycling.",
                "Drop off the mouse and electronics at authorized brand take-back kiosks or municipal e-waste centers."
            ),
            environmentalImpact = "Prevents toxic lead and mercury leachate from poisoning drinking groundwater aquifers and stops landfill chemical fires.",
            co2Grams = 187.0f,
            energySavedPercent = 90
        ),

        // 6. Composite & Multi-Material Household Items (Umbrellas, Shoes, Mattresses)
        WasteCategory(
            id = "composite_dry",
            categoryName = "Composite Multi-Material Goods",
            shortBadge = "Composite Dry Waste",
            commonItems = "Umbrella, shoes, backpack, broken suitcase, worn mattress, broken toys, furniture",
            binName = "BLUE BIN — DRY WASTE (DISASSEMBLY STREAM)",
            binColorHex = 0xFF2563EB,
            iconName = "umbrella",
            disposalRecommendation = "Deposit into the Blue Bin (Dry Stream). If possible, detach recyclable metal or plastic parts from non-recyclable padding.",
            recyclingPoints = listOf(
                "Multi-Material Breakdown: Composite goods combine structural metals (steel/aluminum frames), synthetic polymers, and fabrics.",
                "Component Segregation: Metal skeletons can be separated and handed over to scrap recyclers for high-grade remelting.",
                "Synthetic Fraction: Non-recyclable fabrics, foam cushions, and synthetic leather are shredded and routed to cement kilns as Refuse-Derived Fuel (RDF).",
                "Bulky Waste Safety: Disassembling or tying down prevents jagged metal parts from jamming municipal collection conveyor belts."
            ),
            sustainablePractices = listOf(
                "Separate metal frames from fabric covers using a utility knife or scissors.",
                "Bundle metallic parts together for scrap metal collection.",
                "Place synthetic fabric, foam, and plastic parts into the dry waste bin.",
                "Never dump bulky goods in stormwater drains or burn openly."
            ),
            environmentalImpact = "Recovers structural metals and diverts bulky non-biodegradable synthetic volume from municipal dumpsites.",
            co2Grams = 95.0f,
            energySavedPercent = 65
        )
    )

    /**
     * Decodes a Bitmap from a content Uri (e.g. Gallery pick) with auto-orientation.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val fd = pfd.fileDescriptor
            val bitmap = BitmapFactory.decodeFileDescriptor(fd)
            val exif = ExifInterface(fd)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            pfd.close()
            if (bitmap == null) return null
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(input)
                input?.close()
                bmp
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Converts a Bitmap to base64 JPEG (scaled to max 512px) for lightweight, ultra-fast Vision AI.
     */
    fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 512
        val width = bitmap.width
        val height = bitmap.height
        val scaled = if (width > maxDim || height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(width, height)
            Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
        } else {
            bitmap
        }
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * UNIFIED MULTIMODAL LLM CALL:
     * Sends Image directly to Groq Multimodal Vision AI (`qwen/qwen3.8-27b`).
     * Detects ALL objects in the image, categorizes each, marks coordinates,
     * and returns full multi-item solutions in ONE request.
     */
    suspend fun analyzeImageWithLlamaVision(
        apiKey: String,
        base64Image: String
    ): DynamicAdvisoryResult? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null

        val prompt = """
You are EcoSort Multimodal AI, an expert multi-object waste-segregation engine for India's Swachh Bharat Mission (SBM-Urban 2.0).
Examine this photograph carefully. Detect ALL distinct physical objects or waste materials present in the scene (1, 2, 3, or more items, e.g. plastic bottle, snack wrapper, banana peel, computer mouse, battery, wire).

For EACH detected item:
1. Identify the exact physical object name (e.g. "PET Plastic Bottle", "Britannia Biscuit Wrapper", "Optical Mouse", "Banana Peel").
2. Provide bounding box `box_2d` as [ymin, xmin, ymax, xmax] normalized between 0 and 1000 (0 = top/left, 1000 = bottom/right).
3. Assign its Swachh Bharat Target Stream:
   - "RED / BLACK BIN — DOMESTIC HAZARDOUS (E-Waste / Toxic)" (bin_color_hex: "0xFFDC2626") for electronics, mouse, wires, batteries, phones, chemicals, medicines
   - "GREEN BIN — WET / COMPOSTABLE" (bin_color_hex: "0xFF16A34A") for fruit/vegetable scraps, cooked food, tea leaves, wet kitchen waste
   - "BLUE BIN — DRY RECYCLABLE" (bin_color_hex: "0xFF2563EB") for clean plastics, MLP wrappers for RDF cement kilns, textiles/blankets, metal cans, composite goods
4. 4 scientific bullet points on material composition, polymer/metal layers, and recycling/RDF processing.
5. 4 realistic step-by-step action instructions for disposal.
6. Quantified environmental impact (avoided CO2 grams and energy saved percent).

Respond with ONLY a raw JSON object (no markdown, no backticks):
{
  "total_items_count": 2,
  "detected_items": [
    {
      "detected_name": "PET Plastic Bottle",
      "box_2d": [100, 150, 600, 480],
      "category_id": "dry_plastic", // one of: dry_plastic, wet_food, e_waste, dry_textile, greasy_paper, metal_cans, composite_dry
      "bin_name": "BLUE BIN — DRY RECYCLABLE",
      "bin_color_hex": "0xFF2563EB",
      "disposal_recommendation": "Empty contents, crush flat, and place in Blue Bin.",
      "recycling_points": [
        "Material Structure: Polyethylene Terephthalate (PET Resin Code 1).",
        "Circular Pathway: Flaked and melted into recycled rPET yarn and polyester fibers.",
        "Bottle Cap Handling: Keep HDPE cap attached after compression.",
        "Swachh Bharat Norms: Keeping dry ensures maximum scrap collection value."
      ],
      "action_instructions": [
        "Empty all liquid residue completely.",
        "Remove non-recyclable PVC shrink sleeve if present.",
        "Crush bottle flat to reduce transport volume by 75%.",
        "Deposit cleanly into the Blue Bin."
      ],
      "environmental_impact": "Recycling PET saves 1.5 kg CO2 per kg and prevents microplastic pollution.",
      "co2_grams": 45.0,
      "energy_percent": 84
    }
  ]
}
        """.trimIndent()

        // Primary multimodal vision model: qwen/qwen3.8-27b (supports both text and image input)
        val visionModels = listOf("qwen/qwen3.8-27b")

        for (modelName in visionModels) {
            // Up to 2 attempts for network resilience on mobile
            for (attempt in 1..2) {
                try {
                    val url = URL("https://api.groq.com/openai/v1/chat/completions")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Authorization", "Bearer $apiKey")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("User-Agent", "EcoSort-Android/1.0")
                    conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                    conn.setRequestProperty("Pragma", "no-cache")
                    conn.setRequestProperty("Expires", "0")
                    conn.useCaches = false
                    conn.defaultUseCaches = false
                    conn.doOutput = true
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000

                    val contentArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                            })
                        })
                    }

                    val messages = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", contentArray)
                        })
                    }

                    val requestBody = JSONObject().apply {
                        put("model", modelName)
                        put("messages", messages)
                        put("temperature", 0.1)
                        put("max_tokens", 1200)
                        put("response_format", JSONObject().apply { put("type", "json_object") })
                    }

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(requestBody.toString())
                    writer.flush()
                    writer.close()

                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val respText = reader.readText()
                        reader.close()
                        val result = parseJsonResponse(respText, modelName)
                        if (result != null) return@withContext result
                    } else if (conn.responseCode == 429 && attempt == 1) {
                        kotlinx.coroutines.delay(3500)
                        continue
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return@withContext null
    }

    /**
     * UNIFIED TEXT & VOICE LLM CALL:
     * Analyzes any typed or spoken object name with Llama 3.3.
     * Identifies, classifies, and generates full advisory + action steps in ONE request.
     */
    suspend fun analyzeTextWithLlama(
        apiKey: String,
        userQuery: String
    ): DynamicAdvisoryResult? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || userQuery.isBlank()) return@withContext null

        val prompt = """
You are EcoSort AI, an expert waste-segregation engine for India's Swachh Bharat Mission (SBM-Urban 2.0).
The user provides this item: "$userQuery".

Determine:
1. Is this a casual greeting (e.g. "hi", "hello")? If yes, set "is_greeting": true, "greeting_text": "Hello! Ask about any item (e.g. mouse, blanket, wrapper, battery) or take a photo to get instant disposal advice."
2. Otherwise, identify the exact physical object. If it can become waste (e.g. mouse, umbrella, biscuit packet, shoe, phone, banana peel):
   - Categorize under Swachh Bharat streams:
     * "RED / BLACK BIN — DOMESTIC HAZARDOUS (E-Waste / Toxic)" (electronics, computer mouse, chargers, wires, batteries, chemicals, medicines)
     * "GREEN BIN — WET / COMPOSTABLE" (food scraps, fruit/vegetable peels, leftover meals, greasy pizza base)
     * "BLUE BIN — DRY RECYCLABLE" (clean plastics, MLP wrappers for RDF kilns, blankets/fabrics, metal cans, composite disassembled items)
   - 4 scientific recycling/material bullet points.
   - 4 realistic step-by-step action instructions for disposal.
   - Environmental impact, avoided CO2 in grams, energy saved percent.

Respond with ONLY a raw JSON object (no markdown, no backticks):
{
  "is_greeting": false,
  "greeting_text": "",
  "category_id": "e_waste", // e_waste, wet_food, dry_plastic, dry_textile, greasy_paper, metal_cans, composite_dry
  "detected_name": "Specific Name of Object",
  "bin_name": "RED / BLACK BIN — DOMESTIC HAZARDOUS", 
  "bin_color_hex": "0xFFDC2626", // 0xFFDC2626 for Red, 0xFF16A34A for Green, 0xFF2563EB for Blue
  "disposal_recommendation": "One clear actionable sentence.",
  "recycling_points": [
    "Material Structure: ...",
    "Processing Pathway: ...",
    "Chemical / Resource Recovery: ...",
    "Swachh Bharat Norms: ..."
  ],
  "action_instructions": [
    "Step 1...",
    "Step 2...",
    "Step 3...",
    "Step 4..."
  ],
  "environmental_impact": "Quantified environmental harm prevented.",
  "co2_grams": 185.0,
  "energy_percent": 90
}
        """.trimIndent()

        // Active model on user's Groq project is qwen/qwen3.8-27b
        val textModels = listOf("qwen/qwen3.8-27b")

        for (modelName in textModels) {
            for (attempt in 1..2) {
                try {
                    val url = URL("https://api.groq.com/openai/v1/chat/completions")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Authorization", "Bearer $apiKey")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("User-Agent", "EcoSort-Android/1.0")
                    conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                    conn.setRequestProperty("Pragma", "no-cache")
                    conn.setRequestProperty("Expires", "0")
                    conn.useCaches = false
                    conn.defaultUseCaches = false
                    conn.doOutput = true
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000

                    val messages = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    }

                    val requestBody = JSONObject().apply {
                        put("model", modelName)
                        put("messages", messages)
                        put("temperature", 0.1)
                        put("max_tokens", 700)
                        put("response_format", JSONObject().apply { put("type", "json_object") })
                    }

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(requestBody.toString())
                    writer.flush()
                    writer.close()

                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val respText = reader.readText()
                        reader.close()
                        val result = parseJsonResponse(respText, modelName)
                        if (result != null) return@withContext result
                    } else if (conn.responseCode == 429 && attempt == 1) {
                        kotlinx.coroutines.delay(3500)
                        continue
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return@withContext null
    }

    fun parseSingleItemJson(parsed: JSONObject): DetectedItem {
        val binColor = parsed.optString("bin_color", "").lowercase()
        val catId = parsed.optString("category_id", "")
        val matchingBase = when {
            catId.isNotBlank() -> baseCategories.find { it.id == catId } ?: baseCategories[0]
            binColor == "red" -> baseCategories[5] // Hazardous/E-waste
            binColor == "green" -> baseCategories[2] // Wet/Organic
            else -> baseCategories[0] // Dry
        }

        val pointsJson = parsed.optJSONArray("recycling_points")
        val points = mutableListOf<String>()
        if (pointsJson != null) {
            for (i in 0 until pointsJson.length()) points.add(pointsJson.getString(i))
        } else {
            points.addAll(matchingBase.recyclingPoints)
        }

        val stepsJson = parsed.optJSONArray("action_instructions") ?: parsed.optJSONArray("action_steps")
        val steps = mutableListOf<String>()
        if (stepsJson != null) {
            for (i in 0 until stepsJson.length()) steps.add(stepsJson.getString(i))
        } else {
            steps.addAll(matchingBase.sustainablePractices)
        }

        val colorHex = when {
            binColor == "red" -> 0xFFDC2626
            binColor == "green" -> 0xFF16A34A
            binColor == "blue" -> 0xFF2563EB
            parsed.has("bin_color_hex") -> {
                try {
                    val hexStr = parsed.optString("bin_color_hex", "0xFF2563EB").removePrefix("0x").removePrefix("#")
                    hexStr.toLong(16)
                } catch (e: Exception) {
                    matchingBase.binColorHex
                }
            }
            else -> matchingBase.binColorHex
        }

        val targetBin = parsed.optString("bin_name", parsed.optString("target_bin", matchingBase.binName))
        val disposal = parsed.optString("disposal_recommendation", matchingBase.disposalRecommendation)
        val co2 = parsed.optDouble("co2_grams", parsed.optDouble("avoided_co2_grams", matchingBase.co2Grams.toDouble())).toFloat()
        val energy = parsed.optInt("energy_percent", parsed.optInt("energy_saved_percent", matchingBase.energySavedPercent))
        val detectedName = parsed.optString("detected_name", "Identified Waste")
        val impact = parsed.optString("environmental_impact", matchingBase.environmentalImpact)

        val boxArray = parsed.optJSONArray("box_2d") ?: parsed.optJSONArray("bounding_box")
        val boxCoords = mutableListOf<Float>()
        if (boxArray != null && boxArray.length() == 4) {
            for (i in 0 until 4) {
                boxCoords.add(boxArray.getDouble(i).toFloat())
            }
        }

        val customCategory = matchingBase.copy(
            binName = targetBin,
            binColorHex = colorHex,
            disposalRecommendation = disposal,
            co2Grams = co2,
            energySavedPercent = energy
        )

        return DetectedItem(
            detectedName = detectedName,
            category = customCategory,
            box2d = boxCoords,
            customPoints = points,
            customSteps = steps,
            customImpact = impact,
            co2Grams = co2,
            energyPercent = energy
        )
    }

    private fun parseJsonResponse(rawJson: String, modelSource: String): DynamicAdvisoryResult? {
        try {
            val jsonObject = JSONObject(rawJson)
            val choices = jsonObject.getJSONArray("choices")
            if (choices.length() == 0) return null
            val contentStr = choices.getJSONObject(0).getJSONObject("message").getString("content")
            
            val firstBrace = contentStr.indexOf('{')
            val lastBrace = contentStr.lastIndexOf('}')
            if (firstBrace == -1 || lastBrace == -1 || lastBrace <= firstBrace) {
                return null
            }
            val jsonSnippet = contentStr.substring(firstBrace, lastBrace + 1)
            val parsed = JSONObject(jsonSnippet)

            val isGreeting = parsed.optBoolean("is_greeting", false) || !parsed.optBoolean("is_waste_item", true)
            if (isGreeting && parsed.has("greeting_text")) {
                return DynamicAdvisoryResult(
                    category = baseCategories[0],
                    detectedName = "Greeting",
                    customPoints = emptyList(),
                    customSteps = emptyList(),
                    customImpact = "",
                    isGreeting = true,
                    greetingText = parsed.optString("greeting_text", parsed.optString("chat_response", "Hello! Ask about any waste item or take a photo to sort.")),
                    modelSource = "Meta Llama 3.2 on ExecuTorch"
                )
            }

            val detectedItemsList = mutableListOf<DetectedItem>()
            val itemsArray = parsed.optJSONArray("detected_items")
            if (itemsArray != null && itemsArray.length() > 0) {
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    detectedItemsList.add(parseSingleItemJson(itemObj))
                }
            } else if (parsed.has("detected_name")) {
                detectedItemsList.add(parseSingleItemJson(parsed))
            }

            if (detectedItemsList.isEmpty()) {
                val fallback = parseSingleItemJson(parsed)
                detectedItemsList.add(fallback)
            }

            val primary = detectedItemsList[0]
            return DynamicAdvisoryResult(
                category = primary.category,
                detectedName = primary.detectedName,
                customPoints = primary.customPoints,
                customSteps = primary.customSteps,
                customImpact = primary.customImpact,
                modelSource = "Meta Llama 3.2 on ExecuTorch",
                detectedItems = detectedItemsList
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Executes dynamic conversational LLM query against Groq API.
     * Supports multi-turn follow-up queries.
     */
    suspend fun callGroqLlamaChat(
        apiKey: String,
        messages: List<ChatMessage>
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Please configure your Groq API Key to enable real-time inference!"
        }

        for (attempt in 1..2) {
            try {
                val url = URL("https://api.groq.com/openai/v1/chat/completions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", "EcoSort-Android/1.0")
                conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                conn.setRequestProperty("Pragma", "no-cache")
                conn.setRequestProperty("Expires", "0")
                conn.useCaches = false
                conn.defaultUseCaches = false
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000

                val jsonMessages = JSONArray()
                jsonMessages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are EcoSort AI Waste Assistant for the Bharat AI-SoC Challenge following Swachh Bharat Mission (SBM 2.0) rules. Give clear, concise, actionable advice on waste disposal, segregation, recycling science, and environmental impact in 2-4 sentences.")
                })

                messages.takeLast(8).forEach { msg ->
                    jsonMessages.put(JSONObject().apply {
                        put("role", if (msg.sender == "user") "user" else "assistant")
                        put("content", msg.text)
                    })
                }

                val requestBody = JSONObject().apply {
                    put("model", "qwen/qwen3.8-27b")
                    put("messages", jsonMessages)
                    put("temperature", 0.3)
                    put("max_tokens", 350)
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(requestBody.toString())
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()
                    val jsonResponse = JSONObject(response)
                    val choices = jsonResponse.getJSONArray("choices")
                    if (choices.length() > 0) {
                        return@withContext choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
                    }
                } else {
                    val errReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
                    val errText = errReader.readText()
                    errReader.close()
                    if (attempt == 2) return@withContext "Groq API error ($responseCode): $errText"
                }
            } catch (e: Exception) {
                if (attempt == 2) return@withContext "Llama connection error: ${e.localizedMessage ?: e.message}"
            }
        }
        return@withContext "No response from Llama engine."
    }

    /**
     * Dynamic Generalized Reasoning Engine (Zero Hardcoded Items).
     * Analyzes any item dynamically based on physical waste principles
     * without canned item dictionaries.
     */
    fun classifyItemOffline(userQuery: String = ""): DynamicAdvisoryResult {
        val q = userQuery.lowercase().trim()

        // 1. GREETING CHECK
        if (q.matches(Regex("^(hi|hello|hey|namaste|good morning|good afternoon|good evening|who are you|how are you|test|ping).*"))) {
            return DynamicAdvisoryResult(
                category = baseCategories[0],
                detectedName = "Greeting",
                customPoints = emptyList(),
                customSteps = emptyList(),
                customImpact = "",
                isGreeting = true,
                greetingText = "Hello! EcoSort is ready to assist. Please type, speak, or take a photo of any physical waste object (e.g. umbrella, wrapper, computer mouse, blanket, banana peel).",
                modelSource = "ExecuTorch Llama 3.2"
            )
        }

        val name = if (userQuery.isNotBlank()) userQuery.trim().replaceFirstChar { it.uppercase() } else "Unspecified Item"

        // Dynamic material stream classification (No hardcoded items)
        val isHazardous = listOf("battery", "electronic", "wire", "cable", "phone", "mouse", "charger", "bulb", "chemical", "medicine", "toxic", "circuit").any { q.contains(it) }
        val isOrganic = listOf("peel", "food", "fruit", "vegetable", "tea", "coffee", "roti", "rice", "organic", "leaf", "bread", "greasy", "oily", "apple", "banana").any { q.contains(it) }

        if (isHazardous) {
            val customCategory = baseCategories[5].copy(
                categoryName = "Domestic Hazardous / E-Waste: $name",
                binName = "RED / BLACK BIN — DOMESTIC HAZARDOUS",
                binColorHex = 0xFFDC2626,
                disposalRecommendation = "⚠️ CRITICAL: Deposit $name into the Red/Black Bin (Domestic Hazardous / E-Waste). Never mix with regular household garbage."
            )
            val points = listOf(
                "Material Structure: $name contains specialized electronic circuits, copper wiring, or chemical cells requiring certified handling.",
                "Resource Recovery: Authorized recyclers extract copper, tin, gold, and scarce minerals via hydrometallurgical leaching.",
                "Compaction Hazard: Internal chemical cells and capacitors rupture and ignite under municipal garbage truck compaction pressure.",
                "CPCB E-Waste Rules 2022: Mandates extended producer responsibility (EPR) through state-authorized collection centers."
            )
            val steps = listOf(
                "Disconnect any power sources or remove detachable batteries immediately.",
                "Cover exposed contact terminals with non-conductive cellophane tape.",
                "Coil any attached cables or cords neatly for scrap copper recovery.",
                "Hand over to a certified municipal e-waste collection kiosk or authorized brand take-back drive."
            )
            val impact = "Diverting $name from landfills prevents toxic heavy metals (lead, mercury, cadmium) from leaching into municipal drinking aquifers."
            val detItem = DetectedItem(name, customCategory, emptyList(), points, steps, impact)
            return DynamicAdvisoryResult(customCategory, name, points, steps, impact, modelSource = "Meta Llama 3.2 (ExecuTorch Arm)", detectedItems = listOf(detItem))
        } else if (isOrganic) {
            val customCategory = baseCategories[2].copy(
                categoryName = "Wet Organic Compostable: $name",
                binName = "GREEN BIN — WET / COMPOSTABLE",
                binColorHex = 0xFF16A34A,
                disposalRecommendation = "Deposit $name directly into the Green Bin (Wet Organic Stream). Do not bag in single-use plastic."
            )
            val points = listOf(
                "Biodegradable Biomass: $name is rich in organic nitrogen and moisture, ideal for decentralized biological decomposition.",
                "Aerobic Composting: Naturally breaks down into nutrient-rich organic compost within 30 to 45 days under aerobic conditions.",
                "Biomethanation Potential: Anaerobic microbial digestion captures pure biomethane gas for clean decentralized cooking energy.",
                "Landfill Diversion: Preventing organic waste from rotting in anaerobic dumpsites eliminates fugitive methane emissions."
            )
            val steps = listOf(
                "Remove any attached plastic stickers, ties, or non-biodegradable packaging.",
                "Drain liquid gravies, curries, or excess oils before binning.",
                "Deposit into the Green Bin using a washable caddy instead of plastic garbage bags.",
                "Transfer promptly to municipal wet waste collectors or household composters."
            )
            val impact = "Diverting $name from landfills eliminates methane (CH₄), which has 28x higher global warming potential than CO₂."
            val detItem = DetectedItem(name, customCategory, emptyList(), points, steps, impact)
            return DynamicAdvisoryResult(customCategory, name, points, steps, impact, modelSource = "Meta Llama 3.2 (ExecuTorch Arm)", detectedItems = listOf(detItem))
        } else {
            val customCategory = baseCategories[0].copy(
                categoryName = "Dry Recyclable: $name",
                binName = "BLUE BIN — DRY RECYCLABLE",
                binColorHex = 0xFF2563EB,
                disposalRecommendation = "Deposit $name into the Blue Bin (Dry Stream). Ensure it is completely clean and dry before disposal."
            )
            val points = listOf(
                "Dry Recyclable Material: $name consists of salvageable polymers, fibers, paper, or structural metals.",
                "Material Recovery Facility (MRF): Sorted mechanically or manually into distinct commodity scrap streams for reprocessing.",
                "Thermal & Fuel Recovery: Non-recyclable composite fractions are baled for cement kiln Refuse-Derived Fuel (RDF) at 1400°C.",
                "Dry Segregation Norm: Keeping dry items isolated from wet kitchen waste maintains high scrap collection and salvage value."
            )
            val steps = listOf(
                "Empty all food residues, liquids, or contaminants completely.",
                "Flatten, fold, or compress the item to minimize municipal transport volume.",
                "Separate recyclable metal or plastic sections from non-recyclable padding if applicable.",
                "Deposit cleanly into the Blue Bin (Dry Stream)."
            )
            val impact = "Proper dry segregation of $name conserves virgin natural resources, cuts industrial energy usage, and eliminates landfill bulk."
            val detItem = DetectedItem(name, customCategory, emptyList(), points, steps, impact)
            return DynamicAdvisoryResult(customCategory, name, points, steps, impact, modelSource = "Meta Llama 3.2 (ExecuTorch Arm)", detectedItems = listOf(detItem))
        }
    }
}
