package net.lumania.lumaniaNPCController.npc;

import net.lumania.lumaniaNPCController.models.NPC;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListNPC {

    // Map<NPCType, NPC>
    public static final Map<String, NPC> NPCs = new HashMap<>();
    public static final Map<Player, Map<NPC, Boolean>> spawnedNPCs = new HashMap<>();

    public static boolean NPCsContainsNPCWithType(NPC npc){
        if (NPCs.containsValue(npc)){
            for (Map.Entry<String, NPC> entry : NPCs.entrySet()) {
                if (entry.getValue().equals(npc)) {
                    if (entry.getKey().equals(npc.getType())){
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
