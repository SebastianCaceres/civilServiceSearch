package com.civilService.search.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing an active or terminated candidate list placement for Civil Service exams.
 */
@Entity
@Table(name = "civil_service_list_records", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"exam_no", "list_no", "list_agency_code", "first_name", "last_name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Indexed
public class CivilServiceListRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "socrata_id")
    @JsonProperty(":id")
    private String socrataId;

    @Column(name = "exam_no")
    @JsonProperty("exam_no")
    @KeywordField
    private String examNo;

    @Column(name = "list_no")
    @JsonProperty("list_no")
    @GenericField
    private BigDecimal listNo;

    @Column(name = "first_name")
    @JsonProperty("first_name")
    @FullTextField
    private String firstName;

    @Column(name = "mi")
    @JsonProperty("mi")
    private String mi;

    @Column(name = "last_name")
    @JsonProperty("last_name")
    @FullTextField
    private String lastName;

    @Column(name = "adj_fa")
    @JsonProperty("adj_fa")
    private BigDecimal adjFa;

    @Column(name = "list_title_code")
    @JsonProperty("list_title_code")
    private String listTitleCode;

    @Column(name = "list_title_desc")
    @JsonProperty("list_title_desc")
    @FullTextField
    private String listTitleDesc;

    @Column(name = "group_no")
    @JsonProperty("group_no")
    private String groupNo;

    @Column(name = "list_agency_code")
    @JsonProperty("list_agency_code")
    private String listAgencyCode;

    @Column(name = "list_agency_desc")
    @JsonProperty("list_agency_desc")
    @FullTextField
    private String listAgencyDesc;

    @Column(name = "established_date")
    @JsonProperty("established_date")
    @JsonDeserialize(using = CivilServiceListRecord.FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime establishedDate;

    @Column(name = "anniversary_date")
    @JsonProperty("anniversary_date")
    @JsonDeserialize(using = CivilServiceListRecord.FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime anniversaryDate;

    @Column(name = "termination_date")
    @JsonProperty("termination_date")
    @JsonDeserialize(using = CivilServiceListRecord.FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime terminationDate;

    @Column(name = "status")
    @KeywordField
    private String status; // "active" or "terminated"

    @Column(name = "list_div_code")
    @JsonProperty("list_div_code")
    private String listDivCode;

    @Column(name = "published_date")
    @JsonProperty("published_date")
    private String publishedDate;

    @Column(name = "extension_date")
    @JsonProperty("extension_date")
    private String extensionDate;

    @Column(name = "veteran_credit")
    @JsonProperty("veteran_credit")
    private String veteranCredit;

    @Column(name = "parent_lgy_credit")
    @JsonProperty("parent_lgy_credit")
    private String parentLgyCredit;

    @Column(name = "sibling_lgy_credit")
    @JsonProperty("sibling_lgy_credit")
    private String siblingLgyCredit;

    @Column(name = "residency_credit")
    @JsonProperty("residency_credit")
    private String residencyCredit;

    public static class FlexibleLocalDateTimeDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<LocalDateTime> {
        private static final java.time.format.DateTimeFormatter FORMATTER_MM_DD_YYYY = java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy");
        private static final java.time.format.DateTimeFormatter FORMATTER_YYYY_MM_DD = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

        @Override
        public LocalDateTime deserialize(com.fasterxml.jackson.core.JsonParser p, com.fasterxml.jackson.databind.DeserializationContext ctxt)
                throws java.io.IOException {
            String value = p.getValueAsString();
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            value = value.trim();
            try {
                if (value.contains("/")) {
                    return java.time.LocalDate.parse(value, FORMATTER_MM_DD_YYYY).atStartOfDay();
                } else if (value.contains("T")) {
                    try {
                        return LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } catch (Exception e) {
                        return java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
                    }
                } else {
                    return java.time.LocalDate.parse(value, FORMATTER_YYYY_MM_DD).atStartOfDay();
                }
            } catch (Exception e) {
                throw new java.io.IOException("Failed to parse date: " + value, e);
            }
        }
    }
}
