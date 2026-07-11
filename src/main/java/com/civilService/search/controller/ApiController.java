package com.civilService.search.controller;

import java.util.NoSuchElementException;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;

import com.civilService.search.service.SearchService;

@Controller
public class ApiController {

    private final SearchService searchService;

    public ApiController(SearchService searchService) {
        this.searchService = searchService;
    }



    @GetMapping({"/", "/search"})
    public String searchPage() {
        return "search";
    }


    @GetMapping("/results")
    public String resultsPage(@RequestParam(defaultValue = "") String q,
                              @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                              Model model) {
        SearchService.SearchResponse response = searchService.searchEntries(q);
        model.addAttribute("q", q);
        model.addAttribute("results", response.results());
        model.addAttribute("resultCount", response.totalCount());
        model.addAttribute("searchMs", response.tookMs());
        
        boolean isHtmx = hxRequest != null;
        model.addAttribute("isHtmx", isHtmx);
        
        if (isHtmx) {
            return "results :: results-list";
        }
        return "results";
    }

    @GetMapping("/record/{id}")
    public String recordPage(@PathVariable long id, @RequestParam(defaultValue = "") String q, Model model) {
        try {
            com.civilService.search.entity.CivilServiceListRecord record = searchService.getRecordById(id);
            model.addAttribute("entry", record);
            model.addAttribute("q", q);
            return "record";
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/record/{id}/details")
    public String recordDetails(@PathVariable long id, Model model) {
        try {
            com.civilService.search.entity.CivilServiceListRecord record = searchService.getRecordById(id);
            if (record == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
            }
            model.addAttribute("entry", record);
            model.addAttribute("estimation", searchService.getCertificationOrEstimation(record));
            return "record :: details-fragments";
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }
}
