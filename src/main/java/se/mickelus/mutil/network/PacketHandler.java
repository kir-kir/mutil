package se.mickelus.mutil.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.function.Supplier;

@ParametersAreNonnullByDefault
public class PacketHandler {
    private static final Logger logger = LogManager.getLogger();

    private final SimpleChannel channel;
    private final ArrayList<Class<? extends AbstractPacket>> packets = new ArrayList<>();

    public PacketHandler(String namespace, String channelId, String protocolVersion) {
        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(namespace, channelId),
                () -> protocolVersion,
                protocolVersion::equals,
                protocolVersion::equals);
    }

    /**
     * Register your packet with the pipeline. Discriminators are automatically set.
     *
     * @param packetClass the class to register
     * @param supplier A supplier returning an object instance of packetClass
     *
     * @return whether registration was successful. Failure may occur if 256 packets have been registered or if the registry already contains this packet
     */
    public <T extends AbstractPacket> boolean registerPacket(Class<T> packetClass, Supplier<T> supplier) {
        if (packets.size() > 256) {
            logger.warn("Attempted to register packet but packet list is full: " + packetClass.toString());
            return false;
        }

        if (packets.contains(packetClass)) {
            logger.warn("Attempted to register packet but packet is already in list: " + packetClass.toString());
            return false;
        }

        channel.messageBuilder(packetClass, packets.size())
                .encoder(AbstractPacket::toBytes)
                .decoder(buffer -> {
                    T packet = supplier.get();
                    packet.fromBytes(buffer);
                    return packet;
                })
                .consumerNetworkThread(this::onMessage)
                .add();

        packets.add(packetClass);
        return true;
    }

    public void onMessage(AbstractPacket message, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isServer()) {
                message.handle(ctx.get().getSender());
            } else {
                message.handle(getClientPlayer());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private Player getClientPlayer() {
        return Minecraft.getInstance().player;
    }

    public void sendTo(AbstractPacket message, ServerPlayer player) {
        channel.sendTo(message, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public void sendToAllPlayers(AbstractPacket message) {
        channel.send(PacketDistributor.ALL.noArg(), message);
    }

    public void sendToAllPlayersNear(AbstractPacket message, BlockPos pos, double r2, ResourceKey<Level> dim) {
        channel.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(pos.getX(), pos.getY(), pos.getZ(), r2, dim)), message);
    }

    @OnlyIn(Dist.CLIENT)
    public void sendToServer(AbstractPacket message) {
        // crashes sometimes happen due to the connection being null
        if (Minecraft.getInstance().getConnection() != null) {
            channel.sendToServer(message);
        }
    }
}