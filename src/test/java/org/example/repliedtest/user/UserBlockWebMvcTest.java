package org.example.repliedtest.user;

import org.example.repliedtest.support.ApiExceptionHandler;
import org.example.repliedtest.support.ApiExceptions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class)
@Import(ApiExceptionHandler.class)
class UserBlockWebMvcTest {

    @Autowired
    MockMvc mvc;
    @MockBean
    UserService userService; // not used, but needed to construct UserController
    @MockBean
    BlockService blockService;

    @Test
    void block_happy_204() throws Exception {
        mvc.perform(post("/users/U/block/T")).andExpect(status().isNoContent());
    }

    @Test
    void block_self_400() throws Exception {
        Mockito.doThrow(new ApiExceptions.BadRequest("cannot block yourself"))
                .when(blockService).block("U", "U");
        mvc.perform(post("/users/U/block/U"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void block_unknown_404() throws Exception {
        Mockito.doThrow(new ApiExceptions.NotFound("user not found"))
                .when(blockService).block("X", "T");
        mvc.perform(post("/users/X/block/T"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void unblock_happy_204() throws Exception {
        mvc.perform(delete("/users/U/block/T")).andExpect(status().isNoContent());
    }

    @Test
    void unblock_unknown_404() throws Exception {
        Mockito.doThrow(new ApiExceptions.NotFound("user not found"))
                .when(blockService).unblock("U", "Z");
        mvc.perform(delete("/users/U/block/Z"))
                .andExpect(status().isNotFound());
    }
}
