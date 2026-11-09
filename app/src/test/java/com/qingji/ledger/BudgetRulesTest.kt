package com.qingji.ledger

import org.junit.Assert.*
import org.junit.Test

class BudgetRulesTest {
    private fun accounts() = listOf(
        AssetAccount(id = "bank", name = "工资卡", kind = "BANK"),
        AssetAccount(id = "card", name = "信用卡", kind = "CREDIT_CARD"),
        AssetAccount(id = "huabei", name = "花呗", kind = "HUABEI"),
        AssetAccount(id = "credit", name = "信用支付", kind = "CREDIT"),
        AssetAccount(id = "cash", name = "现金", kind = "CASH")
    )
    private fun expense(id: String, amount: Long, assetId: String?) = Entry(id = id, type = EXPENSE, amount = amount, categoryId = "expense-0", assetId = assetId, date = "2026-11-09")
    private fun refund(id: String, amount: Long, original: String, assetId: String?) = Entry(id = id, type = REFUND, amount = amount, categoryId = "expense-0", refundOf = original, assetId = assetId, date = "2026-11-09")

    @Test fun creditAssetsAreExcludedFromBudgetButNotFromTotals() {
        val entries = listOf(expense("e1", 10000, "bank"), expense("e2", 20000, "card"), expense("e3", 5000, "huabei"), expense("e4", 3000, "credit"))
        val s = Snapshot(entries = entries, assets = accounts(), settings = Settings())
        assertEquals(setOf("card", "huabei", "credit"), s.budgetExcludedAssetIds())
        assertEquals(10000L, budgetExpense(entries, s.budgetExcludedAssetIds()))
        assertEquals(38000L, totals(entries).expense)
    }

    @Test fun emptyExclusionKeepsLegacyNetExpense() {
        val entries = listOf(expense("e1", 10000, "bank"), refund("r1", 2500, "e1", "cash"))
        assertEquals(totals(entries).expense, budgetExpense(entries, emptySet()))
        assertEquals(7500L, budgetExpense(entries, emptySet()))
    }

    @Test fun refundFollowsOriginalExpenseAccountNotItsOwn() {
        val entries = listOf(
            expense("e1", 10000, "card"), refund("r1", 3000, "e1", "cash"),
            expense("e2", 8000, "bank"), refund("r2", 2000, "e2", "card")
        )
        assertEquals(6000L, budgetExpense(entries, setOf("card")))
    }

    @Test fun manualExclusionAndAutoExclusionToggle() {
        val entries = listOf(expense("e1", 10000, "cash"), expense("e2", 7000, "bank"))
        val s = Snapshot(entries = entries, assets = accounts(), settings = Settings(budgetExcludedAssets = setOf("cash")))
        assertEquals(setOf("card", "huabei", "credit", "cash"), s.budgetExcludedAssetIds())
        assertEquals(7000L, budgetExpense(entries, s.budgetExcludedAssetIds()))
        val off = s.copy(settings = s.settings.copy(budgetExcludeCredit = false))
        assertEquals(setOf("cash"), off.budgetExcludedAssetIds())
        assertEquals(7000L, budgetExpense(entries, off.budgetExcludedAssetIds()))
    }

    @Test fun deletedEntriesNeverCountTowardsBudget() {
        val entries = listOf(expense("e1", 10000, "bank").copy(deletedAt = 1L), expense("e2", 5000, "cash"))
        assertEquals(5000L, budgetExpense(entries, setOf("card")))
    }

    @Test fun categoryBudgetUsesTheSameExclusionRule() {
        val entries = listOf(
            expense("e1", 10000, "bank").copy(categoryId = "expense-1"),
            expense("e2", 7000, "card").copy(categoryId = "expense-1"),
            expense("e3", 2000, "cash").copy(categoryId = "expense-2")
        )
        assertEquals(10000L, budgetExpense(entries.filter { it.categoryId == "expense-1" }, setOf("card")))
    }

    @Test fun unassignedExpenseStaysInBudget() {
        val entries = listOf(expense("e1", 1234, null))
        assertEquals(1234L, budgetExpense(entries, setOf("card")))
    }

    @Test fun newCreditKindsAreRecognised() {
        assertTrue(isCreditAsset("CREDIT_CARD")); assertTrue(isCreditAsset("HUABEI")); assertTrue(isCreditAsset("CREDIT"))
        assertFalse(isCreditAsset("BANK")); assertFalse(isCreditAsset("OTHER"))
        assertEquals("信用卡", ASSET_KINDS["CREDIT_CARD"]); assertEquals("花呗", ASSET_KINDS["HUABEI"])
    }
}
