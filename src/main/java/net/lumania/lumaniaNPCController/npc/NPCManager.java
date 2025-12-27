package net.lumania.lumaniaNPCController.npc;

import net.lumania.lumaniaNPCController.LumaniaNPCController;
import net.lumania.lumaniaNPCController.models.NPC;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class NPCManager {

    // entityId -> NPC mapping for quick lookup if needed elsewhere
    public static final Map<Integer, NPC> ENTITIES = new HashMap<>();

    // Map für die laufenden Tasks pro Spieler
    private static final Map<UUID, Integer> activeSpawnTasks = new HashMap<>();

    // Neue Methode: Alle Tasks stoppen
    public static void stopAllSpawnHandlers() {
        // Alle Tasks beenden
        for (Integer taskId : activeSpawnTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        activeSpawnTasks.clear();
        ListNPC.spawnedNPCs.clear();
    }

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

        ListNPC.spawnedNPCs.putIfAbsent(player, new HashMap<>());

        // Beende den alten Task für diesen Spieler, falls vorhanden
        UUID playerId = player.getUniqueId();
        if (activeSpawnTasks.containsKey(playerId)) {
            Bukkit.getScheduler().cancelTask(activeSpawnTasks.get(playerId));
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                // Prüfe, ob das Plugin noch aktiv ist
                LumaniaNPCController plugin = LumaniaNPCController.getPlugin(LumaniaNPCController.class);
                if (plugin == null || !plugin.isEnabled()) {
                    this.cancel();
                    activeSpawnTasks.remove(playerId);
                    return;
                }

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

                        Bukkit.getScheduler().runTaskLater(
                                LumaniaNPCController.getPlugin(LumaniaNPCController.class),
                                () -> {
                                    NPC.enableSkinLayers(npc.getPlayer(), player);
                                },
                                3L
                        );
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

        // 1) NPC in Tablist anzeigen
        Packet<?> addTab = new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                npc
        );
        safeSendPacket(targetConn, addTab);

        // 2) Spawn NPC entity (hier musst du noch deinen Packet-Constructor fixen)
        safeSendPacket(targetConn, buildAddEntityPacket(npc)); // dein code

        // 3) Head rotation
        float headRotation = safeGetHeadRotation(npc);
        safeSendPacket(targetConn, new ClientboundRotateHeadPacket(
                npc,
                (byte)((headRotation * 256f) / 360f)
        ));

        // 4) Metadata
        List<SynchedEntityData.DataValue<?>> entityData = safeGetEntityData(npc);
        if (entityData != null && !entityData.isEmpty()) {
            safeSendPacket(targetConn, new ClientboundSetEntityDataPacket(
                    safeGetEntityId(npc),
                    entityData
            ));
        }

        // 5) Später aus Tabliste entfernen
        Bukkit.getScheduler().runTaskLater(JavaPluginProvider.plugin(), () -> {
            Packet<?> removeTab = new ClientboundPlayerInfoRemovePacket(
                    List.of(npc.getUUID())
            );
            safeSendPacket(targetConn, removeTab);
        }, 40L);

        // 6) NPC-Tracking
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
        // ERST alle Tasks stoppen
        stopAllSpawnHandlers();

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

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Packet<?> buildAddEntityPacket(ServerPlayer npc) {
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");

            // 1) Versuche statische Fabrikmethoden: create(Entity), of(Entity), a(Entity)
            String[] factoryNames = new String[] {"create", "of", "a", "b"};
            for (String name : factoryNames) {
                try {
                    for (Method m : packetClass.getDeclaredMethods()) {
                        if (!m.getName().equals(name)) continue;
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length == 1 && params[0].isAssignableFrom(npc.getClass())) {
                            m.setAccessible(true);
                            Object pkt = m.invoke(null, npc);
                            if (pkt != null) return (Packet<?>) pkt;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 2) Versuche Konstruktoren: probiere logisch passende Argumente zusammenzustellen
            Constructor<?>[] ctors = packetClass.getDeclaredConstructors();
            // Werte, die wir mehrfach brauchen können:
            Object uuid = invokeIfExists(npc, new String[] {"getUUID", "getUniqueID", "getUuid", "getProfileId"});
            if (uuid == null) {
                try { uuid = npc.getUUID(); } catch (Throwable ignored) {}
            }
            Integer idVal = null;
            try { idVal = (Integer) invokeIfExists(npc, new String[] {"getId", "getEntityId", "id"}); } catch (Throwable ignored) {}
            if (idVal == null) {
                try { idVal = npc.getId(); } catch (Throwable ignored) {}
            }
            Double xVal = asDouble(invokeIfExists(npc, new String[] {"getX", "locX", "x", "getPosX"}));
            Double yVal = asDouble(invokeIfExists(npc, new String[] {"getY", "locY", "y", "getPosY"}));
            Double zVal = asDouble(invokeIfExists(npc, new String[] {"getZ", "locZ", "z", "getPosZ"}));
            Float xRotVal = asFloat(invokeIfExists(npc, new String[] {"getXRot", "getRotation", "xRot", "getXRotation"}));
            Float yRotVal = asFloat(invokeIfExists(npc, new String[] {"getYRot", "getYRotation", "yRot"}));
            Double yHeadRotVal = asDouble(invokeIfExists(npc, new String[] {"getYHeadRot", "getHeadRotation", "yHeadRot"}));

            Object entityType = null;
            try { entityType = npc.getType(); } catch (Throwable ignored) {
                try { entityType = invokeIfExists(npc, new String[] {"getType", "getEntityType"}); } catch (Throwable ignored2) {}
            }

            // For Vec3 class try to build a zero vector if needed
            Class<?> vec3Class = null;
            try { vec3Class = Class.forName("net.minecraft.world.phys.Vec3"); } catch (Throwable ignored) {}
            Object zeroVec = null;
            if (vec3Class != null) {
                try {
                    Constructor<?> vCtor = vec3Class.getDeclaredConstructor(double.class, double.class, double.class);
                    vCtor.setAccessible(true);
                    zeroVec = vCtor.newInstance(0.0d, 0.0d, 0.0d);
                } catch (Throwable ignored) { zeroVec = null; }
            }

            for (Constructor<?> ctor : ctors) {
                try {
                    ctor.setAccessible(true);
                    Class<?>[] params = ctor.getParameterTypes();
                    Object[] args = new Object[params.length];
                    boolean skipCtor = false;
                    // Keep counters for assigning x,y,z in order to doubles
                    int doubleAssignIndex = 0;
                    for (int i = 0; i < params.length; i++) {
                        Class<?> p = params[i];

                        if (p.isAssignableFrom(npc.getClass()) || p.isAssignableFrom(Class.forName("net.minecraft.server.level.ServerPlayer"))) {
                            args[i] = npc;
                            continue;
                        }
                        if (p == int.class || p == Integer.class) {
                            args[i] = (idVal != null) ? idVal : 0;
                            continue;
                        }
                        if (p == java.util.UUID.class) {
                            args[i] = uuid;
                            continue;
                        }
                        if (p == double.class || p == Double.class) {
                            // Give X,Y,Z in order for first three doubles, then try head rotation or 0
                            if (doubleAssignIndex == 0 && xVal != null) { args[i] = xVal; }
                            else if (doubleAssignIndex == 1 && yVal != null) { args[i] = yVal; }
                            else if (doubleAssignIndex == 2 && zVal != null) { args[i] = zVal; }
                            else if (yHeadRotVal != null) { args[i] = yHeadRotVal; }
                            else { args[i] = 0.0d; }
                            doubleAssignIndex++;
                            continue;
                        }
                        if (p == float.class || p == Float.class) {
                            // assign rotation floats
                            if (xRotVal != null) { args[i] = xRotVal; }
                            else if (yRotVal != null) { args[i] = yRotVal; }
                            else args[i] = 0.0f;
                            continue;
                        }
                        // EntityType
                        try {
                            Class<?> entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType");
                            if (entityTypeClass.isAssignableFrom(p) || p.isAssignableFrom(entityTypeClass) || p == entityTypeClass) {
                                args[i] = entityType;
                                continue;
                            }
                        } catch (Throwable ignore) {}

                        // Vec3
                        if (vec3Class != null && p.isAssignableFrom(vec3Class)) {
                            args[i] = zeroVec;
                            continue;
                        }

                        // Collection or List -> maybe a single entry collection (not common here) -> pass null
                        if (java.util.Collection.class.isAssignableFrom(p)) {
                            args[i] = java.util.Collections.emptyList();
                            continue;
                        }

                        // If param is Object-like, give null
                        args[i] = null;
                    }

                    // Try instantiate
                    Object pkt = ctor.newInstance(args);
                    if (pkt != null) return (Packet<?>) pkt;
                } catch (Throwable t) {
                    // try next constructor
                }
            }

        } catch (Throwable t) {
            Bukkit.getLogger().warning("[LNPC] buildAddEntityPacket error: " + t.getMessage());
            t.printStackTrace();
        }
        return null;
    }

    /* Helper methods */
    private static Object invokeIfExists(Object target, String[] methodNames) {
        if (target == null) return null;
        for (String name : methodNames) {
            try {
                Method m = null;
                try {
                    m = target.getClass().getMethod(name);
                } catch (NoSuchMethodException nsme) {
                    // try declared
                    try { m = target.getClass().getDeclaredMethod(name); } catch (NoSuchMethodException ignore) {}
                }
                if (m != null) {
                    m.setAccessible(true);
                    return m.invoke(target);
                }
            } catch (Throwable ignored) {}
        }
        // try fields
        for (String fName : methodNames) {
            try {
                Field f = target.getClass().getDeclaredField(fName);
                f.setAccessible(true);
                return f.get(target);
            } catch (Throwable ignored) {}
        }
        return null;
    }
    private static Double asDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Throwable ignored) { return null; }
    }
    private static Float asFloat(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).floatValue();
        try { return Float.parseFloat(o.toString()); } catch (Throwable ignored) { return null; }
    }

}
