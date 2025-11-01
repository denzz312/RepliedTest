package org.example.repliedtest.follow;

import org.example.repliedtest.support.ApiExceptionHandler;
import org.example.repliedtest.support.ApiExceptions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FollowController.class)
@Import(ApiExceptionHandler.class)
class FollowAcceptDenyWebMvcTest {

    @Autowired MockMvc mvc;
    @MockBean FollowService service;

    @Test
    void accept_happy_204() throws Exception {
        mvc.perform(post("/follows/requests/T/accept/F"))
                .andExpect(status().isNoContent());
    }

    @Test
    void accept_missing_request_404() throws Exception {
        Mockito.doThrow(new ApiExceptions.NotFound("no pending follow request"))
                .when(service).acceptFollowRequest("T", "F");

        mvc.perform(post("/follows/requests/T/accept/F"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deny_happy_204() throws Exception {
        mvc.perform(post("/follows/requests/T/deny/F"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deny_missing_request_404() throws Exception {
        Mockito.doThrow(new ApiExceptions.NotFound("no pending follow request"))
                .when(service).denyFollowRequest("T", "F");

        mvc.perform(post("/follows/requests/T/deny/F"))
                .andExpect(status().isNotFound());
    }
}

