package com.quantumstore.orders;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/catalog/products")
@RegisterRestClient(configKey = "catalog-api")
@Produces(MediaType.APPLICATION_JSON)
public interface CatalogClient {

    @GET
    @Path("/{id}")
    ProductDto findById(@PathParam("id") Long id);
}