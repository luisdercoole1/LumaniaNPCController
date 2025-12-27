package net.lumania.lumaniaNPCController.npc;

import net.lumania.lumaniaNPCController.models.NPC;

import java.util.ArrayList;

public class CreateNPC {
    public static void addNPC(NPC npc){
        // Initialisiere Liste für diesen Type, falls noch nicht vorhanden
        if (!ListNPC.NPCs.containsKey(npc.getType())) {
            ListNPC.NPCs.put(npc.getType(), new ArrayList<>());
        }

        // Füge NPC zur Liste hinzu
        ListNPC.NPCs.get(npc.getType()).add(npc);
    }
}
