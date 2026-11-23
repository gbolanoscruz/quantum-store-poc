package com.quantumstore.orders;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/inventory")
@RegisterRestClient(configKey = "inventory-api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface InventoryClient {

    @POST
    @Path("/{sku}/reserve")
    Response reserve(@PathParam("sku") String sku, QuantityRequest request);

    @POST
    @Path("/{sku}/release")
    Response release(@PathParam("sku") String sku, QuantityRequest request);
}