package com.quantumstore.catalog;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

@Path("/api/catalog/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductResource {

    @GET
    public List<Product> listAll() {
        return Product.list("active", true);
    }

    @GET
    @Path("/{id}")
    public Product findById(@PathParam("id") Long id) {
        Product product = Product.findById(id);
        if (product == null || !product.active) {
            throw new NotFoundException("Product not found: " + id);
        }
        return product;
    }

    @POST
    @Transactional
    public Response create(@Valid Product product) {
        product.id = null;
        product.active = true;
        product.persist();
        return Response.created(URI.create("/api/catalog/products/" + product.id))
                .entity(product)
                .build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response softDelete(@PathParam("id") Long id) {
        Product product = Product.findById(id);
        if (product == null || !product.active) {
            throw new NotFoundException("Product not found: " + id);
        }
        product.active = false;
        return Response.noContent().build();
    }
}