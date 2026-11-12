package com.github.groundbreakingmc.gikymessage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.Style;

import java.lang.reflect.Method;

/**
 * What the Adventure API present at run time can represent. The library is compiled against
 * a newer Adventure than many platforms provide. A template that needs a missing feature is
 * rejected while it compiles, instead of failing with a linkage error once it is rendered.
 */
final class Platform {

    /** Shadow colors, added in Adventure 4.18. */
    static final boolean SHADOW_COLORS = declares(Style.Builder.class, "shadowColor")
            && present("net.kyori.adventure.text.format.ShadowColor");

    /** Object components such as player heads and sprites, added in Adventure 4.25. */
    static final boolean OBJECT_COMPONENTS = declares(Component.class, "object")
            && present("net.kyori.adventure.text.object.ObjectContents");

    /** Fallback text of a translatable component, added in Adventure 4.13. */
    static final boolean TRANSLATION_FALLBACKS = declares(TranslatableComponent.class, "fallback");

    private Platform() {
    }

    // The method must exist on the class this library links against. Another copy of Adventure
    // on a different class loader can make the argument types resolvable without it.
    private static boolean declares(Class<?> type, String name) {
        try {
            for (final Method method : type.getMethods()) {
                if (method.getName().equals(name)) return true;
            }
            return false;
        } catch (LinkageError incomplete) {
            return false;
        }
    }

    private static boolean present(String type) {
        try {
            Class.forName(type, false, Platform.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }
}
