package com.habittracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HabitApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private Clock clock;

  @Test
  void requiresAuthenticationForHabitData() throws Exception {
    mockMvc.perform(get("/api/habits")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/stats/dashboard")).andExpect(status().isUnauthorized());
  }

  @Test
  void registersLogsInAndReadsProfile() throws Exception {
    String email = uniqueEmail();
    String token = register(email);
    assertThat(token).isNotBlank();

    mockMvc
        .perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.displayName").value("Test User"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email.toUpperCase(), "password", "password123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString())
        .andExpect(jsonPath("$.user.email").value(email));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", "wrong-password"))))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationBody(email)))
        .andExpect(status().isConflict());
  }

  @Test
  void createsHabitChecksInAndReadsStats() throws Exception {
    String token = register(uniqueEmail());
    LocalDate today = LocalDate.now(clock);
    long habitId = createHabit(token, "Read ten pages", "DAILY", null, null);

    mockMvc
        .perform(
            get("/api/habits")
                .param("date", today.toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(habitId))
        .andExpect(jsonPath("$[0].completedToday").value(false))
        .andExpect(jsonPath("$[0].currentStreak").value(0));

    mockMvc
        .perform(
            post("/api/habits/{id}/check-ins", habitId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("date", today.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(true))
        .andExpect(jsonPath("$.currentStreak").value(1));

    // Checking in twice is idempotent.
    mockMvc
        .perform(
            post("/api/habits/{id}/check-ins", habitId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("date", today.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Already checked in for this day"));

    mockMvc
        .perform(
            get("/api/habits/{id}/calendar", habitId)
                .param("year", String.valueOf(today.getYear()))
                .param("month", String.valueOf(today.getMonthValue()))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completedCount").value(1))
        .andExpect(
            jsonPath("$.targetCount").value(today.lengthOfMonth() - today.getDayOfMonth() + 1));

    mockMvc
        .perform(
            get("/api/stats/dashboard")
                .param("date", today.toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.today.completed").value(1))
        .andExpect(jsonPath("$.today.percentage").value(100))
        .andExpect(jsonPath("$.bestCurrentStreak").value(1))
        .andExpect(jsonPath("$.bestStreakUnit").value("days"));

    mockMvc
        .perform(
            delete("/api/habits/{id}/check-ins", habitId)
                .param("date", today.toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(false));

    mockMvc
        .perform(
            get("/api/stats/dashboard")
                .param("date", today.toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.today.completed").value(0));

    mockMvc
        .perform(
            delete("/api/habits/{id}", habitId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isNoContent());
  }

  @Test
  void reordersHabitsAndKeepsUsersIsolated() throws Exception {
    String firstToken = register(uniqueEmail());
    long firstHabit = createHabit(firstToken, "Stretch", "DAILY", null, null);
    long secondHabit = createHabit(firstToken, "Journal", "DAILY", null, null);

    mockMvc
        .perform(
            put("/api/habits/order")
                .header(HttpHeaders.AUTHORIZATION, bearer(firstToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("habitIds", List.of(secondHabit, firstHabit)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(secondHabit))
        .andExpect(jsonPath("$[1].id").value(firstHabit));

    String secondToken = register(uniqueEmail());
    mockMvc
        .perform(
            get("/api/habits/{id}", firstHabit)
                .header(HttpHeaders.AUTHORIZATION, bearer(secondToken)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            post("/api/habits/{id}/check-ins", firstHabit)
                .header(HttpHeaders.AUTHORIZATION, bearer(secondToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("date", LocalDate.now(clock).toString()))))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            post("/api/habits/{id}/archive", firstHabit)
                .header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true));

    mockMvc
        .perform(get("/api/habits").header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(secondHabit));
  }

  @Test
  void rejectsInvalidFrequencyConfiguration() throws Exception {
    String token = register(uniqueEmail());

    mockMvc
        .perform(
            post("/api/habits")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "name",
                            "Invalid specific days",
                            "frequencyType",
                            "SPECIFIC_DAYS",
                            "weekdays",
                            List.of()))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/habits")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Invalid weekly", "frequencyType", "WEEKLY"))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/habits")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "  ", "frequencyType", "DAILY"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.name").exists());
  }

  private long createHabit(
      String token, String name, String frequencyType, Integer timesPerWeek, List<Integer> weekdays)
      throws Exception {
    Map<String, Object> payload = new java.util.HashMap<>();
    payload.put("name", name);
    payload.put("description", "Created by the API test");
    payload.put("category", "Testing");
    payload.put("frequencyType", frequencyType);
    if (timesPerWeek != null) {
      payload.put("timesPerWeek", timesPerWeek);
    }
    if (weekdays != null) {
      payload.put("weekdays", weekdays);
    }

    MvcResult result =
        mockMvc
            .perform(
                post("/api/habits")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(payload)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.frequencyType").value(frequencyType))
            .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.path("id").asLong();
  }

  private String register(String email) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registrationBody(email)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isString())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
  }

  private String registrationBody(String email) throws Exception {
    return json(
        Map.of(
            "email", email,
            "displayName", "Test User",
            "password", "password123"));
  }

  private String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@example.com";
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
