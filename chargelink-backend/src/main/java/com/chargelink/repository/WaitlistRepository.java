package com.chargelink.repository;

import com.chargelink.entity.Waitlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface WaitlistRepository extends JpaRepository<Waitlist, UUID> {

    @Query("SELECT w FROM Waitlist w WHERE w.status = 'waiting' AND w.wantedTo < :now")
    List<Waitlist> findExpiredWaitlists(@Param("now") ZonedDateTime now);

    @Query("SELECT w FROM Waitlist w WHERE w.status = 'notified' AND w.notifiedAt < :threshold")
    List<Waitlist> findUnresponsiveWaitlists(@Param("threshold") ZonedDateTime threshold);

    List<Waitlist> findByUserIdOrderByJoinedAtDesc(UUID userId);
    List<Waitlist> findByChargerIdAndStatusOrderByJoinedAtAsc(UUID chargerId, String status);

    @Query("SELECT COUNT(w) > 0 FROM Waitlist w WHERE w.user.id = :userId " +
            "AND w.status = 'waiting' " +
            "AND w.wantedFrom < :endTime AND w.wantedTo > :startTime")
    boolean hasOverlappingWaitlistForUser(@Param("userId") UUID userId,
                                          @Param("startTime") ZonedDateTime startTime,
                                          @Param("endTime") ZonedDateTime endTime);
}
