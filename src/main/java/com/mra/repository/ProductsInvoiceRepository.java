package com.mra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mra.model.Products;

@Repository
public interface ProductsInvoiceRepository extends JpaRepository<Products, Integer> {

}
