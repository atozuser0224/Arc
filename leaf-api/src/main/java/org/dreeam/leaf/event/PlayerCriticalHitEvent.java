package org.dreeam.leaf.event;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player is about to land a critical hit on an entity.
 * A critical hit occurs when the player is falling, not on ground, not blind,
 * not sprinting, not on a ladder/vine/water, and using a full attack strength swing.
 *
 * <p>Cancelling this event prevents the hit from being a critical hit (damage multiplier
 * of 1.5x is not applied and the crit particles are not shown), but the attack still proceeds normally.</p>
 *
 * <pre>{@code
 * @EventHandler
 * public void onCrit(PlayerCriticalHitEvent e) {
 *     if (e.getPlayer().hasPermission("myplugin.nocrit")) {
 *         e.setCancelled(true);
 *     }
 * }
 * }</pre>
 */
public class PlayerCriticalHitEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;
    private final Entity target;

    public PlayerCriticalHitEvent(@NotNull Player player, @NotNull Entity target) {
        super(player);
        this.target = target;
    }

    /**
     * Returns the entity that the critical hit is being applied to.
     *
     * @return the hit entity
     */
    @NotNull
    public Entity getTarget() {
        return target;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
