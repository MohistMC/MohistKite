package com.mohistmc.kite.plugin;

import com.mohistmc.kite.MohistKiteAgent;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.apache.commons.lang3.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Warning;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.UnknownDependencyException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spigotmc.CustomTimingsHandler;
import org.spongepowered.asm.mixin.Mixins;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Represents a Java plugin loader, allowing plugins in the form of .jar
 */
public final class MohistKitePluginLoader implements PluginLoader {
    private final Pattern[] fileFilters = new Pattern[]{
            Pattern.compile("\\.papershelled\\.jar$"),
            Pattern.compile("\\.ps\\.jar$")
    };
    private final HashMap<String, MohistKitePlugin> plugins = new HashMap<>();
    private final HashMap<File, JarFile> jarFiles = new HashMap<>();
    private final HashMap<String, JarFile> jarFilesMap = new HashMap<>();
    private CustomTimingsHandler pluginParentTimer;

    //Cache for paper check.
    private boolean checkedPaper = false;
    private boolean isPaper = false;
    private MethodHandle eeCreate;
    private MethodHandle newTimed;

    @SuppressWarnings("unused")
    @Nullable
    public JarFile getPluginJar(@NotNull String name) {
        return jarFilesMap.get(name.replace(' ', '_'));
    }

    @SuppressWarnings("unused")
    @Nullable
    public MohistKitePlugin getPlugin(@NotNull String name) {
        return plugins.get(name.replace(' ', '_'));
    }

    @SuppressWarnings("unused")
    @NotNull
    public Collection<MohistKitePlugin> getPlugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    @Override
    @NotNull
    public MohistKitePlugin loadPlugin(@NotNull final File file) throws InvalidPluginException {
        if (!file.exists()) {
            throw new InvalidPluginException(new FileNotFoundException(file + " does not exist"));
        }

        try {
            JarFile jar = jarFiles.get(file);
            if (jar == null) jarFiles.put(file, jar = new JarFile(file));
            PluginDescriptionFile description = getPluginDescription(jar);
            MohistKitePluginDescription paperShelledPluginDescription = getPluginMohistKiteDescription(jar);
            String name = description.getName();
            File dataFolder = new File(file.getParent(), name);

            if (dataFolder.exists() && !dataFolder.isDirectory()) {
                throw new InvalidPluginException(String.format(
                        "Projected datafolder: `%s' for %s (%s) exists and is not a directory",
                        dataFolder,
                        description.getFullName(),
                        file
                ));
            }

            for (final String pluginName : description.getDepend()) {
                Plugin current = plugins.get(pluginName);
                if (current == null) {
                    throw new UnknownDependencyException("Unknown dependency " + pluginName +
                            ". Please download and install " + pluginName + " to run this plugin.");
                }
            }

            jarFiles.put(file, jar);
            MohistKiteAgent.getInstrumentation().appendToSystemClassLoaderSearch(jar);

            try {
                Class<?> jarClass;
                try {
                    jarClass = Class.forName(description.getMain());
                } catch (ClassNotFoundException ex) {
                    throw new InvalidPluginException("Cannot find main class `" + description.getMain() + "'", ex);
                }

                Class<? extends MohistKitePlugin> pluginClass;
                try {
                    pluginClass = jarClass.asSubclass(MohistKitePlugin.class);
                } catch (ClassCastException ex) {
                    throw new InvalidPluginException("main class `" + description.getMain() + "' does not extend JavaPlugin", ex);
                }

                MohistKitePlugin plugin = pluginClass.getConstructor(MohistKitePluginLoader.class,
                                MohistKitePluginDescription.class, PluginDescriptionFile.class, File.class)
                        .newInstance(this, paperShelledPluginDescription, description, file);

                jarFilesMap.put(name, jar);
                paperShelledPluginDescription.getMixins().forEach(it -> Mixins.addConfiguration(name + "|" + it));
                plugins.put(name, plugin);

                return plugin;
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException ex) {
                throw new InvalidPluginException("No public constructor", ex);
            } catch (InstantiationException ex) {
                throw new InvalidPluginException("Abnormal plugin type", ex);
            } catch (Throwable ex) {
                throw new InvalidPluginException(ex);
            }
        } catch (IOException | InvalidDescriptionException ex) {
            throw new InvalidPluginException(ex);
        }
    }

    @NotNull
    public MohistKitePluginDescription getPluginMohistKiteDescription(@NotNull JarFile jar)
            throws InvalidDescriptionException {
        Validate.notNull(jar, "File cannot be null");
        JarEntry entry = jar.getJarEntry("papershelled.plugin.json");
        if (entry == null) return new MohistKitePluginDescription();
        try (InputStream is = jar.getInputStream(entry)) {
            return new Gson().fromJson(new InputStreamReader(is), MohistKitePluginDescription.class);
        } catch (IOException | YAMLException ex) {
            throw new InvalidDescriptionException(ex);
        }
    }

    @NotNull
    public PluginDescriptionFile getPluginDescription(@NotNull JarFile jar) throws InvalidDescriptionException {
        Validate.notNull(jar, "File cannot be null");
        JarEntry entry = jar.getJarEntry("plugin.yml");
        if (entry == null) {
            throw new InvalidDescriptionException(new FileNotFoundException("Jar does not contain plugin.yml"));
        }
        try (InputStream is = jar.getInputStream(entry)) {
            return new PluginDescriptionFile(is);
        } catch (IOException | YAMLException ex) {
            throw new InvalidDescriptionException(ex);
        }
    }

    @Override
    @NotNull
    public PluginDescriptionFile getPluginDescription(@NotNull File file) throws InvalidDescriptionException {
        if (jarFiles.containsKey(file)) return getPluginDescription(jarFiles.get(file));
        else try (JarFile jar = new JarFile(file)) {
            return getPluginDescription(jar);
        } catch (Throwable e) {
            throw new InvalidDescriptionException(e);
        }
    }

    @SuppressWarnings("unused")
    @NotNull
    public MohistKitePluginDescription getPluginMohistKiteDescription(@NotNull File file)
            throws InvalidDescriptionException {
        if (jarFiles.containsKey(file)) return getPluginMohistKiteDescription(jarFiles.get(file));
        else try (JarFile jar = new JarFile(file)) {
            return getPluginMohistKiteDescription(jar);
        } catch (Throwable e) {
            throw new InvalidDescriptionException(e);
        }
    }

    @Override
    @NotNull
    public Pattern[] getPluginFileFilters() {
        return fileFilters.clone();
    }

    private void checkPaper() {
        if (checkedPaper) {
            return;
        }
        checkedPaper = true;
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<? extends EventExecutor> timedClz;
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            isPaper = true;
            timedClz = Class.forName("co.aikar.timings.TimedEventExecutor").asSubclass(EventExecutor.class);
            eeCreate = lookup.findStatic(EventExecutor.class, "create", MethodType.methodType(EventExecutor.class, Method.class, Class.class));
            newTimed = lookup.findConstructor(timedClz, MethodType.methodType(void.class, EventExecutor.class, Plugin.class, Method.class, Class.class));
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            pluginParentTimer = new CustomTimingsHandler("*** Plugin");
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot initialize EventExecutor implementation for Paper!", e);
        }
    }

    @Override
    @NotNull
    public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(@NotNull Listener listener, @NotNull final Plugin plugin) {
        Validate.notNull(plugin, "Plugin can not be null");
        Validate.notNull(listener, "Listener can not be null");

        Map<Class<? extends Event>, Set<RegisteredListener>> ret = new HashMap<>();
        Set<Method> methods;
        try {
            Method[] publicMethods = listener.getClass().getMethods();
            Method[] privateMethods = listener.getClass().getDeclaredMethods();
            methods = new HashSet<>(publicMethods.length + privateMethods.length, 1.0f);
            methods.addAll(Arrays.asList(publicMethods));
            methods.addAll(Arrays.asList(privateMethods));
        } catch (NoClassDefFoundError e) {
            plugin.getLogger().severe("Plugin " + plugin.getDescription().getFullName() + " has failed to register events for " + listener.getClass() + " because " + e.getMessage() + " does not exist.");
            return ret;
        }

        for (final Method method : methods) {
            final EventHandler eh = method.getAnnotation(EventHandler.class);
            if (eh == null) continue;
            // Do not register bridge or synthetic methods to avoid event duplication
            // Fixes SPIGOT-893
            if (method.isBridge() || method.isSynthetic()) {
                continue;
            }
            final Class<?> checkClass;
            if (method.getParameterTypes().length != 1 || !Event.class.isAssignableFrom(checkClass = method.getParameterTypes()[0])) {
                plugin.getLogger().severe(plugin.getDescription().getFullName() + " attempted to register an invalid EventHandler method signature \"" + method.toGenericString() + "\" in " + listener.getClass());
                continue;
            }
            final Class<? extends Event> eventClass = checkClass.asSubclass(Event.class);
            method.setAccessible(true);
            Set<RegisteredListener> eventSet = ret.computeIfAbsent(eventClass, k -> new HashSet<>());

            for (Class<?> clazz = eventClass; Event.class.isAssignableFrom(clazz); clazz = clazz.getSuperclass()) {
                // This loop checks for extending deprecated events
                if (clazz.getAnnotation(Deprecated.class) != null) {
                    Warning warning = clazz.getAnnotation(Warning.class);
                    plugin.getLogger().log(
                            Level.WARNING,
                            String.format(
                                    "\"%s\" has registered a listener for %s on method \"%s\", but the event is Deprecated. \"%s\"; please notify the authors %s.",
                                    plugin.getDescription().getFullName(),
                                    clazz.getName(),
                                    method.toGenericString(),
                                    (warning != null && warning.reason().length() != 0) ? warning.reason() : "Server performance will be affected",
                                    Arrays.toString(plugin.getDescription().getAuthors().toArray())));
                    break;
                }
            }

            checkPaper();
            try {
                EventExecutor executor = (EventExecutor) newTimed.invoke((EventExecutor) eeCreate.invoke(method, eventClass), plugin, method, eventClass);
                eventSet.add(new RegisteredListener(listener, executor, eh.priority(), plugin, eh.ignoreCancelled()));
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        return ret;
    }

    @Override
    public void enablePlugin(@NotNull final Plugin plugin) {
        Validate.isTrue(plugin instanceof MohistKitePlugin, "Plugin is not associated with this PluginLoader");
        if (plugin.isEnabled()) return;
        plugin.getLogger().info("Enabling " + plugin.getDescription().getFullName());
        MohistKitePlugin psPlugin = (MohistKitePlugin) plugin;
        try {
            psPlugin.setEnabled(true);
        } catch (Throwable ex) {
            MohistKiteAgent.LOGGER.log(Level.SEVERE, "Error occurred while enabling " +
                    plugin.getDescription().getFullName() + " (Is it up to date?)", ex);
        }
        Bukkit.getPluginManager().callEvent(new PluginEnableEvent(plugin));
    }

    @Override
    public void disablePlugin(@NotNull Plugin plugin) {
        Validate.isTrue(plugin instanceof MohistKitePlugin, "Plugin is not a MohistKitePlugin");
        if (!plugin.isEnabled()) return;
        String message = String.format("Disabling %s", plugin.getDescription().getFullName());
        plugin.getLogger().info(message);
        Bukkit.getPluginManager().callEvent(new PluginDisableEvent(plugin));
        try {
            ((MohistKitePlugin) plugin).setEnabled(false);
        } catch (Throwable ex) {
            MohistKiteAgent.LOGGER.log(Level.SEVERE, "Error occurred while disabling " +
                    plugin.getDescription().getFullName() + " (Is it up to date?)", ex);
        }
    }
}
