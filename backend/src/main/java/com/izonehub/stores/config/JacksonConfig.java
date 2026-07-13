package com.izonehub.stores.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        // Force lazy loading to be disabled (proxies serialize as null)
        module.configure(Hibernate6Module.Feature.FORCE_LAZY_LOADING, false);
        module.configure(Hibernate6Module.Feature.REPLACE_PERSISTENT_COLLECTIONS, true);
        return module;
    }
}
