package me.server.strongholdblock;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructurePiece;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

public class StrongholdBlock extends JavaPlugin implements Listener {

    private static final String BYPASS = "strongholdblock.bypass";
    private static final String BLOCKED_MESSAGE = "§cStrongholds are off limits on this server.";
    private static final String EJECTED_MESSAGE = "§cStrongholds are off limits. You were sent to spawn.";

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        // Every second (20 ticks), send anyone found inside a stronghold to spawn.
        // First run after 2 seconds (40 ticks) so the server has finished starting.
        getServer().getScheduler().runTaskTimer(this, this::ejectSweep, 40L, 20L);

        getLogger().info("StrongholdBlock enabled.");
    }

    /** True if the location is inside (or 1 block next to) any stronghold room or corridor. */
    private boolean inStronghold(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;

        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        for (GeneratedStructure gs : world.getStructures(chunkX, chunkZ, Structure.STRONGHOLD)) {
            for (StructurePiece piece : gs.getPieces()) {
                BoundingBox box = piece.getBoundingBox().clone().expand(1.0);
                if (box.contains(loc.getX(), loc.getY(), loc.getZ())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void ejectSweep() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.hasPermission(BYPASS)) continue;

            if (inStronghold(player.getLocation())) {
                Location spawn = player.getWorld().getSpawnLocation().add(0.5, 0, 0.5);
                player.teleport(spawn);
                player.sendMessage(EJECTED_MESSAGE);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Only check when the player changes block, to keep it cheap.
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS)) return;

        if (inStronghold(to) && !inStronghold(from)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(BLOCKED_MESSAGE));
        }
    }

    // PlayerTeleportEvent does not trigger onMove, so it needs its own handler
    // (ender pearls, chorus fruit, /tp, etc.).
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS)) return;

        if (inStronghold(to)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(BLOCKED_MESSAGE));
        }
    }
}
