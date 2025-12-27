package net.lumania.lumaniaNPCController.events;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import net.lumania.lumaniaNPCController.LumaniaNPCController;
import net.lumania.lumaniaNPCController.models.NPC;
import net.lumania.lumaniaNPCController.npc.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class NPCInteraction {

    private static final Map<Integer, InteractionHandler> interactionHandlers = new HashMap<>();
    private static boolean initialized = false;
    private static LumaniaNPCController plugin;

    /**
     * Repräsentiert einen Interaction-Handler mit Callbacks für verschiedene Aktionen
     */
    public static class InteractionHandler {
        private final Consumer<Player> onRightClick;
        private final Consumer<Player> onLeftClick;
        private final Consumer<Player> onAttack;

        private InteractionHandler(Consumer<Player> onRightClick, Consumer<Player> onLeftClick, Consumer<Player> onAttack) {
            this.onRightClick = onRightClick;
            this.onLeftClick = onLeftClick;
            this.onAttack = onAttack;
        }

        public void handleRightClick(Player player) {
            if (onRightClick != null) {
                onRightClick.accept(player);
            }
        }

        public void handleLeftClick(Player player) {
            if (onLeftClick != null) {
                onLeftClick.accept(player);
            }
        }

        public void handleAttack(Player player) {
            if (onAttack != null) {
                onAttack.accept(player);
            }
        }
    }

    /**
     * Builder für InteractionHandler
     */
    public static class InteractionHandlerBuilder {
        private Consumer<Player> onRightClick;
        private Consumer<Player> onLeftClick;
        private Consumer<Player> onAttack;

        /**
         * Setzt den Callback für Rechtsklick
         * @param callback Die Funktion, die beim Rechtsklick ausgeführt wird
         * @return Dieser Builder
         */
        public InteractionHandlerBuilder onRightClick(Consumer<Player> callback) {
            this.onRightClick = callback;
            return this;
        }

        /**
         * Setzt den Callback für Linksklick
         * @param callback Die Funktion, die beim Linksklick ausgeführt wird
         * @return Dieser Builder
         */
        public InteractionHandlerBuilder onLeftClick(Consumer<Player> callback) {
            this.onLeftClick = callback;
            return this;
        }

        /**
         * Setzt den Callback für Angriff
         * @param callback Die Funktion, die beim Angriff ausgeführt wird
         * @return Dieser Builder
         */
        public InteractionHandlerBuilder onAttack(Consumer<Player> callback) {
            this.onAttack = callback;
            return this;
        }

        /**
         * Erstellt den InteractionHandler
         * @return Der erstellte InteractionHandler
         */
        public InteractionHandler build() {
            if (onRightClick == null && onLeftClick == null && onAttack == null) {
                throw new IllegalStateException("Mindestens ein Callback muss gesetzt sein!");
            }
            return new InteractionHandler(onRightClick, onLeftClick, onAttack);
        }
    }

    /**
     * Initialisiert das NPC-Interaction-System
     * @param pluginInstance Die Plugin-Instanz
     */
    public static void initialize(LumaniaNPCController pluginInstance) {
        if (initialized) {
            return;
        }

        plugin = pluginInstance;
        registerPacketListener();
        initialized = true;

        Bukkit.getLogger().info("[NPCInteraction] NPC Interaction System initialisiert");
    }

    /**
     * Registriert einen Interaction-Handler für einen NPC
     * @param npc Der NPC, für den die Interaktion registriert werden soll
     * @param handler Der InteractionHandler mit den Callbacks
     */
    public static void registerInteraction(NPC npc, InteractionHandler handler) {
        if (!initialized) {
            throw new IllegalStateException("NPCInteraction muss zuerst initialisiert werden!");
        }

        int entityId = NPCManager.safeGetEntityId(npc.getPlayer());
        interactionHandlers.put(entityId, handler);
    }

    /**
     * Registriert einen Interaction-Handler für einen NPC über seine Entity-ID
     * @param entityId Die Entity-ID des NPCs
     * @param handler Der InteractionHandler mit den Callbacks
     */
    public static void registerInteraction(int entityId, InteractionHandler handler) {
        if (!initialized) {
            throw new IllegalStateException("NPCInteraction muss zuerst initialisiert werden!");
        }

        interactionHandlers.put(entityId, handler);
    }

    /**
     * Entfernt einen Interaction-Handler für einen NPC
     * @param npc Der NPC, dessen Interaktion entfernt werden soll
     */
    public static void unregisterInteraction(NPC npc) {
        int entityId = NPCManager.safeGetEntityId(npc.getPlayer());
        interactionHandlers.remove(entityId);
    }

    /**
     * Entfernt einen Interaction-Handler über die Entity-ID
     * @param entityId Die Entity-ID des NPCs
     */
    public static void unregisterInteraction(int entityId) {
        interactionHandlers.remove(entityId);
    }

    /**
     * Erstellt einen neuen InteractionHandlerBuilder
     * @return Ein neuer Builder
     */
    public static InteractionHandlerBuilder builder() {
        return new InteractionHandlerBuilder();
    }

    /**
     * Registriert den ProtocolLib PacketListener für NPC-Interaktionen
     */
    private static void registerPacketListener() {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();

        if (manager == null) {
            Bukkit.getLogger().severe("[NPCInteraction] ProtocolLib nicht gefunden oder nicht richtig initialisiert.");
            return;
        }

        manager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                int entityID = packet.getIntegers().read(0);

                // Prüfe, ob für diese Entity-ID ein Handler registriert ist
                InteractionHandler handler = interactionHandlers.get(entityID);
                if (handler == null) {
                    return;
                }

                EnumWrappers.EntityUseAction action = packet.getEnumEntityUseActions().read(0).getAction();

                // Haupt-Hand-Interaktionen
                if (action == EnumWrappers.EntityUseAction.INTERACT) {
                    EnumWrappers.Hand hand = packet.getEnumEntityUseActions().read(0).getHand();

                    if (hand == EnumWrappers.Hand.MAIN_HAND) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            handler.handleRightClick(event.getPlayer());
                        });
                    }
                }
                // Linksklick/Angriff
                else if (action == EnumWrappers.EntityUseAction.ATTACK) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        handler.handleAttack(event.getPlayer());
                    });
                }
            }
        });

    }

    /**
     * Gibt alle registrierten Entity-IDs zurück
     * @return Map mit allen registrierten Interaction-Handlern
     */
    public static Map<Integer, InteractionHandler> getRegisteredInteractions() {
        return new HashMap<>(interactionHandlers);
    }

    /**
     * Prüft, ob für einen NPC eine Interaktion registriert ist
     * @param npc Der NPC
     * @return true, wenn eine Interaktion registriert ist
     */
    public static boolean hasInteraction(NPC npc) {
        int entityId = NPCManager.safeGetEntityId(npc.getPlayer());
        return interactionHandlers.containsKey(entityId);
    }

    /**
     * Prüft, ob für eine Entity-ID eine Interaktion registriert ist
     * @param entityId Die Entity-ID
     * @return true, wenn eine Interaktion registriert ist
     */
    public static boolean hasInteraction(int entityId) {
        return interactionHandlers.containsKey(entityId);
    }

    /**
     * Entfernt alle registrierten Interaktionen
     */
    public static void clearAllInteractions() {
        interactionHandlers.clear();
    }
}
