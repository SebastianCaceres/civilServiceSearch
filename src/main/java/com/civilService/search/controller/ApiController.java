package com.civilService.search.controller;

import java.security.SecureRandom;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;

import com.civilService.search.dto.CivilServiceListRecordDto;
import com.civilService.search.dto.SearchResponseDto;
import com.civilService.search.service.SearchService;
import jakarta.servlet.http.HttpSession;

import org.altcha.altcha.v1.Altcha;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;


@Controller
public class ApiController {

    private final SearchService searchService;
    private final Map<String, Long> verifiedSignatures = new ConcurrentHashMap<>();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiController.class);

    private final String hmacKey = generateSecureRandomKey();

    public ApiController(SearchService searchService) {
        this.searchService = searchService;
    }

    private static String generateSecureRandomKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // --- Helper Method to keep code DRY ---

    // --- Main Endpoints ---

    @GetMapping({"/", "/search"})
    public String searchPage(@RequestParam(defaultValue = "") String fullName,
                             @RequestParam(defaultValue = "") String examOrTitle,
                             HttpSession session,
                             Model model) {
        model.addAttribute("fullName", fullName);
        model.addAttribute("examOrTitle", examOrTitle);
        //Boolean altchaVerified = (Boolean) session.getAttribute("altchaVerified");
        return "search";
    }

    @GetMapping("/results")
    public String resultsPage(@RequestParam(defaultValue = "") String q,
                              @RequestParam(defaultValue = "") String fullName,
                              @RequestParam(defaultValue = "") String examOrTitle,
                              @RequestParam(defaultValue = "1") int page,
                              HttpSession session,
                              Model model) {

        Boolean altchaVerified = (Boolean) session.getAttribute("altchaVerified");

        // SECURITY FIX: Enforce CAPTCHA regardless of search parameters
        if (altchaVerified == null || !altchaVerified) {
            model.addAttribute("error", "Security verification failed. Please solve the puzzle again.");
            model.addAttribute("fullName", fullName);
            model.addAttribute("examOrTitle", examOrTitle);
            return "search";
        }

        if (!isValidSearch(q, fullName, examOrTitle)) {
            model.addAttribute("error", "Full Name and Exam Number or Job Title are required.");
            model.addAttribute("fullName", fullName);
            model.addAttribute("examOrTitle", examOrTitle);
            return "search";
        }

        processPageResults(q, fullName, examOrTitle, page, model);
        return "results";
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

    // --- Altcha Endpoints ---

    @GetMapping("/captcha-fragment")
    public String getCaptchaFragment(HttpSession session, Model model) {
        Boolean altchaVerified = (Boolean) session.getAttribute("altchaVerified");

        // If already verified, return success fragment directly
        if (Boolean.TRUE.equals(altchaVerified)) {
            return "fragments :: captcha-success";
        }

        try {
            var options = new Altcha.ChallengeOptions()
                    .algorithm(Altcha.Algorithm.SHA256)
                    .maxNumber(100000)
                    .hmacKey(hmacKey)
                    .expiresInSeconds(600);
            var challenge = Altcha.createChallenge(options);

            model.addAttribute("algorithm", challenge.algorithm());
            model.addAttribute("challenge", challenge.challenge());
            model.addAttribute("maxnumber", challenge.maxnumber());
            model.addAttribute("salt", challenge.salt());
            model.addAttribute("signature", challenge.signature());

            // Store generation timestamp to prevent instant/sub-second submissions
            session.setAttribute("challengeGeneratedAt", System.currentTimeMillis());
        } catch (Exception e) {
            model.addAttribute("error", "Failed to generate security verification challenge.");
        }
        return "fragments :: altcha-widget";
    }

    @GetMapping("/verify")
    public String verifyCaptcha(
            @RequestParam(value = "altcha", required = false) String altchaPayload,
            HttpSession session) {

        // 1. Rate limit verification attempts per session to prevent brute forcing
        Integer attempts = (Integer) session.getAttribute("verifyAttempts");
        if (attempts == null) {
            attempts = 0;
        }
        attempts++;
        session.setAttribute("verifyAttempts", attempts);
        if (attempts > 10) {
            log.warn("Session exceeded maximum CAPTCHA verification attempts.");
            return "fragments :: captcha-error";
        }

        // Web component submits a single Base64 encoded JSON string named "altcha"
        if (altchaPayload == null || altchaPayload.isBlank()) {
            return "fragments :: captcha-missing";
        }

        // 2. Prevent instant submissions (bots trying to pre-solve or automate instantly)
        Long generatedAt = (Long) session.getAttribute("challengeGeneratedAt");
        if (generatedAt != null) {
            long elapsed = System.currentTimeMillis() - generatedAt;
            if (elapsed < 1000) { // Reject if solved in less than 1 second
                log.warn("CAPTCHA solved too fast ({}ms). Suspected bot behavior.", elapsed);
                return "fragments :: captcha-error";
            }
        }

        boolean verified = false;
        String signature = null;
        try {
            // Parse the signature from the Base64 payload to check for replay attacks
            byte[] decoded = java.util.Base64.getDecoder().decode(altchaPayload);
            String json = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (matcher.find()) {
                signature = matcher.group(1);
            }

            // 3. Replay attack check: signature must not have been verified already
            if (signature != null) {
                // Self-clean old signatures (> 10 mins)
                long now = System.currentTimeMillis();
                verifiedSignatures.entrySet().removeIf(entry -> now - entry.getValue() > 600_000L);

                if (verifiedSignatures.containsKey(signature)) {
                    log.warn("Replay attack detected for signature: {}", signature);
                    return "fragments :: captcha-error";
                }
            }

            // Verify the Base64 payload directly using the library
            verified = Altcha.verifySolution(altchaPayload, hmacKey, true);
        } catch (Exception e) {
            // Ignore or log exceptions depending on your environment needs
        }

        if (verified) {
            // Save the signature to the replay cache
            if (signature != null) {
                verifiedSignatures.put(signature, System.currentTimeMillis());
            }
            // Reset attempts on successful verification
            session.setAttribute("verifyAttempts", 0);
            session.setAttribute("altchaVerified", true);
            return "fragments :: captcha-success";
        } else {
            return "fragments :: captcha-error";
        }
    }

    // --- Private Search Helpers ---

    private boolean isValidSearch(String q, String fullName, String examOrTitle) {
        if (q != null && !q.isBlank()) {
            return true;
        }
        return fullName != null && !fullName.isBlank() && examOrTitle != null && !examOrTitle.isBlank();
    }

    private void processPageResults(String q, String fullName, String examOrTitle, int page, Model model) {
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
    }
}