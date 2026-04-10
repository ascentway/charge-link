package com.chargelink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "waitlist")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Waitlist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charger_id", nullable = false)
    private Charger charger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(name = "wanted_from", nullable = false)
    private ZonedDateTime wantedFrom;

    @Column(name = "wanted_to", nullable = false)
    private ZonedDateTime wantedTo;

    @Column(name = "status")
    @Builder.Default
    private String status = "waiting";

    @Column(name = "notified_at")
    private ZonedDateTime notifiedAt;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private ZonedDateTime joinedAt;
}
