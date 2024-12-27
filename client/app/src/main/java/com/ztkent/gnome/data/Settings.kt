package com.ztkent.gnome.data

import com.google.gson.Gson
import com.ztkent.gnome.model.DeviceListModel


data class GnomeSettings(var tempUnits: String)

fun storeSettings(viewModel: DeviceListModel, settings: GnomeSettings) {
    val gson = Gson()
    val json = gson.toJson(settings)
    val editor = viewModel.gnomeSettings.edit()
    editor.putString("Settings", json)
    editor.apply()
}
fun getSettings(viewModel: DeviceListModel): GnomeSettings {
    val gson = Gson()
    val json = viewModel.gnomeSettings.getString("Settings", null) ?: return GnomeSettings("F")
    return gson.fromJson(json, GnomeSettings::class.java)
}