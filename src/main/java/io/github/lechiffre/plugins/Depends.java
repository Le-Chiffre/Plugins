package io.github.lechiffre.plugins;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Add this annotation to member variables to plugin or injected types.
 * When used inside a plugin, the plugin loader will make sure that each
 * Depends member is initialized correctly BEFORE the class constructor is called.
 *
 * It is not recommended to use this outside of a plugin, but when needed,
 * call PluginLoader.currentLoader.resolveDependencies(this) to get valid instances for dependencies.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Depends {}
