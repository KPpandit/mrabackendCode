package com.mra.repository;

import com.mra.Entity.InventoryFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryFileRepository extends JpaRepository<InventoryFile, Long> {
//    Optional<InventoryFile> findByInvoiceIdentifier(String invoiceIdentifier);
    List<InventoryFile> findByInvoiceIdentifier(String invoiceIdentifier);

}