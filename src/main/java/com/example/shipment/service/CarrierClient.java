package com.example.shipment.service;

import com.example.shipment.domain.CarrierStatus;
import com.example.shipment.domain.CarrierType;
import com.example.shipment.domain.Shipment;

/**
 * Abstraction over an external carrier tracking API.
 * Implementations are expected to perform a (potentially blocking) network call.
 */
public interface CarrierClient {

    CarrierType getCarrier();

    /**
     * Fetch the current status for the given shipment from this carrier.
     * This call is assumed to be blocking and is therefore executed on a dedicated executor.
     */
    CarrierStatus fetchStatus(Shipment shipment) throws CarrierClientException;
}
