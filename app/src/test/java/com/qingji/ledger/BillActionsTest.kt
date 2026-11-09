package com.qingji.ledger

import org.junit.Assert.*
import org.junit.Test

class BillActionsTest{
    private val expense=Entry(id="e",type=EXPENSE,amount=1234,categoryId="expense-0",assetId="wx",createdAt=10,updatedAt=10,revision=3)
    private fun snapshot(e:Entry)=Snapshot(entries=listOf(e),assets=listOf(AssetAccount(id="wx",name="微信",kind="WECHAT",openingBalance=10000)))
    @Test fun deletionHidesBillReversesBalanceAndRetainsUndo(){
        val plan=prepareEntryDeletion(expense,0,20)
        assertEquals(0,snapshot(plan.after).live().size);assertEquals(10000L,snapshot(plan.after).assetBalances()["wx"])
        assertEquals(0L,totals(snapshot(plan.after).live()).expense)
        assertEquals(8766L,snapshot(plan.before).assetBalances()["wx"]);assertEquals(4L,plan.after.revision)
        assertEquals(20L,plan.after.deletedAt);assertEquals(20L,plan.after.updatedAt)
    }
    @Test fun incomeAndRefundDeletionReverseCorrectAmounts(){
        for(type in listOf(INCOME,REFUND)){
            val e=expense.copy(type=type);val plan=prepareEntryDeletion(e,0,20)
            assertEquals(11234L,snapshot(plan.before).assetBalances()["wx"]);assertEquals(10000L,snapshot(plan.after).assetBalances()["wx"])
        }
    }
    @Test fun missingDeletedAndLinkedRefundProtectionRemain(){
        assertThrows(IllegalArgumentException::class.java){prepareEntryDeletion(null,0,20)}
        assertThrows(IllegalArgumentException::class.java){prepareEntryDeletion(expense.copy(deletedAt=15),0,20)}
        assertThrows(IllegalArgumentException::class.java){prepareEntryDeletion(expense,100,20)}
        assertNull(expense.deletedAt)
    }
    @Test fun legacyAndUnassignedBillsCanBeDeletedWithoutMovingBalance(){
        for(e in listOf(expense.copy(affectsBalance=false),expense.copy(assetId=null))){
            val plan=prepareEntryDeletion(e,0,20);assertEquals(snapshot(plan.before).assetBalances(),snapshot(plan.after).assetBalances())
            assertTrue(snapshot(plan.after).live().isEmpty())
        }
    }
    @Test fun notePatchChangesOnlyNoteAndAuditFields(){
        val updated=entryWithNote(expense,"午餐",30)
        assertEquals(expense.copy(note="午餐",updatedAt=30,revision=4),updated)
        assertEquals(snapshot(expense).assetBalances(),snapshot(updated).assetBalances())
        assertEquals("",entryWithNote(updated,"",40).note)
    }
    @Test fun notePatchCannotResurrectDeletedBillOrExceedLimit(){
        assertThrows(IllegalArgumentException::class.java){entryWithNote(expense.copy(deletedAt=20),"x",30)}
        assertThrows(IllegalArgumentException::class.java){entryWithNote(null,"x",30)}
        assertThrows(IllegalArgumentException::class.java){entryWithNote(expense,"x".repeat(501),30)}
        assertEquals(500,entryWithNote(expense,"x".repeat(500),30).note.length)
    }
}
