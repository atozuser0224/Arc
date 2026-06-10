@file:JvmName("Movement")

package dev.arc.api.movement

import dev.arc.api.nms.nms
import org.bukkit.entity.Entity
import org.bukkit.util.Vector

/**
 * Movement & physics controls. Most are thin wrappers over stable Bukkit API; [noClip] reaches the NMS
 * `noPhysics` flag through the bridge (so it no-ops without arc-server present).
 */

/** Whether gravity applies to this entity. */
public var Entity.gravity: Boolean
    get() = hasGravity()
    set(value) {
        setGravity(value)
    }

/** Mount this entity onto [vehicle]. */
public fun Entity.ride(vehicle: Entity) {
    vehicle.addPassenger(this)
}

/** Dismount this entity from whatever it is riding. */
public fun Entity.dismount() {
    vehicle?.removePassenger(this)
}

/** Add an impulse in [direction] (normalised) scaled by [strength] to current velocity. */
public fun Entity.push(direction: Vector, strength: Double) {
    velocity = velocity.add(direction.clone().normalize().multiply(strength))
}

/** Set this entity's facing without moving it. */
public fun Entity.rotate(yaw: Float, pitch: Float) {
    setRotation(yaw, pitch)
}

/** Server-side no-clip (NMS `noPhysics`): the entity passes through blocks. Requires the NMS bridge. */
public var Entity.noClip: Boolean
    get() = nms["noPhysics"].unwrap<Boolean>() ?: false
    set(value) {
        nms["noPhysics"] = value
    }
