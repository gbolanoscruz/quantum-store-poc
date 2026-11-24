package com.quantumstore.orders;

import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    @RestClient
    CatalogClient catalogClient;

    @Inject
    @RestClient
    InventoryClient inventoryClient;

    @Inject
    MeterRegistry meterRegistry;

    public record CreateOrderRequest(
            @NotBlank @Email String customerEmail,
            @NotNull Long productId,
            @Min(1) int quantity) {}

    @GET
    public List<Order> listAll() {
        return Order.listAll(Sort.descending("createdAt"));
    }

    @GET
    @Path("/{id}")
    public Order findById(@PathParam("id") Long id) {
        Order order = Order.findById(id);
        if (order == null) {
            throw new NotFoundException("Order not found: " + id);
        }
        return order;
    }

    @POST
    @Transactional
    public Response createOrder(@Valid CreateOrderRequest request) {
        // 1. Validate product & get authoritative price from catalog-service
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

        if (!product.active()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Product is inactive: " + product.sku())
                    .build();
        }

        // 2. Reserve stock in inventory-service
        try {
            inventoryClient.reserve(product.sku(), new QuantityRequest(request.quantity()));
        } catch (WebApplicationException ex) {
            int status = ex.getResponse().getStatus();
            if (status == 404 || status == 409) {
                return Response.status(Response.Status.CONFLICT)
                        .entity("Unable to reserve stock for SKU: " + product.sku())
                        .build();
            }
            throw ex;
        }

        // 3. Persist order; compensate inventory if DB write fails
        try {
            Order order = new Order();
            order.customerEmail = request.customerEmail().trim();
            order.productId = product.id();
            order.sku = product.sku();
            order.productName = product.name();
            order.quantity = request.quantity();
            order.unitPrice = product.price();
            order.totalAmount = product.price().multiply(BigDecimal.valueOf(request.quantity()));
            order.status = OrderStatus.CONFIRMED;
            order.createdAt = OffsetDateTime.now();
            order.updatedAt = OffsetDateTime.now();
            order.persistAndFlush();
            meterRegistry.counter("quantum_orders_total", "outcome", "confirmed", "sku", order.sku).increment();
            Log.infof("Order created id=%d sku=%s qty=%d total=%s", order.id, order.sku, order.quantity, order.totalAmount);
            return Response.status(Response.Status.CREATED).entity(order).build();
        } catch (RuntimeException ex) {
            try {
                meterRegistry.counter("quantum_orders_total", "outcome", "compensated", "sku", product.sku()).increment();
                Log.errorf(ex, "Order persistence failed for sku=%s, releasing reserved stock", product.sku());
                inventoryClient.release(product.sku(), new QuantityRequest(request.quantity()));
            } catch (Exception ignored) {
                // Best-effort compensation
            }
            throw ex;
        }
    }

    @POST
    @Path("/{id}/cancel")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public Response cancelOrder(@PathParam("id") Long id) {
        Order order = Order.findById(id);
        if (order == null) {
            throw new NotFoundException("Order not found: " + id);
        }
        if (order.status == OrderStatus.CANCELLED) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Order is already cancelled: " + id)
                    .build();
        }

        // Release reserved stock back to inventory-service
        inventoryClient.release(order.sku, new QuantityRequest(order.quantity));

        order.status = OrderStatus.CANCELLED;
        order.updatedAt = OffsetDateTime.now();
        meterRegistry.counter("quantum_orders_total", "outcome", "cancelled", "sku", order.sku).increment();
        Log.infof("Order cancelled id=%d sku=%s qty=%d (stock released)", order.id, order.sku, order.quantity);
        return Response.ok(order).build();
    }

    @POST
    @Path("/{id}/mark-paid")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public Response markPaid(@PathParam("id") Long id) {
        Order order = Order.findById(id);
        if (order == null) {
            throw new NotFoundException("Order not found: " + id);
        }
        if (order.status == OrderStatus.CANCELLED) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Cannot pay a cancelled order: " + id)
                    .build();
        }
        if (order.status == OrderStatus.PAID) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Order is already paid: " + id)
                    .build();
        }

        order.status = OrderStatus.PAID;
        order.updatedAt = OffsetDateTime.now();
        meterRegistry.counter("quantum_orders_total", "outcome", "paid", "sku", order.sku).increment();
        Log.infof("Order marked as PAID id=%d", order.id);
        return Response.ok(order).build();
    }
}

