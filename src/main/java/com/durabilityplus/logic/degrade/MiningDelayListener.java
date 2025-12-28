package com.durabilityplus.logic.degrade;

import com.durabilityplus.DurabilityPlusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Delay-mode mining controller (Paper-first; safe fallbacks).
 *
 * Flow:
 * - BlockDamageEvent starts/refreshes a Track and ALWAYS cancels vanilla digging.
 * - Baseline break-time is computed:
 * (1) Paper Attribute.BLOCK_BREAK_SPEED (reflective),
 * (2) Paper BlockData#getDestroySpeed(ItemStack, boolean) (reflective),
 * (3) heuristic (tags).
 * - A 2-tick scheduler advances cracks 0..9; when finished, we break server-side and clear cracks.
 * - BlockBreakEvent is always cancelled while tracked — we own the break timing.
 * - If ProtocolLib is present, BLOCK_DIG packets are cancelled for tracked players.
 */
public final class MiningDelayListener implements Listener {

    private final DurabilityPlusPlugin plugin;
    private final DegradationConfig dcfg;
    private final MiningAnimationHelper anim;

    /** Active track per player */
    private final Map<UUID, Track> tracks = new HashMap<>();

    /** 2-tick driver task id */
    private int tickTaskId = -1;

    /** Simple game-tick clock (we bump by +2 every scheduler run) */
    private long tickClock = 0L;

    public MiningDelayListener(DurabilityPlusPlugin plugin,
                               DegradationConfig dcfg,
                               MiningAnimationHelper animOrNull) {
        this.plugin = plugin;
        this.dcfg = dcfg;
        this.anim = animOrNull;


        tryHookProtocolDigCancel();

        this.tickTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            tickClock += 2;
            tick();
        }, 0L, 2L);
    }

    // -----------------------------------------------------------
    // ProtocolLib (optional) — cancel client dig when we own it
    // -----------------------------------------------------------
    private void tryHookProtocolDigCancel() {
        try {
            com.comphenix.protocol.ProtocolManager pm =
                    com.comphenix.protocol.ProtocolLibrary.getProtocolManager();

            com.comphenix.protocol.events.PacketAdapter adapter =
                    new com.comphenix.protocol.events.PacketAdapter(
                            plugin,
                            com.comphenix.protocol.events.ListenerPriority.HIGHEST,
                            com.comphenix.protocol.PacketType.Play.Client.BLOCK_DIG
                    ) {
                        @Override
                        public void onPacketReceiving(com.comphenix.protocol.events.PacketEvent e) {
                            if (e.isCancelled()) return;
                            if (!dcfg.isEnabled() || !dcfg.miningEnabled() || dcfg.miningModeEffect()) return;

                            // If we are currently tracking this player, cancel ALL dig actions so
                            // vanilla never advances/finishes the break on the client side.
                            UUID id = e.getPlayer().getUniqueId();
                            if (tracks.containsKey(id)) {
                                try {
                                    var pt = e.getPacket().getPlayerDigTypes().readSafely(0);
                                    if (pt != null) {
                                        String name = pt.name();
                                        if ("START_DESTROY_BLOCK".equals(name)
                                                || "STOP_DESTROY_BLOCK".equals(name)
                                                || "ABORT_DESTROY_BLOCK".equals(name)) {
                                            e.setCancelled(true);
                                            return;
                                        }
                                    }
                                } catch (Throwable ignored) {

                                }
                                e.setCancelled(true);
                            }
                        }
                    };

            pm.addPacketListener(adapter);
            plugin.getLogger().info("[DurabilityPlus] ProtocolLib dig cancel hook active.");
        } catch (Throwable t) {
            plugin.getLogger().info("[DurabilityPlus] ProtocolLib not present; cannot cancel client dig.");
        }
    }

    /** Call from onDisable() or reload to stop the driver and clear overlays. */
    public void shutdown() {
        if (tickTaskId != -1) {
            Bukkit.getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
        for (Map.Entry<UUID, Track> en : tracks.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null && anim != null) anim.clearCrack(p, en.getValue().block);
        }
        tracks.clear();
    }

    // ------------------------------------------------------------------------
    // Entry: player begins to damage a block (our main gate)
    // ------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent e) {
        if (!dcfg.isEnabled() || !dcfg.miningEnabled() || dcfg.miningModeEffect()) return;

        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir()) return; // fists or empty

        Block b = e.getBlock();

        int baseline = computeVanillaTicks(p, b, tool);

        if (baseline <= 0) baseline = 30; // ~1.5s at 20 TPS; tune if desired

// Config curve multiplier (>=1.0)
        int percent = remainingPercent(tool);
        double factor = Math.max(1.0, resolveDelayFactor(tool, percent));


        int targetTicks = Math.max(2, (int) Math.round(baseline * factor));
        Track t = tracks.get(p.getUniqueId());
        if (t != null && t.sameTarget(b, tool)) {
            t.targetTicks = targetTicks;
            t.accruedTicks = 0;
            t.lastTouchTick = tickClock;
            if (anim != null) anim.sendCrack(p, b, 0);
        } else {
            Track nt = new Track(b, tool, targetTicks, tickClock);
            tracks.put(p.getUniqueId(), nt);
            if (anim != null) anim.sendCrack(p, b, 0);
        }

        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Track t = tracks.get(p.getUniqueId());
        if (t == null) return;
        if (!t.sameTarget(e.getBlock(), p.getInventory().getItemInMainHand())) return;

        e.setCancelled(true);
    }

    // ------------------------------------------------------------------------
    // Cleanup/safety
    // ------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent e) {
        clear(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldChange(PlayerItemHeldEvent e) {
        abortIfTracking(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        abortIfTracking(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        // If a tracked block changed out from under us, abort its tracker
        for (Map.Entry<UUID, Track> en : new ArrayList<>(tracks.entrySet())) {
            Track t = en.getValue();
            if (t.block.equals(e.getBlock())) {
                Player p = Bukkit.getPlayer(en.getKey());
                if (p != null && anim != null) anim.clearCrack(p, t.block);
                tracks.remove(en.getKey());
            }
        }
    }

    // ------------------------------------------------------------------------
    // Core ticking & visuals (every 2 ticks)
    // ------------------------------------------------------------------------
    private void tick() {
        if (tracks.isEmpty()) return;

        int capTicks = Math.max(0, dcfg.maxTrackSeconds()) * 20; // seconds -> ticks

        Iterator<Map.Entry<UUID, Track>> it = tracks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Track> entry = it.next();
            UUID id = entry.getKey();
            Track t = entry.getValue();
            Player p = Bukkit.getPlayer(id);

            if (p == null) { it.remove(); continue; }

            if (!t.stillValidFor(p)) {
                if (anim != null) anim.clearCrack(p, t.block);
                it.remove();
                continue;
            }

            if (capTicks > 0 && (tickClock - t.lastTouchTick) > capTicks) {
                if (anim != null) anim.clearCrack(p, t.block);
                it.remove();
                continue;
            }

            t.accruedTicks += 2;

            // Push crack stage proportionally (0..9)
            if (anim != null) {
                int stage = Math.min(9, (int)Math.floor(10.0 * t.accruedTicks / Math.max(1, t.targetTicks)));
                anim.sendCrack(p, t.block, stage);
            }

            if (t.finished()) {
                Block b = t.block;
                ItemStack tool = t.toolSnapshot;

                if (anim != null) anim.clearCrack(p, b); // clear overlay first
                Bukkit.getScheduler().runTask(plugin, () -> b.breakNaturally(tool)); // break server-side
                it.remove();
            }
        }
    }

    private void abortIfTracking(Player p) {
        Track t = tracks.remove(p.getUniqueId());
        if (t != null && anim != null) anim.clearCrack(p, t.block);
    }

    private void clear(Player p) {
        Track t = tracks.remove(p.getUniqueId());
        if (t != null && anim != null) anim.clearCrack(p, t.block);
    }

    // ------------------------------------------------------------------------
    // Vanilla time estimation (Paper attribute -> Paper alt -> heuristic)
    // ------------------------------------------------------------------------
    private int computeVanillaTicks(Player p, Block b, ItemStack tool) {
        int tAttr = vanillaTicksFromAttribute(p);
        if (tAttr > 0) return tAttr;

        Integer t2 = vanillaTicksFromPaperDestroySpeed(b, tool);
        if (t2 != null && t2 > 0) return t2;

        return heuristicVanillaTicks(b.getType(), tool.getType());
    }

    /**
     * Paper: use Attribute.BLOCK_BREAK_SPEED without a compile-time dependency.
     * Returns ticks to break at 20 TPS, or -1 if unavailable.
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    private int vanillaTicksFromAttribute(Player p) {
        try {
            Class<?> attrEnum = Class.forName("org.bukkit.attribute.Attribute");
            Object BLOCK_BREAK_SPEED = Enum.valueOf((Class<Enum>) attrEnum.asSubclass(Enum.class), "BLOCK_BREAK_SPEED");

            Method getAttribute = p.getClass().getMethod("getAttribute", attrEnum);
            Object inst = getAttribute.invoke(p, BLOCK_BREAK_SPEED);
            if (inst == null) return -1;

            Method getValue = inst.getClass().getMethod("getValue");
            double speed = ((Number) getValue.invoke(inst)).doubleValue(); // “progress per tick”
            if (speed <= 0d) return -1;

            // Convert to ticks @20tps (progress sum of 1.0 finishes a block)
            return Math.max(1, (int) Math.round(20.0 / speed));
        } catch (Throwable ignore) {
            return -1;
        }
    }

    private Integer vanillaTicksFromPaperDestroySpeed(Block b, ItemStack tool) {
        try {
            Object data = b.getBlockData();
            Method m = data.getClass().getMethod("getDestroySpeed", ItemStack.class, boolean.class);
            Object ret = m.invoke(data, tool, Boolean.TRUE);
            if (ret instanceof Number) {
                float speed = ((Number) ret).floatValue();
                if (speed <= 0f) return null;
                return Math.max(1, Math.round(20f / speed));
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private int heuristicVanillaTicks(Material block, Material tool) {
        boolean pick = Tag.MINEABLE_PICKAXE.isTagged(block);
        boolean shovel = Tag.MINEABLE_SHOVEL.isTagged(block);
        boolean axe = Tag.MINEABLE_AXE.isTagged(block);
        boolean hoe = Tag.MINEABLE_HOE.isTagged(block);
        String tn = tool.name();

        if (pick) return tn.endsWith("_PICKAXE") ? 16 : 32;
        if (shovel) return tn.endsWith("_SHOVEL") ? 10 : 20;
        if (axe) return tn.endsWith("_AXE") ? 12 : 24;
        if (hoe) return tn.endsWith("_HOE") ? 8 : 16;
        return 12;
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------
    private int remainingPercent(ItemStack tool) {
        if (tool == null) return 100;
        if (!(tool.getItemMeta() instanceof Damageable d)) return 100;
        int max = tool.getType().getMaxDurability();
        if (max <= 0) return 100;
        int rem = Math.max(0, max - d.getDamage());
        return Math.max(0, Math.min(100, Math.round(rem * 100f / max)));
    }

    /** Config: per-item override first, then base curve (returns >=1.0). */
    private double resolveDelayFactor(ItemStack tool, int percent) {
        Material mat = (tool == null) ? null : tool.getType();
        return dcfg.miningDelayFactorFor(mat, percent);
    }

    // ------------------------------------------------------------------------
    // Track
    // ------------------------------------------------------------------------
    private static final class Track {
        final Block block;
        final ItemStack toolSnapshot;
        int targetTicks; // required ticks
        int accruedTicks; // progressed ticks
        long lastTouchTick; // last time we saw BlockDamage for this target (in tickClock units)

        Track(Block b, ItemStack tool, int targetTicks, long startTick) {
            this.block = b;
            this.toolSnapshot = (tool == null ? null : tool.clone());
            this.targetTicks = Math.max(2, targetTicks);
            this.accruedTicks = 0;
            this.lastTouchTick = startTick;
        }

        boolean finished() { return accruedTicks >= targetTicks; }

        boolean sameTarget(Block b, ItemStack curTool) {
            if (!this.block.equals(b)) return false;
            if (toolSnapshot == null && curTool == null) return true;
            if (toolSnapshot == null || curTool == null) return false;
            return toolSnapshot.getType() == curTool.getType();
        }

        boolean stillValidFor(Player p) {
            Block sight = p.getTargetBlockExact(6);
            if (sight == null || !sight.equals(block)) return false;

            ItemStack cur = p.getInventory().getItemInMainHand();
            if (toolSnapshot == null && cur == null) return true;
            if (toolSnapshot == null || cur == null) return false;
            return toolSnapshot.getType() == cur.getType();
        }
    }
}