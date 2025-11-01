package net.lumania.lumaniaNPCController.npc;

import net.lumania.lumaniaNPCController.models.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

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
}
