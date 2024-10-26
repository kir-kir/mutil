package se.mickelus.mutil;


import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforgespi.Environment;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
class ConfigHandler {
    public static Client client;
    static ModConfigSpec clientSpec;

    public static void setup(IEventBus modBus, ModContainer container) {
        if (Environment.get().getDist().isClient()) {
            setupClient();
            container.registerConfig(ModConfig.Type.CLIENT, clientSpec);
            modBus.register(ConfigHandler.client);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void setupClient() {
        final Pair<Client, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Client::new);
        clientSpec = specPair.getRight();
        client = specPair.getLeft();
    }

    @OnlyIn(Dist.CLIENT)
    public static class Client {
        public ModConfigSpec.BooleanValue queryPerks;

        Client(ModConfigSpec.Builder builder) {
            queryPerks = builder
                    .comment("Controls if perks data should be queried on startup")
                    .define("query_perks", true);
        }
    }
}
