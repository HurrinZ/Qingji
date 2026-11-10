@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.qingji.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import java.time.*
import kotlin.math.abs

@Composable fun AssetChoice(title:String,s:Snapshot,selected:String?,onSelect:(String?)->Unit,includeNone:Boolean=true,pool:String?=null){
    CompactAssetChoice(title,s,selected,onSelect,includeNone,pool)
}
@Composable fun AssetsScreen(s:Snapshot,onCreate:()->Unit,onDetails:(AssetAccount)->Unit,onTransfer:()->Unit){
    var archived by rememberSaveable{mutableStateOf(false)}
    val balances=remember(s.assets,s.entries,s.transfers,s.adjustments){s.assetBalances()};val active=s.assets.filter{!it.archived}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,5.dp,16.dp,24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Panel{
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("总资产",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium);InfoHint("资产说明","总资产只累加余额为正的账户，分为流动资金与理财资金；负债账户不冲减总资产，另在下方单独列出。手动记录本地余额，不连接支付平台。旧账默认不扣减新增余额；转账、期初和校准不计收支。")}
            MoneyText(s.assetTotal(),size=34.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Column(Modifier.weight(1f)){Hint(poolLabel(POOL_LIQUID));Text("¥"+money(s.assetTotal(POOL_LIQUID)),fontSize=17.sp,fontWeight=FontWeight.Medium)}
                Column(Modifier.weight(1f)){Hint(poolLabel(POOL_INVEST));Text("¥"+money(s.assetTotal(POOL_INVEST)),fontSize=17.sp,fontWeight=FontWeight.Medium)}
            }
            if(active.isNotEmpty())AssetDistribution(s)
            OutlinedButton(onClick=onTransfer,enabled=active.size>=2,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.SwapHoriz,null,Modifier.size(19.dp));Spacer(Modifier.width(8.dp));Text("转账")}
        }}
        item{Heading("账户","添加",onCreate);ChoiceRow(listOf("使用中","已归档"),if(archived)1 else 0){archived=it==1}}
        val shown=s.assets.filter{it.archived==archived}
        if(shown.isEmpty())item{EmptyState(if(archived)"暂无归档账户" else "添加一项资产",if(archived)"" else "填写当前余额",if(archived)null else "添加账户",onCreate)}
        items(shown,key={it.id}){asset->Panel(Modifier.clickable{onDetails(asset)}){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                AssetBadge(asset.kind)
                Column(Modifier.weight(1f)){Text(asset.name,fontWeight=FontWeight.Medium,maxLines=1);Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){Text(poolLabel(asset.pool),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall,maxLines=1);if((balances[asset.id]?:0)<0)Text("余额为负",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)}}
                Text("¥"+money(balances[asset.id]?:0),fontSize=19.sp,fontWeight=FontWeight.Medium,color=if((balances[asset.id]?:0)<0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Outlined.ChevronRight,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }}
    }
}
@Composable fun AssetEditor(asset:AssetAccount?,s:Snapshot,vm:LedgerViewModel,onDismiss:()->Unit){
    var name by rememberSaveable(asset?.id){mutableStateOf(asset?.name?:"")};var kind by rememberSaveable(asset?.id){mutableStateOf(asset?.kind?:"BANK")};var amount by rememberSaveable(asset?.id){mutableStateOf("")};var rename by rememberSaveable{mutableStateOf(false)}
    var pool by rememberSaveable(asset?.id){mutableStateOf(normalizePool(asset?.pool?:POOL_LIQUID))}
    val displayName=name.ifBlank{suggestedAssetName(kind,s.assets)}
    val value=parseBalance(amount.ifBlank{"0"}.trimEnd('.'))
    val form:@Composable ColumnScope.()->Unit={
        AssetKindPicker(kind){kind=it}
        ChoiceRow(ASSET_POOLS.values.toList(),ASSET_POOLS.keys.indexOf(pool)){i->pool=ASSET_POOLS.keys.elementAt(i)}
        Hint(if(pool==POOL_INVEST)"归入理财资金：只有理财账单能关联此账户；换池会同步更新该账户已有账单。" else "归入流动资金：只有流动账单能关联此账户；换池会同步更新该账户已有账单。")
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(displayName,Modifier.weight(1f),fontWeight=FontWeight.Medium);IconButton(onClick={rename=true}){Icon(Icons.Outlined.Edit,"修改账户名称",Modifier.size(18.dp))};InfoHint("资产账户","名称自动填写，可点击铅笔修改。当前余额作为期初，不算收入。旧账若已包含在余额中，请勿再次开启余额联动。这里只记录账面余额，不实际扣款。")}
        if(asset==null){Hint("当前余额");AmountDisplay(amount){amount=""}}
        else {Hint("期初余额");MoneyText(asset.openingBalance,size=28.sp)}
    }
    if(asset==null)FastInputSheet("添加资产",onDismiss,form=form,footer={
        AmountKeypad{amount=keypadInput(amount,it)}
        SubmitButton(enabled=value!=null,done={onDismiss();vm.notify("已添加")}){vm.repo.saveAsset(AssetAccount(name=displayName.trim(),kind=kind,openingBalance=requireNotNull(value),pool=pool))}
    })else EditorSheet("编辑账户",onDismiss){form();SubmitButton(done={onDismiss();vm.notify("已保存")}){vm.repo.saveAsset(asset.copy(name=displayName.trim(),kind=kind,pool=pool))}}
    if(rename)TextInputDialog("账户名称",displayName,30,{rename=false},{name=it.trim()})
}
@Composable fun CalibrationEditor(asset:AssetAccount,s:Snapshot,vm:LedgerViewModel,onDismiss:()->Unit){
    var amount by rememberSaveable{mutableStateOf("")};var note by rememberSaveable{mutableStateOf("")};val current=s.assetBalances()[asset.id]?:0;val target=parseBalance(amount.trimEnd('.'))
    FastInputSheet("校准余额",onDismiss,form={
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){AssetBadge(asset.kind,32.dp);Text(asset.name,Modifier.weight(1f));InfoHint("余额校准","填写实际余额。只记录差额，不计收支；后续修改旧流水仍会影响余额。")}
        Hint("实际余额");AmountDisplay(amount){amount=""}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("¥"+money(current),color=MaterialTheme.colorScheme.onSurfaceVariant);Icon(Icons.Outlined.ArrowForward,null,Modifier.size(18.dp));Text(if(target==null)"—" else "¥"+money(target),fontWeight=FontWeight.Medium)}
        NoteAction(note){note=it}
    },footer={AmountKeypad{amount=keypadInput(amount,it)};SubmitButton(enabled=target!=null,done={onDismiss();vm.notify("已校准")}){vm.repo.calibrateAsset(asset.id,requireNotNull(target),note)}})
}
@Composable fun TransferEditor(transfer:AssetTransfer?,preferredSource:String?,s:Snapshot,vm:LedgerViewModel,onDismiss:()->Unit){
    val active=s.assets.filter{!it.archived};val first=preferredSource?.takeIf{id->active.any{it.id==id}}?:s.defaultAssetId()
    var from by rememberSaveable(transfer?.id){mutableStateOf(transfer?.fromAssetId?:first)};var to by rememberSaveable(transfer?.id){mutableStateOf(transfer?.toAssetId?:active.firstOrNull{it.id!=first}?.id)}
    var amount by rememberSaveable(transfer?.id){mutableStateOf(transfer?.let{money(it.amount)}?:"")};var date by rememberSaveable(transfer?.id){mutableStateOf(transfer?.date?:LocalDate.now().toString())};var note by rememberSaveable(transfer?.id){mutableStateOf(transfer?.note?:"")};val context=LocalContext.current
    val value=keypadMoney(amount);val current=from?.let{s.assetBalances()[it]}?:0L;val oldSourceRestore=if(transfer!=null&&transfer.fromAssetId==from)transfer.amount else if(transfer!=null&&transfer.toAssetId==from)-transfer.amount else 0L
    FastInputSheet(if(transfer==null)"转账" else "编辑转账",onDismiss,form={
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Hint("转出",Modifier.weight(1f));InfoHint("转账","同时更新两个账户，不计收支。手续费请另记支出。")}
        CompactAssetChoice("转出账户",s,from,{from=it},false)
        Icon(Icons.Outlined.South,"转入",Modifier.align(Alignment.CenterHorizontally).size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        CompactAssetChoice("转入账户",s,to,{to=it},false)
        AmountDisplay(amount){amount=""}
        if(value!=null&&current+oldSourceRestore-value<0)Text("转出账户余额不足",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){NoteAction(note){note=it};TextButton(onClick={pickDate(context,LocalDate.parse(date)){date=it.toString()}}){Icon(Icons.Outlined.CalendarToday,null,Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text(if(date==LocalDate.now().toString())"今天" else date.substring(5))}}
        if(transfer!=null)TextButton(onClick={vm.action{val old=vm.repo.deleteTransfer(transfer.id);withContext(Dispatchers.Main){onDismiss()};vm.notices.emit(Notice("转账已删除"){vm.repo.saveTransfer(old.copy(deletedAt=null))})}}){Text("删除",color=MaterialTheme.colorScheme.error)}
    },footer={
        AmountKeypad{amount=keypadInput(amount,it)}
        SubmitButton(enabled=value!=null&&from!=null&&to!=null&&from!=to,done={onDismiss();vm.notify("已保存")}){val a=requireNotNull(from){"请选择转出账户"};val b=requireNotNull(to){"请选择转入账户"};val v=requireNotNull(keypadMoney(amount)){"请输入有效金额"};vm.repo.saveTransfer((transfer?:AssetTransfer(fromAssetId=a,toAssetId=b,amount=v)).copy(fromAssetId=a,toAssetId=b,amount=v,date=date,note=note))}
    })
}
private data class AssetHistory(val id:String,val date:String,val title:String,val amount:Long,val note:String,val createdAt:Long,val entry:Entry?=null,val transfer:AssetTransfer?=null,val adjustment:AssetAdjustment?=null,val linkedOnly:Boolean=false)
@Composable fun AssetDetails(asset:AssetAccount,s:Snapshot,vm:LedgerViewModel,onDismiss:()->Unit,onEdit:()->Unit,onCalibrate:()->Unit,onTransfer:()->Unit,onEntry:(Entry)->Unit,onTransferEdit:(AssetTransfer)->Unit){
    var count by rememberSaveable(asset.id){mutableIntStateOf(50)}
    val balance=s.assetBalances()[asset.id]?:0L
    val history=remember(s.entries,s.transfers,s.adjustments,s.assets,s.categories,asset.id){
        val entries=s.live().filter{it.assetId==asset.id}.map{e->AssetHistory(e.id,e.date,(s.category(e.categoryId)?.name?:"收支")+when(e.type){REFUND->" · 退款";INCOME->" · 收入";else->" · 支出"},entryEffect(e)?.second?:e.amount,e.note,e.createdAt,entry=e,linkedOnly=!e.affectsBalance)}
        val transfers=s.transfers.filter{it.deletedAt==null&&(it.fromAssetId==asset.id||it.toAssetId==asset.id)}.map{t->val outgoing=t.fromAssetId==asset.id;val other=s.assets.find{it.id==if(outgoing)t.toAssetId else t.fromAssetId}?.name?:"另一账户";AssetHistory(t.id,t.date,(if(outgoing)"转出 → " else "转入 ← ")+other,if(outgoing)-t.amount else t.amount,t.note,t.createdAt,transfer=t)}
        val adjustments=s.adjustments.filter{it.deletedAt==null&&it.assetId==asset.id}.map{a->AssetHistory(a.id,a.date,"余额校准（当时设为 ¥${money(a.targetBalance)}）",a.delta,a.note,a.createdAt,adjustment=a)}
        (entries+transfers+adjustments).sortedWith(compareByDescending<AssetHistory>{it.date}.thenByDescending{it.createdAt})
    }
    EditorSheet(asset.name,onDismiss){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){AssetBadge(asset.kind);MoneyText(balance,size=30.sp,color=if(balance<0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)}
        if(balance<0)Text("余额为负，请核对流水",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        Hint("期初 ¥${money(asset.openingBalance)} · ${if(asset.archived)"已归档" else "使用中"}")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onEdit,modifier=Modifier.weight(1f)){Text("编辑账户")};OutlinedButton(onClick=onCalibrate,enabled=!asset.archived,modifier=Modifier.weight(1f)){Text("校准余额")}}
        if(!asset.archived)OutlinedButton(onClick=onTransfer,enabled=s.assets.count{!it.archived}>=2,modifier=Modifier.fillMaxWidth()){Text("从此账户转账")}
        TextButton(onClick={vm.action(if(asset.archived)"资产已恢复" else "资产已归档"){vm.repo.archiveAsset(asset.id,!asset.archived);withContext(Dispatchers.Main){onDismiss()}}},modifier=Modifier.fillMaxWidth()){Text(if(asset.archived)"恢复此账户" else "归档此账户（余额需为0）")}
        Row(verticalAlignment=Alignment.CenterVertically){Text("流水",Modifier.weight(1f),fontWeight=FontWeight.Bold);InfoHint("流水口径","转账、期初和校准不计收支；仅关联记录不改变余额。")}
        if(history.isEmpty())Hint("暂无流水")
        history.take(count).forEach{h->Panel(Modifier.clickable{h.entry?.let(onEntry);h.transfer?.let(onTransferEdit)}){
            Text(h.title,fontWeight=FontWeight.Medium);Text((if(h.linkedOnly)"¥ " else if(h.amount>=0)"+" else "−")+money(abs(h.amount))+(if(h.linkedOnly)" · 仅关联" else ""),color=if(h.linkedOnly)MaterialTheme.colorScheme.onSurfaceVariant else if(h.amount>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface);Hint(h.date+if(h.note.isBlank())"" else " · "+h.note)
            h.adjustment?.let{adjustment->TextButton(onClick={vm.action{val old=vm.repo.deleteAdjustment(adjustment.id);withContext(Dispatchers.Main){onDismiss()};vm.notices.emit(Notice("校准已撤销"){vm.repo.db.withTransaction{vm.repo.validateAssetEffects(emptyMap(),mapOf(old.assetId to old.delta));vm.repo.dao.putAdjustment(old.copy(deletedAt=null))}})}}){Text("撤销这次校准")}}
        }}
        if(history.size>count)TextButton(onClick={count+=50}){Text("加载更多 · 共${history.size}条")}
    }
}
