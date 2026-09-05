package com.example.injectionsites

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.NumberPicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

private val Blue = Color(0xFF1557C0)
private val Skin = Color(0xFFF1C39D)
private val SurfaceTint = Color(0xFFF6F8FC)

/** Persisted, centralised timing settings. Injection and sensor cycles stay independent. */
internal data class TimingSettings(val redHours: Float = 12f, val orangeHours: Float = 24f, val yellowHours: Float = 36f, val sensorStageDays: Long = 10, val sensorHiddenDays: Long = 40) {
    fun valid() = redHours > 0 && redHours < orangeHours && orangeHours < yellowHours && sensorStageDays > 0 && sensorStageDays * 3 < sensorHiddenDays
}
internal val DefaultSettings = TimingSettings()
private var currentSettings = DefaultSettings
private const val SETTINGS_KEY = "timing_settings"; internal const val AVATAR_KEY = "avatar_style"
internal fun loadSettings(context: Context) = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).let { p -> TimingSettings(p.getFloat("red",12f),p.getFloat("orange",24f),p.getFloat("yellow",36f),p.getLong("sensorStage",10),p.getLong("sensorHidden",40)).takeIf { it.valid() } ?: DefaultSettings }
private fun saveSettings(context: Context, value: TimingSettings): Boolean = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).edit().putFloat("red",value.redHours).putFloat("orange",value.orangeHours).putFloat("yellow",value.yellowHours).putLong("sensorStage",value.sensorStageDays).putLong("sensorHidden",value.sensorHiddenDays).commit()
private val RED = Color(0xFFDC2626); private val ORANGE = Color(0xFFF97316); private val YELLOW = Color(0xFFEAB308); private val GREEN = Color(0xFF16A34A)
internal fun sensorVisualStage(eventDateTime: Long, now: Long = System.currentTimeMillis(), settings: TimingSettings = currentSettings): Int? {
    val days = ((now - eventDateTime).coerceAtLeast(0L)) / 86_400_000L
    return when {
        days < settings.sensorStageDays -> 0
        days < settings.sensorStageDays * 2 -> 1
        days < settings.sensorStageDays * 3 -> 2
        days < settings.sensorHiddenDays -> 3
        else -> null
    }
}
private object SensorLifecycle {
    val DARK = Color(0xFF475569); val MEDIUM = Color(0xFF7C8796); val LIGHT = Color(0xFFB8C0CB); val FADED = Color(0xFFDEE3EA)
    fun colorAt(eventDateTime: Long, now: Long = System.currentTimeMillis()): Color? = when (sensorVisualStage(eventDateTime, now)) { 0 -> DARK; 1 -> MEDIUM; 2 -> LIGHT; 3 -> FADED; else -> null }
}
class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Blue)) { InjectionApp() } } } }
enum class EntryMode(val label: String) { INSULINA("Insulina"), SENSORE("Sensore") }
enum class InsulinType(val label: String, val color: Color) { RAPIDA("Rapida", Color(0xFFF97316)), BASALE("Basale", Blue) }
/** New styles can be added without moving the anatomical zone geometry. */
enum class AvatarStyle(val label: String, val front: Int, val back: Int) {
    UOMO("Uomo", R.drawable.avatar_man_front, R.drawable.avatar_man_back),
    DONNA("Donna", R.drawable.avatar_woman_front, R.drawable.avatar_woman_back),
    YETI("Yeti", R.drawable.avatar_yeti_front, R.drawable.avatar_yeti_back);

    /** Dedicated anatomical artwork, when supplied, selected by avatar and side. */
    fun zoom(area: BodyArea): Int = when (this) {
        UOMO -> when (area) {
            BodyArea.LEFT_ARM -> R.drawable.avatar_man_arm_right
            BodyArea.RIGHT_ARM -> R.drawable.avatar_man_arm_left
            BodyArea.ABDOMEN -> R.drawable.avatar_man_abdomen
            BodyArea.LEFT_THIGH -> R.drawable.avatar_man_thigh_right
            BodyArea.RIGHT_THIGH -> R.drawable.avatar_man_thigh_left
            BodyArea.LEFT_GLUTE -> R.drawable.avatar_man_glute_right
            BodyArea.RIGHT_GLUTE -> R.drawable.avatar_man_glute_left
        }
        DONNA -> when (area) {
            BodyArea.LEFT_ARM -> R.drawable.avatar_woman_arm_right
            BodyArea.RIGHT_ARM -> R.drawable.avatar_woman_arm_left
            BodyArea.ABDOMEN -> R.drawable.avatar_woman_abdomen
            BodyArea.LEFT_THIGH -> R.drawable.avatar_woman_thigh_right
            BodyArea.RIGHT_THIGH -> R.drawable.avatar_woman_thigh_left
            BodyArea.LEFT_GLUTE -> R.drawable.avatar_woman_glute_right
            BodyArea.RIGHT_GLUTE -> R.drawable.avatar_woman_glute_left
        }
        YETI -> when (area) {
            BodyArea.LEFT_ARM -> R.drawable.avatar_yeti_arm_right
            BodyArea.RIGHT_ARM -> R.drawable.avatar_yeti_arm_left
            BodyArea.ABDOMEN -> R.drawable.avatar_yeti_abdomen
            BodyArea.LEFT_THIGH -> R.drawable.avatar_yeti_thigh_right
            BodyArea.RIGHT_THIGH -> R.drawable.avatar_yeti_thigh_left
            BodyArea.LEFT_GLUTE -> R.drawable.avatar_yeti_glute_right
            BodyArea.RIGHT_GLUTE -> R.drawable.avatar_yeti_glute_left
        }
    }
}
enum class BodyArea(val label: String, val color: Color, val zones: List<String>) {
    RIGHT_ARM("Braccio destro", Color(0xFF2878D4), listOf("Superiore interna", "Superiore esterna", "Inferiore interna", "Inferiore esterna")), LEFT_ARM("Braccio sinistro", Color(0xFF2878D4), listOf("Superiore interna", "Superiore esterna", "Inferiore interna", "Inferiore esterna")),
    ABDOMEN("Addome", Color(0xFF54A83E), listOf("Superiore destra esterna", "Superiore destra interna", "Superiore sinistra interna", "Superiore sinistra esterna", "Inferiore destra esterna", "Inferiore destra interna", "Inferiore sinistra interna", "Inferiore sinistra esterna")),
    RIGHT_THIGH("Coscia destra", Color(0xFFF28B19), listOf("Superiore interna", "Superiore esterna", "Inferiore interna", "Inferiore esterna")), LEFT_THIGH("Coscia sinistra", Color(0xFFF28B19), listOf("Superiore interna", "Superiore esterna", "Inferiore interna", "Inferiore esterna")),
    RIGHT_GLUTE("Gluteo destro", Color(0xFF7C4DCC), listOf("Superiore", "Inferiore")), LEFT_GLUTE("Gluteo sinistro", Color(0xFF7C4DCC), listOf("Superiore", "Inferiore"))
}
data class RecordItem(
    val area: BodyArea,
    val zone: Int,
    val mode: EntryMode,
    val insulinType: InsulinType?,
    /** Date and time of the event selected by the user. */
    val eventDateTime: Long = System.currentTimeMillis(),
    /** Technical insertion timestamp, used only to break ties. */
    val createdAt: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString()
) {
    /** Compatibility alias for the existing persisted and backup schema. */
    val time: Long get() = eventDateTime
}

internal const val STORAGE = "injection_sites"; internal const val HISTORY_KEY = "injection_history"; internal const val LEGACY_SENSOR_KEY = "sensor_position"
internal fun RecordItem.toJson() = JSONObject().apply { put("id",id);put("area", area.name); put("zone", zone); put("mode", mode.name); put("insulinType", insulinType?.name); put("time", eventDateTime); put("createdAt", createdAt) }
internal fun recordFromJson(json: JSONObject): RecordItem? = runCatching {
    val eventDateTime=if(json.has("eventDateTime"))json.getLong("eventDateTime")else json.getLong("time");val area=BodyArea.valueOf(json.getString("area"));val zone=json.getInt("zone");val mode=EntryMode.valueOf(json.getString("mode"));val insulin=json.optString("insulinType").takeIf { it.isNotBlank()&&it!="null" }?.let(InsulinType::valueOf);val createdAt=json.optLong("createdAt",eventDateTime)
    val id=json.optString("id").takeIf { it.isNotBlank() } ?: UUID.nameUUIDFromBytes("${area.name}|$zone|${mode.name}|${insulin?.name}|$eventDateTime|$createdAt".toByteArray()).toString()
    RecordItem(area,zone,mode,insulin,eventDateTime,createdAt,id)
}.getOrNull()
private val eventDateTimeDescending = compareByDescending<RecordItem> { it.eventDateTime }.thenByDescending { it.createdAt }
internal fun orderedRecords(records: List<RecordItem>) = records.sortedWith(eventDateTimeDescending)
internal fun loadRecords(context: Context): List<RecordItem> = runCatching {
    val preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
    val records = JSONArray(preferences.getString(HISTORY_KEY, "[]")).let { data -> (0 until data.length()).mapNotNull { recordFromJson(data.getJSONObject(it)) } }.toMutableList()
    preferences.getString(LEGACY_SENSOR_KEY, null)?.let { raw -> recordFromJson(JSONObject(raw))?.takeIf { legacy -> records.none { it.mode == EntryMode.SENSORE && it.eventDateTime == legacy.eventDateTime } }?.let(records::add) }
    orderedRecords(records)
}.getOrDefault(emptyList())
private fun saveRecords(context: Context, records: List<RecordItem>) { val data = JSONArray(); records.forEach { data.put(it.toJson()) }; context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).edit().putString(HISTORY_KEY, data.toString()).remove(LEGACY_SENSOR_KEY).apply() }
internal fun activeSensor(records: List<RecordItem>): RecordItem? = records.filter { it.mode == EntryMode.SENSORE }.maxWithOrNull(compareBy<RecordItem> { it.eventDateTime }.thenBy { it.createdAt })

@Composable fun InjectionApp() {
    val context = LocalContext.current; var screen by remember { mutableStateOf("home") }; var area by remember { mutableStateOf<BodyArea?>(null) }
    var avatar by remember { mutableStateOf(context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).getString(AVATAR_KEY, AvatarStyle.YETI.name)?.let { runCatching { AvatarStyle.valueOf(it) }.getOrNull() } ?: AvatarStyle.YETI) }
    var settings by remember { mutableStateOf(loadSettings(context)) }; currentSettings = settings
    val records = remember { mutableStateListOf<RecordItem>().also { it.addAll(loadRecords(context)) } }
    val refreshTick by produceState(initialValue = 0) { while (true) { delay(60_000); value++ } }
    fun persist() = saveRecords(context, records)
    when (screen) {
        "home" -> key(refreshTick, avatar, settings) { HomeScreen(records, activeSensor(records), avatar, { area = it; screen = "area" }, { screen = "history" }, { screen = "settings" }) }
        "area" -> area?.let { selected -> AreaScreen(selected, records, activeSensor(records), avatar, { record -> records.add(record);records.sortWith(eventDateTimeDescending);persist() }) { screen = "home" } }
        "settings" -> SettingsScreen(avatar, settings, { avatar = it; context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).edit().putString(AVATAR_KEY, it.name).apply() }, { candidate -> saveSettings(context,candidate).also { saved -> if(saved) settings=candidate } }, { AppBackupState(records.toList(),settings,avatar) }, { imported -> records.clear();records.addAll(imported.records);settings=imported.settings;avatar=imported.avatar }) { screen = "home" }
        else -> HistoryScreen(records, { record -> records.remove(record); persist() }) { screen = "home" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HomeScreen(records: List<RecordItem>, sensor: RecordItem?, avatar: AvatarStyle, onArea: (BodyArea) -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) = Scaffold(topBar = { TopAppBar(title = { Column { Text("Nuova iniezione", fontWeight = FontWeight.Bold); Text("Seleziona una zona sulla sagoma", fontSize = 12.sp, color = Color.Gray) } }, actions = { IconButton(onClick = onHistory) { Icon(Icons.Default.History, "Storico") }; IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Impostazioni") } }) }) { padding -> LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { BodyMap(records, sensor, avatar, onArea) }; item { AvailabilityLegend() }; item { Text("Tutte le aree", fontWeight = FontWeight.Bold, fontSize = 18.sp) }; itemsIndexed(BodyArea.entries) { _, bodyArea -> AreaCard(bodyArea) { onArea(bodyArea) } }; item { Spacer(Modifier.height(20.dp)) } } }
@Composable private fun AvailabilityLegend() = Card(colors = CardDefaults.cardColors(containerColor = SurfaceTint)) { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Disponibilità:", fontWeight = FontWeight.Bold, fontSize = 13.sp); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { LegendDot(RED, "< ${currentSettings.redHours} h", Modifier.weight(1f)); LegendDot(ORANGE, "${currentSettings.redHours}–${currentSettings.orangeHours} h", Modifier.weight(1f)) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { LegendDot(YELLOW, "${currentSettings.orangeHours}–${currentSettings.yellowHours} h", Modifier.weight(1f)); LegendDot(GREEN, "> ${currentSettings.yellowHours} h", Modifier.weight(1f)) } } }
@Composable private fun LegendDot(color: Color, text: String, modifier: Modifier = Modifier) = Row(modifier, verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).background(color, CircleShape)); Spacer(Modifier.width(4.dp)); Text(text, fontSize = 11.sp, maxLines = 1) }
private fun zoneColor(area: BodyArea, index: Int, records: List<RecordItem>): Color { val last = records.filter { it.mode == EntryMode.INSULINA && it.area == area && it.zone == index }.maxByOrNull { it.eventDateTime } ?: return GREEN; val hours = (System.currentTimeMillis() - last.eventDateTime).coerceAtLeast(0) / 3_600_000f; return when { hours < currentSettings.redHours -> RED; hours < currentSettings.orangeHours -> ORANGE; hours < currentSettings.yellowHours -> YELLOW; else -> GREEN } }
@Composable private fun AreaCard(area: BodyArea, onClick: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = area.color.copy(alpha = .08f))) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(14.dp).background(area.color, CircleShape)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(area.label, fontWeight = FontWeight.Bold); Text("${area.zones.size} zone selezionabili", fontSize = 12.sp, color = Color.Gray) }; Icon(Icons.Default.ChevronRight, null, tint = Blue) } }
@Composable private fun BodyMap(records: List<RecordItem>, sensor: RecordItem?, avatar: AvatarStyle, onArea: (BodyArea) -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = SurfaceTint)) { Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Vista a specchio", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Tocca un gruppo di zone per ingrandirlo", fontSize = 12.sp, color = Color.Gray); FrontBodyCanvas(records, sensor, avatar, onArea); HorizontalDivider(Modifier.padding(vertical = 4.dp)); Text("Vista posteriore · glutei", fontWeight = FontWeight.SemiBold, fontSize = 13.sp); GluteCanvas(records, sensor, avatar, onArea) } }

private data class Hit(val rect: Rect, val area: BodyArea, val zone: Int)
private fun sensorMarkers(records: List<RecordItem>, active: RecordItem?, hit: Hit): List<Pair<Color, Boolean>> = records.asSequence().filter { it.mode == EntryMode.SENSORE && it.area == hit.area && it.zone == hit.zone }.sortedWith(eventDateTimeDescending).mapNotNull { record -> SensorLifecycle.colorAt(record.eventDateTime)?.let { color -> color to (record === active || record == active) } }.take(3).toList()
internal data class ImagePlacement(val left: Int, val top: Int, val width: Int, val height: Int) {
    fun point(normalized: Offset) = Offset(left + normalized.x * width, top + normalized.y * height)
}
private data class DashboardZone(val path: Path, val bounds: Rect, val area: BodyArea, val zone: Int)
internal fun fitImage(imageWidth: Int, imageHeight: Int, canvas: Size): ImagePlacement {
    val scale = minOf(canvas.width / imageWidth, canvas.height / imageHeight)
    val width = (imageWidth * scale).roundToInt(); val height = (imageHeight * scale).roundToInt()
    return ImagePlacement(((canvas.width - width) / 2f).roundToInt(), ((canvas.height - height) / 2f).roundToInt(), width, height)
}
private fun anatomicalPath(placement: ImagePlacement, points: List<Offset>) = Path().apply {
    placement.point(points.first()).let { moveTo(it.x, it.y) }
    points.drop(1).forEach { placement.point(it).let { point -> lineTo(point.x, point.y) } }
    close()
}
private fun splitDashboardPath(outline: Path, area: BodyArea, columns: Int, rows: Int, reverseColumns: Boolean = false): List<DashboardZone> {
    val bounds = outline.getBounds()
    return List(columns * rows) { cell ->
        val row = cell / columns; val column = cell % columns
        val clip = Path().apply { addRect(Rect(bounds.left + bounds.width * column / columns, bounds.top + bounds.height * row / rows, bounds.left + bounds.width * (column + 1) / columns, bounds.top + bounds.height * (row + 1) / rows)) }
        val path = Path.combine(PathOperation.Intersect, outline, clip)
        val zone = row * columns + if (reverseColumns) columns - 1 - column else column
        DashboardZone(path, path.getBounds(), area, zone)
    }
}
private fun mirrored(points: List<Offset>) = points.map { Offset(1f - it.x, it.y) }
private fun frontOutline(avatar: AvatarStyle, area: BodyArea): List<Offset> {
    val left = when (area) {
        BodyArea.LEFT_ARM -> when (avatar) {
            AvatarStyle.UOMO -> listOf(Offset(.245f,.26f),Offset(.315f,.235f),Offset(.35f,.285f),Offset(.34f,.35f),Offset(.315f,.41f),Offset(.275f,.43f),Offset(.24f,.39f),Offset(.225f,.32f))
            AvatarStyle.DONNA -> listOf(Offset(.345f,.27f),Offset(.39f,.29f),Offset(.405f,.35f),Offset(.395f,.42f),Offset(.37f,.46f),Offset(.335f,.44f),Offset(.32f,.37f))
            AvatarStyle.YETI -> listOf(Offset(.25f,.395f),Offset(.325f,.41f),Offset(.345f,.48f),Offset(.325f,.56f),Offset(.285f,.63f),Offset(.235f,.605f),Offset(.205f,.52f),Offset(.215f,.445f))
        }
        BodyArea.ABDOMEN -> return when (avatar) {
            AvatarStyle.UOMO -> listOf(Offset(.35f,.34f),Offset(.65f,.34f),Offset(.665f,.405f),Offset(.64f,.455f),Offset(.58f,.475f),Offset(.42f,.475f),Offset(.36f,.455f),Offset(.335f,.405f))
            AvatarStyle.DONNA -> listOf(Offset(.405f,.395f),Offset(.595f,.395f),Offset(.615f,.445f),Offset(.585f,.49f),Offset(.415f,.49f),Offset(.385f,.445f))
            AvatarStyle.YETI -> listOf(Offset(.35f,.44f),Offset(.65f,.44f),Offset(.69f,.51f),Offset(.675f,.61f),Offset(.625f,.675f),Offset(.375f,.675f),Offset(.325f,.61f),Offset(.31f,.51f))
        }
        BodyArea.LEFT_THIGH -> when (avatar) {
            AvatarStyle.UOMO -> listOf(Offset(.305f,.59f),Offset(.49f,.60f),Offset(.475f,.67f),Offset(.445f,.72f),Offset(.34f,.715f),Offset(.3f,.665f))
            AvatarStyle.DONNA -> listOf(Offset(.36f,.535f),Offset(.49f,.565f),Offset(.48f,.65f),Offset(.45f,.70f),Offset(.38f,.685f),Offset(.345f,.59f))
            AvatarStyle.YETI -> listOf(Offset(.33f,.70f),Offset(.49f,.715f),Offset(.48f,.82f),Offset(.44f,.87f),Offset(.335f,.855f),Offset(.305f,.76f))
        }
        else -> return mirrored(frontOutline(avatar, when (area) { BodyArea.RIGHT_ARM -> BodyArea.LEFT_ARM; BodyArea.RIGHT_THIGH -> BodyArea.LEFT_THIGH; else -> area }))
    }
    return left
}
private fun backOutline(avatar: AvatarStyle, area: BodyArea): List<Offset> {
    val left = when (avatar) {
        AvatarStyle.UOMO -> listOf(Offset(.34f,.505f),Offset(.49f,.505f),Offset(.49f,.62f),Offset(.445f,.64f),Offset(.355f,.625f),Offset(.315f,.57f))
        AvatarStyle.DONNA -> listOf(Offset(.365f,.505f),Offset(.49f,.515f),Offset(.49f,.59f),Offset(.45f,.615f),Offset(.375f,.60f),Offset(.345f,.55f))
        AvatarStyle.YETI -> listOf(Offset(.335f,.59f),Offset(.49f,.58f),Offset(.49f,.71f),Offset(.44f,.74f),Offset(.34f,.715f),Offset(.30f,.65f))
    }
    return if (area == BodyArea.LEFT_GLUTE) left else mirrored(left)
}
private fun frontZones(avatar: AvatarStyle, placement: ImagePlacement): List<DashboardZone> = buildList {
    addAll(splitDashboardPath(anatomicalPath(placement, frontOutline(avatar, BodyArea.LEFT_ARM)), BodyArea.LEFT_ARM, 2, 2, reverseColumns = true))
    addAll(splitDashboardPath(anatomicalPath(placement, frontOutline(avatar, BodyArea.RIGHT_ARM)), BodyArea.RIGHT_ARM, 2, 2))
    addAll(splitDashboardPath(anatomicalPath(placement, frontOutline(avatar, BodyArea.ABDOMEN)), BodyArea.ABDOMEN, 4, 2))
    addAll(splitDashboardPath(anatomicalPath(placement, frontOutline(avatar, BodyArea.LEFT_THIGH)), BodyArea.LEFT_THIGH, 2, 2))
    addAll(splitDashboardPath(anatomicalPath(placement, frontOutline(avatar, BodyArea.RIGHT_THIGH)), BodyArea.RIGHT_THIGH, 2, 2))
}
private fun gluteZones(avatar: AvatarStyle, placement: ImagePlacement): List<DashboardZone> = buildList {
    addAll(splitDashboardPath(anatomicalPath(placement, backOutline(avatar, BodyArea.LEFT_GLUTE)), BodyArea.LEFT_GLUTE, 1, 2))
    addAll(splitDashboardPath(anatomicalPath(placement, backOutline(avatar, BodyArea.RIGHT_GLUTE)), BodyArea.RIGHT_GLUTE, 1, 2))
}
private fun DrawScope.drawDashboardZone(zone: DashboardZone, records: List<RecordItem>, sensor: RecordItem?) {
    drawZonePath(ZonePath(zone.path, zone.bounds, zone.zone), zoneColor(zone.area, zone.zone, records).copy(alpha=.72f), sensorMarkers(records, sensor, Hit(zone.bounds, zone.area, zone.zone)))
}
@Composable private fun rawImageBitmap(resource: Int): ImageBitmap {
    val resources = LocalContext.current.resources
    return remember(resources, resource) { BitmapFactory.decodeResource(resources, resource, BitmapFactory.Options().apply { inScaled = false }).asImageBitmap() }
}
@Composable private fun FrontBodyCanvas(records: List<RecordItem>, sensor: RecordItem?, avatar: AvatarStyle, onArea: (BodyArea) -> Unit) {
    val bitmap = rawImageBitmap(avatar.front)
    Canvas(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height).pointerInput(avatar, bitmap.width, bitmap.height) { detectTapGestures { point -> val placement=fitImage(bitmap.width,bitmap.height,Size(size.width.toFloat(),size.height.toFloat()));frontZones(avatar,placement).firstOrNull { it.path.contains(point) }?.let { onArea(it.area) } } }) {
        val placement=fitImage(bitmap.width,bitmap.height,size);drawImage(bitmap,dstOffset=IntOffset(placement.left,placement.top),dstSize=IntSize(placement.width,placement.height),filterQuality=FilterQuality.High);frontZones(avatar,placement).forEach { drawDashboardZone(it,records,sensor) }
    }
}
@Composable private fun GluteCanvas(records: List<RecordItem>, sensor: RecordItem?, avatar: AvatarStyle, onArea: (BodyArea) -> Unit) {
    val bitmap = rawImageBitmap(avatar.back)
    Canvas(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height).pointerInput(avatar, bitmap.width, bitmap.height) { detectTapGestures { point -> val placement=fitImage(bitmap.width,bitmap.height,Size(size.width.toFloat(),size.height.toFloat()));gluteZones(avatar,placement).firstOrNull { it.path.contains(point) }?.let { onArea(it.area) } } }) {
        val placement=fitImage(bitmap.width,bitmap.height,size);drawImage(bitmap,dstOffset=IntOffset(placement.left,placement.top),dstSize=IntSize(placement.width,placement.height),filterQuality=FilterQuality.High);gluteZones(avatar,placement).forEach { drawDashboardZone(it,records,sensor) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AreaScreen(area: BodyArea, records: List<RecordItem>, sensor: RecordItem?, avatar: AvatarStyle, onSaved: (RecordItem) -> Unit, onBack: () -> Unit) {
    var mode by remember { mutableStateOf(EntryMode.INSULINA) }; var insulinType by remember { mutableStateOf(InsulinType.RAPIDA) }; var zone by remember { mutableStateOf<Int?>(null) }; var saved by remember { mutableStateOf(false) }
    var eventDateTime by remember { mutableLongStateOf(Calendar.getInstance().apply { set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0) }.timeInMillis) }; var error by remember { mutableStateOf<String?>(null) }; var pending by remember { mutableStateOf<RecordItem?>(null) }
    fun prepareSave() { val selected=zone ?: return; val item=RecordItem(area,selected,mode,if(mode==EntryMode.INSULINA) insulinType else null,eventDateTime); if(eventDateTime > System.currentTimeMillis()) pending=item else { onSaved(item); saved=true; error=null } }
    if(pending != null) AlertDialog(onDismissRequest={pending=null},title={Text("Data futura")},text={Text("La data e l'ora selezionate sono nel futuro. Vuoi salvare comunque?")},confirmButton={TextButton(onClick={pending?.let(onSaved); pending=null; saved=true; error=null}){Text("Salva")}},dismissButton={TextButton(onClick={pending=null}){Text("Annulla")}})
    Scaffold(topBar = { TopAppBar(title = { Text(area.label, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack,"Indietro") } }) }, bottomBar = { Surface(shadowElevation = 8.dp) { Button(onClick = ::prepareSave, enabled = zone != null, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Icon(Icons.Default.Save,null); Spacer(Modifier.width(8.dp)); Text("Salva posizione") } } }) { padding -> LazyColumn(Modifier.padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { item { Text("Zoom dedicato · ${area.zones.size} zone",color=Blue,fontWeight=FontWeight.SemiBold); ModePicker(mode,{mode=it},insulinType,{insulinType=it}); EventDateTimeFields(eventDateTime){eventDateTime=it;saved=false;error=null}; error?.let { Text(it,color=MaterialTheme.colorScheme.error) } }; item { Text("Tocca una zona",fontWeight=FontWeight.Bold,fontSize=18.sp); ZoomBodyDiagram(area,zone,records,sensor,avatar) { zone=it; saved=false } }; itemsIndexed(area.zones) { index,_ -> ZoneRow(index,displayZoneName(area,index),zone==index,zoneColor(area,index,records),sensorMarkers(records,sensor,Hit(Rect.Zero,area,index)).isNotEmpty()) { zone=index; saved=false } }; if(saved) item { Text(if(mode==EntryMode.INSULINA) "Iniezione salvata." else "Sensore salvato.",color=Color(0xFF166534),fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(bottom=76.dp)) } else item { Spacer(Modifier.height(76.dp)) } } }
}
private fun formatDate(time: Long) = SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN).format(Date(time)); private fun formatTime(time: Long) = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(time))
internal fun displayZoneName(area: BodyArea,index: Int): String {
    val stored=area.zones[index]
    if(area!=BodyArea.ABDOMEN)return stored
    return if(index%4<2)stored.replace("destra","sinistra") else stored.replace("sinistra","destra")
}
@Composable private fun EventDateTimeFields(eventDateTime: Long,onChange: (Long)->Unit) {
    val context=LocalContext.current;var showTimePicker by remember { mutableStateOf(false) }
    fun calendar()=Calendar.getInstance().apply { timeInMillis=eventDateTime }
    fun showDatePicker(){val initial=calendar();DatePickerDialog(context,{_,year,month,day->onChange(calendar().apply{set(Calendar.YEAR,year);set(Calendar.MONTH,month);set(Calendar.DAY_OF_MONTH,day);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis)},initial.get(Calendar.YEAR),initial.get(Calendar.MONTH),initial.get(Calendar.DAY_OF_MONTH)).show()}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Quando è avvenuto l'evento",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedCard(onClick=::showDatePicker,modifier=Modifier.weight(1.35f)){Column(Modifier.padding(horizontal=14.dp,vertical=10.dp)){Text("Data",fontSize=12.sp,color=Color.Gray);Text(formatDate(eventDateTime),fontWeight=FontWeight.SemiBold)}}
        OutlinedCard(onClick={showTimePicker=true},modifier=Modifier.weight(.8f)){Column(Modifier.padding(horizontal=14.dp,vertical=10.dp)){Text("Ora",fontSize=12.sp,color=Color.Gray);Text(formatTime(eventDateTime),fontWeight=FontWeight.SemiBold)}}
    }}
    if(showTimePicker) TimeWheelDialog(eventDateTime,{showTimePicker=false},{selectedHour,selectedMinute->onChange(calendar().apply{set(Calendar.HOUR_OF_DAY,selectedHour);set(Calendar.MINUTE,selectedMinute);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis);showTimePicker=false})
}
@Composable private fun TimeWheelDialog(eventDateTime:Long,onDismiss:()->Unit,onConfirm:(Int,Int)->Unit){
    val initial=remember(eventDateTime){Calendar.getInstance().apply{timeInMillis=eventDateTime}};var selectedHour by remember(eventDateTime){mutableIntStateOf(initial.get(Calendar.HOUR_OF_DAY))};var selectedMinute by remember(eventDateTime){mutableIntStateOf(initial.get(Calendar.MINUTE))}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Seleziona l'ora")},text={Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){Text(String.format(Locale.ITALIAN,"%02d:%02d",selectedHour,selectedMinute),fontSize=28.sp,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){TimeWheel(0,23,selectedHour){selectedHour=it};TimeWheel(0,59,selectedMinute){selectedMinute=it}}}},confirmButton={TextButton(onClick={onConfirm(selectedHour,selectedMinute)}){Text("Conferma")}},dismissButton={TextButton(onClick=onDismiss){Text("Annulla")}})
}
@Composable private fun TimeWheel(min:Int,max:Int,value:Int,onValue:(Int)->Unit)=AndroidView(factory={context->NumberPicker(context).apply{minValue=min;maxValue=max;wrapSelectorWheel=true;descendantFocusability=NumberPicker.FOCUS_BLOCK_DESCENDANTS;setFormatter{String.format(Locale.ITALIAN,"%02d",it)};setOnValueChangedListener{_,_,new->onValue(new)}}},update={it.value=value},modifier=Modifier.width(88.dp).height(150.dp))
@Composable private fun ModePicker(mode: EntryMode,setMode: (EntryMode)->Unit,insulin: InsulinType,setInsulin: (InsulinType)->Unit) = Column(verticalArrangement=Arrangement.spacedBy(6.dp)) { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { EntryMode.entries.forEach { FilterChip(selected=mode==it,onClick={setMode(it)},label={Text(it.label)}) } }; if(mode==EntryMode.INSULINA) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { InsulinType.entries.forEach { FilterChip(selected=insulin==it,onClick={setInsulin(it)},label={Text(it.label)}) } } }
@Composable private fun ZoneRow(index: Int,name: String,selected: Boolean,color: Color,hasSensor: Boolean,onClick: ()->Unit) = Card(Modifier.fillMaxWidth().clickable(onClick=onClick),colors=CardDefaults.cardColors(containerColor=if(selected) color.copy(alpha=.18f) else SurfaceTint)) { Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(30.dp).background(color,CircleShape),contentAlignment=Alignment.Center) { Text("${index+1}",color=Color.White,fontWeight=FontWeight.Bold) }; Spacer(Modifier.width(12.dp)); Text(name,Modifier.weight(1f)); if(hasSensor) { Box(Modifier.size(18.dp).background(SensorLifecycle.DARK,CircleShape)); Spacer(Modifier.width(8.dp)) }; if(selected) Icon(Icons.Default.CheckCircle,null,tint=GREEN) } }
@Composable private fun ZoomBodyDiagram(area: BodyArea,selected: Int?,records: List<RecordItem>,sensor: RecordItem?,avatar: AvatarStyle,onSelect: (Int)->Unit) = Box(Modifier.fillMaxWidth().aspectRatio(300f / 310f).background(SurfaceTint,RoundedCornerShape(20.dp))) {
    Image(painterResource(avatar.zoom(area)), null, Modifier.fillMaxSize(), contentScale=ContentScale.Fit)
    Canvas(Modifier.fillMaxSize().pointerInput(area, avatar) { detectTapGestures { point -> zoomZones(avatar,area,size.width/300f).firstOrNull { it.path.contains(point) }?.let { onSelect(it.zone) } } }) { val s=size.width/300f; zoomZones(avatar,area,s).forEach { zone -> drawZonePath(zone,zoneColor(area,zone.zone,records).let { if(selected==zone.zone) it else it.copy(alpha=.72f) },sensorMarkers(records,sensor,Hit(zone.bounds,area,zone.zone))) } }
}

private data class ZonePath(val path: Path, val bounds: Rect, val zone: Int)
private fun Path.contains(point: Offset): Boolean {
    val bounds = getBounds()
    val clip = android.graphics.Region(
        bounds.left.toInt() - 1,
        bounds.top.toInt() - 1,
        bounds.right.toInt() + 1,
        bounds.bottom.toInt() + 1
    )
    return android.graphics.Region().apply { setPath(asAndroidPath(), clip) }
        .contains(point.x.toInt(), point.y.toInt())
}
private fun DrawScope.drawZonePath(zone: ZonePath, color: Color, sensors: List<Pair<Color, Boolean>>) {
    drawPath(zone.path,color); drawPath(zone.path,Color.White.copy(alpha=.72f),style=Stroke(1.5f))
    val center=zone.bounds.center; drawCircle(Color.White.copy(alpha=.85f),10f,center)
    drawContext.canvas.nativeCanvas.drawText("${zone.zone+1}",center.x-3.5f,center.y+4f,android.graphics.Paint().apply { this.color=android.graphics.Color.DKGRAY;textSize=11f;isFakeBoldText=true })
    sensors.forEachIndexed { index,(sensorColor,active) -> val p=Offset(center.x+index.coerceAtMost(2)*9f-9f,center.y);drawCircle(Color.White.copy(alpha=if(active).95f else .55f),if(active)15f else 11f,p);drawCircle(sensorColor.copy(alpha=if(active)1f else .38f),if(active)12f else 8f,p);if(active)drawCircle(Color.White.copy(alpha=.45f),4f,Offset(p.x-3f,p.y-3f)) }
}
private fun zoomZones(avatar: AvatarStyle,area: BodyArea,s: Float): List<ZonePath> {
    val points=zoomOutline(avatar,area);val outline=Path().apply { moveTo(points[0].x*s,points[0].y*s);points.drop(1).forEach { lineTo(it.x*s,it.y*s) };close() };val b=outline.getBounds();val count=area.zones.size
    return List(count) { index ->
        val columnCount=if(count==8)4 else if(count==4)2 else 1;val rowCount=if(count==8)2 else if(count==4)2 else 2
        var column=index%columnCount
        if((area==BodyArea.LEFT_ARM||area==BodyArea.LEFT_THIGH)&&columnCount==2)column=1-column
        val row=index/columnCount;val cell=Path().apply { addRect(Rect(b.left+b.width*column/columnCount,b.top+b.height*row/rowCount,b.left+b.width*(column+1)/columnCount,b.top+b.height*(row+1)/rowCount)) }
        val path=Path.combine(PathOperation.Intersect,outline,cell);ZonePath(path,path.getBounds(),index)
    }
}
private fun zoomOutline(avatar: AvatarStyle,area: BodyArea): List<Offset> {
    fun p(vararg xy: Int)=xy.toList().chunked(2).map { Offset(it[0].toFloat(),it[1].toFloat()) }
    return when(area) {
        BodyArea.ABDOMEN -> when(avatar){ AvatarStyle.UOMO->p(48,48,252,48,246,95,230,155,216,205,84,205,70,155,54,95);AvatarStyle.DONNA->p(72,70,228,70,220,112,211,162,220,210,239,247,61,247,80,210,89,162,80,112);AvatarStyle.YETI->p(91,47,209,47,226,90,232,155,225,238,205,273,95,273,75,238,68,155,74,90) }
        BodyArea.LEFT_ARM,BodyArea.RIGHT_ARM -> armOutline(avatar,area==BodyArea.LEFT_ARM)
        BodyArea.LEFT_THIGH,BodyArea.RIGHT_THIGH -> thighOutline(avatar,area==BodyArea.LEFT_THIGH)
        BodyArea.LEFT_GLUTE,BodyArea.RIGHT_GLUTE -> gluteOutline(avatar,area==BodyArea.LEFT_GLUTE)
    }
}
private fun armOutline(a:AvatarStyle,left:Boolean):List<Offset>{
    fun p(vararg v:Int)=v.toList().chunked(2).map{Offset(it[0].toFloat(),it[1].toFloat())}
    return when(a){AvatarStyle.UOMO->if(left)p(128,38,158,38,169,70,166,122,160,183,158,238,151,252,137,246,132,190,126,125,119,72) else p(142,38,172,38,181,72,174,125,168,190,163,246,149,252,142,238,140,183,134,122,131,70);AvatarStyle.DONNA->if(left)p(130,40,158,40,166,70,163,125,160,185,157,247,145,255,135,242,132,185,127,125,122,70) else p(142,40,170,40,178,70,173,125,168,185,165,242,155,255,143,247,140,185,137,125,134,70);AvatarStyle.YETI->if(left)p(116,35,159,29,178,50,180,100,172,158,169,220,162,245,137,245,130,220,125,158,112,100,105,52) else p(141,29,184,35,195,52,188,100,175,158,170,220,163,245,138,245,131,220,128,158,120,100,122,50)}
}
private fun thighOutline(a:AvatarStyle,left:Boolean):List<Offset>{
    fun p(vararg v:Int)=v.toList().chunked(2).map{Offset(it[0].toFloat(),it[1].toFloat())}
    return when(a){AvatarStyle.UOMO->if(left)p(91,105,183,105,193,125,190,150,180,198,169,233,139,235,119,215,107,175,99,125) else p(117,105,209,105,201,125,193,175,181,215,161,235,131,233,120,198,110,150,107,125);AvatarStyle.DONNA->if(left)p(111,76,175,76,190,90,192,124,183,178,170,230,140,240,121,220,111,174,106,115) else p(125,76,189,76,194,115,189,174,179,220,160,240,130,230,117,178,108,124,110,90);AvatarStyle.YETI->if(left)p(101,34,183,34,201,74,204,128,194,180,178,224,143,230,119,216,104,174,96,116) else p(117,34,199,34,204,116,196,174,181,216,157,230,122,224,106,180,96,128,99,74)}
}
private fun gluteOutline(a:AvatarStyle,left:Boolean):List<Offset>{
    fun p(vararg v:Int)=v.toList().chunked(2).map{Offset(it[0].toFloat(),it[1].toFloat())}
    return when(a){AvatarStyle.UOMO->if(left)p(99,105,171,105,178,135,176,176,163,205,126,207,104,188,96,150) else p(129,105,201,105,204,150,196,188,174,207,137,205,124,176,122,135);AvatarStyle.DONNA->if(left)p(98,104,172,104,181,136,177,180,162,211,123,210,101,188,94,148) else p(128,104,202,104,206,148,199,188,177,210,138,211,123,180,119,136);AvatarStyle.YETI->if(left)p(105,86,175,86,181,125,178,170,164,209,128,212,107,190,98,145) else p(125,86,195,86,202,145,193,190,172,212,136,209,122,170,119,125)}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HistoryScreen(records: List<RecordItem>, onDelete: (RecordItem)->Unit, onBack: ()->Unit) { val formatter=remember { SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.ITALIAN) }; var deleting by remember { mutableStateOf<RecordItem?>(null) }; deleting?.let { record -> AlertDialog(onDismissRequest={deleting=null},title={Text("Eliminare registrazione?")},text={Text("Questa operazione non può essere annullata.")},confirmButton={TextButton(onClick={onDelete(record);deleting=null}){Text("Elimina")}},dismissButton={TextButton(onClick={deleting=null}){Text("Annulla")}}) }; Scaffold(topBar={ TopAppBar(title={Text("Storico")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Indietro")}}) }) { padding -> if(records.isEmpty()) Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center){Text("Nessuna registrazione",color=Color.Gray)} else LazyColumn(Modifier.padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){itemsIndexed(records){_,record->Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=when { record.mode == EntryMode.SENSORE -> Color(0xFFE5E7EB); record.insulinType == InsulinType.RAPIDA -> Color(0xFFE5F5E9); else -> Color(0xFFF0E6FA) })){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(record.area.label,fontWeight=FontWeight.Bold);Text("${displayZoneName(record.area,record.zone)} · ${record.mode.label}");record.insulinType?.let{Text(it.label,color=it.color,fontWeight=FontWeight.SemiBold)};Text(formatter.format(Date(record.eventDateTime)),fontSize=12.sp,color=Color.Gray)};IconButton(onClick={deleting=record}){Icon(Icons.Default.Delete,"Elimina registrazione",tint=MaterialTheme.colorScheme.error)}}}}} } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SettingsScreen(avatar: AvatarStyle, settings: TimingSettings, onAvatar: (AvatarStyle)->Unit, onSettings: (TimingSettings)->Boolean, backupState: ()->AppBackupState, onImported: (AppBackupState)->Unit, onBack: () -> Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var red by remember { mutableStateOf(settings.redHours.toString()) }; var orange by remember { mutableStateOf(settings.orangeHours.toString()) }; var yellow by remember { mutableStateOf(settings.yellowHours.toString()) }
    var stage by remember { mutableStateOf(settings.sensorStageDays.toString()) }; var hidden by remember { mutableStateOf(settings.sensorHiddenDays.toString()) }; var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) };var saved by remember { mutableStateOf(false) }
    LaunchedEffect(saved){if(saved){delay(2_500);saved=false}}
    fun save(){
        val candidate=TimingSettings(red.toFloatOrNull() ?: -1f,orange.toFloatOrNull() ?: -1f,yellow.toFloatOrNull() ?: -1f,stage.toLongOrNull() ?: -1,hidden.toLongOrNull() ?: -1)
        if(!candidate.valid()){saved=false;error="Impossibile salvare: le soglie devono essere crescenti e il sensore deve avere tre stadi prima della scomparsa.";return}
        saving=true;saved=false;error=null
        scope.launch{
            val persisted=runCatching{onSettings(candidate)}.getOrDefault(false)
            delay(150)
            val reloaded=runCatching{loadSettings(context)}.getOrNull()
            if(persisted&&reloaded==candidate){saved=true;error=null}else{saved=false;error="Impossibile salvare le impostazioni. Riprova."}
            saving=false
        }
    }
    Scaffold(
        topBar={TopAppBar(title={Text("Impostazioni")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Indietro")}})},
        bottomBar={Surface(shadowElevation=8.dp){Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){
            if(saved){Text("Impostazioni salvate",color=Color(0xFF166534),fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(8.dp))}
            Button(onClick=::save,enabled=!saving,modifier=Modifier.fillMaxWidth(),colors=if(saved)ButtonDefaults.buttonColors(containerColor=GREEN)else ButtonDefaults.buttonColors()){
                Icon(if(saved)Icons.Default.Check else Icons.Default.Save,null);Spacer(Modifier.width(8.dp));Text(when{saving->"Salvataggio…";saved->"Salvato";else->"Salva impostazioni"})
            }
        }}},
    ){padding->LazyColumn(Modifier.padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item { Text("Avatar",fontWeight=FontWeight.Bold,fontSize=20.sp); Text("La scelta cambia solo la grafica, non lo storico.",color=Color.Gray,fontSize=13.sp) }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { AvatarStyle.entries.forEach { style -> FilterChip(selected=avatar==style,onClick={onAvatar(style)},label={Text(style.label)},modifier=Modifier.weight(1f)) } } }
        item { Text("Tempi colori iniezione (ore)",fontWeight=FontWeight.Bold,fontSize=20.sp); Text("Rosso fino a, arancione fino a, giallo fino a; poi verde.",fontSize=13.sp,color=Color.Gray) }
        item { TimingField("Rosso",red){red=it;saved=false;error=null}; TimingField("Arancione",orange){orange=it;saved=false;error=null}; TimingField("Giallo",yellow){yellow=it;saved=false;error=null} }
        item { Text("Tempi sensore (giorni)",fontWeight=FontWeight.Bold,fontSize=20.sp); Text("Ogni stadio dura il valore indicato; il sensore sparisce al giorno impostato.",fontSize=13.sp,color=Color.Gray) }
        item { TimingField("Durata stadio",stage){stage=it;saved=false;error=null}; TimingField("Scomparsa",hidden){hidden=it;saved=false;error=null} }
        error?.let { item { Text(it,color=MaterialTheme.colorScheme.error) } }
        item { BackupSection(backupState){imported->red=imported.settings.redHours.toString();orange=imported.settings.orangeHours.toString();yellow=imported.settings.yellowHours.toString();stage=imported.settings.sensorStageDays.toString();hidden=imported.settings.sensorHiddenDays.toString();saved=false;error=null;onImported(imported)} }
        item { OutlinedButton(onClick={ red="12.0";orange="24.0";yellow="36.0";stage="10";hidden="40";saved=false;if(onSettings(DefaultSettings))error=null else error="Impossibile ripristinare le impostazioni predefinite." },Modifier.fillMaxWidth(),enabled=!saving){Icon(Icons.Default.Restore,null);Spacer(Modifier.width(8.dp));Text("Ripristina valori predefiniti")} }
        item { Spacer(Modifier.height(72.dp)) }
    }}
}
@Composable private fun TimingField(label: String, value: String, onValue: (String)->Unit) = OutlinedTextField(value,onValue, label={Text(label)},singleLine=true, modifier=Modifier.fillMaxWidth())

private data class PendingExport(val state: AppBackupState, val password: CharArray)
private data class PendingImport(val backup: DecodedBackup, val password: CharArray)
private enum class ImportChoice { MERGE, REPLACE }

@Composable private fun BackupSection(currentState: ()->AppBackupState, onImported: (AppBackupState)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) };var message by remember { mutableStateOf<String?>(null) };var messageIsError by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) };var importPasswordDialog by remember { mutableStateOf(false) };var exportPassword by remember { mutableStateOf("") };var exportConfirmation by remember { mutableStateOf("") };var importPassword by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) };var importUri by remember { mutableStateOf<Uri?>(null) };var pendingImport by remember { mutableStateOf<PendingImport?>(null) };var importChoice by remember { mutableStateOf(ImportChoice.MERGE) };var replaceConfirmation by remember { mutableStateOf(false) }
    fun finishImport(choice:ImportChoice){
        val pending=pendingImport?:return;val before=currentState();busy=true;message=null
        scope.launch{
            val result=withContext(Dispatchers.IO){runCatching{BackupManager.applyImport(context,before,pending.backup,choice==ImportChoice.REPLACE,pending.password)}}
            pending.password.fill('\u0000');pendingImport=null;replaceConfirmation=false;busy=false
            result.onSuccess{outcome->onImported(outcome.state);message="Backup importato: ${outcome.importedCount} elementi importati";messageIsError=false}.onFailure{message=it.message?:"Impossibile importare il backup.";messageIsError=true}
        }
    }
    val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        val pending=pendingExport;pendingExport=null
        if(uri==null){pending?.password?.fill('\u0000')}else if(pending!=null){busy=true;message=null;scope.launch{val result=withContext(Dispatchers.IO){runCatching{context.contentResolver.openOutputStream(uri,"w")?.use{BackupManager.write(context,it,pending.state,pending.password)}?:error("Impossibile aprire il file selezionato.")}};pending.password.fill('\u0000');busy=false;result.onSuccess{message="Backup esportato";messageIsError=false}.onFailure{message=it.message?:"Impossibile esportare il backup.";messageIsError=true}}}
    }
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};importUri=uri;importPassword="";importPasswordDialog=true}}
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        HorizontalDivider();Text("Backup dei dati",fontWeight=FontWeight.Bold,fontSize=20.sp);Text("Il file protetto consente di trasferire storico, sensori e impostazioni su un altro dispositivo.",fontSize=13.sp,color=Color.Gray)
        Button(onClick={exportPassword="";exportConfirmation="";exportDialog=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.UploadFile,null);Spacer(Modifier.width(8.dp));Text("Esporta backup")}
        OutlinedButton(onClick={importLauncher.launch(arrayOf("application/octet-stream","application/json"))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Download,null);Spacer(Modifier.width(8.dp));Text("Importa backup")}
        if(busy){Text("Operazione in corso…",color=Blue,fontWeight=FontWeight.SemiBold)}
        message?.let{Text(it,color=if(messageIsError)MaterialTheme.colorScheme.error else Color(0xFF166534),fontWeight=FontWeight.SemiBold)}
    }
    if(exportDialog) PasswordDialog("Proteggi il backup","Scegli una password di almeno 8 caratteri.",exportPassword,{exportPassword=it},exportConfirmation,{exportConfirmation=it},{exportDialog=false;exportPassword="";exportConfirmation=""}){
        when{exportPassword.length<8->{message="La password del backup deve contenere almeno 8 caratteri.";messageIsError=true};exportPassword!=exportConfirmation->{message="Le password del backup non coincidono.";messageIsError=true};else->{pendingExport=PendingExport(currentState(),exportPassword.toCharArray());exportPassword="";exportConfirmation="";exportDialog=false;exportLauncher.launch("InSofina-backup-${SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())}.insofia-backup")}}
    }
    if(importPasswordDialog) PasswordDialog("Apri il backup","Inserisci la password usata per proteggere il file.",importPassword,{importPassword=it},null,null,{importPasswordDialog=false;importPassword="";importUri=null}){
        val uri=importUri?:return@PasswordDialog;if(importPassword.isEmpty()){message="Inserisci la password del backup.";messageIsError=true}else{val password=importPassword.toCharArray();importPassword="";importPasswordDialog=false;busy=true;scope.launch{val result=withContext(Dispatchers.IO){runCatching{context.contentResolver.openInputStream(uri)?.use{BackupManager.read(it,password)}?:error("Impossibile aprire il file selezionato.")}};busy=false;result.onSuccess{decoded->pendingImport=PendingImport(decoded,password);importChoice=ImportChoice.MERGE}.onFailure{password.fill('\u0000');message=it.message?:"Backup corrotto, incompatibile o password errata.";messageIsError=true};importUri=null}}
    }
    pendingImport?.let{pending->AlertDialog(onDismissRequest={pending.password.fill('\u0000');pendingImport=null},title={Text("Anteprima backup")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(Date(pending.backup.exportedAt))}");Text("Storico: ${pending.backup.state.records.size} elementi");Text("Sensori: ${pending.backup.state.records.count{it.mode==EntryMode.SENSORE}}");Row(verticalAlignment=Alignment.CenterVertically){RadioButton(importChoice==ImportChoice.MERGE,{importChoice=ImportChoice.MERGE});Text("Unisci con i dati esistenti")};Row(verticalAlignment=Alignment.CenterVertically){RadioButton(importChoice==ImportChoice.REPLACE,{importChoice=ImportChoice.REPLACE});Text("Sostituisci tutti i dati")}}},confirmButton={TextButton(onClick={if(importChoice==ImportChoice.REPLACE)replaceConfirmation=true else finishImport(ImportChoice.MERGE)}){Text("Importa")}},dismissButton={TextButton(onClick={pending.password.fill('\u0000');pendingImport=null}){Text("Annulla")}})}
    if(replaceConfirmation) AlertDialog(onDismissRequest={replaceConfirmation=false},title={Text("Sostituire tutti i dati?")},text={Text("Storico e impostazioni locali saranno sostituiti. Prima dell'operazione verrà creato automaticamente un backup di sicurezza privato.")},confirmButton={TextButton(onClick={finishImport(ImportChoice.REPLACE)}){Text("Sostituisci")}},dismissButton={TextButton(onClick={replaceConfirmation=false}){Text("Annulla")}})
}

@Composable private fun PasswordDialog(title:String,description:String,password:String,onPassword:(String)->Unit,confirmation:String?,onConfirmation:((String)->Unit)?,onDismiss:()->Unit,onConfirm:()->Unit)=AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text(description);OutlinedTextField(password,onPassword,label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation());if(confirmation!=null&&onConfirmation!=null)OutlinedTextField(confirmation,onConfirmation,label={Text("Conferma password")},singleLine=true,visualTransformation=PasswordVisualTransformation())}},confirmButton={TextButton(onClick=onConfirm){Text("Continua")}},dismissButton={TextButton(onClick=onDismiss){Text("Annulla")}})
