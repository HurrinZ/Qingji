package com.qingji.ledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseDeviceTest {
    @Test fun assetTransactionsAndArchiveRules()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build();val repo=Repository(context,db);repo.initialize()
        repo.saveAsset(AssetAccount(id="bank",name="bank",openingBalance=100000));repo.saveAsset(AssetAccount(id="wx",name="wx",kind="WECHAT",openingBalance=50000))
        val e=Entry(id="purchase",type=EXPENSE,amount=3000,categoryId="expense-0",assetId="wx");repo.save(e)
        repo.saveTransfer(AssetTransfer(fromAssetId="bank",toAssetId="wx",amount=1000))
        Assert.assertEquals(48000L,repo.snapshot().assetBalances()["wx"])
        repo.delete(e.id);Assert.assertEquals(51000L,repo.snapshot().assetBalances()["wx"])
        try{repo.archiveAsset("wx",true);Assert.fail("Must not hide a nonzero asset")}catch(_:IllegalArgumentException){}
        repo.calibrateAsset("wx",0,"reconcile");repo.archiveAsset("wx",true)
        try{repo.undo(e);Assert.fail("Must restore an archived account before changing its balance")}catch(_:IllegalArgumentException){}
        repo.archiveAsset("wx",false);repo.undo(e);Assert.assertEquals(-3000L,repo.snapshot().assetBalances()["wx"])
        db.close()
    }
    @Test fun databasePersistsAndRestoresAtomically()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="integration-test.db";context.deleteDatabase(name)
        var db=Room.databaseBuilder(context,LedgerDatabase::class.java,name).build()
        val repo=Repository(context,db);repo.initialize()
        val e=Entry(id="integration",type=EXPENSE,amount=1234,categoryId="expense-0")
        repo.save(e);val backup=repo.snapshot();db.close()
        db=Room.databaseBuilder(context,LedgerDatabase::class.java,name).build()
        val reopened=Repository(context,db);Assert.assertEquals(1234L,reopened.dao.entry("integration")?.amount)
        reopened.restore(backup);reopened.restore(backup);Assert.assertEquals(1,reopened.dao.entries().size)
        val r=Entry(type=REFUND,amount=1000,categoryId="expense-0",refundOf=e.id);reopened.save(r)
        try{reopened.save(r.copy(id="over",amount=1000));Assert.fail("Must reject excess refund")}catch(_:IllegalArgumentException){}
        Assert.assertEquals(2,reopened.dao.entries().size)
        db.close();context.deleteDatabase(name);Unit
    }
    @Test fun billSwipeActionsPersistDeleteUndoAndNoteWithoutChangingBalance()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build();val repo=Repository(context,db);repo.initialize()
        repo.saveAsset(AssetAccount(id="wx",name="微信",kind="WECHAT",openingBalance=10000))
        val e=Entry(id="swipe",type=EXPENSE,amount=1234,categoryId="expense-0",assetId="wx");repo.save(e)
        repo.updateNote(e.id,"午餐");Assert.assertEquals("午餐",repo.dao.entry(e.id)?.note);Assert.assertEquals(8766L,repo.snapshot().assetBalances()["wx"])
        val old=repo.delete(e.id);Assert.assertTrue(repo.snapshot().live().isEmpty());Assert.assertEquals(10000L,repo.snapshot().assetBalances()["wx"])
        try{repo.updateNote(e.id,"late");Assert.fail("Note edits must not restore deleted bills")}catch(_:IllegalArgumentException){}
        repo.undo(old);Assert.assertEquals("午餐",repo.snapshot().live().single().note);Assert.assertEquals(8766L,repo.snapshot().assetBalances()["wx"])
        db.close()
    }
}
