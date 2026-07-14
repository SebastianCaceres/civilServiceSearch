package com.civilService.search.dto;

import com.civilService.search.entity.CivilServiceRecord;
import com.civilService.search.service.CivilServiceSyncService.CivilListPayrollRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class CertificationEstimationDto {
    private boolean hasCertificate;
    private CivilServiceRecord certificate;
    private BigDecimal maxReachNumber;
    private LocalDateTime firstRequestedDate;
    private LocalDateTime lastRequestedDate;
    private Long estimatedDaysToReach;
    private boolean isAppointed;
    private CivilListPayrollRecord payrollRecord;
    private boolean isExpired;

    private double progressPercentage = 0.0;
    private String remainingRanks = "N/A";
    private String reachRateText = "N/A";
    private String progressBarColor = "#007bff";

    private String statusText = "Not Appointed";
    private String statusStyle = "color: black; font-weight: normal;";
    private String payrollCardStyle = "display: none;";

    private String agencyText = "N/A";
    private String titleText = "N/A";
    private String reachableDateText = "N/A";
    private String requestDateText = "N/A";
    private String expirationDateText = "N/A";
    private String certIssueNoText = "N/A";
    private String certSeqNoText = "N/A";
    private String noCertifiedText = "N/A";
    private String noRequestedText = "N/A";
    private String salaryText = "N/A";
    private String provisionalReplacementText = "N/A";
    private String selCertDescriptionText = "N/A";

    private String maxReachNumberText = "N/A";
    private String firstRequestedDateText = "N/A";
    private String lastRequestedDateText = "N/A";
    private String estimatedDaysToReachStyle = "color: #d9534f; font-weight: bold;";
    private String estimationStatus = "N/A";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static String formatOrNa(Object val) {
        if (val == null) return "N/A";
        if (val instanceof LocalDateTime) {
            return ((LocalDateTime) val).format(DATE_FORMATTER);
        }
        if (val instanceof BigDecimal) {
            return ((BigDecimal) val).stripTrailingZeros().toPlainString();
        }
        return val.toString();
    }

    public static CertificationEstimationDto fromCertificate(
            CivilServiceRecord cert,
            boolean isAppointed,
            CivilListPayrollRecord payrollRecord,
            boolean isExpired,
            BigDecimal maxReachNumber,
            LocalDateTime firstRequestedDate,
            LocalDateTime lastRequestedDate,
            String reachRateText) {
        CertificationEstimationDto dto = new CertificationEstimationDto();
        dto.hasCertificate = true;
        dto.certificate = cert;
        dto.isAppointed = isAppointed;
        dto.payrollRecord = payrollRecord;
        dto.isExpired = isExpired;
        dto.maxReachNumber = maxReachNumber;
        dto.firstRequestedDate = firstRequestedDate;
        dto.lastRequestedDate = lastRequestedDate;
        
        dto.progressPercentage = 100.0;
        dto.remainingRanks = "0.000";
        dto.reachRateText = reachRateText != null ? reachRateText : "N/A (Already Reached)";

        if (isAppointed) {
            dto.statusText = "Appointed";
            dto.statusStyle = "color: green; font-weight: bold;";
            dto.progressBarColor = "#28a745";
        } else if (isExpired) {
            dto.statusText = "Expired";
            dto.statusStyle = "color: #d9534f; font-weight: bold;";
            dto.progressBarColor = "#d9534f";
        } else {
            dto.statusText = "Not Appointed";
            dto.statusStyle = "color: black; font-weight: normal;";
            dto.progressBarColor = "#28a745";
        }
        dto.payrollCardStyle = isAppointed ? "display: block;" : "display: none;";

        dto.maxReachNumberText = formatOrNa(maxReachNumber);
        dto.firstRequestedDateText = formatOrNa(firstRequestedDate);
        dto.lastRequestedDateText = formatOrNa(lastRequestedDate);

        if (cert != null) {
            dto.agencyText = formatOrNa(cert.getListAgencyDesc()) + " (" + formatOrNa(cert.getListAgencyCode()) + ")";
            dto.titleText = formatOrNa(cert.getListTitleDesc());
            dto.reachableDateText = formatOrNa(cert.getCertDate());
            dto.requestDateText = formatOrNa(cert.getRequestDate());
            dto.expirationDateText = formatOrNa(cert.getCertExpirationDate());
            dto.certIssueNoText = formatOrNa(cert.getCertIssueNo());
            dto.certSeqNoText = formatOrNa(cert.getCertSeqNo());
            dto.noCertifiedText = formatOrNa(cert.getNoCertified());
            dto.noRequestedText = formatOrNa(cert.getNoRequested());
            dto.salaryText = cert.getSalary() != null ? "$" + cert.getSalary() : "N/A";
            dto.provisionalReplacementText = formatOrNa(cert.getProvisionalReplacement());
            dto.selCertDescriptionText = formatOrNa(cert.getSelCertDescription());
        }
        return dto;
    }

    public static CertificationEstimationDto fromEstimation(
            BigDecimal maxReachNumber,
            LocalDateTime firstRequestedDate,
            LocalDateTime lastRequestedDate,
            Long estimatedDaysToReach,
            String estimationStatus,
            boolean isAppointed,
            CivilListPayrollRecord payrollRecord,
            double progressPercentage,
            String remainingRanks,
            String reachRateText,
            boolean isExpired) {
        CertificationEstimationDto dto = new CertificationEstimationDto();
        dto.hasCertificate = false;
        dto.maxReachNumber = maxReachNumber;
        dto.firstRequestedDate = firstRequestedDate;
        dto.lastRequestedDate = lastRequestedDate;
        dto.estimatedDaysToReach = estimatedDaysToReach;
        dto.estimationStatus = isExpired ? "Expired" : estimationStatus;
        dto.isAppointed = isAppointed;
        dto.payrollRecord = payrollRecord;
        dto.isExpired = isExpired;

        dto.progressPercentage = progressPercentage;
        dto.remainingRanks = remainingRanks;
        dto.reachRateText = reachRateText;

        if (isAppointed) {
            dto.statusText = "Appointed";
            dto.statusStyle = "color: green; font-weight: bold;";
            dto.progressBarColor = "#28a745";
        } else if (isExpired) {
            dto.statusText = "Expired";
            dto.statusStyle = "color: #d9534f; font-weight: bold;";
            dto.progressBarColor = "#d9534f";
        } else {
            dto.statusText = "Not Appointed";
            dto.statusStyle = "color: black; font-weight: normal;";
            dto.progressBarColor = "#007bff";
        }
        dto.payrollCardStyle = isAppointed ? "display: block;" : "display: none;";

        dto.maxReachNumberText = formatOrNa(maxReachNumber);
        dto.firstRequestedDateText = formatOrNa(firstRequestedDate);
        dto.lastRequestedDateText = formatOrNa(lastRequestedDate);
        
        if (isExpired) {
            dto.estimatedDaysToReachStyle = "color: #d9534f; font-weight: bold;";
        } else {
            dto.estimatedDaysToReachStyle = estimatedDaysToReach != null && estimatedDaysToReach == 0 
                    ? "color: green; font-weight: bold;" 
                    : "color: #d9534f; font-weight: bold;";
        }
        return dto;
    }

    public static CertificationEstimationDto emptyEstimation(String statusMessage, boolean isAppointed, CivilListPayrollRecord payrollRecord, boolean isExpired) {
        CertificationEstimationDto dto = new CertificationEstimationDto();
        dto.hasCertificate = false;
        dto.isAppointed = isAppointed;
        dto.payrollRecord = payrollRecord;
        dto.isExpired = isExpired;
        dto.estimationStatus = isExpired ? "Expired" : statusMessage;

        if (isAppointed) {
            dto.statusText = "Appointed";
            dto.statusStyle = "color: green; font-weight: bold;";
            dto.progressBarColor = "#28a745";
        } else if (isExpired) {
            dto.statusText = "Expired";
            dto.statusStyle = "color: #d9534f; font-weight: bold;";
            dto.progressBarColor = "#d9534f";
        } else {
            dto.statusText = "Not Appointed";
            dto.statusStyle = "color: black; font-weight: normal;";
            dto.progressBarColor = "#007bff";
        }
        dto.payrollCardStyle = isAppointed ? "display: block;" : "display: none;";
        return dto;
    }
}
