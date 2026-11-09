package com.qingji.ledger

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class AssetsLogicTest {
    private fun base()=Snapshot(categories=defaultCategories(),assets=listOf(AssetAccount(id="bank",name="工资卡",openingBalance=1_000_000),AssetAccount(id="wx",name="微信",kind="WECHAT",openingBalance=50_000),AssetAccount(id="ali",name="支付宝",kind="ALIPAY",openingBalance=20_000)),ready=true)
    private fun spend()=Entry(id="expense",type=EXPENSE,amount=3000,categoryId="expense-0",assetId="wx",date="2026-11-01")
    @Test fun fullDesignExampleBalancesAndStatistics(){
        val e=spend();val refund=Entry(type=REFUND,amount=2000,categoryId=e.categoryId,refundOf=e.id,assetId="ali",date="2026-11-02")
        val s=base().copy(entries=listOf(e,refund),transfers=listOf(AssetTransfer(fromAssetId="bank",toAssetId="wx",amount=100_000)))
        assertEquals(mapOf("bank" to 900_000L,"wx" to 147_000L,"ali" to 22_000L),s.assetBalances());assertEquals(1_069_000L,s.assetTotal());assertEquals(1000L,totals(s.entries).expense);assertEquals(0L,totals(s.entries).income)
    }
    @Test fun editingAndSwitchingAccountNeverDoubleDebit(){
        val e=spend();val s=base().copy(entries=listOf(e));assertEquals(47_000L,s.assetBalances()["wx"])
        val edited=e.copy(amount=5000,assetId="bank");val updated=s.copy(entries=listOf(edited));assertEquals(50_000L,updated.assetBalances()["wx"]);assertEquals(995_000L,updated.assetBalances()["bank"])
        assertEquals(995_000L,updated.projectedBalance("bank",edited,edited));assertEquals(47_000L,s.projectedBalance("wx",e,e))
    }
    @Test fun deleteAndUndoRestoreEffects(){val e=spend();val deleted=base().copy(entries=listOf(e.copy(deletedAt=1)));assertEquals(50_000L,deleted.assetBalances()["wx"]);assertEquals(47_000L,deleted.copy(entries=listOf(e)).assetBalances()["wx"])}
    @Test fun linkedHistoricalRecordDoesNotDebit(){val e=spend().copy(affectsBalance=false);val s=base().copy(entries=listOf(e));assertEquals(50_000L,s.assetBalances()["wx"]);assertEquals(3000L,totals(s.entries).expense);assertEquals(1_070_000L,s.assetTotal())}
    @Test fun incomeAndUnassignedIncomeAreDifferent(){val e=Entry(type=INCOME,amount=10000,categoryId="income-0",assetId="bank");assertEquals(1_010_000L,base().copy(entries=listOf(e)).assetBalances()["bank"]);assertEquals(1_000_000L,base().copy(entries=listOf(e.copy(assetId=null))).assetBalances()["bank"])}
    @Test fun transferEditDeleteAndTotals(){val t=AssetTransfer(id="t",fromAssetId="bank",toAssetId="wx",amount=10000);val s=base().copy(transfers=listOf(t));assertEquals(base().assetTotal(),s.assetTotal());assertEquals(0L,totals(s.entries).income);assertEquals(0L,totals(s.entries).expense);assertEquals(base().assetBalances(),s.copy(transfers=listOf(t.copy(deletedAt=1))).assetBalances());assertEquals(40_000L,s.copy(transfers=listOf(t.copy(fromAssetId="wx",toAssetId="ali"))).assetBalances()["wx"])}
    @Test fun calibrationIsAOneTimeDifferenceNotIncome(){val e=spend();val s=base().copy(entries=listOf(e),adjustments=listOf(AssetAdjustment(assetId="wx",delta=3000,targetBalance=50_000)));assertEquals(50_000L,s.assetBalances()["wx"]);assertEquals(0L,totals(s.entries).income);assertEquals(48_000L,s.copy(entries=listOf(e.copy(amount=5000))).assetBalances()["wx"])}
    @Test fun negativeBalanceIsVisibleAndOverflowIsRejected(){val s=base().copy(entries=listOf(spend().copy(amount=100_000)));assertEquals(-50_000L,s.assetBalances()["wx"]);assertThrows(ArithmeticException::class.java){base().copy(adjustments=listOf(AssetAdjustment(assetId="bank",delta=Long.MAX_VALUE,targetBalance=0))).assetBalances()}}
    @Test fun zeroBalanceInputAndRecentDefault(){assertEquals(0L,parseBalance("0.00"));assertEquals(1234L,parseBalance("12.34"));assertNull(parseBalance("-1"));val s=base().copy(entries=listOf(spend().copy(createdAt=5)));assertEquals("wx",s.defaultAssetId());assertEquals("bank",s.copy(assets=s.assets.map{if(it.id=="wx")it.copy(archived=true)else it}).defaultAssetId())}
    @Test fun assetBackupRoundTrip(){val s=base().copy(entries=listOf(spend()),transfers=listOf(AssetTransfer(fromAssetId="bank",toAssetId="wx",amount=1000)),adjustments=listOf(AssetAdjustment(assetId="ali",delta=500,targetBalance=20500)));val restored=BackupCodec.decode(BackupCodec.encode(s));assertEquals(s.assets,restored.assets);assertEquals(s.transfers,restored.transfers);assertEquals(s.adjustments,restored.adjustments);assertEquals(s.entries,restored.entries);assertEquals(s.assetBalances(),restored.assetBalances())}
    @Test fun versionOneBackupHasNoImplicitAssetsOrBalanceEffects(){val legacy=JSONObject(BackupCodec.encode(Snapshot(entries=listOf(spend().copy(assetId=null)),categories=defaultCategories())));legacy.put("schemaVersion",1);legacy.remove("assets");legacy.remove("transfers");legacy.remove("adjustments");legacy.getJSONArray("entries").getJSONObject(0).apply{remove("assetId");remove("affectsBalance")};val decoded=BackupCodec.decode(legacy.toString());assertTrue(decoded.assets.isEmpty());assertNull(decoded.entries.single().assetId);assertFalse(decoded.entries.single().affectsBalance);assertEquals(3000L,totals(decoded.entries).expense)}
    @Test fun badAssetReferencesAndSelfTransfersAreRejected(){val invalid=JSONObject(BackupCodec.encode(base().copy(entries=listOf(spend()))));invalid.getJSONArray("entries").getJSONObject(0).put("assetId","missing");assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(invalid.toString())};val self=JSONObject(BackupCodec.encode(base().copy(transfers=listOf(AssetTransfer(fromAssetId="bank",toAssetId="bank",amount=10)))));assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(self.toString())}}
    @Test fun archivedNonZeroAccountBackupIsRejected(){val s=base().copy(assets=base().assets.map{if(it.id=="bank")it.copy(archived=true)else it});assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(BackupCodec.encode(s))}}
}
