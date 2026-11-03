package com.itmecasey.aeroflux.compat.crafttweaker;

import com.blamejared.crafttweaker.api.plugin.CraftTweakerPlugin;
import com.blamejared.crafttweaker.api.plugin.ICraftTweakerPlugin;
import com.blamejared.crafttweaker.api.plugin.IRecipeComponentRegistrationHandler;
import com.blamejared.crafttweaker.api.recipe.component.BuiltinRecipeComponents;

/**
 * Registers the recipe components {@link FluxRecipeHandler} takes machine recipes apart into.
 * CraftTweaker defines inputs with counts but leaves registering them to the mods that use them;
 * registering one twice with the same type is allowed, so other mods doing the same is fine.
 * CraftTweaker finds this class by its annotation and only when it is installed.
 */
@CraftTweakerPlugin("aeroflux:main")
public class AeroFluxCraftTweakerPlugin implements ICraftTweakerPlugin {
    @Override
    public void registerRecipeComponents(IRecipeComponentRegistrationHandler handler) {
        handler.registerRecipeComponent(BuiltinRecipeComponents.Input.INGREDIENTS_WITH_AMOUNTS);
        handler.registerRecipeComponent(FluxRecipeHandler.EXTRAS);
    }
}
