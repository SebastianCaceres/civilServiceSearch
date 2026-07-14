package com.civilService.search.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.DocumentId;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.SearchEntity;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ScaledNumberField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ProjectionConstructor;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IdProjection;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing an active or terminated candidate list placement for Civil Service exams.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SearchEntity
@Indexed
public class CivilServiceListRecord {

    @DocumentId
    private Long id;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty(":id")
    private String socrataId;

    @JsonProperty("exam_no")
    @FullTextField(projectable = Projectable.YES)
    private String examNo;

    @JsonProperty("list_no")
    @ScaledNumberField(decimalScale = 3, projectable = Projectable.YES)
    private BigDecimal listNo;

    @JsonProperty("first_name")
    @FullTextField(projectable = Projectable.YES)
    private String firstName;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("mi")
    private String mi;

    @JsonProperty("last_name")
    @FullTextField(projectable = Projectable.YES)
    private String lastName;

    @ScaledNumberField(decimalScale = 3, projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("adj_fa")
    private BigDecimal adjFa;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("list_title_code")
    private String listTitleCode;

    @JsonProperty("list_title_desc")
    @FullTextField(projectable = Projectable.YES)
    private String listTitleDesc;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("group_no")
    private String groupNo;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("list_agency_code")
    private String listAgencyCode;

    @JsonProperty("list_agency_desc")
    @FullTextField(projectable = Projectable.YES)
    private String listAgencyDesc;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("established_date")
    @JsonDeserialize(using = CivilServiceListRecord.FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime establishedDate;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("anniversary_date")
    @JsonDeserialize(using = CivilServiceListRecord.FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime anniversaryDate;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("termination_date")
    @JsonDeserialize(using = CivilServiceListRecord.FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime terminationDate;

    @KeywordField(projectable = Projectable.YES)
    private String status; // "active" or "terminated"

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("list_div_code")
    private String listDivCode;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("published_date")
    private String publishedDate;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("extension_date")
    private String extensionDate;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("veteran_credit")
    private String veteranCredit;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("parent_lgy_credit")
    private String parentLgyCredit;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("sibling_lgy_credit")
    private String siblingLgyCredit;

    @GenericField(projectable = Projectable.YES, searchable = Searchable.NO)
    @JsonProperty("residency_credit")
    private String residencyCredit;

    @ProjectionConstructor
    public CivilServiceListRecord(
            @IdProjection Long id,
            String examNo,
            BigDecimal listNo,
            String firstName,
            String mi,
            String lastName,
            String listTitleDesc,
            String listAgencyDesc,
            String status) {
        this.id = id;
        this.examNo = examNo;
        this.listNo = listNo;
        this.firstName = firstName;
        this.mi = mi;
        this.lastName = lastName;
        this.listTitleDesc = listTitleDesc;
        this.listAgencyDesc = listAgencyDesc;
        this.status = status;
    }

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
