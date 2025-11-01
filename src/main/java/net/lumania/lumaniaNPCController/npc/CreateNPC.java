package net.lumania.lumaniaNPCController.npc;

import net.lumania.lumaniaNPCController.models.NPC;

import java.util.Map;

public class CreateNPC {

    public static void addNPC(NPC npc){
        if (!ListNPC.NPCsContainsNPCWithType(npc)){
            ListNPC.NPCs.put(npc.getType(), npc);
        }
    }

}
