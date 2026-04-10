package com.chargelink.controller;

import com.chargelink.dto.SessionDto;
import com.chargelink.dto.StartSessionRequest;
import com.chargelink.dto.StopSessionRequest;
import com.chargelink.security.SupabaseUserDetails;
import com.chargelink.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping("/start")
    public ResponseEntity<SessionDto> startSession(
            @Valid @RequestBody StartSessionRequest request,
            @AuthenticationPrincipal SupabaseUserDetails user) {

        SessionDto session = sessionService.startSession(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PatchMapping("/{id}/stop")
    public ResponseEntity<SessionDto> stopSession(
            @PathVariable UUID id,
            @Valid @RequestBody StopSessionRequest request,
            @AuthenticationPrincipal SupabaseUserDetails user) {

        SessionDto session = sessionService.stopSession(user.getId(), id, request);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/me")
    public ResponseEntity<List<SessionDto>> getMySessions(
            @AuthenticationPrincipal SupabaseUserDetails user) {

        return ResponseEntity.ok(sessionService.getUserSessions(user.getId()));
    }
}
