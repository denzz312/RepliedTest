package org.example.repliedtest.user;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(controllers = UserController.class)
@Import(ApiExceptionHandler.class)
class UserControllerWebMvcTest {

    @Autowired MockMvc mvc;
    @MockBean UserService userService;
    @MockBean BlockService blockService; // not used, but needed to construct UserController

    @Test
    void create_user_happy_path_201() throws Exception {
        var saved = UserNode.builder()
                .id("u-1").username("alice").birthdate("1990-05-10").build();

        Mockito.when(userService.createUser("alice")).thenReturn(saved);

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/users/u-1"))
                .andExpect(jsonPath("$.id").value("u-1"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.birthdate").value("1990-05-10"));
    }

    @Test
    void create_user_duplicate_username_400() throws Exception {
        Mockito.when(userService.createUser("alice"))
                .thenThrow(new ApiExceptions.BadRequest("username already taken"));

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value("username already taken"))
                .andExpect(jsonPath("$.ts").exists());
    }

    @Test
    void get_user_404_when_missing() throws Exception {
        Mockito.when(userService.getVisibleUser(eq("u-404"), any()))
                .thenThrow(new ApiExceptions.NotFound("user not found: u-404"));

        mvc.perform(get("/users/u-404").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"))
                .andExpect(jsonPath("$.message").value("user not found: u-404"))
                .andExpect(jsonPath("$.ts").exists());
    }
}
