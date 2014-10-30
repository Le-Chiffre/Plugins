package io.github.lechiffre.plugins;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
/**
 * Indicates that one instance of this class should be shared between all users.
 * This also applies to classes derived from types with this annotation.
 */
public @interface Shared {}
