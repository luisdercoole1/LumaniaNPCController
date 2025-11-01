package net.lumania.lumaniaNPCController;

import net.lumania.lumaniaNPCController.events.NPCInteraction;
import net.lumania.lumaniaNPCController.npc.NPCListener;
import net.lumania.lumaniaNPCController.npc.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class LumaniaNPCController extends JavaPlugin {

    @Override
    public void onEnable() {
        // Prepare config (optional: npc_render_range)
        saveDefaultConfig();

        // Initialisiere das NPC-Interaction-System
        NPCInteraction.initialize(this);

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
        // Entferne alle Interaktionen beim Disable
        NPCInteraction.clearAllInteractions();

        NPCManager.deleteNPCs();

        Bukkit.getConsoleSender().sendMessage(
                "\n" +
                        "§f[LumaniaNPCController] LumaniaNPCController disabled" +
                        "\n");
    }
}
