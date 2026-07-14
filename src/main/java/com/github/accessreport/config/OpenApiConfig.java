package com.github.accessreport.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata shown by Swagger UI. */
@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI accessReportOpenApi() {
        return new OpenAPI().info(new Info().title("GitHub Organization Access Report API").version("1.0.0")
                .description("Aggregated GitHub repository collaborator access for one configured organization."));
    }
}
