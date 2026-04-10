package com.chargelink.service;

import com.chargelink.dto.BusinessMapper;
import com.chargelink.dto.ReportStatusRequest;
import com.chargelink.dto.StatusReportDto;
import com.chargelink.entity.Charger;
import com.chargelink.entity.StatusReport;
import com.chargelink.entity.User;
import com.chargelink.repository.ChargerRepository;
import com.chargelink.repository.StatusReportRepository;
import com.chargelink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final StatusReportRepository statusReportRepository;
    private final UserRepository userRepository;
    private final ChargerRepository chargerRepository;
    private final BusinessMapper businessMapper;

    @Transactional
    public void submitReport(UUID userId, UUID chargerId, ReportStatusRequest request) {
        log.info("User {} submitting report for charger {}: {}", userId, chargerId, request.getReportedStatus());

        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new com.chargelink.exception.ResourceNotFoundException("Reporter user not found"));
        Charger charger = chargerRepository.findById(chargerId)
                .orElseThrow(() -> new com.chargelink.exception.ResourceNotFoundException("Target charger not found"));

        StatusReport report = StatusReport.builder()
                .charger(charger)
                .reportedBy(reporter)
                .reportedStatus(request.getReportedStatus())
                .note(request.getNote())
                .photoUrl(request.getPhotoUrl())
                // In a wider app we might calculate 'confidence' based on user history.
                // For now, relies on DB default (5) or passed down.
                .build();

        // The PostgreSQL auto-trigger 'trg_update_charger_status' will execute under the hood
        // to aggregate and apply this if consensus reaches 2+ in 30 mins!
        statusReportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public List<StatusReportDto> getRecentReports(UUID chargerId) {
        return statusReportRepository.findTop10ByChargerIdOrderByReportedAtDesc(chargerId)
                .stream()
                .map(businessMapper::toStatusReportDto)
                .collect(Collectors.toList());
    }
}
