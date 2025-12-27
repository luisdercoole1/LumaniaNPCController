package net.lumania.lumaniaNPCController.models;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.lumania.lumaniaNPCController.utils.Skin;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class NPC {

    private String name;
    private String[] skin;
    private String type;
    private GameProfile profile;
    private ClientInformation clientInformation;
    private CommonListenerCookie cookie;
    private ServerPlayer player;
    private Connection connection;
    private Location location;

    public NPC(String name, String skinName, String type, Location location){
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel world = ((CraftWorld) Objects.requireNonNull(location.getWorld())).getHandle();

        this.name = name;
        this.skin = Skin.getSkin(skinName);
        this.type = type;
        this.location = location;

        this.profile = new GameProfile(UUID.randomUUID(), name);

// Apply skins
        try {
            if (this.skin != null && this.skin.length >= 2) {
                this.profile.getProperties().put("textures", new Property("textures", this.skin[0], this.skin[1]));
            }
        } catch (Throwable ignored) {}

// 1.21.8: ClientInformation has no public constructor → use factory
        this.clientInformation = ClientInformation.createDefault();

// 1.21.8: Cookie via constructor (factory removed)
        this.cookie = new CommonListenerCookie(
                profile,
                0,                    // latency (ping)
                this.clientInformation,
                false                 // not listed
        );

// 1.21.8: ServerPlayer constructor unchanged
        net.minecraft.server.level.ServerPlayer serverPlayer =
                new ServerPlayer(server, world, profile, clientInformation);

// dummy connection
        Connection dummyConnection = new Connection(PacketFlow.SERVERBOUND);

// 1.21.8: Constructor now requires cookie
        ServerGamePacketListenerImpl dummyListener =
                new ServerGamePacketListenerImpl(server, dummyConnection, serverPlayer, cookie);

        serverPlayer.setPos(location.getX(), location.getY(), location.getZ());

// Bukkit teleport
        Player bukkitNPC = serverPlayer.getBukkitEntity();
        Location npcLocation = new Location(location.getWorld(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
        bukkitNPC.teleport(npcLocation);

        this.player = serverPlayer;

    }

    public static void enableSkinLayers(ServerPlayer npc, org.bukkit.entity.Player viewer) {
        try {
            // EntityDataAccessor für Player Skin Flags (Index 17)
            EntityDataAccessor<Byte> DATA_PLAYER_MODE_CUSTOMISATION =
                    new EntityDataAccessor<>(17, EntityDataSerializers.BYTE);

            // Hole die EntityData des NPCs
            SynchedEntityData entityData = npc.getEntityData();

            // Setze alle Skin-Layer auf sichtbar (127 = alle Bits gesetzt)
            entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 127);

            // Erstelle das Metadata-Update-Paket
            List<SynchedEntityData.DataValue<?>> dataValues = new ArrayList<>();
            dataValues.add(SynchedEntityData.DataValue.create(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 127));

            ClientboundSetEntityDataPacket metadataPacket =
                    new ClientboundSetEntityDataPacket(npc.getId(), dataValues);

            // Sende das Paket an den Spieler
            ServerGamePacketListenerImpl connection = ((CraftPlayer) viewer).getHandle().connection;
            connection.send(metadataPacket);

        } catch (Exception e) {
            Bukkit.getLogger().warning("[NPC] Fehler beim Setzen der Skin-Layer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getSkin() {
        return skin;
    }

    public void setSkin(String[] skin) {
        this.skin = skin;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public GameProfile getProfile() {
        return profile;
    }

    public void setProfile(GameProfile profile) {
        this.profile = profile;
    }

    public ClientInformation getClientInformation() {
        return clientInformation;
    }

    public void setClientInformation(ClientInformation clientInformation) {
        this.clientInformation = clientInformation;
    }

    public CommonListenerCookie getCookie() {
        return cookie;
    }

    public void setCookie(CommonListenerCookie cookie) {
        this.cookie = cookie;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public void setPlayer(ServerPlayer player) {
        this.player = player;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}
