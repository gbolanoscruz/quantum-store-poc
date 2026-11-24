package com.quantumstore.payments;

import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/api/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    @Inject
    @RestClient
    OrdersClient ordersClient;

    public record CreatePaymentRequest(
            @NotNull Long orderId,
            @NotBlank String paymentMethod) {}

    @GET
    public List<Payment> listAll() {
        return Payment.listAll(Sort.descending("createdAt"));
    }

    @GET
    @Path("/{id}")
    public Payment findById(@PathParam("id") Long id) {
        Payment payment = Payment.findById(id);
        if (payment == null) {
            throw new NotFoundException("Payment not found: " + id);
        }
        return payment;
    }

    @GET
    @Path("/order/{orderId}")
    public List<Payment> findByOrderId(@PathParam("orderId") Long orderId) {
        return Payment.list("orderId", Sort.descending("createdAt"), orderId);
    }

    @POST
    @Transactional
    public Response processPayment(@Valid CreatePaymentRequest request) {
        // 1. Idempotency check: reject duplicate payment for an already paid order
        if (Payment.findCapturedByOrderId(request.orderId()) != null) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Order already has a captured payment: " + request.orderId())
                    .build();
        }

        // 2. Fetch authoritative order details from orders-service
        OrderDto order;
        try {
            order = ordersClient.findById(request.orderId());
        } catch (WebApplicationException ex) {
            if (ex.getResponse().getStatus() == 404) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Order does not exist in orders-service: " + request.orderId())
                        .build();
            }
            throw ex;
        }

        if (!"CONFIRMED".equals(order.status())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Order is not eligible for payment (status=" + order.status() + ")")
                    .build();
        }

        String method = request.paymentMethod().trim().toUpperCase();
        Payment payment = new Payment();
        payment.orderId = order.id();
        payment.customerEmail = order.customerEmail();
        payment.amount = order.totalAmount();
        payment.currency = "USD";
        payment.paymentMethod = method;
        payment.transactionRef = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        payment.createdAt = OffsetDateTime.now();
        payment.updatedAt = OffsetDateTime.now();

        // 3. Simulate gateway decline when paymentMethod is "DECLINED"
        if ("DECLINED".equals(method)) {
            payment.status = PaymentStatus.FAILED;
            payment.persist();
            return Response.status(Response.Status.PAYMENT_REQUIRED).entity(payment).build();
        }

        // 4. Mark order as PAID in orders-service before finalizing capture
        ordersClient.markPaid(order.id(), "{}");

        payment.status = PaymentStatus.CAPTURED;
        payment.persist();
        return Response.status(Response.Status.CREATED).entity(payment).build();
    }

    @POST
    @Path("/{id}/refund")
    @Consumes(MediaType.WILDCARD)
    @Transactional
    public Response refundPayment(@PathParam("id") Long id) {
        Payment payment = Payment.findById(id);
        if (payment == null) {
            throw new NotFoundException("Payment not found: " + id);
        }
        if (payment.status != PaymentStatus.CAPTURED) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Only CAPTURED payments can be refunded (status=" + payment.status + ")")
                    .build();
        }

        // Compensate upstream: cancel order (which releases stock in inventory-service)
        ordersClient.cancelOrder(payment.orderId, "{}");

        payment.status = PaymentStatus.REFUNDED;
        payment.updatedAt = OffsetDateTime.now();
        return Response.ok(payment).build();
    }
}