package se.mickelus.mutil.data;

import com.google.common.collect.Maps;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.Environment;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.stream.Collectors;

@ParametersAreNonnullByDefault
public class DataStore<V> extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {
    protected static final int jsonExtLength = ".json".length();
    private static final Logger logger = LogManager.getLogger();
    protected Gson gson;
    protected String namespace;
    protected String directory;
    protected Class<V> dataClass;
    protected Map<ResourceLocation, JsonElement> rawData;
    protected Map<ResourceLocation, V> dataMap;
    protected List<Runnable> listeners;
    private DataDistributor syncronizer;

    public DataStore(Gson gson, String namespace, String directory, Class<V> dataClass, DataDistributor synchronizer) {
        this.gson = gson;
        this.namespace = namespace;
        this.directory = directory;

        this.dataClass = dataClass;
        this.syncronizer = synchronizer;

        rawData = Collections.emptyMap();
        dataMap = Collections.emptyMap();

        listeners = new LinkedList<>();
    }

    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        logger.debug("Reading data for {} data store...", directory);
        Map<ResourceLocation, JsonElement> map = Maps.newHashMap();
        int i = this.directory.length() + 1;

        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager.listResources(directory, rl -> rl.getPath().endsWith(".json")).entrySet()) {
            if (!namespace.equals(entry.getKey().getNamespace())) {
                continue;
            }

            String path = entry.getKey().getPath();
            ResourceLocation location = new ResourceLocation(entry.getKey().getNamespace(), path.substring(i, path.length() - jsonExtLength));

            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json;

                if (dataClass.isArray()) {
                    JsonArray sources = getSources(entry.getValue());
                    json = GsonHelper.fromJson(gson, reader, JsonArray.class);
                    json.getAsJsonArray().forEach(element -> {
                        if (element.isJsonObject()) {
                            element.getAsJsonObject().add("sources", sources);
                        }
                    });
                } else {
                    json = GsonHelper.fromJson(gson, reader, JsonElement.class);
                    json.getAsJsonObject().add("sources", getSources(entry.getValue()));
                }

                if (json != null) {
                    if (shouldLoad(json)) {
                        JsonElement duplicate = map.put(location, json);
                        if (duplicate != null) {
                            throw new IllegalStateException("Duplicate data ignored with ID " + location);
                        }
                    } else {
                        logger.debug("Skipping data '{}' due to condition", entry.getKey());
                    }
                } else {
                    logger.error("Couldn't load data from '{}' as it's null or empty", entry.getKey());
                }
            } catch (IllegalArgumentException | IOException | JsonParseException exception) {
                logger.error("Couldn't parse data '{}' from '{}'", location, entry.getKey(), exception);
            }
        }

        return map;
    }

    protected JsonArray getSources(Resource resource) {
        String fileId = resource.sourcePackId();
        JsonArray result = new JsonArray();

        ModList.get().getModFiles().stream()
                .filter(modInfo -> fileId.equals(modInfo.getFile().getFileName()))
                .flatMap(fileInfo -> fileInfo.getMods().stream())
                .map(IModInfo::getDisplayName)
                .forEach(result::add);

        if (result.size() == 0) {
            result.add(fileId);
        }

        return result;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> splashList, ResourceManager resourceManager, ProfilerFiller profiler) {
        rawData = splashList;

        // PacketHandler dependencies get upset when called upon before the server has started properly
        if (Environment.get().getDist().isDedicatedServer() && ServerLifecycleHooks.getCurrentServer() != null) {
            syncronizer.sendToAll(directory, rawData);
        }

        parseData(rawData);
    }

    public void sendToPlayer(ServerPlayer player) {
        syncronizer.sendToPlayer(player, directory, rawData);
    }

    public void loadFromPacket(Map<ResourceLocation, String> data) {
        Map<ResourceLocation, JsonElement> splashList = data.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            if (dataClass.isArray()) {
                                return GsonHelper.fromJson(gson, entry.getValue(), JsonArray.class);
                            } else {
                                return GsonHelper.fromJson(gson, entry.getValue(), JsonElement.class);
                            }
                        }
                ));

        parseData(splashList);
    }

    public void parseData(Map<ResourceLocation, JsonElement> splashList) {
        logger.info("Loaded {} {}", String.format("%3d", splashList.values().size()), directory);
        dataMap = splashList.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> gson.fromJson(entry.getValue(), dataClass)
                ));

        processData();

        listeners.forEach(Runnable::run);
    }

    protected boolean shouldLoad(JsonElement json) {
        if (json.isJsonArray()) {
            JsonArray arr = json.getAsJsonArray();
            if (arr.size() > 0) {
                json = arr.get(0);
            }
        }

        if (!json.isJsonObject()) {
            return true;
        }

        JsonObject jsonObject = json.getAsJsonObject();
        return !jsonObject.has("conditions")
                || CraftingHelper.processConditions(GsonHelper.getAsJsonArray(jsonObject, "conditions"), ICondition.IContext.EMPTY);
    }

    protected void processData() {

    }

    public Map<ResourceLocation, JsonElement> getRawData() {
        return rawData;
    }

    public String getDirectory() {
        return directory;
    }

    /**
     * Get the resource at the given location from the set of resources that this listener is managing
     *
     * @param resourceLocation A resource location
     * @return An object matching the type of this listener, or null if none exists at the given location
     */
    public V getData(ResourceLocation resourceLocation) {
        return dataMap.get(resourceLocation);
    }

    /**
     * @return all data from this store.
     */
    public Map<ResourceLocation, V> getData() {
        return dataMap;
    }

    /**
     * Get all resources (if any) that are within the directory denoted by the provided resource location
     *
     * @param resourceLocation
     * @return
     */
    public Collection<V> getDataIn(ResourceLocation resourceLocation) {
        return getData().entrySet().stream()
                .filter(entry -> resourceLocation.getNamespace().equals(entry.getKey().getNamespace())
                        && entry.getKey().getPath().startsWith(resourceLocation.getPath()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /**
     * Listen to changes on resources in this store
     *
     * @param callback A runnable that is to be called when the store is reloaded
     */
    public void onReload(Runnable callback) {
        listeners.add(callback);
    }
}
