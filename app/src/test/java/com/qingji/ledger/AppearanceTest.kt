package com.qingji.ledger

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class AppearanceTest{
    @Test fun allPresetsAndEdgeCustomColorsHaveReadableText(){
        (ACCENT_OPTIONS.map{it.id}+listOf("#000000","#FFFFFF","#808080","#0000FF","#FF0000","#00FF00")).forEach{accent->
            listOf(false,true).forEach{dark->val p=paletteColors(accent,dark)
                assertTrue(accent,contrastRatio(p.primary,p.surface)>=4.5)
                assertTrue(accent,contrastRatio(p.primary,p.onPrimary)>=4.5)
                assertTrue(accent,contrastRatio(p.container,p.onContainer)>=4.5)
            }
        }
    }
    @Test fun themePersistsThroughBackupAndOldBackupsUseDefault(){
        val s=Snapshot(categories=defaultCategories(),settings=Settings(theme="DARK",accent="#ab12cd"))
        val decoded=BackupCodec.decode(BackupCodec.encode(s))
        assertEquals("#AB12CD",decoded.settings.accent);assertEquals("DARK",decoded.settings.theme)
        val old=JSONObject(BackupCodec.encode(s));old.getJSONObject("settings").remove("accent")
        assertEquals("SUN",BackupCodec.decode(old.toString()).settings.accent)
    }
    @Test fun invalidBackupColorRejected(){
        val root=JSONObject(BackupCodec.encode(Snapshot(categories=defaultCategories())))
        root.getJSONObject("settings").put("accent","red;bad")
        assertThrows(IllegalArgumentException::class.java){BackupCodec.decode(root.toString())}
    }
    @Test fun decimalKeypadNeverRoundsMoney(){
        var amount="";listOf("0",".","1","0","9").forEach{amount=keypadInput(amount,it)}
        assertEquals("0.10",amount);assertEquals(10L,keypadMoney(amount))
        amount=keypadInput(amount,"DEL");assertEquals(10L,keypadMoney(amount))
        assertEquals("0.1",keypadInput(amount,"."));assertNull(keypadMoney("0"))
        assertEquals(100L,keypadMoney("1."));assertEquals("",keypadInput(amount,"CLEAR"))
    }
    @Test fun keypadRestrictsToExistingMoneyLimits(){
        var value="";"999999999".forEach{value=keypadInput(value,it.toString())}
        assertEquals("99999999",value);value=keypadInput(value,".");value=keypadInput(value,"9");value=keypadInput(value,"9")
        assertEquals(MAX_MONEY,keypadMoney(value));assertEquals(value,keypadInput(value,"9"))
        assertEquals("0.",keypadInput("","."));assertEquals("5",keypadInput("0","5"))
    }
}
