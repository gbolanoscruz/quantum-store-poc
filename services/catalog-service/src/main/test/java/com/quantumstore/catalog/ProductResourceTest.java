package com.quantumstore.catalog;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProductResourceTest {

    @Test
    void shouldListFlywaySeededProducts() {
        given()
            .when().get("/api/catalog/products")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(3))
            .body("sku", hasItem("QTM-CPU-001"));
    }

    @Test
    void shouldCreateFetchAndSoftDeleteProduct() {
        Integer createdId = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "sku": "QTM-TEST-900",
                  "name": "Quantum Test SSD",
                  "description": "Integration test item",
                  "price": 150.00
                }
                """)
            .when().post("/api/catalog/products")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("sku", is("QTM-TEST-900"))
            .extract().path("id");

        given()
            .when().get("/api/catalog/products/{id}", createdId)
            .then()
            .statusCode(200)
            .body("name", is("Quantum Test SSD"));

        given()
            .when().delete("/api/catalog/products/{id}", createdId)
            .then()
            .statusCode(204);

        given()
            .when().get("/api/catalog/products/{id}", createdId)
            .then()
            .statusCode(404);
    }

    @Test
    void shouldRejectInvalidProductPayload() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "sku": "",
                  "name": "",
                  "price": -5
                }
                """)
            .when().post("/api/catalog/products")
            .then()
            .statusCode(400);
    }
}