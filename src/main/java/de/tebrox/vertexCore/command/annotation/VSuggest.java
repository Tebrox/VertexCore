package de.tebrox.vertexCore.command.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface VSuggest {
    /**
     * Command path, same format as @VCommand/@VSub:
     * e.g. "vertexcore" or "vertexcore migrate"
     */
    String value();
}
