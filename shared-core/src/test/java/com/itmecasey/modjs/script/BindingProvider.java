package com.itmecasey.modjs.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

/** Test double with the same shape as ModJS's BindingProvider, so the reflective bridge can be exercised without ModJS. */
@FunctionalInterface
public interface BindingProvider {
    void install(ScriptType type, Context cx, ScriptableObject scope);
}
