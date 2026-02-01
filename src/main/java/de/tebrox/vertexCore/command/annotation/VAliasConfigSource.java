package de.tebrox.vertexCore.command.annotation;

import de.tebrox.vertexCore.config.ConfigObject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface VAliasConfigSource {

    /**
     * Consumer main config class annotated with @StoreAt("config.yml") (or other file).
     * Use ConfigObject.class as "not set".
     */
    Class<? extends ConfigObject> config() default ConfigObject.class;

    /**
     * Optional file override (relative to plugin data folder). Overrides "config()".
     */
    String file() default "";

    /**
     * String-list path template. Supports "{root}" placeholder.
     */
    String rootAliasesPath() default "commands.{root}.aliases";
}
