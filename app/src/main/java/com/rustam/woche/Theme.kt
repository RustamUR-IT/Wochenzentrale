package com.rustam.woche

import android.content.Context

enum class Frame(val label: String) { ROUND("Rund"), BRACKETS("Klammern"), ENGRAVED("Gravur") }
enum class FontStyle(val label: String) { SANS("Klar"), SERIF("Serif"), MONO("Mono") }
enum class Fx(val label: String) { NONE("Keine"), STARS("Sterne"), EMBERS("Glut"), SNOW("Schnee"), CIRCLE("Zauberkreis") }

/** Ein Stil. palette = Farbe je Kategorie in der Reihenfolge von [Cat]. */
class WTheme(
    val id: String,
    val name: String,
    val tagline: String,
    val bgTop: Long,
    val bgBottom: Long,
    val panel: Long,
    val line: Long,
    val text: Long,
    val mute: Long,
    val accent: Long,
    val now: Long,
    val palette: LongArray,
    val frame: Frame,
    val font: FontStyle,
    val fx: Fx,
    val glow: Boolean,
    val caps: Boolean,
) {
    fun cat(c: Cat): Long = palette[c.ordinal]
}

val THEMES: List<WTheme> = listOf(
    WTheme("system", "System-Fenster", "Leuchtende Statusfenster wie in einem Dungeon-Game",
        0xFF05070D, 0xFF0A1430, 0xFF0A1124, 0xFF16223F, 0xFFDDEBFF, 0xFF6E86AD, 0xFF6FD3FF, 0xFFFF4D6D,
        longArrayOf(0xFF7C8CFF, 0xFF4FC3FF, 0xFF5CFFB0, 0xFFFF5C7A, 0xFFFFD84D, 0xFFC77DFF, 0xFF39F0E0, 0xFF8FA3BF),
        Frame.BRACKETS, FontStyle.MONO, Fx.STARS, glow = true, caps = true),
    WTheme("leere", "Leere", "Violettes Licht, Zauberkreis im Hintergrund",
        0xFF07040C, 0xFF170A26, 0xFF120A1E, 0xFF2A1B3D, 0xFFEDE3FF, 0xFF8E7FAD, 0xFFB47CFF, 0xFFFF5FA2,
        longArrayOf(0xFF9A8CFF, 0xFF8F7BFF, 0xFF6FE3C1, 0xFFFF6FA3, 0xFFE8C46A, 0xFFD58CFF, 0xFF7FD6FF, 0xFF9C93B5),
        Frame.BRACKETS, FontStyle.SANS, Fx.CIRCLE, glow = true, caps = false),
    WTheme("grimoire", "Grimoire", "Altes Zauberbuch, Gold auf dunklem Leder",
        0xFF1C130B, 0xFF0B0704, 0xFF21160C, 0xFF3A2A18, 0xFFF3E6C8, 0xFFA89270, 0xFFE3B866, 0xFFE0604A,
        longArrayOf(0xFFB7A6E0, 0xFF7FA7D9, 0xFF9CCB86, 0xFFD9785B, 0xFFE8C064, 0xFFC995C9, 0xFF7CC2B4, 0xFFB8A88A),
        Frame.ENGRAVED, FontStyle.SERIF, Fx.CIRCLE, glow = false, caps = false),
    WTheme("glut", "Drachenglut", "Aufsteigende Funken, warmes Feuer",
        0xFF160604, 0xFF240B05, 0xFF1C0B06, 0xFF3A1A10, 0xFFFFE9DC, 0xFFB98A76, 0xFFFF7A3D, 0xFFFFD24A,
        longArrayOf(0xFFA99BD6, 0xFF7FB0E8, 0xFF9BD67F, 0xFFFF6A3D, 0xFFFFC247, 0xFFF28CB8, 0xFF6FD0C8, 0xFFC7A79A),
        Frame.ROUND, FontStyle.SERIF, Fx.EMBERS, glow = true, caps = false),
    WTheme("frost", "Frostrunen", "Eisiges Blau, leiser Schneefall",
        0xFF061019, 0xFF0D2433, 0xFF0B1A26, 0xFF1C3446, 0xFFE8FBFF, 0xFF7FA3B5, 0xFFA8F0FF, 0xFFFF7A9C,
        longArrayOf(0xFFB5C3FF, 0xFF8FD6FF, 0xFF9FF0D0, 0xFFFF9FB5, 0xFFF5E6A0, 0xFFD9C2FF, 0xFFA8F0FF, 0xFFA9BCC9),
        Frame.BRACKETS, FontStyle.SANS, Fx.SNOW, glow = true, caps = true),
    WTheme("hain", "Elfenhain", "Glühwürmchen im nächtlichen Wald",
        0xFF06120D, 0xFF0E2219, 0xFF0C1A13, 0xFF1E3A2B, 0xFFEAF7EC, 0xFF86A894, 0xFF7FE3A8, 0xFFF2C14E,
        longArrayOf(0xFFB9B0E8, 0xFF8CC8E8, 0xFF9FE39A, 0xFFF0A07A, 0xFFE8D77A, 0xFFE7A8D8, 0xFF7FE3C4, 0xFFA9B8A6),
        Frame.ENGRAVED, FontStyle.SERIF, Fx.STARS, glow = false, caps = false),
    WTheme("klassisch", "Klassisch", "Ruhig und schlicht, ohne Effekte",
        0xFF0F1117, 0xFF0F1117, 0xFF161922, 0xFF262A36, 0xFFE8EAF0, 0xFF8A91A5, 0xFFFF5D62, 0xFFFF5D62,
        longArrayOf(0xFFAAB3F0, 0xFF9CC3FF, 0xFF8FE0AE, 0xFFFFB39F, 0xFFFFD966, 0xFFE0B4F5, 0xFF7FE0E8, 0xFFB8BECE),
        Frame.ROUND, FontStyle.SANS, Fx.NONE, glow = false, caps = false),
)

/** Der aktive Look: Stil plus deine Anpassungen. */
data class Look(
    val theme: WTheme,
    val frame: Frame,
    val font: FontStyle,
    val fx: Fx,
    val glow: Boolean,
    val caps: Boolean,
    val fill: Float,
) {
    fun fg(c: Cat): Long = theme.cat(c)

    companion object {
        fun of(t: WTheme, fill: Float = 0.22f) = Look(t, t.frame, t.font, t.fx, t.glow, t.caps, fill)
    }
}

object LookStore {
    private const val P = "woche"

    fun load(ctx: Context): Look {
        val sp = ctx.getSharedPreferences(P, 0)
        val t = THEMES.firstOrNull { it.id == sp.getString("theme", "system") } ?: THEMES[0]
        val frame = Frame.values().firstOrNull { it.name == sp.getString("frame", null) } ?: t.frame
        val font = FontStyle.values().firstOrNull { it.name == sp.getString("font", null) } ?: t.font
        val fx = Fx.values().firstOrNull { it.name == sp.getString("fx", null) } ?: t.fx
        val glow = if (sp.contains("glow")) sp.getBoolean("glow", t.glow) else t.glow
        val caps = if (sp.contains("caps")) sp.getBoolean("caps", t.caps) else t.caps
        return Look(t, frame, font, fx, glow, caps, sp.getFloat("fill", 0.22f))
    }

    /** Neuer Stil setzt die Feineinstellungen auf die Vorgaben des Stils zurück. */
    fun setTheme(ctx: Context, id: String) {
        ctx.getSharedPreferences(P, 0).edit().putString("theme", id)
            .remove("frame").remove("font").remove("fx").remove("glow").remove("caps").apply()
    }

    fun put(ctx: Context, key: String, value: String) = ctx.getSharedPreferences(P, 0).edit().putString(key, value).apply()
    fun putBool(ctx: Context, key: String, value: Boolean) = ctx.getSharedPreferences(P, 0).edit().putBoolean(key, value).apply()
    fun putFloat(ctx: Context, key: String, value: Float) = ctx.getSharedPreferences(P, 0).edit().putFloat(key, value).apply()
}
