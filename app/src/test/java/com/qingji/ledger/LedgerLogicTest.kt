package com.qingji.ledger

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class LedgerLogicTest {
    @Test fun centsNeverUseFloatingPoint() { assertEquals(1L,parseMoney("0.01"));assertEquals(12345L,parseMoney("123.45"));assertEquals(MAX_MONEY,parseMoney("99999999.99"));listOf("0","-1","1.001","1e3","NaN","100000000.00",".5").forEach{assertNull(it,parseMoney(it))};assertEquals("99999999.99",money(MAX_MONEY)) }
    @Test fun refundsAreNegativeSpendingAndSavingsAreSeparate(){val expense=Entry(id="e",type=EXPENSE,amount=10000,categoryId="expense-0");val income=Entry(type=INCOME,amount=50000,categoryId="income-0");val refund=Entry(type=REFUND,amount=2500,categoryId="expense-0",refundOf="e");val deleted=expense.copy(id="deleted",deletedAt=1);val t=totals(listOf(expense,income,refund,deleted));assertEquals(7500L,t.expense);assertEquals(42500L,t.surplus);assertEquals(2500L,t.refunds);val s=Snapshot(entries=listOf(expense),events=listOf(GoalEvent(goalId="g",amount=5000)));assertEquals(5000L,s.progress("g"));assertEquals(10000L,totals(s.entries).expense)}
    @Test fun currentMonthUsesSameDayCountAndFullMonthUsesFullPreviousMonth(){val range=periodRange(Period.MONTH,LocalDate.of(2026,11,9),LocalDate.of(2026,11,9));assertEquals(9L,range.days);assertEquals(DateRange(LocalDate.of(2026,10,1),LocalDate.of(2026,10,9)),comparisonRange(Period.MONTH,range));val april=periodRange(Period.MONTH,LocalDate.of(2026,4,2),LocalDate.of(2026,11,9));assertEquals(LocalDate.of(2026,3,31),comparisonRange(Period.MONTH,april).end)}
    @Test fun leapYearAndMondayWeek(){val leap=periodRange(Period.MONTH,LocalDate.of(2028,2,10),LocalDate.of(2029,1,1));assertEquals(29,leap.end.dayOfMonth);val week=periodRange(Period.WEEK,LocalDate.of(2026,11,9),LocalDate.of(2026,11,11));assertEquals(LocalDate.of(2026,11,9),week.start);assertEquals(3L,week.days)}
    @Test fun reminderSkipsPastTimeAndHandlesWeekdays(){val s=Settings(enabled=true,time="21:30",days=setOf(1,3));val monday=ZonedDateTime.of(2026,11,9,22,0,0,0,ZoneId.of("Asia/Shanghai"));assertEquals(LocalDate.of(2026,11,11),nextReminder(s,monday)?.toLocalDate());assertNull(nextReminder(s.copy(enabled=false),monday));assertNull(nextReminder(s.copy(days=emptySet()),monday))}
    @Test fun defaultBudgetNeverOverridesExplicitMonth(){val s=Snapshot(budgets=listOf(Budget("*","",500000),Budget("2026-11","",420000)));assertEquals(420000L,s.budget("2026-11")?.limit);assertEquals(500000L,s.budget("2026-12")?.limit)}
    @Test fun fiftyThousandEntriesAggregateCorrectly(){val list=List(50_000){Entry(id=it.toString(),type=EXPENSE,amount=123,categoryId="expense-0")};val begin=System.nanoTime();assertEquals(6_150_000L,totals(list).expense);println("50k aggregate milliseconds="+(System.nanoTime()-begin)/1_000_000)}
}
