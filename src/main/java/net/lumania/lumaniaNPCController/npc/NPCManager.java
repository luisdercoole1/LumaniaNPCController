package net.lumania.lumaniaNPCController.npc;

import net.lumania.lumaniaNPCController.LumaniaNPCController;
import net.lumania.lumaniaNPCController.models.NPC;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R4.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Ports the spawn/packet logic from the original NixoModus NPC util
 * into the LumaniaNPCController plugin using the controller's NPC model.
 */
public class NPCManager {

    // entityId -> NPC mapping for quick lookup if needed elsewhere
    public static final Map<Integer, NPC> ENTITIES = new HashMap<>();
    
    // Map für die laufenden Tasks pro Spieler
    private static final Map<UUID, Integer> activeSpawnTasks = new HashMap<>();

    // ---------- Helper methods (reflection-safe) ----------

    public static void safeSendPacket(ServerGamePacketListenerImpl connection, Packet<?> packet) {
        try {
            Method sendMethod = null;
            try {
                sendMethod = connection.getClass().getMethod("send", Packet.class);
            } catch (NoSuchMethodException e1) {
                try {
                    sendMethod = connection.getClass().getMethod("a", Packet.class);
                } catch (NoSuchMethodException e2) {
                    try {
                        sendMethod = connection.getClass().getMethod("sendPacket", Packet.class);
                    } catch (NoSuchMethodException e3) {
                        for (Method method : connection.getClass().getMethods()) {
                            if (method.getParameterCount() == 1 &&
                                method.getParameterTypes()[0].isAssignableFrom(Packet.class) &&
                                method.getReturnType() == void.class) {
                                sendMethod = method;
                                break;
                            }
                        }
                    }
                }
            }

            if (sendMethod != null) {
                sendMethod.setAccessible(true);
                sendMethod.invoke(connection, packet);
            } else {
                Bukkit.getLogger().warning("[LNPC] Could not find packet send method on ServerGamePacketListenerImpl");
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[LNPC] Error sending packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static float safeGetHeadRotation(ServerPlayer npc) {
        try {
            Method headRotMethod = null;
            try {
                headRotMethod = npc.getClass().getMethod("getYHeadRot");
            } catch (NoSuchMethodException e1) {
                try {
                    headRotMethod = npc.getClass().getMethod("cm");
                } catch (NoSuchMethodException ignored) {}
            }
            if (headRotMethod != null) {
                headRotMethod.setAccessible(true);
                return (float) headRotMethod.invoke(npc);
            }
            return npc.getBukkitEntity().getLocation().getYaw();
        } catch (Exception e) {
            return npc.getBukkitEntity().getLocation().getYaw();
        }
    }

    public static int safeGetEntityId(ServerPlayer npc) {
        try {
            Method idMethod = null;
            try {
                idMethod = npc.getClass().getMethod("getId");
            } catch (NoSuchMethodException e1) {
                try {
                    idMethod = npc.getClass().getMethod("af");
                } catch (NoSuchMethodException e2) {
                    try {
                        idMethod = npc.getClass().getMethod("getEntityId");
                    } catch (NoSuchMethodException ignored) {}
                }
            }
            if (idMethod != null) {
                idMethod.setAccessible(true);
                return (int) idMethod.invoke(npc);
            }
            return npc.getBukkitEntity().getEntityId();
        } catch (Exception e) {
            return npc.getBukkitEntity().getEntityId();
        }
    }

    private static List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> safeGetEntityData(ServerPlayer npc) {
        try {
            Method getEntityDataMethod = null;
            try {
                getEntityDataMethod = npc.getClass().getMethod("getEntityData");
            } catch (NoSuchMethodException e1) {
                try {
                    getEntityDataMethod = npc.getClass().getMethod("aj");
                } catch (NoSuchMethodException e2) {
                    try {
                        getEntityDataMethod = npc.getClass().getMethod("ai");
                    } catch (NoSuchMethodException ignored) {}
                }
            }
            if (getEntityDataMethod == null) return new ArrayList<>();
            getEntityDataMethod.setAccessible(true);
            Object entityData = getEntityDataMethod.invoke(npc);
            if (entityData == null) return new ArrayList<>();

            Method packDirtyMethod = null;
            try {
                packDirtyMethod = entityData.getClass().getMethod("packDirty");
            } catch (NoSuchMethodException e1) {
                try {
                    packDirtyMethod = entityData.getClass().getMethod("c");
                } catch (NoSuchMethodException e2) {
                    try {
                        packDirtyMethod = entityData.getClass().getMethod("b");
                    } catch (NoSuchMethodException ignored) {}
                }
            }
            if (packDirtyMethod == null) return new ArrayList<>();
            packDirtyMethod.setAccessible(true);
            Object result = packDirtyMethod.invoke(entityData);
            if (result instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> list =
                        (List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>>) result;
                return list;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static UUID safeGetUUID(ServerPlayer npc) {
        try {
            Method uuidMethod = null;
            try {
                uuidMethod = npc.getClass().getMethod("getUUID");
            } catch (NoSuchMethodException e1) {
                try {
                    uuidMethod = npc.getClass().getMethod("ct");
                } catch (NoSuchMethodException ignored) {}
            }
            if (uuidMethod != null) {
                uuidMethod.setAccessible(true);
                return (UUID) uuidMethod.invoke(npc);
            }
            return npc.getBukkitEntity().getUniqueId();
        } catch (Exception e) {
            return npc.getBukkitEntity().getUniqueId();
        }
    }

    // ---------- Public API ----------

    public static void spawnHandlerNPC(Player player) {
    Bukkit.getLogger().info("[LNPC] Spawn-Handler für Spieler gestartet: " + player.getName());
    Bukkit.getLogger().info("[LNPC] Anzahl NPCs in Liste: " + ListNPC.getAllNPCs().size());
    
    ListNPC.spawnedNPCs.putIfAbsent(player, new HashMap<>());

    // Beende den alten Task für diesen Spieler, falls vorhanden
    UUID playerId = player.getUniqueId();
    if (activeSpawnTasks.containsKey(playerId)) {
        Bukkit.getScheduler().cancelTask(activeSpawnTasks.get(playerId));
    }

    BukkitRunnable task = new BukkitRunnable() {
        @Override
        public void run() {
            if (!player.isOnline()) {
                this.cancel();
                ListNPC.spawnedNPCs.remove(player);
                activeSpawnTasks.remove(playerId);
                    return;
                }

                double renderRange = getRenderRangeOrDefault(24.0);

                // ÄNDERUNG: Iteriere über alle NPCs
                for (NPC npc : ListNPC.getAllNPCs()) {
                    // Nur gleiche Welt
                    if (!npc.getPlayer().getBukkitEntity().getWorld().equals(player.getWorld())) {
                        boolean isSpawned = ListNPC.spawnedNPCs.get(player).getOrDefault(npc, false);
                        if (isSpawned) {
                            ListNPC.spawnedNPCs.get(player).put(npc, false);
                            despawnNPC(npc, player);
                        }
                        continue;
                    }

                    boolean inRange = npc.getPlayer().getBukkitEntity().getLocation().distance(player.getLocation()) <= renderRange;
                    boolean isSpawned = ListNPC.spawnedNPCs.get(player).getOrDefault(npc, false);

                    if (inRange && !isSpawned) {
                        ListNPC.spawnedNPCs.get(player).put(npc, true);
                        spawnNPC(npc, player);
                    } else if (!inRange && isSpawned) {
                        ListNPC.spawnedNPCs.get(player).put(npc, false);
                        despawnNPC(npc, player);
                    }
                }
            }
        };
        
        int taskId = task.runTaskTimer(JavaPluginProvider.plugin(), 0L, 20L).getTaskId();
        activeSpawnTasks.put(playerId, taskId);
    }

    /**
     * Erneuert die NPC-Liste für alle Spieler oder einen bestimmten Spieler.
     * Rufe diese Methode auf, nachdem du neue NPCs hinzugefügt hast.
     * 
     * @param player Optional: Nur für diesen Spieler erneuern. Null = alle Spieler
     */
    public static void refreshNPCList(Player player) {
        if (player != null) {
            // Nur für einen bestimmten Spieler
            if (player.isOnline()) {
                spawnHandlerNPC(player);
            }
        } else {
            // Für alle Online-Spieler
            for (Player p : Bukkit.getOnlinePlayers()) {
                spawnHandlerNPC(p);
            }
        }
    }

    /**
     * Stoppt den Spawn-Handler für einen bestimmten Spieler
     */
    public static void stopSpawnHandler(Player player) {
        UUID playerId = player.getUniqueId();
        if (activeSpawnTasks.containsKey(playerId)) {
            Bukkit.getScheduler().cancelTask(activeSpawnTasks.get(playerId));
            activeSpawnTasks.remove(playerId);
            ListNPC.spawnedNPCs.remove(player);
        }
    }

    public static void spawnNPC(NPC wrapper, Player toPlayer) {
        ServerPlayer npc = wrapper.getPlayer();
        ServerGamePacketListenerImpl targetConn = ((CraftPlayer) toPlayer).getHandle().connection;

        try {
            Class<?> packetClass = ClientboundPlayerInfoUpdatePacket.class;
            Class<?>[] inner = packetClass.getDeclaredClasses();
            Class<?> entryClass = null;
            for (Class<?> c : inner) {
                if (c.getSimpleName().toLowerCase().contains("entry")) { entryClass = c; break; }
            }

            Object entryInstance = null;
            if (entryClass != null) {
                for (Constructor<?> ctor : entryClass.getDeclaredConstructors()) {
                    Class<?>[] params = ctor.getParameterTypes();
                    if (params.length >= 3 && params[0].isAssignableFrom(com.mojang.authlib.GameProfile.class)) {
                        ctor.setAccessible(true);
                        try {
                            Object gamemodeArg = null;
                            for (Class<?> ptype : params) {
                                if (ptype.getSimpleName().equals("GameType") || ptype.getSimpleName().equals("GameMode")) {
                                    try {
                                        Class<?> gmClass = Class.forName("net.minecraft.world.level.GameType");
                                        gamemodeArg = Enum.valueOf((Class<Enum>) gmClass, "SURVIVAL");
                                    } catch (Exception ex) {
                                        try {
                                            Class<?> gmClass2 = Class.forName("net.minecraft.world.entity.player.GameMode");
                                            gamemodeArg = Enum.valueOf((Class<Enum>) gmClass2, "SURVIVAL");
                                        } catch (Exception ignore2) { gamemodeArg = null; }
                                    }
                                    break;
                                }
                            }
                            Object[] args = new Object[params.length];
                            args[0] = npc.getGameProfile();
                            if (params.length >= 2 && params[1] == int.class) args[1] = 0; else if (params.length >= 2 && params[1] == Integer.class) args[1] = Integer.valueOf(0);
                            for (int i = 2; i < params.length; i++) {
                                if (params[i].isEnum() && gamemodeArg != null && params[i].getClass() == gamemodeArg.getClass()) {
                                    args[i] = gamemodeArg;
                                } else {
                                    args[i] = null;
                                }
                            }
                            entryInstance = ctor.newInstance(args);
                            break;
                        } catch (Throwable t) { /* try next */ }
                    }
                }
            }

            Packet<?> playerInfoPacket = null;
            if (entryInstance != null) {
                List<Object> entries = new ArrayList<>();
                entries.add(entryInstance);
                try {
                    Constructor<?> packCtor = packetClass.getConstructor(ClientboundPlayerInfoUpdatePacket.Action.class, Collection.class);
                    packCtor.setAccessible(true);
                    playerInfoPacket = (Packet<?>) packCtor.newInstance(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, entries);
                } catch (NoSuchMethodException nsme) {
                    for (Constructor<?> c : packetClass.getDeclaredConstructors()) {
                        Class<?>[] params = c.getParameterTypes();
                        if (params.length == 2 && params[0] == ClientboundPlayerInfoUpdatePacket.Action.class) {
                            c.setAccessible(true);
                            playerInfoPacket = (Packet<?>) c.newInstance(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, entries);
                            break;
                        }
                    }
                }
            } else {
                try {
                    Constructor<?> c = packetClass.getConstructor(ClientboundPlayerInfoUpdatePacket.Action.class, ServerPlayer.class);
                    c.setAccessible(true);
                    playerInfoPacket = (Packet<?>) c.newInstance(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, npc);
                } catch (Exception ignored) {}
            }

            if (playerInfoPacket != null) {
                safeSendPacket(targetConn, playerInfoPacket);
            } else {
                Bukkit.getLogger().warning("[LNPC] Konnte PlayerInfoPacket nicht bauen; Skin-Laden könnte fehlschlagen.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // Spawn entity
        safeSendPacket(targetConn, new ClientboundAddEntityPacket(npc));

        // Head rotation
        float headRotation = safeGetHeadRotation(npc);
        safeSendPacket(targetConn, new ClientboundRotateHeadPacket(npc, (byte) ((headRotation * 256f) / 360f)));

        // Metadata
        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> entityData = safeGetEntityData(npc);
        if (entityData != null && !entityData.isEmpty()) {
            safeSendPacket(targetConn, new ClientboundSetEntityDataPacket(safeGetEntityId(npc), entityData));
        }

        // Remove from tab list later
        Bukkit.getScheduler().runTaskLater(JavaPluginProvider.plugin(), () -> {
            safeSendPacket(targetConn, new ClientboundPlayerInfoRemovePacket(List.of(safeGetUUID(npc))));
        }, 40L);

        // track entity id mapping
        ENTITIES.put(npc.getBukkitEntity().getEntityId(), wrapper);
    }

    public static void despawnNPC(NPC wrapper, Player toPlayer) {
        ServerPlayer npc = wrapper.getPlayer();
        int id = npc.getBukkitEntity().getEntityId();
        ClientboundRemoveEntitiesPacket packetDestroy = new ClientboundRemoveEntitiesPacket(new int[]{id});
        ClientboundPlayerInfoRemovePacket packetRemove = new ClientboundPlayerInfoRemovePacket(List.of(safeGetUUID(npc)));
        ServerGamePacketListenerImpl connection = ((CraftPlayer) toPlayer).getHandle().connection;
        safeSendPacket(connection, packetDestroy);
        safeSendPacket(connection, packetRemove);
    }

    public static void deleteNPCs() {
    // Iteriere über alle NPCs (flache Liste)
    for (NPC wrapper : ListNPC.getAllNPCs()) {
        ServerPlayer npc = wrapper.getPlayer();
        int id = npc.getBukkitEntity().getEntityId();
        ClientboundRemoveEntitiesPacket packetDestroy = new ClientboundRemoveEntitiesPacket(new int[]{id});
        ClientboundPlayerInfoRemovePacket packetRemove = new ClientboundPlayerInfoRemovePacket(List.of(safeGetUUID(npc)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            ServerGamePacketListenerImpl connection = ((CraftPlayer) p).getHandle().connection;
            safeSendPacket(connection, packetDestroy);
            safeSendPacket(connection, packetRemove);
        }
    }
    ENTITIES.clear();
}

    private static double getRenderRangeOrDefault(double def) {
        try {
            var plugin = JavaPluginProvider.plugin();
            if (plugin.getConfig().isSet("npc_render_range")) {
                return plugin.getConfig().getDouble("npc_render_range", def);
            }
        } catch (Throwable ignored) {}
        return def;
    }

    // Small util to access our JavaPlugin instance without passing it around
    private static class JavaPluginProvider {
        private static LumaniaNPCController plugin;
        static LumaniaNPCController plugin() {
            if (plugin == null) plugin = JavaPluginRefFinder.find();
            return plugin;
        }
    }

    private static class JavaPluginRefFinder {
        static LumaniaNPCController find() {
            return (LumaniaNPCController) Bukkit.getPluginManager().getPlugin("LumaniaNPCController");
        }
    }
}
