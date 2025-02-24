/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microsphere.spring.config.context.annotation;

import io.microsphere.io.StandardFileWatchService;
import io.microsphere.io.event.FileChangedEvent;
import io.microsphere.io.event.FileChangedListener;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.springframework.core.io.support.ResourcePatternUtils.getResourcePatternResolver;

/**
 * The {@link PropertySourceExtensionLoader} Class for {@link ResourcePropertySource}
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @see ResourcePropertySource
 * @see PropertySourceExtensionAttributes
 * @since 1.0.0
 */
public class ResourcePropertySourceLoader extends PropertySourceExtensionLoader<ResourcePropertySource, PropertySourceExtensionAttributes<ResourcePropertySource>>
        implements ResourceLoaderAware, BeanClassLoaderAware, DisposableBean {

    private ResourcePatternResolver resourcePatternResolver;

    private ClassLoader classLoader;

    private StandardFileWatchService fileWatchService;

    private PropertySourceExtensionAttributes<ResourcePropertySource> extensionAttributes;

    @Override
    protected Resource[] getResources(PropertySourceExtensionAttributes<ResourcePropertySource> extensionAttributes, String propertySourceName, String resourceValue)
            throws Throwable {
        return resourcePatternResolver.getResources(resourceValue);
    }

    @Override
    protected void configureAutoRefreshedResources(PropertySourceExtensionAttributes<ResourcePropertySource> extensionAttributes,
                                                   String propertySourceName, String[] resourceValues,
                                                   List<Resource> resourcesList) throws Throwable {
        // resource location : classpath:/META-INF/a.properties ->
        // actual path(origin) : /users/admin/some-project/META-INF/a.properties
        // name = a

        // actual path(modified) : /users/admin/some-project/META-INF/a.properties
        // -> name = b
        // based on FileSystem
        // interesting kind = MODIFIED

        this.fileWatchService = new StandardFileWatchService();
        this.extensionAttributes = extensionAttributes;

        Listener listener = new Listener();
        for (Resource resource : resourcesList) {
            if (isFileSystemBasedResource(resource)) {
                File resourceFile = resource.getFile();
                fileWatchService.watch(resourceFile, listener, FileChangedEvent.Kind.MODIFIED);
            }
        }

        fileWatchService.start();

    }

    class Listener implements FileChangedListener {

        @Override
        public void onFileModified(FileChangedEvent event) {

            // a.properties > b.properties
            // name = a   >   name = b

            // PropertySource file has been modified
            ConfigurableEnvironment environment = getEnvironment();
            MutablePropertySources propertySources = environment.getPropertySources();
            File resourceFile = event.getFile();
            String encoding = extensionAttributes.getEncoding();
            Resource resource = new FileSystemResource(resourceFile);
            EncodedResource encodedResource = new EncodedResource(resource, encoding);
            PropertySourceFactory factory = createPropertySourceFactory(extensionAttributes);
            String propertySourceName = resourceFile.getAbsolutePath();
            try {
                PropertySource propertySource = factory.createPropertySource(propertySourceName, encodedResource);
                propertySources.addFirst(propertySource);
            } catch (IOException e) {
                logger.error("TODO message", e);
            }
        }
    }

    private boolean isFileSystemBasedResource(Resource resource) {
        return resource instanceof FileSystemResource ||
                resource instanceof FileUrlResource;
    }


    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourcePatternResolver = getResourcePatternResolver(resourceLoader);
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void destroy() throws Exception {
        if (fileWatchService != null) {
            fileWatchService.stop();
        }
    }
}
