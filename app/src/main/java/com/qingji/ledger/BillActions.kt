package com.qingji.ledger

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt

@Composable fun SwipeBillRow(entry:Entry,s:Snapshot,open:Boolean,onOpenChange:(Boolean)->Unit,onClick:()->Unit,onNote:()->Unit,onDelete:()->Unit){
    val reveal=with(LocalDensity.current){144.dp.toPx()}
    var dragging by remember(entry.id){mutableStateOf(false)}
    var drag by remember(entry.id){mutableFloatStateOf(0f)}
    val offset by animateFloatAsState(if(dragging)drag else if(open)-reveal else 0f,animationSpec=if(dragging)snap()else tween(160),label="账单左滑")
    val currentOffset by rememberUpdatedState(offset)
    val currentOpenChange by rememberUpdatedState(onOpenChange)
    Box(Modifier.fillMaxWidth().clipToBounds().semantics{
        customActions=listOf(CustomAccessibilityAction("备注"){onNote();true},CustomAccessibilityAction("删除"){onDelete();true})
    }){
        Row(Modifier.matchParentSize(),horizontalArrangement=Arrangement.End){
            Box(Modifier.width(72.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick=onNote),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){Icon(Icons.Outlined.EditNote,"备注",tint=MaterialTheme.colorScheme.onPrimaryContainer);Text("备注",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onPrimaryContainer)}}
            Box(Modifier.width(72.dp).fillMaxHeight().background(MaterialTheme.colorScheme.error).clickable(onClick=onDelete),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){Icon(Icons.Outlined.DeleteOutline,"删除",tint=MaterialTheme.colorScheme.onError);Text("删除",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onError)}}
        }
        Surface(Modifier.offset{IntOffset(offset.roundToInt(),0)}.fillMaxWidth().pointerInput(entry.id,reveal){
            detectHorizontalDragGestures(onDragStart={drag=currentOffset;dragging=true},onDragEnd={val revealActions=drag< -reveal*.35f;dragging=false;currentOpenChange(revealActions)},onDragCancel={dragging=false}){change,amount->change.consume();drag=(drag+amount).coerceIn(-reveal,0f)}
        },color=MaterialTheme.colorScheme.surface){Box(Modifier.padding(horizontal=20.dp)){EntryRow(entry,s){if(open)onOpenChange(false)else onClick()}}}
    }
}

@Composable fun EntryNoteDialog(entry:Entry,vm:LedgerViewModel,onDismiss:()->Unit){
    var note by rememberSaveable(entry.id){mutableStateOf(entry.note)}
    var busy by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("备注")},text={
        OutlinedTextField(note,{if(it.length<=500)note=it},Modifier.fillMaxWidth(),enabled=!busy,placeholder={Text("添加备注")},minLines=2,maxLines=4)
    },confirmButton={TextButton(enabled=!busy,onClick={busy=true;vm.updateNote(entry.id,note){saved->busy=false;if(saved)onDismiss()}}){Text(if(busy)"保存中…" else "保存")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("取消")}})
}
