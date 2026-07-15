package com.civilService.search;

import com.civilService.search.controller.ApiController;
import com.civilService.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civilService.search.config.SecurityConfig;
import org.springframework.context.annotation.Import;

@WebMvcTest(ApiController.class)
@Import(SecurityConfig.class)
public class SecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @Test
    public void publicEndpointsAreAccessibleAndHardened() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                // Verify Security Hardening Headers
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("worker-src 'self' blob:")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
