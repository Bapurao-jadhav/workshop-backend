package com.workshop;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end API tests against an in-memory H2 database with the real Flyway migration. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    private static final String PASSWORD = "Passw0rdOk";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    // ------------------------------------------------------------------ auth

    @Test
    void registerLoginAndMe() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Asha", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Asha", "email", email, "password", PASSWORD))))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "wrong-pass-1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        String token = login(email, PASSWORD);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationValidationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void adminEndpointsRejectAnonymousAndNormalUsers() throws Exception {
        String userToken = registerUser();

        mvc.perform(get("/api/admin/workshops")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/workshops").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/contacts").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ workshops

    @Test
    void adminWorkshopLifecycleAndPublicVisibility() throws Exception {
        String admin = login("admin@test.com", "AdminPass123");
        long id = createWorkshop(admin, 10, "https://meet.example.com/secret-room");

        // Draft is invisible to the public
        mvc.perform(get("/api/workshops/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/workshops").param("size", "100"))
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").isEmpty());

        // Admin sees it with the meeting link
        mvc.perform(get("/api/admin/workshops/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.meetingLink").value("https://meet.example.com/secret-room"));

        publish(admin, id);

        // Public sees it, without the meeting link
        mvc.perform(get("/api/workshops/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.online").value(true))
                .andExpect(jsonPath("$.meetingLink").value(nullValue()))
                .andExpect(jsonPath("$.availableSeats").value(10))
                .andExpect(jsonPath("$.registrationOpen").value(true));

        mvc.perform(get("/api/workshops").param("q", "bootcamp").param("category", "web development")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]", hasSize(1)));

        // Update
        Map<String, Object> body = workshopBody(25, null);
        body.put("title", "Renamed Bootcamp");
        mvc.perform(put("/api/admin/workshops/" + id).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed Bootcamp"))
                .andExpect(jsonPath("$.capacity").value(25));

        // Unpublish hides it again
        patchStatus(admin, id, "UNPUBLISHED").andExpect(status().isOk());
        mvc.perform(get("/api/workshops/" + id)).andExpect(status().isNotFound());

        // Cancel is terminal
        patchStatus(admin, id, "CANCELLED").andExpect(status().isOk());
        patchStatus(admin, id, "PUBLISHED").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void workshopValidationRules() throws Exception {
        String admin = login("admin@test.com", "AdminPass123");

        Map<String, Object> badTimes = workshopBody(10, null);
        badTimes.put("startTime", "16:00");
        badTimes.put("endTime", "10:00");
        mvc.perform(post("/api/admin/workshops").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(badTimes)))
                .andExpect(status().isBadRequest());

        Map<String, Object> noLocation = workshopBody(10, null);
        noLocation.remove("venue");
        mvc.perform(post("/api/admin/workshops").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(noLocation)))
                .andExpect(status().isBadRequest());

        Map<String, Object> zeroCapacity = workshopBody(0, null);
        mvc.perform(post("/api/admin/workshops").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(zeroCapacity)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("capacity"));
    }

    // ------------------------------------------------------------------ registrations

    @Test
    void registrationFlowIncludingDuplicateCapacityAndCancellation() throws Exception {
        String admin = login("admin@test.com", "AdminPass123");
        long workshopId = createWorkshop(admin, 1, "https://meet.example.com/room");
        publish(admin, workshopId);

        String alice = registerUser();
        String bob = registerUser();

        // Anonymous cannot register
        mvc.perform(post("/api/workshops/" + workshopId + "/registrations")).andExpect(status().isUnauthorized());

        // Alice registers
        MvcResult created = mvc.perform(post("/api/workshops/" + workshopId + "/registrations")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.workshop.meetingLink").value("https://meet.example.com/room"))
                .andReturn();
        long registrationId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Duplicate -> 409, workshop is now full for Bob -> 409
        mvc.perform(post("/api/workshops/" + workshopId + "/registrations")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already registered")));
        mvc.perform(post("/api/workshops/" + workshopId + "/registrations")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("full")));

        // Ownership: Bob cannot see or cancel Alice's registration
        mvc.perform(get("/api/registrations/me/" + registrationId).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/registrations/me/" + registrationId).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());

        // Alice sees it
        mvc.perform(get("/api/registrations/me").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(registrationId));
        mvc.perform(get("/api/registrations/me/" + registrationId).header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellable").value(true));

        // Admin sees the participant and the participant count
        mvc.perform(get("/api/admin/workshops/" + workshopId + "/participants")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].participant.email").isNotEmpty())
                .andExpect(jsonPath("$.content[0].participant.passwordHash").doesNotExist());
        mvc.perform(get("/api/admin/workshops/" + workshopId).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.availableSeats").value(0))
                .andExpect(jsonPath("$.registrationOpen").value(false));
        mvc.perform(get("/api/admin/registrations").param("workshopId", String.valueOf(workshopId))
                        .param("status", "CONFIRMED").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Alice cancels; seat is freed and Bob can now register
        mvc.perform(delete("/api/registrations/me/" + registrationId).header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(delete("/api/registrations/me/" + registrationId).header("Authorization", "Bearer " + alice))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/workshops/" + workshopId + "/registrations")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isCreated());

        // Cancelling the workshop cancels Bob's registration too and blocks new ones
        patchStatus(admin, workshopId, "CANCELLED").andExpect(status().isOk());
        mvc.perform(get("/api/registrations/me").header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));
        mvc.perform(post("/api/workshops/" + workshopId + "/registrations")
                        .header("Authorization", "Bearer " + registerUser()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cannotRegisterForUnpublishedOrUnknownWorkshop() throws Exception {
        String admin = login("admin@test.com", "AdminPass123");
        long draftId = createWorkshop(admin, 5, null);
        String user = registerUser();

        mvc.perform(post("/api/workshops/" + draftId + "/registrations").header("Authorization", "Bearer " + user))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/workshops/999999/registrations").header("Authorization", "Bearer " + user))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ contact + docs

    @Test
    void contactFormAndAdminManagement() throws Exception {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("name", "Sneha");
        form.put("email", "sneha@example.com");
        form.put("subject", "Group booking");
        form.put("message", "We would like to book a workshop for 100 students.");

        MvcResult res = mvc.perform(post("/api/contact").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(form)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn();
        long id = json.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        form.remove("subject");
        mvc.perform(post("/api/contact").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(form)))
                .andExpect(status().isBadRequest());

        String admin = login("admin@test.com", "AdminPass123");
        mvc.perform(get("/api/admin/contacts").param("status", "NEW").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]", hasSize(1)));
        mvc.perform(get("/api/admin/contacts/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Group booking"));
        mvc.perform(patch("/api/admin/contacts/" + id + "/status").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(get("/api/admin/contacts/9999999").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerDocumentsEndpointsAndJwtScheme() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/admin/workshops/{id}/status")))
                .andExpect(content().string(containsString("/api/workshops/{workshopId}/registrations")))
                .andExpect(content().string(containsString("bearerAuth")));
    }

    // ------------------------------------------------------------------ helpers

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String registerUser() throws Exception {
        String email = uniqueEmail();
        MvcResult res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Test User", "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private String login(String email, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = json.readTree(res.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private Map<String, Object> workshopBody(int capacity, String meetingLink) {
        LocalDate date = LocalDate.now().plusDays(30);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Full-Stack Bootcamp");
        body.put("description", "Hands-on workshop");
        body.put("category", "Web Development");
        body.put("instructorName", "Rahul Deshmukh");
        body.put("targetAudience", "Final-year students");
        body.put("date", date.toString());
        body.put("startTime", "10:00");
        body.put("endTime", "16:00");
        body.put("registrationDeadline", date.minusDays(2).atTime(18, 0).toString());
        body.put("venue", "Auditorium B");
        if (meetingLink != null) {
            body.put("meetingLink", meetingLink);
        }
        body.put("capacity", capacity);
        body.put("fee", "499.00");
        return body;
    }

    private long createWorkshop(String adminToken, int capacity, String meetingLink) throws Exception {
        MvcResult res = mvc.perform(post("/api/admin/workshops").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(workshopBody(capacity, meetingLink))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private void publish(String adminToken, long id) throws Exception {
        patchStatus(adminToken, id, "PUBLISHED").andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions patchStatus(String adminToken, long id,
                                                                            String status) throws Exception {
        return mvc.perform(patch("/api/admin/workshops/" + id + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }
}
