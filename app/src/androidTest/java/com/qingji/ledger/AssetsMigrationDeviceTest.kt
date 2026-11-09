package com.qingji.ledger

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetsMigrationDeviceTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),LedgerDatabase::class.java,emptyList())
    @Test fun migrateOldLedgerWithoutInventingAssetDebits(){
        val name="asset-migration-test.db"
        helper.createDatabase(name,1).apply{execSQL("INSERT INTO entries (id,type,amount,categoryId,date,time,note,refundOf,zoneId,createdAt,updatedAt,deletedAt,revision,syncState) VALUES ('legacy','EXPENSE',3000,'expense-0','2026-11-01','12:00','old record',NULL,'Asia/Shanghai',1,1,NULL,1,'LOCAL')");close()}
        helper.runMigrationsAndValidate(name,2,true,LedgerDatabase.MIGRATION_1_2).apply{query("SELECT amount,assetId,affectsBalance FROM entries WHERE id='legacy'").use{c->Assert.assertTrue(c.moveToFirst());Assert.assertEquals(3000,c.getLong(0));Assert.assertTrue(c.isNull(1));Assert.assertEquals(0,c.getInt(2))};close()}
    }
}
