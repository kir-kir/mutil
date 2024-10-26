package se.mickelus.mutil.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;


/**
 * AbstractPacket class. Should be the parent of all packets wishing to use the PacketHandler.
 * @author sirgingalot, mickelus
 */
public abstract class AbstractPacket implements CustomPacketPayload {

    /**
     * Encode the packet data into the ByteBuf stream. Complex data sets may need specific data handlers (See @link{cpw.mods.fml.common.network.ByteBuffUtils})
     *
     * @param buffer the buffer to encode into
     */
    public abstract void toBytes(FriendlyByteBuf buffer);

    /**
     * Decode the packet data from the ByteBuf stream. Complex data sets may need specific data handlers (See @link{cpw.mods.fml.common.network.ByteBuffUtils})
     *
     * @param buffer the buffer to decode from
     */
    public abstract void fromBytes(FriendlyByteBuf buffer);

    /**
     * Handle the reception of this packet.
     *
     * @param player A reference to the sending player when handled on the server side
     */
    public abstract void handle(Player player);

    /**
     * Utility method that reads a string from a buffer object.
     * @param buffer The buffer containing the string to be read.
     * @return A string read from the buffer
     * @throws IOException
     */
    protected static String readString(FriendlyByteBuf buffer) throws IOException {
        String string = "";
        char c = buffer.readChar();

        while(c != '\0') {
            string += c;
            c = buffer.readChar();
        }

        return string;
    }

    protected static void writeString(String string, FriendlyByteBuf buffer) throws IOException {
        for (int i = 0; i < string.length(); i++) {
            buffer.writeChar(string.charAt(i));
        }
        buffer.writeChar('\0');
    }
}