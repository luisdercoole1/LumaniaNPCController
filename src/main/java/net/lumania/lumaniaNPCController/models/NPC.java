package net.lumania.lumaniaNPCController.models;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.lumania.lumaniaNPCController.utils.Skin;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_20_R4.CraftServer;
import org.bukkit.craftbukkit.v1_20_R4.CraftWorld;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
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

    public NPC(String name, String[] skin, String type, GameProfile profile, ClientInformation clientInformation, CommonListenerCookie cookie, ServerPlayer player, Connection connection, Location location) {
        this.name = name;
        this.skin = skin;
        this.type = type;
        this.profile = profile;
        this.clientInformation = clientInformation;
        this.cookie = cookie;
        this.player = player;
        this.connection = connection;
        this.location = location;
    }

    public NPC(String name, String skinName, String type, Location location){
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel world = ((CraftWorld) Objects.requireNonNull(location.getWorld())).getHandle();

        this.name = name;
        this.skin = Skin.getSkin(skinName);
        this.type = type;
        this.location = location;

        this.profile = new GameProfile(UUID.randomUUID(), name);
        // Apply skin textures to profile
        try {
            if (this.skin != null && this.skin.length >= 2) {
                this.profile.getProperties().put("textures", new Property("textures", this.skin[0], this.skin[1]));
            }
        } catch (Throwable ignored) {}

        this.clientInformation = new ClientInformation(
                "en_us",             // Sprache
                8,                   // Sichtweite
                ChatVisiblity.FULL,  // Chat-Modus
                true,                // Chat-Farben erlaubt
                0,                   // Model Custom Flags
                HumanoidArm.RIGHT,   // Standard-Hand
                false,               // Textfilter
                true                 // Listing erlaubt
        );

        this.cookie = new CommonListenerCookie(
                profile,      // GameProfile
                0,            // latency
                clientInformation,   // ClientInformation
                true          // secure profile, egal bei NPCs
        );

        net.minecraft.server.level.ServerPlayer serverPlayer = new ServerPlayer(server, world, profile, clientInformation);

        Connection dummyConnection = new Connection(PacketFlow.SERVERBOUND);
        ServerGamePacketListenerImpl dummyListener = new ServerGamePacketListenerImpl(
                server,
                dummyConnection,
                serverPlayer,      // gültiger Spieler statt null
                cookie
        );
        try {
            Field connectionField = ServerPlayer.class.getDeclaredField("connection");
            connectionField.setAccessible(true);
            connectionField.set(serverPlayer, dummyListener);
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.connection = dummyConnection;
        this.location = location;

        serverPlayer.setPos(location.getX(), location.getY(), location.getZ());
        Player bukkitNPC = serverPlayer.getBukkitEntity();
        Location npcLocation = new Location(location.getWorld(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        bukkitNPC.teleport(npcLocation);

        this.player = serverPlayer;
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
