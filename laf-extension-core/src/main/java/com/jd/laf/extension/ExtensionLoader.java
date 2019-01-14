package com.jd.laf.extension;

import java.util.*;

/**
 * 扩展点加载器
 */
public interface ExtensionLoader {

    /**
     * 加载扩展点
     *
     * @param extensible 可扩展的接口
     * @return 扩展点列表
     */
    <T> Collection<Plugin<T>> load(Class<T> extensible);

    /**
     * 包装器
     */
    class Wrapper implements ExtensionLoader {
        protected Set<ExtensionLoader> loaders = new LinkedHashSet<ExtensionLoader>();

        public Wrapper(final ExtensionLoader... loaders) {
            if (loaders != null) {
                for (ExtensionLoader loader : loaders) {
                    add(loader);
                }
            }
        }

        public Wrapper(final Collection<ExtensionLoader> loaders) {
            if (loaders != null) {
                for (ExtensionLoader loader : loaders) {
                    add(loader);
                }
            }
        }

        protected void add(final ExtensionLoader loader) {
            if (loader == null) {
                return;
            } else if (loader instanceof Wrapper) {
                for (ExtensionLoader l : ((Wrapper) loader).loaders) {
                    add(l);
                }
            } else {
                loaders.add(loader);
            }
        }

        @Override
        public <T> Collection<Plugin<T>> load(final Class<T> extensible) {

            //多个插件加载器，避免加载相同的实例，做了去重
            List<Plugin<T>> result = new LinkedList<Plugin<T>>();

            if (loaders != null) {
                for (ExtensionLoader loader : loaders) {
                    Collection<Plugin<T>> plugins = loader.load(extensible);
                    if (plugins != null) {
                        result.addAll(plugins);
                    }
                }
            }

            return result;
        }
    }

}
