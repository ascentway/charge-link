package com.chargelink.controller;

import com.chargelink.dto.VehicleRequest;
import com.chargelink.dto.VehicleResponse;
import com.chargelink.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicle Management", description = "Endpoints for managing user electric vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    @Operation(summary = "Add a vehicle", description = "Registers a new electric vehicle for the authenticated user.")
    @PostMapping
    public ResponseEntity<VehicleResponse> addVehicle(@Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.ok(vehicleService.addVehicle(request));
    }

    @Operation(summary = "Delete a vehicle", description = "Removes a vehicle registration for the authenticated user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Vehicle successfully deleted"),
            @ApiResponse(responseCode = "404", description = "Vehicle not found"),
            @ApiResponse(responseCode = "403", description = "You do not own this vehicle")
    })
    @DeleteMapping("/{registrationNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVehicle(@PathVariable String registrationNo) {
        vehicleService.deleteVehicle(registrationNo);
    }
}
