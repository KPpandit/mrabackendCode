package com.mra.controller;

import com.mra.model.Products;
import com.mra.repository.ProductsInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor

public class ProductsController {

    private final ProductsInvoiceRepository productsInvoiceRepository;

    @GetMapping("/getAll")
    public ResponseEntity<List<Products>> getAllProducts() {
        try {
            List<Products> productsList = productsInvoiceRepository.findAll();
            return ResponseEntity.ok(productsList);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
