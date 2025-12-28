package com.durabilityplus.logic.degrade;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.wrappers.BlockPosition;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Sends client-side block crack animations (requires ProtocolLib).
 * Provides both the original (World, Location, animId, stage) API and
 * convenience overloads used by listeners.
 */
public final class MiningAnimationHelper {

    private final Plugin plugin;
    private final ProtocolManager manager;

    public MiningAnimationHelper(Plugin plugin) {
        this.plugin = plugin;
        this.manager = ProtocolLibrary.getProtocolManager();
    }

    /** View distance filter (squared) — 64 blocks radius by default. */
    private static final int VIEW_RADIUS_SQ = 64 * 64;

    /** Optional: remember last stage per (animationId, blockPos) to avoid resending same stage */
    private final Map<Long, Integer> lastStage = new HashMap<>();

    /** Stable key for (animId + xyz). Safer than bit-packing into 21-bit lanes. */
    private static long key(int animationId, int x, int y, int z) {
        long k = 31L * (31L * (31L * animationId + x) + y) + z;
        return k;
    }

    // === BASE API (unchanged signature) ===
    // world/loc callers keep working
    public void sendCrack(World world, Location loc, int animationId, int stage) {
        if (world == null || loc == null) return;

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        int clamped = Math.max(-1, Math.min(9, stage));

        // Throttle: forward only if stage actually changed.
        // IMPORTANT: always forward -1 clears even if cached, to guarantee overlays are removed.
        long k = key(animationId, x, y, z);
        Integer prev = lastStage.get(k);
        if (prev != null && prev == clamped && clamped != -1) return;
        lastStage.put(k, clamped);

        try {
            var packet = manager.createPacket(PacketType.Play.Server.BLOCK_BREAK_ANIMATION);
            packet.getIntegers().write(0, animationId); // animation id (we use miner entityId by convention)
            packet.getBlockPositionModifier().write(0, new BlockPosition(x, y, z));
            packet.getIntegers().write(1, clamped);

            // Send to nearby players in same world
            for (Player viewer : world.getPlayers()) {
                if (viewer.getWorld() != world) continue;
                if (viewer.getLocation().distanceSquared(loc) > VIEW_RADIUS_SQ) continue;
                manager.sendServerPacket(viewer, packet);
            }
        } catch (Exception ex) {
            // Non-spammy: prints only if something is actually wrong (e.g., ProtocolLib mismatch)
            plugin.getLogger().warning("[DurabilityPlus] Failed to send crack packet: " + ex.getMessage());
        }
    }

    // Convenience overloads used by listeners (id = miner entity id, not viewer id)
    public void sendCrack(Player miner, Block block, int stage) {
        if (miner == null || block == null) return;
        sendCrack(miner.getWorld(), block.getLocation(), miner.getEntityId(), stage);
    }

    public void clearCrack(Player miner, Block block) {
        sendCrack(miner, block, -1);
    }

    /* -----------------------------------------------------------
       trackBreaking helper (used by delay listener):
       factor: 0.0..1.0 → mapped to crack stage 0..9
       steps is ignored here (listener controls timing).
       ----------------------------------------------------------- */
    public void trackBreaking(Player viewer, Location loc, double factor, int steps) {
        if (viewer == null || loc == null) return;
        double f = Math.max(0.0, Math.min(1.0, factor));
        int stage = (int) Math.round(f * 9.0);
        sendCrack(viewer.getWorld(), loc, viewer.getEntityId(), stage);
    }

    public void trackBreaking(Player viewer, Block block, double factor, int steps) {
        if (block == null) return;
        trackBreaking(viewer, block.getLocation(), factor, steps);
    }

    /** Optional: clear helper’s internal cache (e.g., on plugin reload) */
    public void clearAll() {
        lastStage.clear();
    }
}