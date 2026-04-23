package com.chargelink.service;

import com.chargelink.dto.VehicleRequest;
import com.chargelink.dto.VehicleResponse;
import com.chargelink.entity.User;
import com.chargelink.entity.Vehicle;
import com.chargelink.entity.enums.ConnectorType;
import com.chargelink.exception.AuthException;
import com.chargelink.repository.UserRepository;
import com.chargelink.repository.VehicleRepository;
import com.chargelink.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VehicleService Unit Tests")
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private VehicleService vehicleService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder().id(userId).build();
    }

    // ─── addVehicle ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("addVehicle: should save and return vehicle response")
    void addVehicle_Success() {
        VehicleRequest request = buildVehicleRequest("MH12AB1234");

        Vehicle savedVehicle = Vehicle.builder()
                .id(UUID.randomUUID())
                .user(user)
                .registrationNo("MH12AB1234")
                .brand("Tata")
                .model("Nexon EV")
                .connectorType(ConnectorType.CCS2)
                .batteryCapacityKwh(40)
                .rangeKm(400)
                .build();

        when(jwtUtil.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(savedVehicle);

        VehicleResponse result = vehicleService.addVehicle(request);

        assertThat(result).isNotNull();
        assertThat(result.getBrand()).isEqualTo("Tata");
        assertThat(result.getModel()).isEqualTo("Nexon EV");
        assertThat(result.getConnectorType()).isEqualTo(ConnectorType.CCS2);
        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    @DisplayName("addVehicle: should normalise registration number — strip spaces and uppercase")
    void addVehicle_NormalisesRegistrationNo() {
        VehicleRequest request = buildVehicleRequest("mh 12 ab 1234");

        Vehicle savedVehicle = Vehicle.builder()
                .id(UUID.randomUUID())
                .user(user)
                .registrationNo("MH12AB1234")
                .brand("Tata")
                .model("Nexon EV")
                .connectorType(ConnectorType.CCS2)
                .build();

        when(jwtUtil.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> {
            Vehicle v = inv.getArgument(0);
            // Confirm normalization was applied before save
            assertThat(v.getRegistrationNo()).isEqualTo("MH12AB1234");
            return savedVehicle;
        });

        vehicleService.addVehicle(request);
    }

    @Test
    @DisplayName("addVehicle: should normalise registration number — strip hyphens")
    void addVehicle_NormalisesRegistrationNo_Hyphens() {
        VehicleRequest request = buildVehicleRequest("MH-12-AB-1234");

        Vehicle savedVehicle = Vehicle.builder()
                .id(UUID.randomUUID())
                .user(user)
                .registrationNo("MH12AB1234")
                .brand("Tata")
                .model("Nexon EV")
                .connectorType(ConnectorType.CCS2)
                .build();

        when(jwtUtil.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> {
            Vehicle v = inv.getArgument(0);
            assertThat(v.getRegistrationNo()).isEqualTo("MH12AB1234");
            return savedVehicle;
        });

        vehicleService.addVehicle(request);
    }

    @Test
    @DisplayName("addVehicle: should throw AuthException when user profile not found")
    void addVehicle_Throws_WhenUserNotFound() {
        when(jwtUtil.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.addVehicle(buildVehicleRequest("MH12AB1234")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("not completely synced");

        verify(vehicleRepository, never()).save(any());
    }

    // ─── deleteVehicle ────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteVehicle: should delete vehicle when user is the owner")
    void deleteVehicle_Success() {
        String regNo = "MH12AB1234";
        Vehicle vehicle = Vehicle.builder()
                .id(UUID.randomUUID())
                .user(user)
                .registrationNo(regNo)
                .build();

        when(jwtUtil.getCurrentUserId()).thenReturn(userId);
        when(vehicleRepository.findByRegistrationNo(regNo)).thenReturn(Optional.of(vehicle));

        vehicleService.deleteVehicle(regNo);

        verify(vehicleRepository).delete(vehicle);
    }

    @Test
    @DisplayName("deleteVehicle: should normalise input before querying")
    void deleteVehicle_NormalisesInput_BeforeQuery() {
        String rawInput = "mh 12 ab 1234";
        String normalized = "MH12AB1234";
        Vehicle vehicle = Vehicle.builder()
                .id(UUID.randomUUID())
                .user(user)
                .registrationNo(normalized)
                .build();

        when(jwtUtil.getCurrentUserId()).thenReturn(userId);
        when(vehicleRepository.findByRegistrationNo(normalized)).thenReturn(Optional.of(vehicle));

        vehicleService.deleteVehicle(rawInput);

        verify(vehicleRepository).findByRegistrationNo(normalized);
        verify(vehicleRepository).delete(vehicle);
    }

    @Test
    @DisplayName("deleteVehicle: should throw AuthException when user is not the vehicle owner")
    void deleteVehicle_Throws_WhenNotOwner() {
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        Vehicle vehicle = Vehicle.builder()
                .id(UUID.randomUUID())
                .user(otherUser)
                .registrationNo("MH12AB1234")
                .build();

        when(jwtUtil.getCurrentUserId()).thenReturn(userId);
        when(vehicleRepository.findByRegistrationNo("MH12AB1234")).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> vehicleService.deleteVehicle("MH12AB1234"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("permission");

        verify(vehicleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteVehicle: should throw AuthException when vehicle not found")
    void deleteVehicle_Throws_WhenVehicleNotFound() {
        when(jwtUtil.getCurrentUserId()).thenReturn(userId);
        when(vehicleRepository.findByRegistrationNo(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.deleteVehicle("UNKNOWN123"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("not found");

        verify(vehicleRepository, never()).delete(any());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private VehicleRequest buildVehicleRequest(String regNo) {
        VehicleRequest r = new VehicleRequest();
        r.setRegistrationNo(regNo);
        r.setBrand("Tata");
        r.setModel("Nexon EV");
        r.setConnectorType(ConnectorType.CCS2);
        r.setBatteryCapacityKwh(40);
        r.setRangeKm(400);
        return r;
    }
}
