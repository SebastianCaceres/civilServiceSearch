package com.civilService.search.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO representing a Civil Service list certification record.
 * Maps to the NYC SODA3 active civil service certifications API payload.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CivilServiceRecord {

    @JsonProperty(":id")
    private String socrataId;

    @JsonProperty("exam_no")
    private String examNo;

    @JsonProperty("list_no")
    private BigDecimal listNo;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("mi")
    private String mi;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("list_agency_code")
    private String listAgencyCode;

    @JsonProperty("list_agency_desc")
    private String listAgencyDesc;

    @JsonProperty("list_title_desc")
    private String listTitleDesc;

    @JsonProperty("cert_seq_no")
    private BigDecimal certSeqNo;

    @JsonProperty("request_date")
    private LocalDateTime requestDate;

    @JsonProperty("salary")
    private BigDecimal salary;

    @JsonProperty("cert_issue_no")
    private Long certIssueNo;

    @JsonProperty("cert_date")
    private LocalDateTime certDate;

    @JsonProperty("reissue_date")
    private LocalDateTime reissueDate;

    @JsonProperty("cert_expiration_date")
    private LocalDateTime certExpirationDate;

    @JsonProperty("no_certified")
    private BigDecimal noCertified;

    @JsonProperty("no_requested")
    private BigDecimal noRequested;

    @JsonProperty("provisional_replacement")
    private String provisionalReplacement;

    @JsonProperty("no_vacancies")
    private BigDecimal noVacancies;

    @JsonProperty("list_title_code")
    private String listTitleCode;

    @JsonProperty("sel_cert_description")
    private String selCertDescription;
}
