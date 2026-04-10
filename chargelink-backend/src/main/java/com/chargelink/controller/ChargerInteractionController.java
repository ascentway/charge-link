package com.chargelink.controller;

import com.chargelink.dto.ReportStatusRequest;
import com.chargelink.dto.StatusReportDto;
import com.chargelink.security.SupabaseUserDetails;
import com.chargelink.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chargers")
@RequiredArgsConstructor
public class ChargerInteractionController {

    private final ReportService reportService;

    @PostMapping("/{id}/report")
    public ResponseEntity<Void> reportStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ReportStatusRequest request,
            @AuthenticationPrincipal SupabaseUserDetails user) {

        reportService.submitReport(user.getId(), id, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<StatusReportDto>> getReportHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(reportService.getRecentReports(id));
    }
}
