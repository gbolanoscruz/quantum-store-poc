package com.quantumstore.orders;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OrderResourceTest {

    @InjectMock
    @RestClient
    CatalogClient catalogClient;

    @InjectMock
    @RestClient
    InventoryClient inventoryClient;

    @Test
    void shouldCreateAndCancelOrderWithStockCompensation() {
        when(catalogClient.findById(1L))
            .thenReturn(new ProductDto(1L, "QTM-CPU-001", "Quantum Core X1", new BigDecimal("150.00"), true));
        when(inventoryClient.reserve(eq("QTM-CPU-001"), any(QuantityRequest.class)))
            .thenReturn(Response.ok().build());
        when(inventoryClient.release(eq("QTM-CPU-001"), any(QuantityRequest.class)))
            .thenReturn(Response.ok().build());

        Integer orderId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "customerEmail": "tester@quantumstore.dev",
                  "productId": 1,
                  "quantity": 2
                }
                """)
            .when().post("/api/orders")
            .then()
            .statusCode(201)
            .body("status", is("CONFIRMED"))
            .body("totalAmount", is(300.0f))
            .extract().path("id");

        verify(inventoryClient).reserve("QTM-CPU-001", new QuantityRequest(2));

        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/api/orders/{id}/cancel", orderId)
            .then()
            .statusCode(200)
            .body("status", is("CANCELLED"));

        verify(inventoryClient).release("QTM-CPU-001", new QuantityRequest(2));

        // Double cancel must return 409 Conflict
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/api/orders/{id}/cancel", orderId)
            .then()
            .statusCode(409);
    }

    @Test
    void shouldMarkConfirmedOrderAsPaid() {
        when(catalogClient.findById(2L))
            .thenReturn(new ProductDto(2L, "QTM-KEY-002", "Mechanical Keyboard", new BigDecimal("100.00"), true));
        when(inventoryClient.reserve(eq("QTM-KEY-002"), any(QuantityRequest.class)))
            .thenReturn(Response.ok().build());

        Integer orderId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "customerEmail": "buyer@quantumstore.dev",
                  "productId": 2,
                  "quantity": 1
                }
                """)
            .when().post("/api/orders")
            .then()
            .statusCode(201)
            .extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/api/orders/{id}/mark-paid", orderId)
            .then()
            .statusCode(200)
            .body("status", is("PAID"));

        // Paying an already paid order must return 409 Conflict
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/api/orders/{id}/mark-paid", orderId)
            .then()
            .statusCode(409);
    }

    @Test
    void shouldReturnConflictWhenInventoryCannotReserve() {
        when(catalogClient.findById(3L))
            .thenReturn(new ProductDto(3L, "QTM-MON-003", "Studio Display", new BigDecimal("500.00"), true));
        when(inventoryClient.reserve(eq("QTM-MON-003"), any(QuantityRequest.class)))
            .thenThrow(new WebApplicationException(409));

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "customerEmail": "buyer@quantumstore.dev",
                  "productId": 3,
                  "quantity": 99
                }
                """)
            .when().post("/api/orders")
            .then()
            .statusCode(409);
    }
}