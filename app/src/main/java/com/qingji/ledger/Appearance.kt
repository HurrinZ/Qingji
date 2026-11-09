package com.qingji.ledger

import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

data class AccentOption(val id:String,val title:String,val hex:String)
val ACCENT_OPTIONS=listOf(
    AccentOption("SUN","暖阳黄","#FFDA63"),
    AccentOption("FOREST","森林绿","#78BE9D"),
    AccentOption("OCEAN","海盐蓝","#80B2ED"),
    AccentOption("LAVENDER","暮光紫","#B39BE5"),
    AccentOption("ROSE","玫瑰粉","#EA9CB5")
)
fun validAccent(raw:String):Boolean=ACCENT_OPTIONS.any{it.id==raw}||raw.matches(Regex("#[0-9a-fA-F]{6}"))
fun normalizeAccent(raw:String):String=if(validAccent(raw))raw.uppercase(Locale.ROOT)else "SUN"
fun accentHex(raw:String):String=ACCENT_OPTIONS.find{it.id==raw}?.hex?:raw.takeIf{it.matches(Regex("#[0-9a-fA-F]{6}"))}?.uppercase(Locale.ROOT)?:ACCENT_OPTIONS.first().hex
fun rgb(hex:String):Int=hex.removePrefix("#").toInt(16)
fun colorHex(value:Int):String=String.format(Locale.ROOT,"#%06X",value and 0xFFFFFF)
fun mixRgb(from:Int,to:Int,weight:Double):Int{
    val w=weight.coerceIn(0.0,1.0)
    fun c(shift:Int)=((((from shr shift) and 255)*(1-w)+((to shr shift) and 255)*w).roundToInt())
    return (c(16) shl 16) or (c(8) shl 8) or c(0)
}
private fun luminance(rgb:Int):Double{
    fun channel(shift:Int):Double{val c=((rgb shr shift) and 255)/255.0;return if(c<=0.04045)c/12.92 else ((c+0.055)/1.055).pow(2.4)}
    return .2126*channel(16)+.7152*channel(8)+.0722*channel(0)
}
fun contrastRatio(a:Int,b:Int):Double{val x=luminance(a);val y=luminance(b);return (maxOf(x,y)+.05)/(minOf(x,y)+.05)}
fun readableInk(background:Int):Int{
    val dark=contrastRatio(background,0x202124);val light=contrastRatio(background,0xFFFFFF)
    if(maxOf(dark,light)<4.5)return 0x000000
    return if(dark>=light)0x202124 else 0xFFFFFF
}
fun contrastingAccent(seed:Int,background:Int):Int{
    if(contrastRatio(seed,background)>=4.5)return seed
    val target=if(luminance(background)>.4)0x000000 else 0xFFFFFF
    for(i in 1..100){val candidate=mixRgb(seed,target,i/100.0);if(contrastRatio(candidate,background)>=4.5)return candidate}
    return target
}
data class PaletteColors(val primary:Int,val onPrimary:Int,val container:Int,val onContainer:Int,val background:Int,val surface:Int,val soft:Int)
fun paletteColors(accent:String,dark:Boolean):PaletteColors{
    val seed=rgb(accentHex(accent));val bg=if(dark)0x14171C else 0xF7F8FA
    val surface=if(dark)0x1C2026 else 0xFFFFFF
    val primary=contrastingAccent(seed,surface)
    val container=if(dark)mixRgb(seed,0x20242B,.65)else seed
    return PaletteColors(primary,readableInk(primary),container,readableInk(container),bg,surface,if(dark)mixRgb(seed,0x20242B,.88)else mixRgb(seed,0xFFFFFF,.88))
}

/** Decimal keypad input stays as text until the existing integer-cent parser commits it. */
fun keypadInput(current:String,key:String):String{
    if(key=="DEL")return current.dropLast(1)
    if(key=="CLEAR")return ""
    if(key==".")return if('.' in current)current else (current.ifEmpty{"0"}+".")
    if(key.length!=1||key[0] !in '0'..'9')return current
    val next=if(current=="0")key else current+key
    val parts=next.split('.')
    if(parts[0].length>8 || parts.getOrNull(1)?.length?.let{it>2}==true)return current
    return next
}
fun keypadMoney(value:String):Long?=parseMoney(value.trimEnd('.'))
