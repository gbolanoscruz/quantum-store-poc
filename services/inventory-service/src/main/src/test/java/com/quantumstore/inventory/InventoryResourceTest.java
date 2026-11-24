package com.quantumstore.inventory;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InventoryResourceTest {

    @InjectMock
    @RestClient
    CatalogClient catalogClient;

    @Test
    void shouldReserveAndReleaseStock() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"quantity": 3}
                """)
            .when().post("/api/inventory/QTM-CPU-001/reserve")
            .then()
            .statusCode(200)
            .body("availableQuantity", is(22))
            .body("reservedQuantity", is(3));

        given()
            .contentType(ContentType.JSON)
            .body("""
                {"quantity": 3}
                """)
            .when().post("/api/inventory/QTM-CPU-001/release")
            .then()
            .statusCode(200)
            .body("availableQuantity", is(25))
            .body("reservedQuantity", is(0));
    }

    @Test
    void shouldRejectReservationWhenStockIsInsufficient() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"quantity": 999}
                """)
            .when().post("/api/inventory/QTM-CPU-001/reserve")
            .then()
            .statusCode(409);
    }

    @Test
    void shouldReturnBadRequestWhenProductNotInCatalog() {
        when(catalogClient.findById(999L)).thenThrow(new WebApplicationException(404));

        given()
            .contentType(ContentType.JSON)
            .body("""
                {"productId": 999, "availableQuantity": 10}
                """)
            .when().post("/api/inventory")
            .then()
            .statusCode(400);
    }

    @Test
    void shouldInitializeStockForValidCatalogProduct() {
        when(catalogClient.findById(42L))
            .thenReturn(new ProductDto(42L, "QTM-TEST-042", "Mocked Product", true));

        given()
            .contentType(ContentType.JSON)
            .body("""
                {"productId": 42, "availableQuantity": 15}
                """)
            .when().post("/api/inventory")
            .then()
            .statusCode(201)
            .body("sku", is("QTM-TEST-042"))
            .body("availableQuantity", is(15));
    }
}