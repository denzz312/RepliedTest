package org.example.repliedtest.support;

import org.example.repliedtest.support.ApiExceptions.BadRequest;
import org.example.repliedtest.support.ApiExceptions.Forbidden;
import org.example.repliedtest.support.ApiExceptions.NotFound;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DummyController.class)
@Import(ApiExceptionHandler.class)
class ApiExceptionHandlerWebMvcTest {

    @Autowired
    MockMvc mvc;

    @Test
    void badRequest_is400_with_json_body() throws Exception {
        mvc.perform(get("/t/bad").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value("bad input"))
                .andExpect(jsonPath("$.ts").exists());
    }

    @Test
    void forbidden_is403_with_json_body() throws Exception {
        mvc.perform(get("/t/forbid").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("forbidden"))
                .andExpect(jsonPath("$.message").value("nope"))
                .andExpect(jsonPath("$.ts").exists());
    }

    @Test
    void notFound_is404_with_json_body() throws Exception {
        mvc.perform(get("/t/miss").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("not_found"))
                .andExpect(jsonPath("$.message").value("not here"))
                .andExpect(jsonPath("$.ts").exists());
    }
}

@RestController
class DummyController {
    @GetMapping("/t/bad")    public void bad()    { throw new BadRequest("bad input"); }
    @GetMapping("/t/forbid") public void forbid() { throw new Forbidden("nope"); }
    @GetMapping("/t/miss")   public void miss()   { throw new NotFound("not here"); }
}
