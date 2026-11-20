package com.hospital.backend.controller;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MongoTemplate mongoTemplate;

    @Test
    public void checkHealth_ReturnsOkAndStatusUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    public void checkLiveness_ReturnsOkAndStatusUp() throws Exception {
        mockMvc.perform(get("/api/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    public void checkReadiness_WhenMongoHealthy_ReturnsOk() throws Exception {
        MongoDatabase mockDb = mock(MongoDatabase.class);
        when(mongoTemplate.getDb()).thenReturn(mockDb);
        when(mockDb.runCommand(any(Document.class))).thenReturn(new Document("ok", 1.0));

        mockMvc.perform(get("/api/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.database").value("UP"));
    }

    @Test
    public void checkReadiness_WhenMongoUnhealthy_ReturnsServiceUnavailable() throws Exception {
        MongoDatabase mockDb = mock(MongoDatabase.class);
        when(mongoTemplate.getDb()).thenReturn(mockDb);
        when(mockDb.runCommand(any(Document.class))).thenThrow(new RuntimeException("Connection refused"));

        mockMvc.perform(get("/api/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.status").value("DOWN"))
                .andExpect(jsonPath("$.data.database").value("DOWN"));
    }
}
