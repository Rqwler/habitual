package com.habittracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs against a real PostgreSQL database so Flyway migrations and PostgreSQL-only SQL are
 * exercised. Skipped automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HabitApiIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("habittracker_it")
          .withUsername("habit")
          .withPassword("habit-test");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.enabled", () -> true);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private Clock clock;

  @Test
  void migrationsAndFullHabitFlowWorkOnPostgres() throws Exception {
    String email = "it-" + UUID.randomUUID() + "@example.com";
    var registerResult =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "email", email,
                                "displayName", "Integration User",
                                "password", "password123"))))
            .andExpect(status().isCreated())
            .andReturn();
    String token =
        objectMapper
            .readTree(registerResult.getResponse().getContentAsString())
            .path("token")
            .asText();

    var habitResult =
        mockMvc
            .perform(
                post("/api/habits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "name", "Postgres walk",
                                "category", "Health",
                                "frequencyType", "DAILY"))))
            .andExpect(status().isCreated())
            .andReturn();
    long habitId =
        objectMapper.readTree(habitResult.getResponse().getContentAsString()).path("id").asLong();

    LocalDate today = LocalDate.now(clock);
    mockMvc
        .perform(
            post("/api/habits/{id}/check-ins", habitId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("date", today.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(true));

    mockMvc
        .perform(
            get("/api/stats/dashboard")
                .param("date", today.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.today.completed").value(1));

    var health = mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn();
    assertThat(health.getResponse().getContentAsString()).contains("\"status\":\"UP\"");
  }
}
