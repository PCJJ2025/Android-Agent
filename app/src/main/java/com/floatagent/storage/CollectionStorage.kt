package com.floatagent.storage

import android.content.Context
import com.floatagent.model.Category
import com.floatagent.model.SavedPlace
import org.json.JSONArray
import org.json.JSONObject

object CollectionStorage {

    private const val PREF_NAME = "float_agent_collections"
    private const val KEY_PLACES = "places"

    fun save(context: Context, place: SavedPlace) {
        val all = getAll(context).toMutableList()
        all.removeAll { it.id == place.id } // 去重
        all.add(0, place)
        persist(context, all)
    }

    fun getAll(context: Context): List<SavedPlace> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PLACES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun delete(context: Context, id: String) {
        val all = getAll(context).filter { it.id != id }
        persist(context, all)
    }

    fun replaceAll(context: Context, places: List<SavedPlace>) {
        persist(context, places)
    }

    private fun persist(context: Context, places: List<SavedPlace>) {
        val arr = JSONArray(places.map { toJson(it) })
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PLACES, arr.toString()).apply()
    }

    private fun toJson(p: SavedPlace) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("category", p.category.name)
        put("address", p.address)
        put("lat", p.lat)
        put("lng", p.lng)
        put("items", JSONArray(p.items))
        put("note", p.note)
        put("source", p.source)
        put("savedAt", p.savedAt)
        put("geocodeTried", p.geocodeTried)
    }

    private fun fromJson(j: JSONObject): SavedPlace {
        val itemsJson = j.optJSONArray("items") ?: JSONArray()
        val items = (0 until itemsJson.length())
            .map { itemsJson.getString(it) }
        return SavedPlace(
            id = j.getString("id"),
            name = j.getString("name"),
            category = Category.fromLabel(j.optString("category", "OTHER")),
            address = j.optString("address"),
            lat = j.optDouble("lat", 0.0),
            lng = j.optDouble("lng", 0.0),
            items = items,
            note = j.optString("note"),
            source = j.optString("source"),
            savedAt = j.optLong("savedAt"),
            geocodeTried = j.optBoolean("geocodeTried", false)
        )
    }
}
