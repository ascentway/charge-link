package com.chargelink.repository;

import com.chargelink.entity.StatusReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StatusReportRepository extends JpaRepository<StatusReport, UUID> {
    List<StatusReport> findTop10ByChargerIdOrderByReportedAtDesc(UUID chargerId);
}
