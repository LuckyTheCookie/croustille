package fr.croustille.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefs by preferencesDataStore("croustille")
private val RESTO = intPreferencesKey("resto_code")
private val REGION = intPreferencesKey("region_code")
private val DONE = booleanPreferencesKey("onboarding_done")
private val MENU13H = booleanPreferencesKey("menu13h")
private val STOCK_ON = booleanPreferencesKey("stock_on")
private val RDV_ON = booleanPreferencesKey("rdv_retrait")
private val STOCK_H = intPreferencesKey("stock_hours")
private val STOCK_SEUIL = intPreferencesKey("stock_seuil")
private val LAST_MENU = stringPreferencesKey("last_menu_date")
private val LAST_STOCK = intPreferencesKey("last_stock_min")
private val LAST_STOCK_DATE = stringPreferencesKey("last_stock_date")

class Prefs(private val ctx: Context) {
    val restoCode: Flow<Int> = ctx.prefs.data.map { it[RESTO] ?: FONDERIE_CODE }
    val regionCode: Flow<Int> = ctx.prefs.data.map { it[REGION] ?: STRASBOURG_CODE }
    val onboardingDone: Flow<Boolean> = ctx.prefs.data.map { it[DONE] ?: false }
    val menu13h: Flow<Boolean> = ctx.prefs.data.map { it[MENU13H] ?: false }
    val stockOn: Flow<Boolean> = ctx.prefs.data.map { it[STOCK_ON] ?: false }
    val rdvRetrait: Flow<Boolean> = ctx.prefs.data.map { it[RDV_ON] ?: false }
    val stockHours: Flow<Int> = ctx.prefs.data.map { it[STOCK_H] ?: 4 }
    val stockSeuil: Flow<Int> = ctx.prefs.data.map { it[STOCK_SEUIL] ?: 10 }
    val lastMenuDate: Flow<String?> = ctx.prefs.data.map { it[LAST_MENU] }
    val lastStockMin: Flow<Int?> = ctx.prefs.data.map { it[LAST_STOCK] }
    val lastStockDate: Flow<String?> = ctx.prefs.data.map { it[LAST_STOCK_DATE] }

    suspend fun choisirResto(region: Int, resto: Int) {
        ctx.prefs.edit {
            it[REGION] = region
            it[RESTO] = resto
            it[DONE] = true
        }
    }

    suspend fun recommencer() { ctx.prefs.edit { it[DONE] = false } }

    suspend fun setMenu13h(v: Boolean) { ctx.prefs.edit { it[MENU13H] = v } }
    suspend fun setRdvRetrait(v: Boolean) { ctx.prefs.edit { it[RDV_ON] = v } }
    suspend fun setStockOn(v: Boolean) { ctx.prefs.edit { it[STOCK_ON] = v } }
    suspend fun setStockHours(v: Int) { ctx.prefs.edit { it[STOCK_H] = v } }
    suspend fun setStockSeuil(v: Int) { ctx.prefs.edit { it[STOCK_SEUIL] = v } }
    suspend fun setLastMenuDate(v: String) { ctx.prefs.edit { it[LAST_MENU] = v } }
    suspend fun setLastStock(min: Int?, date: String?) {
        ctx.prefs.edit {
            if (min == null) it.remove(LAST_STOCK) else it[LAST_STOCK] = min
            if (date == null) it.remove(LAST_STOCK_DATE) else it[LAST_STOCK_DATE] = date
        }
    }
}
