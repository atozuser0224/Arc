@file:JvmName("Skins")

package dev.arc.api.skin

import dev.arc.api.http.httpGet
import dev.arc.api.profile.applySkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player
import java.util.UUID

public data class SkinData(val value: String, val signature: String)

/** Fetch a player's skin from the Mojang API by [uuid]. Returns null on failure. */
public suspend fun fetchSkin(uuid: UUID): SkinData? = withContext(Dispatchers.IO) {
    runCatching {
        val profileUrl = "https://sessionserver.mojang.com/session/minecraft/profile/$uuid?unsigned=false"
        val result = httpGet(profileUrl)
        if (!result.isSuccess) return@runCatching null

        val body = result.body
        val valueMatch = Regex("\"value\"\\s*:\\s*\"([^\"]+)\"").find(body) ?: return@runCatching null
        val sigMatch = Regex("\"signature\"\\s*:\\s*\"([^\"]+)\"").find(body) ?: return@runCatching null
        SkinData(valueMatch.groupValues[1], sigMatch.groupValues[1])
    }.getOrNull()
}

/** Fetch a skin by Minecraft username and apply it to [player]. Returns true on success. */
public suspend fun Player.applySkinFromUsername(username: String): Boolean {
    val uuidResult = httpGet("https://api.mojang.com/users/profiles/minecraft/$username")
    if (!uuidResult.isSuccess) return false
    val uuidStr = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(uuidResult.body)?.groupValues?.get(1)
        ?: return false
    val uuid = runCatching {
        UUID.fromString(
            uuidStr.replace(Regex("(.{8})(.{4})(.{4})(.{4})(.{12})"), "$1-$2-$3-$4-$5"),
        )
    }.getOrNull() ?: return false
    val skin = fetchSkin(uuid) ?: return false
    applySkin(skin.value, skin.signature)
    return true
}
