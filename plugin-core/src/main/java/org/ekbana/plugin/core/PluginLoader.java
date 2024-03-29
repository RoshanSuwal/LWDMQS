package org.ekbana.plugin.core;

import org.ekbana.minikafka.plugin.Plugin;
import org.ekbana.minikafka.plugin.loadbalancer.LoadBalancerFactory;
import org.ekbana.minikafka.plugin.policy.PolicyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

public class PluginLoader {

    private static final Logger logger= LoggerFactory.getLogger(PluginLoader.class);
    private final Map<String, PolicyFactory<?>> policyFactoryMap=new HashMap<>();
    private final Map<String,LoadBalancerFactory<?,?,?>> loadBalancerFactoryMap=new HashMap<>();

    private final File pluginsDir;
    private final AtomicBoolean loading=new AtomicBoolean();

    public PluginLoader(final File pluginsDir) {
        this.pluginsDir = pluginsDir;
    }

    public void loadPlugins(){
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            logger.error("Skipping Plugin Loading. Plugin dir not found: {}" , pluginsDir);
            return;
        }

        if (loading.compareAndSet(false, true)) {
            final File[] files = requireNonNull(pluginsDir.listFiles());
            for (File pluginDir : files) {
                if (pluginDir.isDirectory()) {
                    loadPlugin(pluginDir);
                }
            }
        }
    }

    private void loadPlugin(final File pluginDir) {
        logger.info("Loading plugin: {} " , pluginDir);
        final URLClassLoader pluginClassLoader = createPluginClassLoader(pluginDir);
        final ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(pluginClassLoader);
            for (Plugin plugin : ServiceLoader.load(Plugin.class, pluginClassLoader)) {
                installPlugin(plugin);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(currentClassLoader);
        }
    }

    public void loadBuiltInPlugins(List<Plugin> plugins){
        for (Plugin plugin:plugins){
            installPlugin(plugin);
        }
    }

    private void installPlugin(final Plugin plugin) {
        logger.info("Loading plugin : {} : {} ", plugin.pluginName(),plugin.pluginDescription());
        for (PolicyFactory<?> f : plugin.getPolicyFactories()) {
            logger.info("Installing policy : {} : {}",f.policyName(),f.policyType());
            policyFactoryMap.put(f.policyName(), f);
        }

        for (LoadBalancerFactory<?,?,?> loadBalancerFactory:plugin.getLoadBalancerFactories()){
            logger.info("Installing LoadBalancer : {}",loadBalancerFactory.loadBalancerName());
            loadBalancerFactoryMap.put(loadBalancerFactory.loadBalancerName(), loadBalancerFactory);
        }
    }

    private URLClassLoader createPluginClassLoader(File dir) {
        final URL[] urls = Arrays.stream(Optional.ofNullable(dir.listFiles()).orElse(new File[]{}))
                .sorted()
                .map(File::toURI)
                .map(this::toUrl)
                .toArray(URL[]::new);

        return new PluginClassLoader(urls, getClass().getClassLoader());
    }

    private URL toUrl(final URI uri) {
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public PolicyFactory<?> getPolicyFactory(String name) {
        return policyFactoryMap.get(name);
    }
    public List<PolicyFactory<?>> getPolicyFactories(){
        return new ArrayList<>(policyFactoryMap.values());
    }

    public LoadBalancerFactory<?,?,?> getLoadBalancerFactory(String name) {
        return loadBalancerFactoryMap.get(name);
    }
    public List<LoadBalancerFactory<?,?,?>> getLoadBalancerFactories(){ return new ArrayList<>(loadBalancerFactoryMap.values());
    }
}
