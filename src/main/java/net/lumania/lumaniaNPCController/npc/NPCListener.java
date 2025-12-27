package net.lumania.lumaniaNPCController.npc;

import net.lumania.lumaniaNPCController.models.NPC;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;

public class NPCListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        NPCManager.spawnHandlerNPC(player);
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Map<NPC, Boolean> playerNPCs = ListNPC.spawnedNPCs.get(player);
        if (playerNPCs != null) {
            for (Map.Entry<NPC, Boolean> entry : new HashMap<>(playerNPCs).entrySet()) {
                if (entry.getValue()) { // Wenn NPC gespawnt war
                    NPCManager.despawnNPC(entry.getKey(), player);
                }
            }
            playerNPCs.clear();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMoveNPCRotation(PlayerMoveEvent event) {

        if (!ListNPC.spawnedNPCs.get(event.getPlayer()).isEmpty()) {
            ServerGamePacketListenerImpl connection = ((CraftPlayer) event.getPlayer()).getHandle().connection;

            for (Map.Entry<NPC, Boolean> entry : ListNPC.spawnedNPCs.get(event.getPlayer()).entrySet()) {
                ServerPlayer npc = entry.getKey().getPlayer();
                if (npc.getBukkitEntity().getWorld().equals(event.getPlayer().getWorld())) {

                    Location loc = npc.getBukkitEntity().getLocation();
                    loc.setDirection(event.getPlayer().getLocation().subtract(loc).toVector());

                    float yaw = loc.getYaw();
                    float pitch = loc.getPitch();

                    // Use safe packet sending method to avoid NoSuchMethodError due to obfuscation
                    NPCManager.safeSendPacket(connection, new ClientboundRotateHeadPacket(
                            npc, (byte) ((yaw % 360) * 256 / 360))
                    );

                    NPCManager.safeSendPacket(connection, new ClientboundMoveEntityPacket.Rot(
                            NPCManager.safeGetEntityId(npc), // use reflection-based method to avoid obfuscation issues
                            (byte) ((yaw % 360) * 256 / 360),
                            (byte) ((pitch % 360) * 256 / 360),
                            false
                    ));
                }
            }
        }
    }
}
