package com.chargelink.dto;

import com.chargelink.entity.Booking;
import com.chargelink.entity.Session;
import com.chargelink.entity.StatusReport;
import com.chargelink.entity.Waitlist;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BusinessMapper {

    @Mapping(target = "reportedByFullName", source = "reportedBy.fullName")
    StatusReportDto toStatusReportDto(StatusReport report);

    @Mapping(target = "chargerCode", source = "charger.chargerCode")
    @Mapping(target = "stationName", source = "charger.station.name")
    BookingDto toBookingDto(Booking booking);

    @Mapping(target = "chargerCode", source = "charger.chargerCode")
    @Mapping(target = "stationName", source = "charger.station.name")
    SessionDto toSessionDto(Session session);

    @Mapping(target = "chargerCode", source = "charger.chargerCode")
    @Mapping(target = "stationName", source = "charger.station.name")
    WaitlistDto toWaitlistDto(Waitlist waitlist);
}
