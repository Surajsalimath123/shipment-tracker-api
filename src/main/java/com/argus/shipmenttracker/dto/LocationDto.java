package com.argus.shipmenttracker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocationDto(
    @DecimalMin(value = "-90.0",  message = "latitude must be between -90 and 90")
    @DecimalMax(value = "90.0",   message = "latitude must be between -90 and 90")
    Double latitude,

    @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
    @DecimalMax(value = "180.0",  message = "longitude must be between -180 and 180")
    Double longitude,

    @Size(max = 500, message = "address must be 500 characters or fewer")
    String address
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (latitude != null)  map.put("latitude",  latitude);
        if (longitude != null) map.put("longitude", longitude);
        if (address != null)   map.put("address",   address);
        return map;
    }

    public static LocationDto fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        return new LocationDto(
            asDouble(map.get("latitude")),
            asDouble(map.get("longitude")),
            (String) map.get("address")
        );
    }

    private static Double asDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        return Double.parseDouble(value.toString());
    }
}
