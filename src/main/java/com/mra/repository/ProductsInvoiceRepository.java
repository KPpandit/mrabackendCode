package com.mra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mra.model.Products;

@Repository
public interface ProductsInvoiceRepository extends JpaRepository<Products, Integer> {
    @Modifying
    @Query("DELETE FROM Products p WHERE p.invoice.invoiceId = :invoiceId")
    void deleteByInvoiceId(@Param("invoiceId") int invoiceId);
}
