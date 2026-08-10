package com.themistra.auth.apikey;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** API key config (L7): the public prefix every generated key starts with. */
@ConfigurationProperties(prefix = "themistra.auth.api-key")
@Validated
public record ApiKeyProperties(

        @NotBlank String prefix
) {
}
