package com.example.injectionsites

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2563EB))) { InjectionApp() } }
    }
}

enum class EntryMode(val label:String,val color:Color){ INSULINA("Insulina",Color(0xFF2563EB)), SENSORE("Sensore",Color.Gray) }
enum class InsulinType(val label:String,val color:Color){ RAPIDA("Rapida",Color(0xFFF97316)), BASALE("Basale",Color(0xFF2563EB)) }

enum class BodyArea(val label:String,val color:Color,val zones:List<String>){
    RIGHT_ARM("Braccio destro",Color(0xFF3B82F6),listOf("Superiore interna","Superiore esterna","Inferiore interna","Inferiore esterna")),
    LEFT_ARM("Braccio sinistro",Color(0xFF3B82F6),listOf("Superiore interna","Superiore esterna","Inferiore interna","Inferiore esterna")),
    ABDOMEN("Addome",Color(0xFF22C55E),listOf("Sup. dx esterna","Sup. dx interna","Sup. sx interna","Sup. sx esterna","Inf. dx esterna","Inf. dx interna","Inf. sx interna","Inf. sx esterna")),
    RIGHT_GLUTE("Gluteo destro",Color(0xFF8B5CF6),listOf("Superiore","Inferiore")),
    LEFT_GLUTE("Gluteo sinistro",Color(0xFF8B5CF6),listOf("Superiore","Inferiore")),
    RIGHT_THIGH("Gamba destra",Color(0xFFF59E0B),listOf("Superiore interna","Superiore esterna","Inferiore interna","Inferiore esterna")),
    LEFT_THIGH("Gamba sinistra",Color(0xFFF59E0B),listOf("Superiore interna","Superiore esterna","Inferiore interna","Inferiore esterna"))
}

data class RecordItem(val area:BodyArea,val zone:Int,val mode:EntryMode,val insulinType:InsulinType?,val time:Long=System.currentTimeMillis())

@Composable fun InjectionApp(){
    var screen by remember{ mutableStateOf("home") }
    var area by remember{ mutableStateOf<BodyArea?>(null) }
    val records=remember{ mutableStateListOf<RecordItem>() }
    when(screen){
        "home"->HomeScreen({area=it;screen="area"},{screen="history"})
        "area"->area?.let{AreaScreen(it,records){screen="home"}}
        else->HistoryScreen(records){screen="home"}
    }
}

@Composable fun HomeScreen(onArea:(BodyArea)->Unit,onHistory:()->Unit){
    Scaffold(topBar={Surface(shadowElevation=2.dp){Column(Modifier.fillMaxWidth().padding(16.dp)){Text("Mappa siti",fontWeight=FontWeight.Bold,fontSize=22.sp);Text("Tocca una zona del corpo",color=Color.Gray,fontSize=13.sp)}}}){pad->
        LazyColumn(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            item{ BodyMap(onArea) }
            item{ Text("Zone disponibili",fontWeight=FontWeight.Bold,fontSize=18.sp) }
            itemsIndexed(BodyArea.values()){_,a-> AreaCard(a){onArea(a)} }
            item{ OutlinedButton(onClick=onHistory,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.History,null);Spacer(Modifier.width(8.dp));Text("Storico")} }
        }
    }
}

@Composable fun BodyMap(onArea:(BodyArea)->Unit){
    Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFF8FAFC))){
        Column(Modifier.padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text("Corpo intero",fontWeight=FontWeight.Bold,fontSize=18.sp)
            Text("FRONTE",fontSize=12.sp,color=Color.Gray)
            Spacer(Modifier.height(14.dp))
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    ZonePill("Braccio S\n4 zone",BodyArea.LEFT_ARM,onArea)
                    ZonePill("Addome\n8 zone",BodyArea.ABDOMEN,onArea)
                    ZonePill("Braccio D\n4 zone",BodyArea.RIGHT_ARM,onArea)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    ZonePill("Gamba S\n4 anteriori",BodyArea.LEFT_THIGH,onArea)
                    ZonePill("Gamba D\n4 anteriori",BodyArea.RIGHT_THIGH,onArea)
                }
                Text("RETRO",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=8.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    ZonePill("Gluteo S\n2 zone",BodyArea.LEFT_GLUTE,onArea)
                    ZonePill("Gluteo D\n2 zone",BodyArea.RIGHT_GLUTE,onArea)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Braccia 4+4 • Addome 8 • Glutei 2+2 • Gambe 4+4 solo anteriori",fontSize=12.sp,color=Color.Gray,textAlign=TextAlign.Center)
        }
    }
}

@Composable fun ZonePill(text:String,area:BodyArea,onArea:(BodyArea)->Unit){
    Box(Modifier.width(100.dp).height(66.dp).background(area.color.copy(alpha=.85f),RoundedCornerShape(18.dp)).clickable{onArea(area)},contentAlignment=Alignment.Center){
        Text(text,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
    }
}

@Composable fun AreaCard(area:BodyArea,onClick:()->Unit){
    Card(Modifier.fillMaxWidth().clickable(onClick=onClick),colors=CardDefaults.cardColors(containerColor=area.color.copy(alpha=.10f))){
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(14.dp).background(area.color,CircleShape));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(area.label,fontWeight=FontWeight.Bold);Text("${area.zones.size} zone",fontSize=13.sp,color=Color.Gray)};Icon(Icons.Default.ChevronRight,null)
        }
    }
}

@Composable fun AreaScreen(area:BodyArea,records:MutableList<RecordItem>,onBack:()->Unit){
    var mode by remember{ mutableStateOf(EntryMode.INSULINA) }
    var insulinType by remember{ mutableStateOf(InsulinType.RAPIDA) }
    var zone by remember{ mutableStateOf<Int?>(null) }
    var saved by remember{ mutableStateOf(false) }
    Scaffold(topBar={Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Indietro")};Column{Text(area.label,fontWeight=FontWeight.Bold,fontSize=20.sp);Text("Zoom della zona",fontSize=12.sp,color=Color.Gray)}}}){pad->
        LazyColumn(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            item{Text("1. Cosa vuoi registrare?",fontWeight=FontWeight.Bold,fontSize=18.sp);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){EntryMode.values().forEach{m->FilterChip(selected=mode==m,onClick={mode=m},label={Text(m.label)},leadingIcon={Box(Modifier.size(10.dp).background(m.color,CircleShape))})}}}
            if(mode==EntryMode.INSULINA){ item{Text("2. Tipo di insulina",fontWeight=FontWeight.Bold,fontSize=18.sp);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){InsulinType.values().forEach{t->FilterChip(selected=insulinType==t,onClick={insulinType=t},label={Text(t.label)},leadingIcon={Box(Modifier.size(10.dp).background(t.color,CircleShape))})}}} }
            item{Text(if(mode==EntryMode.INSULINA)"3. Seleziona il punto" else "2. Seleziona il punto",fontWeight=FontWeight.Bold,fontSize=18.sp);ZoomDiagram(area,zone,mode==EntryMode.SENSORE){zone=it}}
            itemsIndexed(area.zones){i,name->Card(Modifier.fillMaxWidth().clickable{zone=i},colors=CardDefaults.cardColors(containerColor=if(zone==i)area.color.copy(alpha=.18f) else Color(0xFFF8FAFC))){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(34.dp).background(area.color,CircleShape),contentAlignment=Alignment.Center){Text("${i+1}",color=Color.White,fontWeight=FontWeight.Bold)};Spacer(Modifier.width(12.dp));Text(name,Modifier.weight(1f));if(zone==i){if(mode==EntryMode.SENSORE)Box(Modifier.size(24.dp).background(Color.Gray,CircleShape)) else Icon(Icons.Default.CheckCircle,null,tint=Color(0xFF16A34A))}}}}
            item{Button(onClick={zone?.let{records.add(0,RecordItem(area,it,mode,if(mode==EntryMode.INSULINA)insulinType else null));saved=true}},enabled=zone!=null,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Save,null);Spacer(Modifier.width(8.dp));Text("Salva posizione")}}
            if(saved){item{Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFDCFCE7))){Text("Posizione salvata",Modifier.padding(16.dp),color=Color(0xFF166534),fontWeight=FontWeight.Bold)}}}
        }
    }
}

@Composable fun ZoomDiagram(area:BodyArea,selected:Int?,sensor:Boolean,onSelect:(Int)->Unit){
    val count=area.zones.size
    val cols=if(count==8)4 else 2
    val rows=(count+cols-1)/cols
    Card(colors=CardDefaults.cardColors(containerColor=area.color.copy(alpha=.08f))){Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Text(when(area){BodyArea.RIGHT_THIGH,BodyArea.LEFT_THIGH->"${area.label}: solo parte anteriore del quadricipite";BodyArea.RIGHT_ARM,BodyArea.LEFT_ARM->"${area.label}: dalla spalla al bicipite";else->area.label},fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
        Spacer(Modifier.height(14.dp));Column(verticalArrangement=Arrangement.spacedBy(6.dp)){repeat(rows){r->Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){repeat(cols){c->val i=r*cols+c;if(i<count){Box(Modifier.width(if(count==8)62.dp else 110.dp).height(if(count==2)110.dp else 92.dp).background(if(selected==i)area.color else area.color.copy(alpha=.65f),RoundedCornerShape(18.dp)).clickable{onSelect(i)},contentAlignment=Alignment.Center){Text("${i+1}",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);if(sensor&&selected==i)Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(26.dp).background(Color.Gray,CircleShape))}}}}}
    }}}
}

@Composable fun HistoryScreen(records:List<RecordItem>,onBack:()->Unit){
    val fmt=remember{SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault())}
    Scaffold(topBar={Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Indietro")};Text("Storico",fontWeight=FontWeight.Bold,fontSize=20.sp)}}){pad->
        if(records.isEmpty())Box(Modifier.fillMaxSize().padding(pad),contentAlignment=Alignment.Center){Text("Nessuna registrazione",color=Color.Gray)} else LazyColumn(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){itemsIndexed(records){_,r->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(r.area.label,fontWeight=FontWeight.Bold);Text("${r.area.zones[r.zone]} • ${r.mode.label}",color=Color.Gray);r.insulinType?.let{Text(it.label,color=it.color,fontWeight=FontWeight.Bold)};Text(fmt.format(Date(r.time)),fontSize=12.sp,color=Color.Gray)}}}}
    }
}
