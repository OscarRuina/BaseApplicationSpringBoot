package com.organization.application.configurations.swagger;

import com.organization.application.messages.ConstantsMessages;
import com.organization.application.messages.SwaggerMessages;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.http.HttpHeaders;

@OpenAPIDefinition(
        info = @Info(
                title = SwaggerMessages.TITTLE,
                description = SwaggerMessages.DESCRIPTION,
                termsOfService = "url with terms and services",
                version = "1.0.0",
                contact = @Contact(
                        name = "contact name",
                        url = "contact url",
                        email = "contact email"
                ),
                license = @License(
                        name = "licence name",
                        url = "licence url"
                )
        ),
        servers = {
                @Server(
                        description = SwaggerMessages.SERVER_DESCRIPTION,
                        url = SwaggerMessages.SERVER_URL
                )
        },
        security = @SecurityRequirement(
                name = ConstantsMessages.SWAGGER_SECURITY_SCHEME_NAME
        )
)
@SecurityScheme(
        name = ConstantsMessages.SWAGGER_SECURITY_SCHEME_NAME,
        description = SwaggerMessages.SECURITY_DESCRIPTION,
        type = SecuritySchemeType.HTTP,
        paramName = HttpHeaders.AUTHORIZATION,
        in = SecuritySchemeIn.HEADER,
        scheme = SwaggerMessages.SECURITY_SCHEME_NAME,
        bearerFormat = SwaggerMessages.SECURITY_BEARER_FORMAT
)
public class SwaggerConfig { }