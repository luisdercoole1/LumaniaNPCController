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
        // 1. Alle aktiven Tasks beenden
        Bukkit.getScheduler().cancelTasks(this);

        // 2. Alle Spawn-Handler für alle Spieler stoppen
        for (Player player : Bukkit.getOnlinePlayers()) {
            NPCManager.stopSpawnHandler(player);
        }

        // 3. Entferne alle Interaktionen beim Disable
        NPCInteraction.clearAllInteractions();

        // 4. NPCs löschen
        NPCManager.deleteNPCs();

        // 5. Kurz warten, damit alle Cleanup-Operationen abgeschlossen werden
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {}

        Bukkit.getConsoleSender().sendMessage(
                "\n" +
                        "§f[LumaniaNPCController] LumaniaNPCController disabled" +
                        "\n");
    }


}

