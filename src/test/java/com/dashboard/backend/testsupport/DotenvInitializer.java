package com.dashboard.backend.testsupport;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.stream.Collectors;

public class DotenvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Dotenv dotenv = Dotenv.configure().filename(".env").load();

        Map<String, Object> properties = dotenv.entries().stream()
                .collect(Collectors.toMap(
                        DotenvEntry::getKey,
                        DotenvEntry::getValue
                ));

        MapPropertySource propertySource = new MapPropertySource("dotenv", properties);
        context.getEnvironment().getPropertySources().addFirst(propertySource);
    }
}