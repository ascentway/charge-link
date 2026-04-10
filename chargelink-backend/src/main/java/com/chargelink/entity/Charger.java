package com.chargelink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "chargers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Charger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    @ToString.Exclude
    private Station station;

    @Column(name = "charger_code")
    private String chargerCode;

    @Column(name = "connector_type", nullable = false)
    private String connectorType;

    @Column(name = "power_kw", precision = 5, scale = 1)
    private BigDecimal powerKw;

    @Column(name = "current_type")
    @Builder.Default
    private String currentType = "DC";

    @Column(name = "current_status")
    @Builder.Default
    private String currentStatus = "unknown";

    @Column(name = "status_updated_at")
    private ZonedDateTime statusUpdatedAt;

    @Column(name = "status_source")
    @Builder.Default
    private String statusSource = "unknown";

    @Column(name = "price_per_kwh", precision = 6, scale = 2)
    private BigDecimal pricePerKwh;

    @Column(name = "price_per_min", precision = 6, scale = 2)
    private BigDecimal pricePerMin;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
