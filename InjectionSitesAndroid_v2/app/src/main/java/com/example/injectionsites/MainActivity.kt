package com.example.injectionsites

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private val Blue = Color(0xFF1557C0)
private val Skin = Color(0xFFF1C39D)
private val SurfaceTint = Color(0xFFF6F8FC)

/** Persisted, centralised timing settings. Injection and sensor cycles stay independent. */
private data class TimingSettings(val redHours: Float = 12f, val orangeHours: Float = 24f, val yellowHours: Float = 36f, val sensorStageDays: Long = 10, val sensorHiddenDays: Long = 40) {
    fun valid() = redHours > 0 && redHours < orangeHours && orangeHours < yellowHours && sensorStageDays > 0 && sensorStageDays * 3 < sensorHiddenDays
}
private val DefaultSettings = TimingSettings()
private var currentSettings = DefaultSettings
private const val SETTINGS_KEY = "timing_settings"; private const val AVATAR_KEY = "avatar_style"
private fun loadSettings(context: Context) = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).let { p -> TimingSettings(p.getFloat("red",12f),p.getFloat("orange",24f),p.getFloat("yellow",36f),p.getLong("sensorStage",10),p.getLong("sensorHidden",40)).takeIf { it.valid() } ?: DefaultSettings }
private fun saveSettings(context: Context, value: TimingSettings) { context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).edit().putFloat("red",value.redHours).putFloat("orange",value.orangeHours).putFloat("yellow",value.yellowHours).putLong("sensorStage",value.sensorStageDays).putLong("sensorHidden",value.sensorHiddenDays).apply() }
private val RED = Color(0xFFDC2626); private val ORANGE = Color(0xFFF97316); private val YELLOW = Color(0xFFEAB308); private val GREEN = Color(0xFF16A34A)
private object SensorLifecycle {
    val DARK = Color(0xFF475569); val MEDIUM = Color(0xFF7C8796); val LIGHT = Color(0xFFB8C0CB); val FADED = Color(0xFFDEE3EA)
    fun colorAt(time: Long, now: Long = System.currentTimeMillis()): Color? { val days=((now-time).coerceAtLeast(0L))/86_400_000L; return when { days < currentSettings.sensorStageDays -> DARK; days < currentSettings.sensorStageDays * 2 -> MEDIUM; days < currentSettings.sensorStageDays * 3 -> LIGHT; days < currentSettings.sensorHiddenDays -> FADED; else -> null } }
}
class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Blue)) { InjectionApp() } } } }
enum class EntryMode(val label: String) { INSULINA("Insulina"), SENSORE("Sensore") }
enum class InsulinType(val label: String, val color: Color) { RAPIDA("Rapida", Color(0xFFF97316)), BASALE("Basale", Blue) }
/** New styles can be added without moving the anatomical zone geometry. */
enum class AvatarStyle(val label: String, val front: Int, val back: Int) { UOMO("Uomo", R.drawable.avatar_man_front, R.drawable.avatar_man_back), DONNA("Donna", R.drawable.avatar_woman_front, R.drawable.avatar_woman_back), YETI("Yeti", R.drawable.avatar_yeti_front, R.drawable.avatar_yeti_back) }
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
    /** Time of the event selected by the user. */
    val time: Long = System.currentTimeMillis(),
    /** Keeps the most recently entered sensor active even when it is backdated. */
    val createdAt: Long = System.currentTimeMillis()
)

private const val STORAGE = "injection_sites"; private const val HISTORY_KEY = "injection_history"; private const val LEGACY_SENSOR_KEY = "sensor_position"
private fun RecordItem.toJson() = JSONObject().apply { put("area", area.name); put("zone", zone); put("mode", mode.name); put("insulinType", insulinType?.name); put("time", time); put("createdAt", createdAt) }
private fun recordFromJson(json: JSONObject): RecordItem? = runCatching { val time = json.getLong("time"); RecordItem(BodyArea.valueOf(json.getString("area")), json.getInt("zone"), EntryMode.valueOf(json.getString("mode")), json.optString("insulinType").takeIf { it.isNotBlank() && it != "null" }?.let(InsulinType::valueOf), time, json.optLong("createdAt", time)) }.getOrNull()
private fun loadRecords(context: Context): List<RecordItem> = runCatching {
    val preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)
    val records = JSONArray(preferences.getString(HISTORY_KEY, "[]")).let { data -> (0 until data.length()).mapNotNull { recordFromJson(data.getJSONObject(it)) } }.toMutableList()
    preferences.getString(LEGACY_SENSOR_KEY, null)?.let { raw -> recordFromJson(JSONObject(raw))?.takeIf { legacy -> records.none { it.mode == EntryMode.SENSORE && it.time == legacy.time } }?.let(records::add) }
    records.sortedByDescending { it.time }
}.getOrDefault(emptyList())
private fun saveRecords(context: Context, records: List<RecordItem>) { val data = JSONArray(); records.forEach { data.put(it.toJson()) }; context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).edit().putString(HISTORY_KEY, data.toString()).remove(LEGACY_SENSOR_KEY).apply() }
private fun activeSensor(records: List<RecordItem>): RecordItem? = records.filter { it.mode == EntryMode.SENSORE }.maxByOrNull { it.createdAt }

@Composable fun InjectionApp() {
    val context = LocalContext.current; var screen by remember { mutableStateOf("home") }; var area by remember { mutableStateOf<BodyArea?>(null) }
    var avatar by remember { mutableStateOf(context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).getString(AVATAR_KEY, AvatarStyle.YETI.name)?.let { runCatching { AvatarStyle.valueOf(it) }.getOrNull() } ?: AvatarStyle.YETI) }
    var settings by remember { mutableStateOf(loadSettings(context)) }; currentSettings = settings
    val records = remember { mutableStateListOf<RecordItem>().also { it.addAll(loadRecords(context)) } }
    val refreshTick by produceState(initialValue = 0) { while (true) { delay(60_000); value++ } }
    fun persist() = saveRecords(context, records)
    when (screen) {
        "home" -> key(refreshTick, avatar, settings) { HomeScreen(records, activeSensor(records), avatar, { area = it; screen = "area" }, { screen = "history" }, { screen = "settings" }) }
        "area" -> area?.let { selected -> AreaScreen(selected, records, activeSensor(records), avatar, { record -> records.add(0, record); persist() }) { screen = "home" } }
        "settings" -> SettingsScreen(avatar, settings, { avatar = it; context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).edit().putString(AVATAR_KEY, it.name).apply() }, { settings = it; saveSettings(context,it) }) { screen = "home" }
        else -> HistoryScreen(records, { record -> records.remove(record); persist() }) { screen = "home" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HomeScreen(records: List<RecordItem>, sensor: RecordItem?, avatar: AvatarStyle, onArea: (BodyArea) -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) = Scaffold(topBar = { TopAppBar(title = { Column { Text("Nuova iniezione", fontWeight = FontWeight.Bold); Text("Seleziona una zona sulla sagoma", fontSize = 12.sp, color = Color.Gray) } }, actions = { IconButton(onClick = onHistory) { Icon(Icons.Default.History, "Storico") }; IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Impostazioni") } }) }) { padding -> LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { BodyMap(records, sensor, avatar, onArea) }; item { AvailabilityLegend() }; item { Text("Tutte le aree", fontWeight = FontWeight.Bold, fontSize = 18.sp) }; itemsIndexed(BodyArea.entries) { _, bodyArea -> AreaCard(bodyArea) { onArea(bodyArea) } }; item { Spacer(Modifier.height(20.dp)) } } }
@Composable private fun AvailabilityLegend() = Card(colors = CardDefaults.cardColors(containerColor = SurfaceTint)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) { Text("Disponibilità:", fontWeight = FontWeight.Bold, fontSize = 13.sp); LegendDot(RED, "< ${currentSettings.redHours} h"); LegendDot(ORANGE, "${currentSettings.redHours}–${currentSettings.orangeHours} h"); LegendDot(YELLOW, "${currentSettings.orangeHours}–${currentSettings.yellowHours} h"); LegendDot(GREEN, "> ${currentSettings.yellowHours} h") } }
@Composable private fun LegendDot(color: Color, text: String) = Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).background(color, CircleShape)); Spacer(Modifier.width(3.dp)); Text(text, fontSize = 11.sp) }
private fun zoneColor(area: BodyArea, index: Int, records: List<RecordItem>): Color { val last = records.filter { it.mode == EntryMode.INSULINA && it.area == area && it.zone == index }.maxByOrNull { it.time } ?: return GREEN; val hours = (System.currentTimeMillis() - last.time).coerceAtLeast(0) / 3_600_000f; return when { hours < currentSettings.redHours -> RED; hours < currentSettings.orangeHours -> ORANGE; hours < currentSettings.yellowHours -> YELLOW; else -> GREEN } }
@Composable private fun AreaCard(area: BodyArea, onClick: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = area.color.copy(alpha = .08f))) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(14.dp).background(area.color, CircleShape)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(area.label, fontWeight = FontWeight.Bold); Text("${area.zones.size} zone selezionabili", fontSize = 12.sp, color = Color.Gray) }; Icon(Icons.Default.ChevronRight, null, tint = Blue) } }
@Composable private fun BodyMap(records: List<RecordItem>, sensor: RecordItem?, avatar: AvatarStyle, onArea: (BodyArea) -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = SurfaceTint)) { Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Vista a specchio", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Tocca un gruppo di zone per ingrandirlo", fontSize = 12.sp, color = Color.Gray); FrontBodyCanvas(records, sensor, avatar, onArea); HorizontalDivider(Modifier.padding(vertical = 4.dp)); Text("Vista posteriore · glutei", fontWeight = FontWeight.SemiBold, fontSize = 13.sp); GluteCanvas(records, sensor, avatar, onArea) } }

private data class Hit(val rect: Rect, val area: BodyArea, val zone: Int)
private fun DrawScope.zoneBox(rect: Rect, color: Color, number: Int, sensorColor: Color?) { drawRoundRect(color, rect.topLeft, rect.size, CornerRadius(9f, 9f)); drawRoundRect(Color.White.copy(alpha = .72f), rect.topLeft, rect.size, CornerRadius(9f, 9f), Stroke(1.5f)); drawCircle(Color.White.copy(alpha = .85f), 10f, rect.center); drawContext.canvas.nativeCanvas.drawText("${number + 1}", rect.center.x - 3.5f, rect.center.y + 4f, android.graphics.Paint().apply { this.color = android.graphics.Color.DKGRAY; textSize = 11f; isFakeBoldText = true }); sensorColor?.let { drawCircle(Color.White.copy(alpha = .88f), 15f, rect.center); drawCircle(it, 12f, rect.center); drawCircle(Color.White.copy(alpha = .35f), 5f, Offset(rect.center.x - 3f, rect.center.y - 3f)) } }
private fun sensorColor(sensor: RecordItem?, hit: Hit) = sensor?.takeIf { it.area == hit.area && it.zone == hit.zone }?.let { SensorLifecycle.colorAt(it.time) }
@Composable private fun FrontBodyCanvas(records: List<RecordItem>, sensor: RecordItem?, avatar: AvatarStyle, onArea: (BodyArea) -> Unit) = Box(Modifier.fillMaxWidth().height(500.dp)) {
    Image(painterResource(avatar.front), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    Canvas(Modifier.fillMaxSize().pointerInput(avatar) { detectTapGestures { point -> frontHits(size.width / 300f).firstOrNull { it.rect.contains(point) }?.let { onArea(it.area) } } }) { val scale = size.width / 300f; frontHits(scale).forEach { zoneBox(it.rect, zoneColor(it.area, it.zone, records).copy(alpha=.72f), it.zone, sensorColor(sensor, it)) } }
}
private fun frontHits(s: Float): List<Hit> {
    fun rect(left: Float, top: Float, right: Float, bottom: Float) = Rect(left * s, top * s, right * s, bottom * s)
    val result = mutableListOf<Hit>()
    fun add(area: BodyArea, x: Float, y: Float, width: Float, height: Float, columns: Int, rows: Int) {
        repeat(rows) { row ->
            repeat(columns) { column ->
                result += Hit(rect(x + column * width / columns, y + row * height / rows, x + (column + 1) * width / columns, y + (row + 1) * height / rows), area, row * columns + column)
            }
        }
    }
    add(BodyArea.LEFT_ARM, 47f, 90f, 30f, 130f, 2, 2); add(BodyArea.RIGHT_ARM, 223f, 90f, 30f, 130f, 2, 2)
    add(BodyArea.ABDOMEN, 103f, 132f, 94f, 96f, 4, 2); add(BodyArea.LEFT_THIGH, 111f, 244f, 34f, 135f, 2, 2); add(BodyArea.RIGHT_THIGH, 155f, 244f, 34f, 135f, 2, 2)
    return result
}
@Composable private fun GluteCanvas(records: List<RecordItem>, sensor: RecordItem?, avatar: AvatarStyle, onArea: (BodyArea) -> Unit) = Box(Modifier.fillMaxWidth().height(300.dp)) {
    Image(painterResource(avatar.back), null, Modifier.fillMaxSize(), contentScale=ContentScale.FillBounds)
    Canvas(Modifier.fillMaxSize().pointerInput(avatar) { detectTapGestures { point -> gluteHits(size.width/300f).firstOrNull { it.rect.contains(point) }?.let { onArea(it.area) } } }) { val s=size.width/300f; gluteHits(s).forEach { zoneBox(it.rect,zoneColor(it.area,it.zone,records).copy(alpha=.72f),it.zone,sensorColor(sensor,it)) } }
}
private fun gluteHits(s: Float): List<Hit> {
    fun rect(left: Float, top: Float, right: Float, bottom: Float) = Rect(left * s, top * s, right * s, bottom * s)
    return listOf(
        Hit(rect(91f, 18f, 148f, 60f), BodyArea.LEFT_GLUTE, 0), Hit(rect(152f, 18f, 209f, 60f), BodyArea.RIGHT_GLUTE, 0),
        Hit(rect(91f, 60f, 148f, 102f), BodyArea.LEFT_GLUTE, 1), Hit(rect(152f, 60f, 209f, 102f), BodyArea.RIGHT_GLUTE, 1)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AreaScreen(area: BodyArea, records: List<RecordItem>, sensor: RecordItem?, avatar: AvatarStyle, onSaved: (RecordItem) -> Unit, onBack: () -> Unit) {
    var mode by remember { mutableStateOf(EntryMode.INSULINA) }; var insulinType by remember { mutableStateOf(InsulinType.RAPIDA) }; var zone by remember { mutableStateOf<Int?>(null) }; var saved by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(formatDate(System.currentTimeMillis())) }; var hour by remember { mutableStateOf(formatTime(System.currentTimeMillis())) }; var error by remember { mutableStateOf<String?>(null) }; var pending by remember { mutableStateOf<RecordItem?>(null) }
    fun prepareSave() { val selected=zone ?: return; val eventTime=parseDateTime(date,hour); if(eventTime==null) { error="Inserisci data (GG/MM/AAAA) e ora (HH:mm) valide."; return }; val item=RecordItem(area,selected,mode,if(mode==EntryMode.INSULINA) insulinType else null,eventTime); if(eventTime > System.currentTimeMillis()) pending=item else { onSaved(item); saved=true; error=null } }
    if(pending != null) AlertDialog(onDismissRequest={pending=null},title={Text("Data futura")},text={Text("La data e l'ora selezionate sono nel futuro. Vuoi salvare comunque?")},confirmButton={TextButton(onClick={pending?.let(onSaved); pending=null; saved=true; error=null}){Text("Salva")}},dismissButton={TextButton(onClick={pending=null}){Text("Annulla")}})
    Scaffold(topBar = { TopAppBar(title = { Text(area.label, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack,"Indietro") } }) }, bottomBar = { Surface(shadowElevation = 8.dp) { Button(onClick = ::prepareSave, enabled = zone != null, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Icon(Icons.Default.Save,null); Spacer(Modifier.width(8.dp)); Text("Salva posizione") } } }) { padding -> LazyColumn(Modifier.padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { item { Text("Zoom dedicato · ${area.zones.size} zone",color=Blue,fontWeight=FontWeight.SemiBold); ModePicker(mode,{mode=it},insulinType,{insulinType=it}); EventDateTimeFields(date,{date=it},hour,{hour=it}); error?.let { Text(it,color=MaterialTheme.colorScheme.error) } }; item { Text("Tocca una zona",fontWeight=FontWeight.Bold,fontSize=18.sp); ZoomBodyDiagram(area,zone,records,sensor,avatar) { zone=it; saved=false } }; itemsIndexed(area.zones) { index,name -> ZoneRow(index,name,zone==index,zoneColor(area,index,records),sensorColor(sensor,Hit(Rect.Zero,area,index)) != null) { zone=index; saved=false } }; if(saved) item { Text(if(mode==EntryMode.INSULINA) "Iniezione salvata." else "Sensore salvato.",color=Color(0xFF166534),fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(bottom=76.dp)) } else item { Spacer(Modifier.height(76.dp)) } } }
}
private fun formatDate(time: Long) = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(time)); private fun formatTime(time: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
private fun parseDateTime(date: String, hour: String): Long? = runCatching { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply { isLenient=false }.parse("$date $hour")?.time }.getOrNull()
@Composable private fun EventDateTimeFields(date: String,onDate: (String)->Unit,hour: String,onHour: (String)->Unit) = Column { Text("Quando è avvenuto l'evento",fontWeight=FontWeight.Bold); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(date,onDate,label={Text("Data")},placeholder={Text("GG/MM/AAAA")},singleLine=true,modifier=Modifier.weight(1.35f)); OutlinedTextField(hour,onHour,label={Text("Ora")},placeholder={Text("HH:mm")},singleLine=true,modifier=Modifier.weight(.8f)) } }
@Composable private fun ModePicker(mode: EntryMode,setMode: (EntryMode)->Unit,insulin: InsulinType,setInsulin: (InsulinType)->Unit) = Column(verticalArrangement=Arrangement.spacedBy(6.dp)) { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { EntryMode.entries.forEach { FilterChip(selected=mode==it,onClick={setMode(it)},label={Text(it.label)}) } }; if(mode==EntryMode.INSULINA) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { InsulinType.entries.forEach { FilterChip(selected=insulin==it,onClick={setInsulin(it)},label={Text(it.label)}) } } }
@Composable private fun ZoneRow(index: Int,name: String,selected: Boolean,color: Color,hasSensor: Boolean,onClick: ()->Unit) = Card(Modifier.fillMaxWidth().clickable(onClick=onClick),colors=CardDefaults.cardColors(containerColor=if(selected) color.copy(alpha=.18f) else SurfaceTint)) { Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(30.dp).background(color,CircleShape),contentAlignment=Alignment.Center) { Text("${index+1}",color=Color.White,fontWeight=FontWeight.Bold) }; Spacer(Modifier.width(12.dp)); Text(name,Modifier.weight(1f)); if(hasSensor) { Box(Modifier.size(18.dp).background(SensorLifecycle.DARK,CircleShape)); Spacer(Modifier.width(8.dp)) }; if(selected) Icon(Icons.Default.CheckCircle,null,tint=GREEN) } }
@Composable private fun ZoomBodyDiagram(area: BodyArea,selected: Int?,records: List<RecordItem>,sensor: RecordItem?,avatar: AvatarStyle,onSelect: (Int)->Unit) = Box(Modifier.fillMaxWidth().height(310.dp).background(SurfaceTint,RoundedCornerShape(20.dp))) {
    Image(painterResource(if (area == BodyArea.RIGHT_GLUTE || area == BodyArea.LEFT_GLUTE) avatar.back else avatar.front), null, Modifier.fillMaxSize(), contentScale=ContentScale.Crop)
    Canvas(Modifier.fillMaxSize().pointerInput(area, avatar) { detectTapGestures { point -> zoomHits(area,size.width/300f).firstOrNull { it.rect.contains(point) }?.let { onSelect(it.zone) } } }) { val s=size.width/300f; zoomHits(area,s).forEach { hit -> zoneBox(hit.rect,zoneColor(area,hit.zone,records).let { if(selected==hit.zone) it else it.copy(alpha=.72f) },hit.zone,sensorColor(sensor,hit)) } }
}
private fun DrawScope.drawZoomSilhouette(area: BodyArea,s: Float) { val shade=Color(0xFFDCEBFF); when(area) { BodyArea.ABDOMEN -> drawRoundRect(shade,Offset(55*s,20*s),Size(190*s,270*s),CornerRadius(42*s,42*s)); BodyArea.RIGHT_GLUTE,BodyArea.LEFT_GLUTE -> drawRoundRect(shade,Offset(55*s,45*s),Size(190*s,210*s),CornerRadius(72*s,72*s)); else -> drawRoundRect(Skin,Offset(90*s,10*s),Size(120*s,290*s),CornerRadius(55*s,55*s)) } }
private fun zoomHits(area: BodyArea, s: Float): List<Hit> {
    fun rect(left: Float, top: Float, right: Float, bottom: Float) = Rect(left * s, top * s, right * s, bottom * s)
    return when (area) {
        BodyArea.ABDOMEN -> List(8) { index -> Hit(rect(65f + (index % 4) * 43, 65f + (index / 4) * 90, 105f + (index % 4) * 43, 150f + (index / 4) * 90), area, index) }
        BodyArea.RIGHT_GLUTE, BodyArea.LEFT_GLUTE -> List(2) { index -> Hit(rect(78f, 70f + index * 92, 222f, 155f + index * 92), area, index) }
        else -> List(4) { index -> Hit(rect(105f + (index % 2) * 47, 42f + (index / 2) * 125, 150f + (index % 2) * 47, 162f + (index / 2) * 125), area, index) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HistoryScreen(records: List<RecordItem>, onDelete: (RecordItem)->Unit, onBack: ()->Unit) { val formatter=remember { SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()) }; var deleting by remember { mutableStateOf<RecordItem?>(null) }; deleting?.let { record -> AlertDialog(onDismissRequest={deleting=null},title={Text("Eliminare registrazione?")},text={Text("Questa operazione non può essere annullata.")},confirmButton={TextButton(onClick={onDelete(record);deleting=null}){Text("Elimina")}},dismissButton={TextButton(onClick={deleting=null}){Text("Annulla")}}) }; Scaffold(topBar={ TopAppBar(title={Text("Storico")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Indietro")}}) }) { padding -> if(records.isEmpty()) Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center){Text("Nessuna registrazione",color=Color.Gray)} else LazyColumn(Modifier.padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){itemsIndexed(records){_,record->Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=when { record.mode == EntryMode.SENSORE -> Color(0xFFE5E7EB); record.insulinType == InsulinType.RAPIDA -> Color(0xFFE5F5E9); else -> Color(0xFFF0E6FA) })){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(record.area.label,fontWeight=FontWeight.Bold);Text("${record.area.zones[record.zone]} · ${record.mode.label}");record.insulinType?.let{Text(it.label,color=it.color,fontWeight=FontWeight.SemiBold)};Text(formatter.format(Date(record.time)),fontSize=12.sp,color=Color.Gray)};IconButton(onClick={deleting=record}){Icon(Icons.Default.Delete,"Elimina registrazione",tint=MaterialTheme.colorScheme.error)}}}}} } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SettingsScreen(avatar: AvatarStyle, settings: TimingSettings, onAvatar: (AvatarStyle)->Unit, onSettings: (TimingSettings)->Unit, onBack: () -> Unit) {
    var red by remember(settings) { mutableStateOf(settings.redHours.toString()) }; var orange by remember(settings) { mutableStateOf(settings.orangeHours.toString()) }; var yellow by remember(settings) { mutableStateOf(settings.yellowHours.toString()) }
    var stage by remember(settings) { mutableStateOf(settings.sensorStageDays.toString()) }; var hidden by remember(settings) { mutableStateOf(settings.sensorHiddenDays.toString()) }; var error by remember { mutableStateOf<String?>(null) }
    fun save() { val candidate=TimingSettings(red.toFloatOrNull() ?: -1f,orange.toFloatOrNull() ?: -1f,yellow.toFloatOrNull() ?: -1f,stage.toLongOrNull() ?: -1,hidden.toLongOrNull() ?: -1); if(candidate.valid()) { onSettings(candidate); error=null } else error="Le soglie devono essere crescenti; il sensore deve avere tre stadi prima della scomparsa." }
    Scaffold(topBar={ TopAppBar(title={Text("Impostazioni")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Indietro")}}) }, bottomBar={ Surface(shadowElevation=8.dp) { Button(onClick=::save,Modifier.fillMaxWidth().padding(16.dp)){Text("Salva impostazioni")} } }) { padding -> LazyColumn(Modifier.padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Avatar",fontWeight=FontWeight.Bold,fontSize=20.sp); Text("La scelta cambia solo la grafica, non lo storico.",color=Color.Gray,fontSize=13.sp) }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { AvatarStyle.entries.forEach { style -> FilterChip(selected=avatar==style,onClick={onAvatar(style)},label={Text(style.label)},modifier=Modifier.weight(1f)) } } }
        item { Text("Tempi colori iniezione (ore)",fontWeight=FontWeight.Bold,fontSize=20.sp); Text("Rosso fino a, arancione fino a, giallo fino a; poi verde.",fontSize=13.sp,color=Color.Gray) }
        item { TimingField("Rosso",red){red=it}; TimingField("Arancione",orange){orange=it}; TimingField("Giallo",yellow){yellow=it} }
        item { Text("Tempi sensore (giorni)",fontWeight=FontWeight.Bold,fontSize=20.sp); Text("Ogni stadio dura il valore indicato; il sensore sparisce al giorno impostato.",fontSize=13.sp,color=Color.Gray) }
        item { TimingField("Durata stadio",stage){stage=it}; TimingField("Scomparsa",hidden){hidden=it} }
        error?.let { item { Text(it,color=MaterialTheme.colorScheme.error) } }
        item { OutlinedButton(onClick={ red="12.0";orange="24.0";yellow="36.0";stage="10";hidden="40";onSettings(DefaultSettings);error=null },Modifier.fillMaxWidth()){Icon(Icons.Default.Restore,null);Spacer(Modifier.width(8.dp));Text("Ripristina valori predefiniti")} }
        item { Spacer(Modifier.height(72.dp)) }
    } }
}
@Composable private fun TimingField(label: String, value: String, onValue: (String)->Unit) = OutlinedTextField(value,onValue, label={Text(label)},singleLine=true, modifier=Modifier.fillMaxWidth())
