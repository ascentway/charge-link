package com.chargelink.repository;

import com.chargelink.entity.Charger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChargerRepository extends JpaRepository<Charger, UUID> {
}
