package com.quantumstore.payments;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PaymentResourceTest {

    @InjectMock
    @RestClient
    OrdersClient ordersClient;

    @Test
    void shouldRecordFailedPaymentWhenDeclined() {
        when(ordersClient.findById(101L))
            .thenReturn(new OrderDto(101L, "buyer@quantumstore.dev", "QTM-CPU-001", new BigDecimal("200.00"), "CONFIRMED"));

        given()
            .contentType(ContentType.JSON)
            .body("""
                {"orderId": 101, "paymentMethod": "DECLINED"}
                """)
            .when().post("/api/payments")
            .then()
            .statusCode(402)
            .body("status", is("FAILED"))
            .body("transactionRef", startsWith("TXN-"));

        verify(ordersClient, never()).markPaid(eq(101L), eq("{}"));
    }

    @Test
    void shouldCapturePaymentEnforceIdempotencyAndRefund() {
        when(ordersClient.findById(102L))
            .thenReturn(new OrderDto(102L, "buyer@quantumstore.dev", "QTM-KEY-002", new BigDecimal("125.00"), "CONFIRMED"));
        when(ordersClient.markPaid(102L, "{}")).thenReturn(Response.ok().build());
        when(ordersClient.cancelOrder(102L, "{}")).thenReturn(Response.ok().build());

        Integer paymentId = given()
            .contentType(ContentType.JSON)
            .body("""
                {"orderId": 102, "paymentMethod": "CARD"}
                """)
            .when().post("/api/payments")
            .then()
            .statusCode(201)
            .body("status", is("CAPTURED"))
            .body("amount", is(125.0f))
            .extract().path("id");

        verify(ordersClient).markPaid(102L, "{}");

        // Idempotency: duplicate payment on the same order must return 409
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"orderId": 102, "paymentMethod": "CARD"}
                """)
            .when().post("/api/payments")
            .then()
            .statusCode(409);

        // Refund triggers upstream order cancellation (and stock release)
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/api/payments/{id}/refund", paymentId)
            .then()
            .statusCode(200)
            .body("status", is("REFUNDED"));

        verify(ordersClient).cancelOrder(102L, "{}");

        // Double refund must return 409
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/api/payments/{id}/refund", paymentId)
            .then()
            .statusCode(409);
    }
}