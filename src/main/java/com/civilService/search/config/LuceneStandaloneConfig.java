package com.civilService.search.config;

import com.civilService.search.entity.CivilServiceListRecord;
import com.civilService.search.entity.SyncMetadata;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.AnnotatedTypeSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import java.util.Collections;
import java.util.Map;

@Configuration
public class LuceneStandaloneConfig {

    private final Environment environment;

    public LuceneStandaloneConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public SearchMapping searchMapping() {
        var builder = SearchMapping.builder(AnnotatedTypeSource.fromClasses(
                        CivilServiceListRecord.class,
                        SyncMetadata.class
                ));

        // Bind all properties prefixed with "hibernate.search"
        Map<String, String> searchProperties = Binder.get(environment)
                .bind("hibernate.search", Bindable.mapOf(String.class, String.class))
                .orElse(Collections.emptyMap());

        for (Map.Entry<String, String> entry : searchProperties.entrySet()) {
            builder.property("hibernate.search." + entry.getKey(), entry.getValue());
        }

        // Fallback default directory root if not specified
        if (!searchProperties.containsKey("backend.directory.root") &&
                !searchProperties.containsKey("backend.directory.type")) {
            builder.property("hibernate.search.backend.directory.root", "./lucene-index");
        }

        return builder.build();
    }
}
