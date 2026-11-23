package com.quantumstore.inventory;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/api/inventory")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InventoryResource {

    @Inject
    @RestClient
    CatalogClient catalogClient;

    public record StockRequest(@NotNull Long productId, @Min(0) int availableQuantity) {}

    public record QuantityRequest(@Min(1) int quantity) {}

    @GET
    public List<InventoryItem> listAll() {
        return InventoryItem.listAll();
    }

    @GET
    @Path("/{sku}")
    public InventoryItem findBySku(@PathParam("sku") String sku) {
        InventoryItem item = InventoryItem.findBySku(sku);
        if (item == null) {
            throw new NotFoundException("Inventory not found for SKU: " + sku);
        }
        return item;
    }

    @POST
    @Transactional
    public Response upsertStock(@Valid StockRequest request) {
        ProductDto product;
        try {
            product = catalogClient.findById(request.productId());
        } catch (WebApplicationException ex) {
            if (ex.getResponse().getStatus() == 404) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Product does not exist in catalog-service: " + request.productId())
                        .build();
            }
            throw ex;
        }

        InventoryItem item = InventoryItem.find("productId", product.id()).firstResult();
        if (item == null) {
            item = new InventoryItem();
            item.productId = product.id();
            item.sku = product.sku();
            item.availableQuantity = request.availableQuantity();
            item.reservedQuantity = 0;
            item.updatedAt = OffsetDateTime.now();
            item.persist();
            return Response.status(Response.Status.CREATED).entity(item).build();
        }

        item.availableQuantity = request.availableQuantity();
        item.updatedAt = OffsetDateTime.now();
        return Response.ok(item).build();
    }

    @POST
    @Path("/{sku}/reserve")
    @Transactional
    public Response reserve(@PathParam("sku") String sku, @Valid QuantityRequest request) {
        InventoryItem item = InventoryItem.findBySku(sku);
        if (item == null) {
            throw new NotFoundException("Inventory not found for SKU: " + sku);
        }
        if (item.availableQuantity < request.quantity()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Insufficient stock for SKU: " + sku)
                    .build();
        }
        item.availableQuantity -= request.quantity();
        item.reservedQuantity += request.quantity();
        item.updatedAt = OffsetDateTime.now();
        return Response.ok(item).build();
    }

    @POST
    @Path("/{sku}/release")
    @Transactional
    public Response release(@PathParam("sku") String sku, @Valid QuantityRequest request) {
        InventoryItem item = InventoryItem.findBySku(sku);
        if (item == null) {
            throw new NotFoundException("Inventory not found for SKU: " + sku);
        }
        if (item.reservedQuantity < request.quantity()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Reserved stock is lower than release amount for SKU: " + sku)
                    .build();
        }
        item.reservedQuantity -= request.quantity();
        item.availableQuantity += request.quantity();
        item.updatedAt = OffsetDateTime.now();
        return Response.ok(item).build();
    }
}