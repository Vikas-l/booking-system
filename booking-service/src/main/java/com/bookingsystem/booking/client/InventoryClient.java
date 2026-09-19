package com.bookingsystem.booking.client;

import com.bookingsystem.booking.exception.InventoryReservationFailedException;
import com.bookingsystem.common.dto.ReservationRequest;
import com.bookingsystem.common.dto.ReservationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper around the Inventory Service's reservation REST API. Kept
 * as a dedicated class (rather than inline RestClient calls in
 * {@link com.bookingsystem.booking.service.BookingService}) so the HTTP
 * concern -- base URL, error translation -- is isolated from saga
 * orchestration logic.
 */
@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(@Value("${inventory-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public ReservationResponse reserve(ReservationRequest request) {
        try {
            return restClient.post()
                    .uri("/api/v1/reservations")
                    .body(request)
                    .retrieve()
                    .body(ReservationResponse.class);
        } catch (HttpClientErrorException e) {
            // 4xx from Inventory Service means the request was understood
            // but rejected on business grounds (e.g. 409 insufficient
            // inventory) -- not a transient/technical failure, so surface
            // it as a saga-terminating error rather than something to retry.
            throw new InventoryReservationFailedException(
                    "Inventory Service rejected reservation: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }
    }

    public void release(Long reservationId) {
        restClient.post()
                .uri("/api/v1/reservations/{id}/release", reservationId)
                .retrieve()
                .toBodilessEntity();
    }
}
