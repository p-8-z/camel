/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dsl.yaml;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.support.RouteBuilderLoaderSupport;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializationMode;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.dsl.yaml.deserializers.CustomResolver;
import org.apache.camel.dsl.yaml.deserializers.EndpointProducerDeserializersResolver;
import org.apache.camel.dsl.yaml.deserializers.ModelDeserializersResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.YamlUnicodeReader;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.parser.Parser;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;

public abstract class YamlRoutesBuilderLoaderSupport extends RouteBuilderLoaderSupport {
    public static final String DESERIALIZATION_MODE = "CamelYamlDslDeserializationMode";

    private LoadSettings settings;
    private YamlDeserializationContext deserializationContext;
    private YamlDeserializationMode deserializationMode;

    public YamlRoutesBuilderLoaderSupport(String extension) {
        super(extension);
    }

    public YamlDeserializationMode getDeserializationMode() {
        return deserializationMode;
    }

    public void setDeserializationMode(YamlDeserializationMode deserializationMode) {
        this.deserializationMode = deserializationMode;
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();

        this.settings = LoadSettings.builder().build();
        this.deserializationContext = new YamlDeserializationContext(settings);
        this.deserializationContext.setCamelContext(getCamelContext());
        this.deserializationContext.addResolvers(new CustomResolver());
        this.deserializationContext.addResolvers(new ModelDeserializersResolver());
        this.deserializationContext.addResolvers(new EndpointProducerDeserializersResolver());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (this.deserializationMode == null) {
            final Map<String, String> options = getCamelContext().getGlobalOptions();
            final String mode = options.getOrDefault(DESERIALIZATION_MODE, YamlDeserializationMode.CLASSIC.name());
            if (mode != null) {
                this.deserializationContext.setDeserializationMode(
                        YamlDeserializationMode.valueOf(mode.toUpperCase(Locale.US)));
            }
        } else {
            this.deserializationContext.setDeserializationMode(deserializationMode);
        }

        ServiceHelper.startService(this.deserializationContext);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopService(this.deserializationContext);

        this.deserializationContext = null;
        this.settings = null;
    }

    @Override
    public RouteBuilder doLoadRouteBuilder(Resource resource) throws Exception {
        ObjectHelper.notNull(deserializationContext, "constructor");
        ObjectHelper.notNull(settings, "settings");

        if (!resource.exists()) {
            throw new FileNotFoundException("Resource not found: " + resource.getLocation());
        }

        try (InputStream is = resource.getInputStream()) {
            final StreamReader reader = new StreamReader(new YamlUnicodeReader(is), settings);
            final Parser parser = new ParserImpl(reader, settings);
            final Composer composer = new Composer(parser, settings);

            return composer.getSingleNode()
                    .map(this::builder)
                    .orElseThrow(() -> new YamlDeserializationException("Unable to deserialize resource"));
        }
    }

    protected LoadSettings getSettings() {
        return this.settings;
    }

    protected YamlDeserializationContext getDeserializationContext() {
        return this.deserializationContext;
    }

    protected abstract RouteBuilder builder(Node node);
}
