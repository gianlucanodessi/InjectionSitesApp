package com.example.injectionsites

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
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
private val SensorGrey = Color(0xFF475569)

/** All injection availability thresholds live here, so they can be changed together. */
private object InjectionAvailability {
    const val RED_UNTIL_HOURS = 12f
    const val ORANGE_UNTIL_HOURS = 24f
    const val YELLOW_UNTIL_HOURS = 36f
    val RED = Color(0xFFDC2626)
    val ORANGE = Color(0xFFF97316)
    val YELLOW = Color(0xFFEAB308)
    val GREEN = Color(0xFF16A34A)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Blue)) { InjectionApp() } }
    }
}

enum class EntryMode(val label: String) { INSULINA("Insulina"), SENSORE("Sensore") }
enum class InsulinType(val label: String, val color: Color) { RAPIDA("Rapida", Color(0xFFF97316)), BASALE("Basale", Blue) }
enum class BodyArea(val label: String, val color: Color, val zones: List<String>) {
    RIGHT_ARM("Braccio destro", Color(0xFF2878D4), listOf("Superiore interna", "Superiore esterna", "Inferiore interna", "Inferiore esterna")),
    LEFT_ARM("Braccio sinistro", Color(0xFF2878D4), listOf("Superiore interna", "Superiore esterna", "Inferiore interna", "Inferiore esterna")),
    ABDOMEN("Addome", Color(0xFF54A83E), listOf("Superiore destra esterna", "Superiore destra interna", "Superiore sinistra interna", "Superiore sinistra esterna", "Inferiore destra esterna", "Inferiore destra interna", "Inferiore sinistra interna", "Inferiore sinistra esterna")),
    RIGHT_THIGH("Coscia destra", Color(0xFFF28B19), listOf("Superiore interna", "Superiore esterna", "Inferiore interna", "Inferiore esterna")),
    LEFT_THIGH("Coscia sinistra", Color(0xFFF28B19), listOf("Superiore interna", "Superiore esterna", "Inferiore interna", "Inferiore esterna")),
    RIGHT_GLUTE("Gluteo destro", Color(0xFF7C4DCC), listOf("Superiore", "Inferiore")),
    LEFT_GLUTE("Gluteo sinistro", Color(0xFF7C4DCC), listOf("Superiore", "Inferiore"))
}
data class RecordItem(val area: BodyArea, val zone: Int, val mode: EntryMode, val insulinType: InsulinType?, val time: Long = System.currentTimeMillis())

private const val STORAGE = "injection_sites"
private const val INJECTIONS_KEY = "injection_history"
private const val SENSOR_KEY = "sensor_position"

private fun RecordItem.toJson() = JSONObject().apply {
    put("area", area.name); put("zone", zone); put("mode", mode.name); put("insulinType", insulinType?.name); put("time", time)
}
private fun recordFromJson(json: JSONObject): RecordItem? = runCatching {
    RecordItem(BodyArea.valueOf(json.getString("area")), json.getInt("zone"), EntryMode.valueOf(json.getString("mode")), json.optString("insulinType").takeIf { it.isNotBlank() && it != "null" }?.let(InsulinType::valueOf), json.getLong("time"))
}.getOrNull()
private fun loadInjections(context: Context): List<RecordItem> = runCatching {
    val data = JSONArray(context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).getString(INJECTIONS_KEY, "[]"))
    (0 until data.length()).mapNotNull { recordFromJson(data.getJSONObject(it)) }.filter { it.mode == EntryMode.INSULINA }.sortedByDescending { it.time }
}.getOrDefault(emptyList())
private fun saveInjections(context: Context, records: List<RecordItem>) {
    val data = JSONArray(); records.forEach { data.put(it.toJson()) }
    context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).edit().putString(INJECTIONS_KEY, data.toString()).apply()
}
private fun loadSensor(context: Context): RecordItem? = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).getString(SENSOR_KEY, null)?.let { runCatching { recordFromJson(JSONObject(it)) }.getOrNull() }
private fun saveSensor(context: Context, sensor: RecordItem) { context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE).edit().putString(SENSOR_KEY, sensor.toJson().toString()).apply() }

@Composable fun InjectionApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf("home") }
    var area by remember { mutableStateOf<BodyArea?>(null) }
    val injections = remember { mutableStateListOf<RecordItem>().also { it.addAll(loadInjections(context)) } }
    var sensor by remember { mutableStateOf(loadSensor(context)) }
    val refreshTick by produceState(initialValue = 0) { while (true) { delay(60_000); value++ } }
    when (screen) {
        "home" -> key(refreshTick) { HomeScreen(injections, sensor, { area = it; screen = "area" }, { screen = "history" }) }
        "area" -> area?.let { selectedArea -> AreaScreen(selectedArea, injections, sensor, onInjectionSaved = { record -> injections.add(0, record); saveInjections(context, injections) }, onSensorSaved = { position -> sensor = position; saveSensor(context, position) }) { screen = "home" } }
        else -> HistoryScreen(injections) { screen = "home" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HomeScreen(injections: List<RecordItem>, sensor: RecordItem?, onArea: (BodyArea) -> Unit, onHistory: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Column { Text("Nuova iniezione", fontWeight = FontWeight.Bold); Text("Seleziona una zona sulla sagoma", fontSize = 12.sp, color = Color.Gray) } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { BodyMap(injections, sensor, onArea) }; item { AvailabilityLegend() }; item { Text("Tutte le aree", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            itemsIndexed(BodyArea.entries) { _, bodyArea -> AreaCard(bodyArea) { onArea(bodyArea) } }
            item { OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) { Icon(Icons.Default.History, null); Spacer(Modifier.width(8.dp)); Text("Storico iniezioni") } }
        }
    }
}

@Composable private fun AvailabilityLegend() = Card(colors = CardDefaults.cardColors(containerColor = SurfaceTint)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) { Text("Disponibilità:", fontWeight = FontWeight.Bold, fontSize = 13.sp); LegendDot(InjectionAvailability.RED, "< 12 h"); LegendDot(InjectionAvailability.ORANGE, "12–24 h"); LegendDot(InjectionAvailability.YELLOW, "24–36 h"); LegendDot(InjectionAvailability.GREEN, "> 36 h") } }
@Composable private fun LegendDot(color: Color, text: String) = Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).background(color, CircleShape)); Spacer(Modifier.width(3.dp)); Text(text, fontSize = 11.sp) }

/** Evaluated independently for each area/zone, and deliberately ignores sensor placement. */
private fun zoneColor(area: BodyArea, index: Int, injections: List<RecordItem>): Color {
    val lastInjection = injections.filter { it.mode == EntryMode.INSULINA && it.area == area && it.zone == index }.maxByOrNull { it.time } ?: return InjectionAvailability.GREEN
    val hours = (System.currentTimeMillis() - lastInjection.time).coerceAtLeast(0) / 3_600_000f
    return when { hours < InjectionAvailability.RED_UNTIL_HOURS -> InjectionAvailability.RED; hours < InjectionAvailability.ORANGE_UNTIL_HOURS -> InjectionAvailability.ORANGE; hours < InjectionAvailability.YELLOW_UNTIL_HOURS -> InjectionAvailability.YELLOW; else -> InjectionAvailability.GREEN }
}

@Composable private fun AreaCard(area: BodyArea, onClick: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = area.color.copy(alpha = .08f))) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(14.dp).background(area.color, CircleShape)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(area.label, fontWeight = FontWeight.Bold); Text("${area.zones.size} zone selezionabili", fontSize = 12.sp, color = Color.Gray) }; Icon(Icons.Default.ChevronRight, null, tint = Blue) } }
@Composable private fun BodyMap(injections: List<RecordItem>, sensor: RecordItem?, onArea: (BodyArea) -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = SurfaceTint)) { Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Sagoma frontale", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Tocca un gruppo di zone per ingrandirlo", fontSize = 12.sp, color = Color.Gray); FrontBodyCanvas(injections, sensor, onArea); Divider(Modifier.padding(vertical = 4.dp)); Text("Vista posteriore · glutei", fontWeight = FontWeight.SemiBold, fontSize = 13.sp); GluteCanvas(injections, sensor, onArea) } }

private data class Hit(val rect: Rect, val area: BodyArea, val zone: Int)
private fun DrawScope.zoneBox(rect: Rect, color: Color, number: Int, hasSensor: Boolean) { drawRoundRect(color, rect.topLeft, rect.size, CornerRadius(9f, 9f)); drawRoundRect(Color.White.copy(alpha = .72f), rect.topLeft, rect.size, CornerRadius(9f, 9f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f)); drawCircle(Color.White.copy(alpha = .85f), 10f, rect.center); drawContext.canvas.nativeCanvas.drawText("${number + 1}", rect.center.x - 3.5f, rect.center.y + 4f, android.graphics.Paint().apply { this.color = android.graphics.Color.DKGRAY; textSize = 11f; isFakeBoldText = true }); if (hasSensor) { drawCircle(Color.White, 11f, Offset(rect.right - 11f, rect.bottom - 11f)); drawCircle(SensorGrey, 7f, Offset(rect.right - 11f, rect.bottom - 11f)) } }
private fun isSensor(sensor: RecordItem?, hit: Hit) = sensor?.let { it.area == hit.area && it.zone == hit.zone } == true

@Composable private fun FrontBodyCanvas(injections: List<RecordItem>, sensor: RecordItem?, onArea: (BodyArea) -> Unit) = Canvas(Modifier.fillMaxWidth().height(500.dp).pointerInput(Unit) { detectTapGestures { point -> frontHits(size.width / 300f).firstOrNull { it.rect.contains(point) }?.let { onArea(it.area) } } }) { val scale = size.width / 300f; drawFrontSilhouette(scale); frontHits(scale).forEach { zoneBox(it.rect, zoneColor(it.area, it.zone, injections), it.zone, isSensor(sensor, it)) } }
private fun DrawScope.drawFrontSilhouette(s: Float) { drawCircle(Skin, 23*s, Offset(150*s, 34*s)); drawRoundRect(Skin, Offset(116*s,58*s), androidx.compose.ui.geometry.Size(68*s,172*s), CornerRadius(32*s,32*s)); drawRoundRect(Skin, Offset(45*s,80*s), androidx.compose.ui.geometry.Size(34*s,170*s), CornerRadius(18*s,18*s)); drawRoundRect(Skin, Offset(221*s,80*s), androidx.compose.ui.geometry.Size(34*s,170*s), CornerRadius(18*s,18*s)); drawRoundRect(Skin, Offset(111*s,220*s), androidx.compose.ui.geometry.Size(35*s,195*s), CornerRadius(18*s,18*s)); drawRoundRect(Skin, Offset(154*s,220*s), androidx.compose.ui.geometry.Size(35*s,195*s), CornerRadius(18*s,18*s)); drawRoundRect(Skin, Offset(106*s,405*s), androidx.compose.ui.geometry.Size(42*s,55*s), CornerRadius(15*s,15*s)); drawRoundRect(Skin, Offset(152*s,405*s), androidx.compose.ui.geometry.Size(42*s,55*s), CornerRadius(15*s,15*s)) }
private fun frontHits(s: Float): List<Hit> {
    fun rect(left: Float, top: Float, right: Float, bottom: Float) = Rect(left * s, top * s, right * s, bottom * s)
    val result = mutableListOf<Hit>()
    fun add(area: BodyArea, x: Float, y: Float, width: Float, height: Float, columns: Int, rows: Int) {
        repeat(rows) { row -> repeat(columns) { column -> result += Hit(rect(x + column * width / columns, y + row * height / rows, x + (column + 1) * width / columns, y + (row + 1) * height / rows), area, row * columns + column) } }
    }
    add(BodyArea.LEFT_ARM, 47f, 90f, 30f, 130f, 2, 2); add(BodyArea.RIGHT_ARM, 223f, 90f, 30f, 130f, 2, 2)
    add(BodyArea.ABDOMEN, 103f, 132f, 94f, 96f, 4, 2); add(BodyArea.LEFT_THIGH, 111f, 244f, 34f, 135f, 2, 2); add(BodyArea.RIGHT_THIGH, 155f, 244f, 34f, 135f, 2, 2)
    return result
}
@Composable private fun GluteCanvas(injections: List<RecordItem>, sensor: RecordItem?, onArea: (BodyArea) -> Unit) = Canvas(Modifier.fillMaxWidth().height(130.dp).pointerInput(Unit) { detectTapGestures { point -> gluteHits(size.width / 300f).firstOrNull { it.rect.contains(point) }?.let { onArea(it.area) } } }) { val scale=size.width/300f; drawRoundRect(Skin,Offset(86*scale,8*scale),androidx.compose.ui.geometry.Size(128*scale,105*scale),CornerRadius(44*scale,44*scale)); gluteHits(scale).forEach { zoneBox(it.rect,zoneColor(it.area,it.zone,injections),it.zone,isSensor(sensor,it)) } }
private fun gluteHits(s: Float): List<Hit> {
    fun rect(left: Float, top: Float, right: Float, bottom: Float) = Rect(left * s, top * s, right * s, bottom * s)
    return listOf(Hit(rect(91f,18f,148f,60f),BodyArea.LEFT_GLUTE,0),Hit(rect(152f,18f,209f,60f),BodyArea.RIGHT_GLUTE,0),Hit(rect(91f,60f,148f,102f),BodyArea.LEFT_GLUTE,1),Hit(rect(152f,60f,209f,102f),BodyArea.RIGHT_GLUTE,1))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AreaScreen(area: BodyArea, injections: MutableList<RecordItem>, sensor: RecordItem?, onInjectionSaved: (RecordItem) -> Unit, onSensorSaved: (RecordItem) -> Unit, onBack: () -> Unit) { var mode by remember { mutableStateOf(EntryMode.INSULINA) }; var insulinType by remember { mutableStateOf(InsulinType.RAPIDA) }; var zone by remember { mutableStateOf<Int?>(null) }; var saved by remember { mutableStateOf(false) }; Scaffold(topBar = { TopAppBar(title = { Text(area.label, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack,"Indietro") } }) }, bottomBar = { Surface(shadowElevation = 8.dp) { Button(onClick = { zone?.let { selected -> val record=RecordItem(area,selected,mode,if(mode==EntryMode.INSULINA) insulinType else null); if(mode==EntryMode.INSULINA) onInjectionSaved(record) else onSensorSaved(record); saved=true } },enabled=zone!=null,modifier=Modifier.fillMaxWidth().padding(16.dp)) { Icon(Icons.Default.Save,null); Spacer(Modifier.width(8.dp)); Text("Salva posizione") } } }) { padding -> LazyColumn(Modifier.padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { item { Text("Zoom dedicato · ${area.zones.size} zone",color=Blue,fontWeight=FontWeight.SemiBold); ModePicker(mode,{mode=it},insulinType,{insulinType=it}) }; item { Text("Tocca una zona",fontWeight=FontWeight.Bold,fontSize=18.sp); ZoomBodyDiagram(area,zone,injections,sensor) { zone=it; saved=false } }; itemsIndexed(area.zones) { index,name -> ZoneRow(index,name,zone==index,zoneColor(area,index,injections),sensor?.let { it.area == area && it.zone == index } == true) { zone=index; saved=false } }; if(saved) item { Text(if(mode==EntryMode.INSULINA) "Iniezione salvata: la zona ora è rossa." else "Posizione del sensore salvata.",color=Color(0xFF166534),fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(bottom=76.dp)) } else item { Spacer(Modifier.height(76.dp)) } } } }
@Composable private fun ModePicker(mode: EntryMode,setMode: (EntryMode)->Unit,insulin: InsulinType,setInsulin: (InsulinType)->Unit) = Column(verticalArrangement=Arrangement.spacedBy(6.dp)) { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { EntryMode.entries.forEach { FilterChip(selected=mode==it,onClick={setMode(it)},label={Text(it.label)}) } }; if(mode==EntryMode.INSULINA) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { InsulinType.entries.forEach { FilterChip(selected=insulin==it,onClick={setInsulin(it)},label={Text(it.label)}) } } }
@Composable private fun ZoneRow(index: Int,name: String,selected: Boolean,color: Color,hasSensor: Boolean,onClick: ()->Unit) = Card(Modifier.fillMaxWidth().clickable(onClick=onClick),colors=CardDefaults.cardColors(containerColor=if(selected) color.copy(alpha=.18f) else SurfaceTint)) { Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(30.dp).background(color,CircleShape),contentAlignment=Alignment.Center) { Text("${index+1}",color=Color.White,fontWeight=FontWeight.Bold) }; Spacer(Modifier.width(12.dp)); Text(name,Modifier.weight(1f)); if(hasSensor) { Box(Modifier.size(18.dp).background(SensorGrey,CircleShape)); Spacer(Modifier.width(8.dp)) }; if(selected) Icon(Icons.Default.CheckCircle,null,tint=InjectionAvailability.GREEN) } }
@Composable private fun ZoomBodyDiagram(area: BodyArea,selected: Int?,injections: List<RecordItem>,sensor: RecordItem?,onSelect: (Int)->Unit) = Canvas(Modifier.fillMaxWidth().height(310.dp).background(SurfaceTint,RoundedCornerShape(20.dp)).pointerInput(area) { detectTapGestures { point -> zoomHits(area,size.width/300f).firstOrNull { it.rect.contains(point) }?.let { onSelect(it.zone) } } }) { val scale=size.width/300f; drawZoomSilhouette(area,scale); zoomHits(area,scale).forEach { hit -> val color=zoneColor(area,hit.zone,injections).let { if(selected==hit.zone) it else it.copy(alpha=.72f) }; zoneBox(hit.rect,color,hit.zone,isSensor(sensor,hit)) } }
private fun DrawScope.drawZoomSilhouette(area: BodyArea,s: Float) { when(area) { BodyArea.ABDOMEN -> drawRoundRect(Skin,Offset(55*s,20*s),androidx.compose.ui.geometry.Size(190*s,270*s),CornerRadius(42*s,42*s)); BodyArea.RIGHT_GLUTE,BodyArea.LEFT_GLUTE -> drawRoundRect(Skin,Offset(55*s,45*s),androidx.compose.ui.geometry.Size(190*s,210*s),CornerRadius(72*s,72*s)); else -> drawRoundRect(Skin,Offset(90*s,10*s),androidx.compose.ui.geometry.Size(120*s,290*s),CornerRadius(55*s,55*s)) } }
private fun zoomHits(area: BodyArea,s: Float): List<Hit> {
    fun rect(left: Float, top: Float, right: Float, bottom: Float) = Rect(left * s, top * s, right * s, bottom * s)
    return when(area) { BodyArea.ABDOMEN -> List(8) { index -> Hit(rect(65f+(index%4)*43,65f+(index/4)*90,105f+(index%4)*43,150f+(index/4)*90),area,index) }; BodyArea.RIGHT_GLUTE,BodyArea.LEFT_GLUTE -> List(2) { index -> Hit(rect(78f,70f+index*92,222f,155f+index*92),area,index) }; else -> List(4) { index -> Hit(rect(105f+(index%2)*47,42f+(index/2)*125,150f+(index%2)*47,162f+(index/2)*125),area,index) } }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HistoryScreen(injections: List<RecordItem>,onBack: ()->Unit) { val formatter=remember { SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()) }; Scaffold(topBar={ TopAppBar(title={Text("Storico iniezioni")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Indietro")}}) }) { padding -> if(injections.isEmpty()) Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center){Text("Nessuna iniezione registrata",color=Color.Gray)} else LazyColumn(Modifier.padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){itemsIndexed(injections){_,record->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(record.area.label,fontWeight=FontWeight.Bold);Text("${record.area.zones[record.zone]} · Insulina");record.insulinType?.let{Text(it.label,color=it.color,fontWeight=FontWeight.SemiBold)};Text(formatter.format(Date(record.time)),fontSize=12.sp,color=Color.Gray)}}}} } }
