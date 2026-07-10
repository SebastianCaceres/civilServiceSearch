package com.civilService.search.component;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

@Component("formatUtil") // We give it a friendly, short name
public class FormatUtil {

    /**
     * Helper method to highlight search query terms inside string text on the server.
     * Escapes HTML first to protect against XSS.
     */
    public String highlight(String text, String query) {
        if (text == null) {
            return "";
        }
        String escapedText = HtmlUtils.htmlEscape(text);
        if (query == null || query.trim().isEmpty()) {
            return escapedText;
        }

        String[] terms = query.split("\\s+");
        for (String term : terms) {
            String cleanTerm = term.replaceAll("[^a-zA-Z0-9]", "");
            if (cleanTerm.length() <= 1) {
                continue;
            }
            String escapedTerm = Pattern.quote(cleanTerm);
            escapedText = escapedText.replaceAll("(?i)(" + escapedTerm + ")",
                    "<mark class=\"bg-warning-subtle text-warning-emphasis p-0 rounded-1\">$1</mark>");
        }
        return escapedText;
    }

}