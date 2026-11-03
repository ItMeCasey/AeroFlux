package com.itmecasey.aeroflux.compat.crafttweaker;

import com.blamejared.crafttweaker.api.plugin.CraftTweakerPlugin;
import com.blamejared.crafttweaker.api.plugin.ICraftTweakerPlugin;
import com.blamejared.crafttweaker.api.plugin.IRecipeComponentRegistrationHandler;

/**
 * Registers the recipe components {@link FluxRecipeHandler} takes machine recipes apart into.
 * CraftTweaker has no component for inputs with counts on this version, so AeroFlux defines its
 * own. CraftTweaker finds this class by its annotation and only when it is installed.
 */
@CraftTweakerPlugin("aeroflux:main")
public class AeroFluxCraftTweakerPlugin implements ICraftTweakerPlugin {
    @Override
    public void registerRecipeComponents(IRecipeComponentRegistrationHandler handler) {
        handler.registerRecipeComponent(FluxRecipeHandler.INPUTS);
        handler.registerRecipeComponent(FluxRecipeHandler.EXTRAS);
    }
}
