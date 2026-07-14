package com.civilService.search;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.civilService.search.controller.ApiController;
import com.civilService.search.entity.CivilServiceListRecord;
import com.civilService.search.dto.SearchHitDto;
import com.civilService.search.dto.SearchResponseDto;
import com.civilService.search.dto.CertificationEstimationDto;
import com.civilService.search.service.SearchService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

class ApiControllerTests {

    private final SearchService searchService = mock(SearchService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ApiController(searchService))
            .setViewResolvers(viewResolver())
            .build();

    private static InternalResourceViewResolver viewResolver() {
        InternalResourceViewResolver resolver = new InternalResourceViewResolver();
        resolver.setPrefix("/templates/");
        resolver.setSuffix(".html");
        return resolver;
    }

    @Test
    void resultsPageReturnsViewAndModel() throws Exception {
        SearchHitDto hit = new SearchHitDto(
                42L,
                "Karim Porgo",
                "COMPUTER SPECIALIST (SOFTWARE)",
                "OPEN COMPETITIVE",
                "5056",
                "514.000",
                "active",
                88,
                false,
                null,
                null,
                null,
                false,
                null,
                null,
                null
        );
        when(searchService.searchEntries(eq("kar por"), eq(1), eq(20)))
                .thenReturn(new SearchResponseDto(List.of(hit), 1, 12, 1, 1));

        mockMvc.perform(get("/results").param("q", "kar por"))
                .andExpect(status().isOk())
                .andExpect(view().name("results"))
                .andExpect(model().attribute("q", "kar por"))
                .andExpect(model().attribute("resultCount", 1))
                .andExpect(model().attributeExists("results"))
                .andExpect(model().attribute("searchMs", 12L));
    }

    @Test
    void rootPathLoadsSearchPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"));
    }

    @Test
    void recordPathLoadsRecordPage() throws Exception {
        CivilServiceListRecord entry = new CivilServiceListRecord();
        entry.setId(42L);
        entry.setFirstName("Karim");
        entry.setLastName("Porgo");
        when(searchService.getRecordById(eq(42L))).thenReturn(entry);

        mockMvc.perform(get("/record/42").param("q", "kar por"))
                .andExpect(status().isOk())
                .andExpect(view().name("record"))
                .andExpect(model().attribute("q", "kar por"))
                .andExpect(model().attributeExists("entry"));
    }

    @Test
    void recordDetailsEndpointReturnsFragmentView() throws Exception {
        CivilServiceListRecord entry = new CivilServiceListRecord();
        entry.setId(42L);
        entry.setFirstName("Karim");
        entry.setLastName("Porgo");
        when(searchService.getRecordById(eq(42L))).thenReturn(entry);
        CertificationEstimationDto estimation = new CertificationEstimationDto();
        estimation.setHasCertificate(false);
        when(searchService.getCertificationOrEstimation(entry))
                .thenReturn(estimation);

        mockMvc.perform(get("/record/42/details"))
                .andExpect(status().isOk())
                .andExpect(view().name("record :: details-fragments"))
                .andExpect(model().attributeExists("entry"))
                .andExpect(model().attributeExists("estimation"));
    }
}
