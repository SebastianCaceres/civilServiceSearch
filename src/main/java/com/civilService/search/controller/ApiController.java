package com.civilService.search.controller;

import java.util.NoSuchElementException;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.civilService.search.dto.SearchResponseDto;
import com.civilService.search.dto.CivilServiceListRecordDto;
import com.civilService.search.service.SearchService;

@Controller
public class ApiController {

    private final SearchService searchService;

    public ApiController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping({"/", "/search"})
    public String searchPage(@RequestParam(defaultValue = "") String fullName,
                             @RequestParam(defaultValue = "") String examOrTitle,
                             Model model) {
        model.addAttribute("fullName", fullName);
        model.addAttribute("examOrTitle", examOrTitle);
        return "search";
    }

    @GetMapping(value = "/results", headers = "!HX-Request")
    public String resultsPage(@RequestParam(defaultValue = "") String q,
                              @RequestParam(defaultValue = "") String fullName,
                              @RequestParam(defaultValue = "") String examOrTitle,
                              @RequestParam(defaultValue = "1") int page,
                              Model model) {
        if (!isValidSearch(q, fullName, examOrTitle)) {
            model.addAttribute("error", "Full Name and Exam Number or Job Title are required.");
            model.addAttribute("fullName", fullName);
            model.addAttribute("examOrTitle", examOrTitle);
            return "search";
        }
        processPageResults(q, fullName, examOrTitle, page, model, false);
        return "results";
    }

    private boolean isValidSearch(String q, String fullName, String examOrTitle) {
        if (q != null && !q.isBlank()) {
            return true;
        }
        return fullName != null && !fullName.isBlank() && examOrTitle != null && !examOrTitle.isBlank();
    }

    private void processPageResults(String q, String fullName, String examOrTitle, int page, Model model, boolean isHtmx) {
        SearchResponseDto response;
        if ((fullName != null && !fullName.isBlank()) || (examOrTitle != null && !examOrTitle.isBlank())) {
            response = searchService.searchEntries(fullName, examOrTitle, page, 20);
        } else {
            response = searchService.searchEntries(q, page, 20);
        }
        model.addAttribute("q", q);
        model.addAttribute("fullName", fullName);
        model.addAttribute("examOrTitle", examOrTitle);
        model.addAttribute("results", response.results());
        model.addAttribute("resultCount", response.totalCount());
        model.addAttribute("searchMs", response.tookMs());
        model.addAttribute("currentPage", response.currentPage());
        model.addAttribute("totalPages", response.totalPages());
        model.addAttribute("isHtmx", isHtmx);
    }

    @GetMapping("/results")
    @HxRequest
    public String resultsPageHtmx(@RequestParam(defaultValue = "") String q,
                                  @RequestParam(defaultValue = "") String fullName,
                                  @RequestParam(defaultValue = "") String examOrTitle,
                                  @RequestParam(defaultValue = "1") int page,
                                  Model model) {
        if (!isValidSearch(q, fullName, examOrTitle)) {
            model.addAttribute("error", "Full Name and Exam Number or Job Title are required.");
            model.addAttribute("fullName", fullName);
            model.addAttribute("examOrTitle", examOrTitle);
            return "search";
        }
        processPageResults(q, fullName, examOrTitle, page, model, true);
        return "results :: results-list";
    }

    @GetMapping("/record/{id}")
    public String recordPage(@PathVariable long id,
                             @RequestParam(defaultValue = "") String q,
                             @RequestParam(defaultValue = "") String fullName,
                             @RequestParam(defaultValue = "") String examOrTitle,
                             Model model) {
        try {
            com.civilService.search.entity.CivilServiceListRecord record = searchService.getRecordById(id);
            model.addAttribute("entry", CivilServiceListRecordDto.fromEntity(record));
            model.addAttribute("q", q);
            model.addAttribute("fullName", fullName);
            model.addAttribute("examOrTitle", examOrTitle);
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
            model.addAttribute("entry", CivilServiceListRecordDto.fromEntity(record));
            model.addAttribute("estimation", searchService.getCertificationOrEstimation(record));
            return "record :: details-fragments";
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }
}
