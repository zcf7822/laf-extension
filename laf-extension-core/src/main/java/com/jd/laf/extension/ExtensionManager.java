package com.jd.laf.extension;

import com.jd.laf.extension.ExtensionMeta.AscendingComparator;
import com.jd.laf.extension.Instantiation.ClazzInstance;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Collections.sort;

/**
 * 扩展点管理
 */
public class ExtensionManager {
    public static ExtensionManager INSTANCE = new ExtensionManager();

    // 扩展点名称
    protected ConcurrentMap<String, ExtensionPoint> names = new ConcurrentHashMap<String, ExtensionPoint>();
    // 扩展点
    protected ConcurrentMap<Class, ExtensionPoint> extensions = new ConcurrentHashMap<Class, ExtensionPoint>();
    // 默认加载器
    protected ExtensionLoader loader = SpiLoader.INSTANCE;

    public ExtensionManager() {
    }

    public ExtensionManager(ExtensionScanner scanner, ExtensionLoader loader) {
        this(loader);
        loadExtension(scanner);
    }

    public ExtensionManager(ExtensionLoader loader) {
        this.loader = loader == null ? SpiLoader.INSTANCE : loader;
    }

    public ExtensionManager(Collection<Class<?>> extensibles) {
        loadExtension(extensibles, loader);
    }

    public ExtensionManager(Collection<Class<?>> extensibles, ExtensionLoader loader) {
        this(loader);
        loadExtension(extensibles, loader);
    }

    protected <T, M> ExtensionPoint<T, M> add(final ExtensionPoint<T, M> ExtensionPoint) {
        if (ExtensionPoint != null) {
            Name<T, String> name = ExtensionPoint.getName();
            //防止并发
            ExtensionPoint<T, M> exists = extensions.putIfAbsent(name.getClazz(), ExtensionPoint);
            if (exists == null) {
                if (name.getName() != null) {
                    names.put(name.getName(), ExtensionPoint);
                }
                return ExtensionPoint;
            }
            return exists;
        }
        return null;
    }

    public <T, M> ExtensionPoint<T, M> getOrLoadExtensionPoint(final Class<T> extensible) {
        return (ExtensionPoint<T, M>) getOrLoadExtensionPoint(extensible, loader, null, null);
    }

    public <T, M> ExtensionPoint<T, M> getOrLoadExtensionPoint(final Class<T> extensible, final ExtensionLoader loader) {
        return (ExtensionPoint<T, M>) getOrLoadExtensionPoint(extensible, loader, null, null);
    }

    public <T, M> ExtensionPoint<T, M> getOrLoadExtensionPoint(final Class<T> extensible, final Comparator<ExtensionMeta<T, M>> comparator) {
        return getOrLoadExtensionPoint(extensible, loader, comparator, null);
    }

    public <T, M> ExtensionPoint<T, M> getOrLoadExtensionPoint(final Class<T> extensible, final ExtensionLoader loader, final Comparator<ExtensionMeta<T, M>> comparator) {
        return getOrLoadExtensionPoint(extensible, loader, comparator, null);
    }

    public <T, M> ExtensionPoint getOrLoadExtensionPoint(final Class<T> extensible,
                                                         final ExtensionLoader loader,
                                                         final Comparator<ExtensionMeta<T, M>> comparator,
                                                         final Classify<T, M> classify) {
        if (extensible == null) {
            return null;
        }
        //判断是否重复添加
        ExtensionPoint<T, M> result = getExtensionPoint(extensible);
        if (result == null) {
            //获取扩展点注解
            Extensible annotation = extensible.getAnnotation(Extensible.class);
            //构造扩展点名称
            Name extensibleName = new Name(extensible, annotation != null && annotation.value() != null
                    && !annotation.value().isEmpty() ? annotation.value() : extensible.getName());
            //加载插件
            Collection<Plugin<T>> plugins = loader == null ? this.loader.load(extensible) : loader.load(extensible);
            List<ExtensionMeta<T, M>> metas = new LinkedList<ExtensionMeta<T, M>>();
            int count = 0;
            int last = Ordered.ORDER;
            boolean sorted = false;
            for (Plugin<T> plugin : plugins) {
                Class<?> pluginClass = plugin.name.getClazz();
                Extension extension = pluginClass.getAnnotation(Extension.class);
                ExtensionMeta<T, M> meta = new ExtensionMeta<T, M>();
                meta.setExtensible(extensibleName);
                meta.setName(plugin.name);
                meta.setInstantiation(plugin.instantiation == null ? ClazzInstance.INSTANCE : plugin.instantiation);
                meta.setTarget(plugin.target);
                meta.setSingleton(plugin.isSingleton() != null ? plugin.isSingleton() :
                        (Prototype.class.isAssignableFrom(pluginClass) ? false :
                                (extension == null ? true : extension.singleton())));
                //获取插件，不存在则创建
                T target = meta.getTarget();
                meta.setExtension(new Name(pluginClass, classify != null ? classify.type(target) :
                        (Type.class.isAssignableFrom(pluginClass) ? ((Type) target).type() :
                                (extension != null && extension.value() != null && !extension.value().isEmpty() ? extension.value() :
                                        pluginClass.getName()))));
                meta.setOrder(Ordered.class.isAssignableFrom(pluginClass) ? ((Ordered) target).order() :
                        (extension == null ? Ordered.ORDER : extension.order()));
                metas.add(meta);
                if (count++ > 0 && meta.getOrder() != last) {
                    //顺序不一样，需要排序
                    sorted = true;
                }

            }
            //排序
            if (sorted) {
                sort(metas, comparator == null ? AscendingComparator.INSTANCE : comparator);
            }

            result = add(new ExtensionSpi(extensibleName, metas));
        }
        return result;
    }

    public void loadExtension(final Collection<Class<?>> extensibles) {
        loadExtension(extensibles, loader);
    }

    public void loadExtension(final Collection<Class<?>> extensibles, final ExtensionLoader loader) {
        if (extensibles != null) {
            ExtensionLoader extensionLoader = loader == null ? this.loader : loader;
            for (Class extensible : extensibles) {
                getOrLoadExtensionPoint(extensible, extensionLoader, null, null);
            }
        }
    }

    public void loadExtension(final ExtensionScanner scanner) {
        loadExtension(scanner, loader);
    }

    public void loadExtension(final ExtensionScanner scanner, final ExtensionLoader loader) {
        if (scanner != null) {
            loadExtension(scanner.scan(), loader);
        }
    }

    /**
     * 获取扩展实现
     *
     * @param type 类型
     * @param name 扩展名称
     * @param <T>
     * @return
     */
    public <T, M> T getExtension(final String type, final M name) {
        ExtensionPoint<T, M> spi = names.get(type);
        return spi == null ? null : (T) spi.get(name);
    }

    /**
     * 获取扩展实现
     *
     * @param extensible 扩展点类
     * @param name       扩展名称
     * @param <T>        扩展实现
     * @return
     */
    public <T> T getExtension(final Class<T> extensible, final Object name) {
        ExtensionPoint spi = getExtensionPoint(extensible);
        return spi == null ? null : (T) spi.get(name);
    }

    /**
     * 获取或加载扩展实现
     *
     * @param extensible 扩展点类
     * @param name       扩展名称
     * @param <T>        扩展实现
     * @return
     */
    public <T, M> T getOrLoadExtension(final Class<T> extensible, final M name) {
        ExtensionPoint<T, M> spi = getOrLoadExtensionPoint(extensible, loader, null);
        return spi == null ? null : spi.get(name);
    }

    /**
     * 获取或加载扩展实现，并返回第一个实现
     *
     * @param extensible 扩展点类
     * @param <T>        扩展实现
     * @return
     */
    public <T, M> T getOrLoadExtension(final Class<T> extensible) {
        ExtensionPoint<T, M> spi = getOrLoadExtensionPoint(extensible, loader, null);
        return spi == null ? null : spi.get();
    }

    /**
     * 获取注解
     *
     * @param extensible
     * @param <T>
     * @return
     */
    protected <T> String getExtensible(final Class<T> extensible) {
        if (extensible == null) {
            return null;
        }
        String type = null;
        Extensible annotation = extensible.getAnnotation(Extensible.class);
        if (annotation != null) {
            type = annotation.value();
        }
        if (type == null || type.isEmpty()) {
            type = extensible.getName();
        }
        return type;
    }

    /**
     * 获取扩展实现
     *
     * @param extensible 扩展点类型
     * @param <T>
     * @return
     */
    public <T, M> Iterable<T> getExtensions(final Class<T> extensible) {
        ExtensionPoint<T, M> spi = getExtensionPoint(extensible);
        return spi == null ? null : spi.extensions();
    }

    /**
     * 获取扩展实现
     *
     * @param extensible 扩展点类型
     * @param <T>
     * @return
     */
    public <T> Iterable<T> getOrLoadExtensions(final Class<T> extensible) {
        ExtensionPoint<T, ?> spi = getOrLoadExtensionPoint(extensible);
        return spi == null ? null : spi.extensions();
    }

    /**
     * 获取扩展点实现
     *
     * @param type 类型
     * @param <T>
     * @return
     */
    public <T, M> Iterable<T> getExtensions(final String type) {
        ExtensionPoint<T, M> spi = names.get(type);
        return spi == null ? null : spi.extensions();
    }

    /**
     * 获取插件接口
     *
     * @param extensible
     * @return
     */
    public <T, M> ExtensionPoint<T, M> getExtensionPoint(final Class<T> extensible) {
        return extensions.get(extensible);
    }

    /**
     * 获取扩展实现
     *
     * @param type 类型
     * @param name 扩展名称
     * @param <T>
     * @param <M>
     * @return
     */
    public static <T, M> T get(final String type, final M name) {
        return INSTANCE.getExtension(type, name);
    }

    /**
     * 获取扩展实现
     *
     * @param extensible 扩展点类
     * @param name       扩展名称
     * @param <T>
     * @param <M>
     * @return
     */
    public static <T, M> T get(final Class<T> extensible, final M name) {
        return INSTANCE.getExtension(extensible, name);
    }

    /**
     * 获取或加载扩展实现
     *
     * @param extensible 扩展点类
     * @param name       扩展名称
     * @param <T>
     * @param <M>
     * @return
     */
    public static <T, M> T getOrLoad(final Class<T> extensible, final M name) {
        return INSTANCE.getOrLoadExtension(extensible, name);
    }

    /**
     * 获取或加载扩展实现
     *
     * @param extensible 扩展点类
     * @param <T>        扩展实现
     * @return
     */
    public static <T> T getOrLoad(final Class<T> extensible) {
        return INSTANCE.getOrLoadExtension(extensible);
    }

    /**
     * 获取扩展实现
     *
     * @param extensible 扩展点类型
     * @param <T>
     * @return
     */
    public static <T> Iterable<T> get(final Class<T> extensible) {
        return INSTANCE.getExtensions(extensible);
    }

    /**
     * 获取扩展实现
     *
     * @param extensible 扩展点类型
     * @param <T>
     * @return
     */
    public static <T> Iterable<T> getOrLoadAll(final Class<T> extensible) {
        return INSTANCE.getOrLoadExtensions(extensible);
    }

    /**
     * 获取扩展点实现
     *
     * @param type 类型
     * @param <T>
     * @return
     */
    public static <T> Iterable<T> get(final String type) {
        return INSTANCE.getExtensions(type);
    }

    /**
     * 获取插件接口
     *
     * @param extensible
     * @return
     */
    public static <T, M> ExtensionPoint<T, M> getSpi(final Class<T> extensible) {
        return INSTANCE.getExtensionPoint(extensible);
    }

    public static <T, M> ExtensionPoint<T, M> getOrLoadSpi(final Class<T> extensible) {
        return INSTANCE.getOrLoadExtensionPoint(extensible);
    }

    public static <T, M> ExtensionPoint<T, M> getOrLoadSpi(final Class<T> extensible, final ExtensionLoader loader) {
        return INSTANCE.getOrLoadExtensionPoint(extensible, loader);
    }

    public static <T, M> ExtensionPoint<T, M> getOrLoadSpi(final Class<T> extensible,
                                                           final Comparator<ExtensionMeta<T, M>> comparator) {
        return INSTANCE.getOrLoadExtensionPoint(extensible, comparator);
    }

    public static <T, M> ExtensionPoint<T, M> getOrLoadSpi(final Class<T> extensible,
                                                           final ExtensionLoader loader,
                                                           final Comparator<ExtensionMeta<T, M>> comparator) {
        return INSTANCE.getOrLoadExtensionPoint(extensible, loader, comparator);
    }

    public static <T, M> ExtensionPoint<T, M> getOrLoadSpi(final Class<T> extensible,
                                                           final ExtensionLoader loader,
                                                           final Comparator<ExtensionMeta<T, M>> comparator,
                                                           final Classify<T, M> classify) {
        return INSTANCE.getOrLoadExtensionPoint(extensible, loader, comparator, classify);
    }

    public static void load(final Collection<Class<?>> extensibles) {
        INSTANCE.loadExtension(extensibles);
    }

    public static void load(final Collection<Class<?>> extensibles, final ExtensionLoader loader) {
        INSTANCE.loadExtension(extensibles, loader);
    }

    public static void load(final ExtensionScanner scanner) {
        INSTANCE.loadExtension(scanner);
    }

    /**
     * 初始化扫描插件并加载
     *
     * @param scanner
     * @param loader
     */
    public static void load(final ExtensionScanner scanner, final ExtensionLoader loader) {
        INSTANCE.loadExtension(scanner, loader);
    }

    /**
     * 注册插件加载器
     *
     * @param loader
     * @see ExtensionManager#register(com.jd.laf.extension.ExtensionLoader)
     */
    @Deprecated
    public static void wrap(final ExtensionLoader loader) {
        register(loader);
    }

    /**
     * 注册插件加载器
     *
     * @param loader
     */
    public static void register(final ExtensionLoader loader) {
        if (loader == null) {
            return;
        }
        INSTANCE.loader = new ExtensionLoader.Wrapper(INSTANCE.loader, loader);
    }

    /**
     * 注销插件加载器
     *
     * @param loader
     */
    public static void deregister(final ExtensionLoader loader) {
        if (loader == null) {
            return;
        }
        Set<ExtensionLoader> excludes = new HashSet<ExtensionLoader>();
        excludes.add(loader);
        INSTANCE.loader = new ExtensionLoader.Wrapper(excludes, INSTANCE.loader);
    }

}
