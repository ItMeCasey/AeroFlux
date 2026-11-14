package com.itmecasey.modjs.script;

import java.util.ArrayList;
import java.util.List;

/** Test double for ModJS's ExtraBindings: keeps the registered providers. */
public final class ExtraBindings {
    public static final List<BindingProvider> PROVIDERS = new ArrayList<>();

    private ExtraBindings() {
    }

    public static void register(BindingProvider provider) {
        PROVIDERS.add(provider);
    }
}
