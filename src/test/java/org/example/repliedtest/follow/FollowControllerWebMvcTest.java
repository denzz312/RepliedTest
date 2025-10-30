package org.example.repliedtest.follow;

import org.example.repliedtest.support.ApiExceptionHandler;
import org.example.repliedtest.support.ApiExceptions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FollowController.class)
@Import(ApiExceptionHandler.class)
class FollowControllerWebMvcTest {

    @Autowired MockMvc mvc;
    @MockBean FollowService service;

    @Test
    void send_happy_202() throws Exception {
        mvc.perform(post("/follows/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromUserId\":\"A\",\"toUserId\":\"B\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void send_self_400() throws Exception {
        Mockito.doThrow(new ApiExceptions.BadRequest("cannot request to follow yourself"))
                .when(service).sendFollowRequest("A", "A");

        mvc.perform(post("/follows/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromUserId\":\"A\",\"toUserId\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void send_blocked_403() throws Exception {
        Mockito.doThrow(new ApiExceptions.Forbidden("request disallowed due to block"))
                .when(service).sendFollowRequest("A", "B");

        mvc.perform(post("/follows/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromUserId\":\"A\",\"toUserId\":\"B\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void send_unknown_404() throws Exception {
        Mockito.doThrow(new ApiExceptions.NotFound("user not found"))
                .when(service).sendFollowRequest("X", "B");

        mvc.perform(post("/follows/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromUserId\":\"X\",\"toUserId\":\"B\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void list_incoming_happy_sorted() throws Exception {
        Mockito.when(service.listIncomingRequests("T"))
                .thenReturn(List.of("U1", "U2", "U3"));

        mvc.perform(get("/follows/requests/T"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0]").value("U1"))
                .andExpect(jsonPath("$[1]").value("U2"))
                .andExpect(jsonPath("$[2]").value("U3"));
    }
}

