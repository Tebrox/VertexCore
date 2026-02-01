package de.tebrox.vertexCore.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface VEnabled {
    boolean value() default true;

    /**
     * Read boolean from consumer main config.
     * Supports "{root}" placeholder. Example: "features.commands.{root}.reload"
     */
    String configPath() default "";

    boolean hideWhenDisabled() default true;

    String message() default "";
}
