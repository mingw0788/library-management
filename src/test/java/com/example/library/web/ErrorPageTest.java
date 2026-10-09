package com.example.library.web;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorPageTest {

    @Autowired private MockMvc mvc;

    @Test
    void forbiddenNotFoundAndServerErrorsUseChinesePagesWithoutInternalDetails() throws Exception {
        assertErrorPage(403, "你没有权限访问此页面");
        assertErrorPage(404, "没有找到这个页面");

        mvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "internal-secret-marker")
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION,
                                new IllegalStateException("internal-secret-marker")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("刚才没有完成请求")))
                .andExpect(content().string(not(containsString("internal-secret-marker"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    private void assertErrorPage(int statusCode, String expectedText) throws Exception {
        mvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, statusCode))
                .andExpect(status().is(statusCode))
                .andExpect(content().string(containsString(expectedText)))
                .andExpect(content().string(not(containsString("Exception"))));
    }
}
