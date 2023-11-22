package com.organization.application.configurations.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String GROUP = "organization";

    private static final String PACKAGE = "com.organization.application";

    private static final String TITTLE = "Your Organization";

    private static final String DESCRIPTION = "Your Description";

    @Bean
    public OpenAPI api(){
        return new OpenAPI()
                .info(new Info().title(TITTLE)
                        .description(DESCRIPTION)
                        .version("v0.0.1"));
    }

}
