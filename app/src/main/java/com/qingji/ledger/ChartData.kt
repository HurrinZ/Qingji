package com.qingji.ledger

data class ChartSlice(val id:String,val label:String,val value:Double)
fun suggestedAssetName(kind:String,existing:List<AssetAccount>):String{
    val base=ASSET_KINDS[kind]?:"其他"
    val names=existing.filter{!it.archived}.map{it.name}.toSet()
    if(base !in names)return base
    var index=2
    while("$base $index" in names)index++
    return "$base $index"
}
fun chartShares(slices:List<ChartSlice>):List<Pair<ChartSlice,Float>>{
    val valid=slices.filter{it.value.isFinite()&&it.value>0}
    val total=valid.sumOf{it.value}
    if(total<=0||!total.isFinite())return emptyList()
    return valid.map{it to (it.value/total).toFloat()}
}
fun Snapshot.assetSlices():List<ChartSlice>{
    val balances=assetBalances()
    return assets.filter{!it.archived&&(balances[it.id]?:0)>0}.groupBy{it.kind}.map{(kind,accounts)->
        ChartSlice(kind,ASSET_KINDS[kind]?:"其他",accounts.sumOf{(balances[it.id]?:0).toDouble()})
    }.sortedByDescending{it.value}
}
