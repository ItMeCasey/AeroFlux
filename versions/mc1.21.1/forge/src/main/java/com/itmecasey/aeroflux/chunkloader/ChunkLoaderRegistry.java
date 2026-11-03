package com.itmecasey.aeroflux.chunkloader;

import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.core.save.SaveSchema;
import com.itmecasey.aeroflux.save.SaveVersion;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every Flux Chunk Loader in one dimension, saved with that dimension's other saved data.
 *
 * <p>A loader adds itself on its first tick and takes itself out when it is broken, so the list
 * outlives unloaded chunks. That makes it the record of which loaders exist without loading
 * anything: the per-player cap counts from it, {@code /aeroflux chunkloaders} lists it, and the
 * ticket check at server start drops every ticket whose loader is not in it.
 */
public final class ChunkLoaderRegistry extends SavedData {
    private static final Codec<Tier> TIER_CODEC = Codec.STRING.xmap(name -> Tier.valueOf(name.toUpperCase(Locale.ROOT)), Tier::id);

    /**
     * One loader.
     *
     * @param pos    where the block is
     * @param owner  the player who placed it, if a player did
     * @param tier   its tier, which sets its area
     * @param active whether it holds its chunk tickets right now
     */
    public record Entry(BlockPos pos, Optional<UUID> owner, Tier tier, boolean active) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Entry::owner),
                TIER_CODEC.fieldOf("tier").forGetter(Entry::tier),
                Codec.BOOL.optionalFieldOf("active", false).forGetter(Entry::active)
        ).apply(instance, Entry::new));
    }

    /** Saved with the loaders, so a later layout can still read this one; a registry saved before versioning has none. */
    private static final Codec<ChunkLoaderRegistry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Entry.CODEC.listOf().fieldOf("loaders").forGetter(registry -> new ArrayList<>(registry.entries.values())),
            Codec.INT.optionalFieldOf(SaveSchema.TAG, 0).forGetter(registry -> SaveSchema.Kind.CHUNK_LOADER_REGISTRY.current())
    ).apply(instance, ChunkLoaderRegistry::new));

    private static final String FILE = "aeroflux_chunk_loaders";

    private static final SavedData.Factory<ChunkLoaderRegistry> FACTORY =
            new SavedData.Factory<>(ChunkLoaderRegistry::new, ChunkLoaderRegistry::load, null);

    /** Sorted by position so the admin list reads the same every time. */
    private final Map<BlockPos, Entry> entries = new TreeMap<>();
    /** The version the data was saved with; the current one for a registry made new. */
    private int loadedVersion = SaveSchema.Kind.CHUNK_LOADER_REGISTRY.current();

    public ChunkLoaderRegistry() {
    }

    private ChunkLoaderRegistry(List<Entry> loaded, int savedVersion) {
        for (Entry entry : loaded) {
            entries.put(entry.pos(), entry);
        }
        this.loadedVersion = SaveVersion.check(SaveSchema.Kind.CHUNK_LOADER_REGISTRY, savedVersion);
        if (SaveSchema.needsMigration(SaveSchema.Kind.CHUNK_LOADER_REGISTRY, savedVersion)) {
            // Older data has no migration to run yet; saving it again writes the current version.
            setDirty();
        }
    }

    /** The codec the registry is saved with, for the self-test. */
    public static Codec<ChunkLoaderRegistry> codec() {
        return CODEC;
    }

    /** The version this registry was loaded with, for the self-test. */
    public int loadedVersion() {
        return loadedVersion;
    }

    /** The registry of a dimension, made empty the first time it is asked for. */
    public static ChunkLoaderRegistry of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    private static ChunkLoaderRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        return CODEC.parse(NbtOps.INSTANCE, tag.get("data")).result().orElseGet(ChunkLoaderRegistry::new);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CODEC.encodeStart(NbtOps.INSTANCE, this).result().ifPresent(data -> tag.put("data", data));
        return tag;
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public Optional<Entry> get(BlockPos pos) {
        return Optional.ofNullable(entries.get(pos));
    }

    public boolean contains(BlockPos pos) {
        return entries.containsKey(pos);
    }

    /** Adds or replaces the entry at its position. */
    public void put(Entry entry) {
        if (!entry.equals(entries.put(entry.pos(), entry))) {
            setDirty();
        }
    }

    /** Takes the loader at a position out, if there is one. */
    public boolean remove(BlockPos pos) {
        boolean removed = entries.remove(pos) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** Records whether the loader at a position holds its tickets. */
    public void setActive(BlockPos pos, boolean active) {
        Entry entry = entries.get(pos);
        if (entry != null && entry.active() != active) {
            put(new Entry(entry.pos(), entry.owner(), entry.tier(), active));
        }
    }

    /** Running loaders in this dimension, apart from the one at {@code except}. */
    public int activeCount(BlockPos except) {
        int count = 0;
        for (Entry entry : entries.values()) {
            if (entry.active() && !entry.pos().equals(except)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Running loaders one owner has in every dimension, apart from one loader (normally the one
     * asking). Loaders nobody placed by hand share the empty owner.
     */
    public static int activeOwnedBy(MinecraftServer server, Optional<UUID> owner, ServerLevel exceptLevel, BlockPos exceptPos) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entry entry : of(level).entries.values()) {
                boolean self = level == exceptLevel && entry.pos().equals(exceptPos);
                if (!self && entry.active() && entry.owner().equals(owner)) {
                    count++;
                }
            }
        }
        return count;
    }
}
