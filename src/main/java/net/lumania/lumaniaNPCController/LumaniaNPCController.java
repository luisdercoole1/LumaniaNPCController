package net.lumania.lumaniaNPCController;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.lumania.lumaniaNPCController.models.NPC;
import net.lumania.lumaniaNPCController.npc.ListNPC;
import net.lumania.lumaniaNPCController.npc.NPCListener;
import net.lumania.lumaniaNPCController.npc.NPCManager;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R4.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

import static net.lumania.lumaniaNPCController.npc.NPCManager.safeGetUUID;
import static net.lumania.lumaniaNPCController.npc.NPCManager.safeSendPacket;

public final class LumaniaNPCController extends JavaPlugin {

    @Override
    public void onEnable() {
        // Prepare config (optional: npc_render_range)
        saveDefaultConfig();

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new NPCListener(), this);

        // Start spawn handler for already online players (e.g., during reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            NPCManager.spawnHandlerNPC(p);
        }

        Bukkit.getConsoleSender().sendMessage(
                "\n" +
                "§f[LumaniaNPCController] LumaniaNPCController enabled" +
                "\n");

    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(
                "\n" +
                        "§f[LumaniaNPCController] LumaniaNPCController disabled" +
                        "\n");
    }

    public static void deleteNPCs() {
        for (NPC npc : ListNPC.NPCs.values()) {
            IntList npcIds = new IntArrayList();
            npcIds.add(npc.getPlayer().getBukkitEntity().getEntityId());

            ClientboundRemoveEntitiesPacket packetDestroy = new ClientboundRemoveEntitiesPacket(npcIds);
            ClientboundPlayerInfoRemovePacket packetRemove = new ClientboundPlayerInfoRemovePacket(List.of(safeGetUUID(npc.getPlayer())));

            for (Player p : Bukkit.getOnlinePlayers()) {
                ServerGamePacketListenerImpl connection = ((CraftPlayer) p).getHandle().connection;
                safeSendPacket(connection, packetDestroy);
                safeSendPacket(connection, packetRemove);
            }
        }
    }
}
