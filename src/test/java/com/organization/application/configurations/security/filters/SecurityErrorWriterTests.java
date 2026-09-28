package com.organization.application.configurations.security.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityErrorWriterTests {

    private static final int STATUS = HttpStatus.GONE.value();

    private static final String MESSAGE = "curated message";

    private final SecurityErrorWriter writer = new SecurityErrorWriter(new ObjectMapper());

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void writeSampleError() throws Exception {
        writer.writeError(response, STATUS, MESSAGE);
    }

    @Test
    @DisplayName("Writes the given status with a JSON content type")
    void writesStatusAndContentType() {
        assertEquals(STATUS, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType());
    }

    @Test
    @DisplayName("Writes only the curated message, without a data field")
    void writesCuratedMessageOnly() throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsString());

        assertEquals(MESSAGE, body.get("message").asText());
        assertFalse(body.has("data"));
    }
}