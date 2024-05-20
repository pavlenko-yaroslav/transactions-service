package ru.yaroslav_pavlenko.TransactionsRestApi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transactionsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Transactions Service API")
                        .version("1.0.0")
                        .description("""
                                Monthly spending limit control service.

                                The API is split into two surfaces:
                                * **Integration API** — ingestion of spending operations from the banking system;
                                * **Client API** — managing limits and reading transactions that exceeded them.
                                """)
                        .contact(new Contact().name("Transactions Service"))
                        .license(new License().name("MIT")))
                .tags(List.of(
                        new Tag().name("Integration API").description("Ingestion of transactions from banking services"),
                        new Tag().name("Client API").description("Client requests: limits and exceedances")));
    }
}
