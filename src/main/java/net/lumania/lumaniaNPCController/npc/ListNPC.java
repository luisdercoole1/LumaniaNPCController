package net.lumania.lumaniaNPCController.npc;

import net.lumania.lumaniaNPCController.models.NPC;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListNPC {

    // Ändere von Map<String, NPC> zu Map<String, List<NPC>>
    public static final Map<String, List<NPC>> NPCs = new HashMap<>();

    public static final Map<Player, Map<NPC, Boolean>> spawnedNPCs = new HashMap<>();

    public static boolean NPCsContainsNPCWithType(NPC npc) {
        return NPCs.containsKey(npc.getType());
    }

    // Neue Hilfsmethode: Gibt alle NPCs zurück (flache Liste)
    public static List<NPC> getAllNPCs() {
        List<NPC> allNPCs = new ArrayList<>();
        for (List<NPC> npcList : NPCs.values()) {
            allNPCs.addAll(npcList);
        }
        return allNPCs;
    }

    // Neue Hilfsmethode: Gibt alle NPCs eines bestimmten Typs zurück
    public static List<NPC> getNPCsByType(String type) {
        return NPCs.getOrDefault(type, new ArrayList<>());
    }
}
