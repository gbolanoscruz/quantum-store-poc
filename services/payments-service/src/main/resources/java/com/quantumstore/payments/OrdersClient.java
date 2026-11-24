package com.quantumstore.payments;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/orders")
@RegisterRestClient(configKey = "orders-api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface OrdersClient {

    @GET
    @Path("/{id}")
    OrderDto findById(@PathParam("id") Long id);

    @POST
    @Path("/{id}/mark-paid")
    Response markPaid(@PathParam("id") Long id, String emptyJson);

    @POST
    @Path("/{id}/cancel")
    Response cancelOrder(@PathParam("id") Long id, String emptyJson);
}