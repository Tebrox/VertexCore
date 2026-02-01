package de.tebrox.vertexCore.command.annotation;

import de.tebrox.vertexCore.command.api.VisibilityPolicy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface VPerm {
    String value();
    VisibilityPolicy visibility() default VisibilityPolicy.IF_EXECUTABLE;
}
