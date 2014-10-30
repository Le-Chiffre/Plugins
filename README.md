Plugins
===

Large software always needs some way of managing dependencies between different parts of the program. Often there will be systems that are shared between multiple others. In many software systems, these problems are not really solved in a structural way - Singletons will pop up all over the place, and nobody really knows what depends on what, and what order they need to be initialized in.

In Babbla this problem would be even more prevalent, because of all the different plugins that can be created by third parties and the common code that could be shared between them. We therefore decided to solve it as a whole by creating the PluginLoader system. By using annotations, we can define what parts of the software each class depends on, and PluginLoader will automatically find and solve any dependencies in the correct order, without needing to create any Singletons.

Implicit application definition
---

In addition to being able to automatically solve dependencies between classes, PluginLoader can also load a set of plugins and services defined in an external file. This means that there is no need to define a “world-class” that decides what cards should be shown, what services should be loaded, and what views should be injected. Instead, we can simply have different configuration files for different run modes of the software. The file is sent to PluginLoader, which will automatically instantiate anything needed by the configuration.

By using this implicit way of defining what the application consists of, we have been able to create a very lean base system. The base Babbla application just consists of a list of friends and an area where cards can be displayed - nothing more. Every other part of the application is defined as isolated classes that may or may not be loaded by the PluginLoader.

Testing support
---

PluginLoader also has advanced support for automated testing, allowing us to deterministically test use cases of the software that depend completely on complex interaction between external input. This is done by combining the flexibility of the signal system (see below) with the ability to dynamically override dependencies through PluginLoader.

PluginLoader has support for overriding both interface types and normal instances. For interface types one needs to provide a default implementation - otherwise, how would PluginLoader know what class to create - while normal classes can be overridden with subtypes. Note that the overriding process is completely transparent to all users, and there is no need to change any existing code. One example of using this in practice MockPlaces class, which is used by unit tests to have control over the restaurants that are currently close by.

Complex dependencies
---

Some plugins need access to data that cannot be expressed as plugins. For example, a service that sends the current position to a server needs access to the current Activity, which is not available until the app has started. For this, PluginLoader has support for custom initializers. One simply provides a class or interface type together with a callback function, and the callback will be called for each dependency that extends or implements the type. Through this one can keep a list of plugins that need an activity, and automatically update them when any is started. 

Ease of use
---

The plugin system is very easy to use without needing to understand its workings. In order to get an instance to a dependency inside a plugin, one simply adds it as a member variable and adds the @Shared annotation. There is nothing more to do - the member will be set to a valid instance before the plugin constructor is even called! Defining classes that should have no more than one instance is just as easy - simply add @Shared to a class and PluginLoader will create no more than one instance of it.
