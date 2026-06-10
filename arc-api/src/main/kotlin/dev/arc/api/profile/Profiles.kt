@file:JvmName("Profiles")

package dev.arc.api.profile

import com.destroystokyo.paper.profile.ProfileProperty
import org.bukkit.entity.Player

/**
 * Player GameProfile / skin helpers over Paper's [com.destroystokyo.paper.profile.PlayerProfile] API - the
 * supported way to read/replace skin texture properties (no raw `GameProfile` reflection).
 *
 * Note: applying a profile takes full visual effect when the client (re)receives the player; for an instant
 * live refresh you additionally need a player-info resend, which is a packet concern.
 */

/** Replace this player's skin texture property ([signature] required for verified skins, else `null`). */
public fun Player.applySkin(texturesValue: String, signature: String? = null) {
    val profile = playerProfile
    profile.setProperty(ProfileProperty("textures", texturesValue, signature))
    playerProfile = profile
}

/** Copy [other]'s skin onto this player. */
public fun Player.copySkinFrom(other: Player) {
    val texture = other.playerProfile.properties.firstOrNull { it.name == "textures" } ?: return
    applySkin(texture.value, texture.signature)
}

/** This player's base64 skin texture value, or `null` if none. */
public val Player.skinTexture: String?
    get() = playerProfile.properties.firstOrNull { it.name == "textures" }?.value
