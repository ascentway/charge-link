package com.chargelink.service;

import com.chargelink.dto.VehicleRequest;
import com.chargelink.dto.VehicleResponse;
import com.chargelink.entity.User;
import com.chargelink.entity.Vehicle;
import com.chargelink.exception.AuthException;
import com.chargelink.repository.UserRepository;
import com.chargelink.repository.VehicleRepository;
import com.chargelink.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public VehicleResponse addVehicle(VehicleRequest request) {
        UUID userId = jwtUtil.getCurrentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User profile not completely synced", HttpStatus.FORBIDDEN));

        String normalizedRegNo = normalizeRegistrationNo(request.getRegistrationNo());

        Vehicle vehicle = Vehicle.builder()
                .user(user)
                .registrationNo(normalizedRegNo)
                .brand(request.getBrand())
                .model(request.getModel())
                .connectorType(request.getConnectorType())
                .batteryCapacityKwh(request.getBatteryCapacityKwh())
                .rangeKm(request.getRangeKm())
                .build();

        vehicle = vehicleRepository.save(vehicle);

        return VehicleResponse.builder()
                .id(vehicle.getId())
                .registrationNo(vehicle.getRegistrationNo())
                .brand(vehicle.getBrand())
                .model(vehicle.getModel())
                .connectorType(vehicle.getConnectorType())
                .batteryCapacityKwh(vehicle.getBatteryCapacityKwh())
                .rangeKm(vehicle.getRangeKm())
                .build();
    }

    @Transactional
    public void deleteVehicle(String registrationNo) {
        UUID userId = jwtUtil.getCurrentUserId();
        String normalizedInput = normalizeRegistrationNo(registrationNo);

        Vehicle vehicle = vehicleRepository.findByRegistrationNo(normalizedInput)
                .orElseThrow(() -> new AuthException("Vehicle with registration " + registrationNo + " not found", HttpStatus.NOT_FOUND));

        if (!vehicle.getUser().getId().equals(userId)) {
            throw new AuthException("You do not have permission to delete this vehicle", HttpStatus.FORBIDDEN);
        }

        vehicleRepository.delete(vehicle);
    }

    private String normalizeRegistrationNo(String regNo) {
        if (regNo == null) return null;
        // Strip hyphens, spaces and convert to uppercase
        return regNo.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
}
