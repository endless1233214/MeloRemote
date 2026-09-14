package com.versarepair.meloremote.data

import android.content.Context
import androidx.core.content.edit
import com.versarepair.meloremote.data.model.AuthenticationMode

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("meloremote_preferences", Context.MODE_PRIVATE)

    var serverAddress: String
        get() = preferences.getString(KEY_SERVER_ADDRESS, "").orEmpty()
        set(value) = preferences.edit { putString(KEY_SERVER_ADDRESS, value) }

    var username: String
        get() = preferences.getString(KEY_USERNAME, "").orEmpty()
        set(value) = preferences.edit { putString(KEY_USERNAME, value) }

    var authenticationMode: AuthenticationMode
        get() = runCatching {
            AuthenticationMode.valueOf(preferences.getString(KEY_AUTH_MODE, "").orEmpty())
        }.getOrDefault(AuthenticationMode.TOKEN)
        set(value) = preferences.edit { putString(KEY_AUTH_MODE, value.name) }

    var selectedPlayerId: String?
        get() = preferences.getString(KEY_SELECTED_PLAYER, null)
        set(value) = preferences.edit { putString(KEY_SELECTED_PLAYER, value) }

    companion object {
        private const val KEY_SERVER_ADDRESS = "server_address"
        private const val KEY_USERNAME = "username"
        private const val KEY_AUTH_MODE = "authentication_mode"
        private const val KEY_SELECTED_PLAYER = "selected_player_id"
    }
}
