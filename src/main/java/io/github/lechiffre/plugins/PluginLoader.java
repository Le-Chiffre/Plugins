package io.github.lechiffre.plugins;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages plugin creation and dependency resolving for plugins.
 */
public class PluginLoader {
    /**
     * A custom initializer for types that implement a certain interface.
     * If an initializer is defined for a type,
     * this will be called each time an instance is created.
     */
    public interface Initializer<T> {
        /** Called whenever an object instance for this initializer is created. */
        void create(T obj);
    }

    /** The default plugin loader. */
    public static PluginLoader currentLoader = new PluginLoader();

    /** The package where named classes are searched. */
    private static final String PLUGIN_PATH = "nu.babbla.babbla.plugins.";

    /** The package where named overrides are searched. */
    private static final String OVERRIDE_PATH = "nu.babbla.";

    /** The name of the native helper lib. */
    private static final String HELPER_LIB = "pluginHelper";

    /** Maps from class name -> class type. */
    private final Map<String, Class> classMap = new HashMap<>();

    /** Maps from class type -> [Initializer]. */
    private final Map<Class, Collection<Initializer>> initMap = new HashMap<>();

    /** Maps from class type -> shared instance cache (if applicable for type). */
    private final Map<Class, Object> sharedInstanceMap = new HashMap<>();

    /** Maps from interface -> initializer (not the class type!). */
    private final Map<Class, Initializer> initializers = new HashMap<>();

    /** Maps from iface type -> overridden implementation. */
    private final Map<Class, Class> overrides = new HashMap<>();

    /** List of retained plugins. */
    private final List<Object> retainList = new ArrayList<>();

    static {
        // On Windows we have separate dlls for 32 and 64 bit.
        if(System.getProperty("os.name").startsWith("Windows")) {
            System.loadLibrary(HELPER_LIB + System.getProperty("sun.arch.data.model"));
        } else {
            System.loadLibrary("pluginHelper");
        }
    }

    public PluginLoader() {
        currentLoader = this;
    }

    /**
     * Tries to load a plugin with the provided name, and resolves its dependencies.
     * @param className The name of the plugin to load.
     * @param retain If set, the plugin will be kept alive for the duration of the program.
     */
    public Object loadPlugin(String className, boolean retain) {
        Class foundClass = loadClass(className);
        return loadPlugin(foundClass, retain);
    }

    /**
     * Loads a plugin of the provided type, and resolves its dependencies.
     * @param type The class to load.
     * @param retain If set, the plugin will be kept alive for the duration of the program.
     */
    public <T> T loadPlugin(Class<T> type, boolean retain) {
        Object obj = instantiateDependency(type);
        if(retain) {
            retainList.add(obj);
        }
        return (T)obj;
    }

    /**
     * Loads a plugin of the provided type, and resolves its dependencies.
     * @param type The class to load.
     */
    public <T> T loadPlugin(Class<T> type) {
        return loadPlugin(type, false);
    }

    /**
     * Resolves plugin dependencies for an existing class instance.
     * This is useful for types that are not loaded through XML or as dependencies.
     * @param obj The object to resolve. This does nothing if there are no dependencies in it.
     */
    public void resolveDependencies(Object obj) {
        loadDependencies(obj.getClass(), obj);
    }

    /**
     * Adds a custom initializer for types that implement the provided interface.
     */
    public <T> void addInitializer(Class<T> iface, Initializer<T> init) {
        initializers.put(iface, init);
    }

    /**
     * Adds a dependency override by name. The provided names are prepended with OVERRIDE_PATH.
     */
    public void override(String iface, String type) {
        override(loadClass(iface, OVERRIDE_PATH), loadClass(type, OVERRIDE_PATH));
    }

    /**
     * Overrides dependencies of the provided interface with an instance of the provided class.
     * This means that plugins that use the interface will get an instance of that class,
     * instead of the default defined in the interface.
     */
    public void override(Class iface, Class type) {
        overrides.put(iface, type);
    }

    /**
     * Removes any overrides defined for the provided interface.
     */
    public void removeOverride(Class iface) {
        overrides.remove(iface);
    }

    /**
     * Returns true if the provided type should be shared.
     */
    public boolean isShared(Class type) {
        while(type != null) {
            if(type.getAnnotation(Shared.class) != null) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    /**
     * Tries to load a class with the provided name using the provided package.
     */
    private Class loadClass(String className, String path) {
        className = path + className;

        // Check if we have cached this type. If not, try to find it.
        Class type = classMap.get(className);
        if(type == null) {
            try {
                type = Thread.currentThread().getContextClassLoader().loadClass(className);
                classMap.put(className, type);
            } catch (ClassNotFoundException e) {
                throw new UnsupportedOperationException("Could not load the plugin class " + className);
            }
        }
        return type;
    }

    /**
     * Tries to load a class with the provided name using the default package.
     */
    private Class loadClass(String className) {
        // Currently we just prepend the default plugin path.
        // TODO: We should really recursively check for the class to allow simpler XML.
        return loadClass(className, PLUGIN_PATH);
    }

    /**
     * Returns the initializers that should be applied to the provided type.
     */
    private Collection<Initializer> getInitializers(Class type) {
        // Return the cache, if we have any.
        Collection<Initializer> init = initMap.get(type);
        if(init != null) return init;

        // Fill the cache if we don't have anything.
        init = findInitializers(type, new HashSet(1));
        initMap.put(type, init);
        return init;
    }

    /**
     * Finds the initializers for the provided type.
     * This is a slow function used to fill the cache only.
     */
    private Collection<Initializer> findInitializers(Class type, Set set) {
        // Check if we have an initializer for this class, a base class, or an interface it implements.
        Initializer init = initializers.get(type);
        if(init != null) set.add(init);

        for(Class c : type.getInterfaces()) {
            init = initializers.get(c);
            if(init != null) set.add(init);
        }

        // Call this recursively for all superclasses.
        Class s = type.getSuperclass();
        if(s != null) {
            findInitializers(s, set);
        }
        return set;
    }

    /**
     * Resolves dependencies in and constructs the provided plugin instance.
     */
    private void initInstance(Object obj, Class type) {
        // Set fields, then call constructor.
        loadDependencies(type, obj);
        constructInstance(type, obj);

        // Call all initializers for this type.
        Collection<Initializer> inits = getInitializers(type);
        for(Initializer i : inits) {
            i.create(obj);
        }
    }

    /**
     * Loads all dependencies for the provided instance.
     * @param dep The instance class type.
     * @param object The instance to load dependencies for.
     */
    private void loadDependencies(Class dep, Object object) {
        while(dep != null) {
            for (Field field : dep.getDeclaredFields()) {
                Depends depends = field.getAnnotation(Depends.class);
                if (depends != null) {
                    Class type = field.getType();
                    if (type != null) {
                        field.setAccessible(true);
                        try {
                            field.set(object, instantiateDependency(type));
                        } catch (IllegalAccessException e) {
                            throw new UnsupportedOperationException("Could not set dependency value.");
                        }
                    }
                }
            }
            dep = dep.getSuperclass();
        }
    }

    /**
     * Resolves a dependency type and creates an instance of it.
     * @param dependency The dependency type to instantiate.
     */
    private Object instantiateDependency(Class dependency) {
        // Check if there are any overrides for this type.
        Class override = overrides.get(dependency);
        if(override != null) {
            return instantiateConcreteDependency(override);
        }

        // If the dependency is an interface or abstract, try to get an implementation.

        if(isAbstract(dependency)) {
            DefaultDepends def = (DefaultDepends)dependency.getAnnotation(DefaultDepends.class);
            if(def != null) {
                return instantiateConcreteDependency(def.value());
            } else {
                // There is no implementation for this interface.
                throw new UnsupportedOperationException(
                        "Could not find an implementation for the interface" + dependency.getName());
            }
        }

        // For a normal type, just create an instance.
        return instantiateConcreteDependency(dependency);
    }

    /**
     * Returns true if the provided class is abstract or an interface.
     */
    private boolean isAbstract(Class c) {
        return ((c.getModifiers() & Modifier.ABSTRACT) > 0) || c.isInterface();
    }

    /**
     * Creates an instance of a concrete dependency type.
     */
    private Object instantiateConcreteDependency(Class dependency) {
        if (isShared(dependency)) {
            Object instance = sharedInstanceMap.get(dependency);
            if (instance == null) {
                instance = allocateInstance(dependency);
                sharedInstanceMap.put(dependency, instance);
                initInstance(instance, dependency);
            }
            return instance;
        } else {
            Object instance = allocateInstance(dependency);
            initInstance(instance, dependency);
            return instance;
        }
    }

    /**
     * Allocates memory for an instance of the provided type and initializes the class header.
     */
    private static native Object allocateInstance(Class type);

    /**
     * Calls the default constructor of the provided type on the provided memory.
     */
    private static native void constructInstance(Class type, Object inst);
}


