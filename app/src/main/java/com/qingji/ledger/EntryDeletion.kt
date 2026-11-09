package com.qingji.ledger

data class EntryDeletion(val before:Entry,val after:Entry)
fun prepareEntryDeletion(entry:Entry?,refunded:Long,now:Long):EntryDeletion{
    val saved=requireNotNull(entry){"账单不存在，请刷新后重试"}
    require(saved.deletedAt==null){"账单已删除"}
    require(saved.type!=EXPENSE||refunded==0L){"请先删除这笔支出关联的退款账单"}
    return EntryDeletion(saved,saved.copy(deletedAt=now,updatedAt=now,revision=Math.addExact(saved.revision,1L)))
}
fun entryWithNote(entry:Entry?,note:String,now:Long):Entry{
    val saved=requireNotNull(entry){"账单不存在"}
    require(saved.deletedAt==null){"账单已删除"}
    require(note.length<=500){"备注最多500字"}
    return saved.copy(note=note,updatedAt=now,revision=Math.addExact(saved.revision,1L))
}
