package com.tradingreporting.api.config;

import com.tradingreporting.api.json.DecimalAsStringModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer decimalAsStringCustomizer() {
        return builder -> builder.modulesToInstall(modules -> modules.add(new DecimalAsStringModule()));
    }
}
