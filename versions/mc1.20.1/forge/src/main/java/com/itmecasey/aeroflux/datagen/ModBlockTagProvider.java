package com.itmecasey.aeroflux.datagen;

import com.itmecasey.aeroflux.AeroFlux;
import com.itmecasey.aeroflux.core.Tier;
import com.itmecasey.aeroflux.registry.ModBlocks;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/** Generates block tags: which tool mines AeroFlux blocks and how strong it must be. */
public class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                               ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, AeroFlux.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.CONDUIT_BUNDLE.get())
                .add(ModBlocks.PIPE_NODE.get());

        for (Tier tier : Tier.values()) {
            tag(BlockTags.MINEABLE_WITH_PICKAXE)
                    .add(ModBlocks.collector(tier).get())
                    .add(ModBlocks.tank(tier).get());
            tag(BlockTags.NEEDS_STONE_TOOL)
                    .add(ModBlocks.collector(tier).get())
                    .add(ModBlocks.tank(tier).get());
        }
    }
}
