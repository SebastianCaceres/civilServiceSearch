package com.civilService.search.dto;

import com.civilService.search.entity.CivilServiceListRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class CivilServiceListRecordDto {
    private Long id;
    private String examNo;
    private BigDecimal listNo;
    private String firstName;
    private String mi;
    private String lastName;
    private BigDecimal adjFa;
    private String listTitleCode;
    private String listTitleDesc;
    private String groupNo;
    private String listAgencyCode;
    private String listAgencyDesc;
    private String listDivCode;
    private String publishedDate;
    private LocalDateTime establishedDate;
    private LocalDateTime anniversaryDate;
    private String extensionDate;
    private String veteranCredit;
    private String parentLgyCredit;
    private String siblingLgyCredit;
    private String residencyCredit;
    private String status;

    public static CivilServiceListRecordDto fromEntity(CivilServiceListRecord record) {
        if (record == null) return null;
        
        String displayStatus = record.getStatus();
        if ("active".equalsIgnoreCase(displayStatus)) {
            displayStatus = "Active";
        } else if ("terminated".equalsIgnoreCase(displayStatus)) {
            displayStatus = "Terminated";
        } else if (displayStatus != null && !displayStatus.isEmpty()) {
            displayStatus = Character.toUpperCase(displayStatus.charAt(0)) + displayStatus.substring(1).toLowerCase();
        }

        return CivilServiceListRecordDto.builder()
                .id(record.getId())
                .examNo(record.getExamNo())
                .listNo(record.getListNo())
                .firstName(record.getFirstName())
                .mi(record.getMi())
                .lastName(record.getLastName())
                .adjFa(record.getAdjFa())
                .listTitleCode(record.getListTitleCode())
                .listTitleDesc(record.getListTitleDesc())
                .groupNo(record.getGroupNo())
                .listAgencyCode(record.getListAgencyCode())
                .listAgencyDesc(record.getListAgencyDesc())
                .listDivCode(record.getListDivCode())
                .publishedDate(record.getPublishedDate())
                .establishedDate(record.getEstablishedDate())
                .anniversaryDate(record.getAnniversaryDate())
                .extensionDate(record.getExtensionDate())
                .veteranCredit(record.getVeteranCredit())
                .parentLgyCredit(record.getParentLgyCredit())
                .siblingLgyCredit(record.getSiblingLgyCredit())
                .residencyCredit(record.getResidencyCredit())
                .status(displayStatus)
                .build();
    }

    public String getFormattedListNo() {
        if (listNo == null) return "";
        return listNo.stripTrailingZeros().toPlainString();
    }

    public String getFullName() {
        return firstName + " " + (mi == null || mi.trim().isEmpty() ? "" : mi.trim() + " ") + lastName;
    }
}
