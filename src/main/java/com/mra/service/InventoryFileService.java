package com.mra.service;

import com.mra.Entity.InventoryFile;
import com.mra.repository.InventoryFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InventoryFileService {

    private final InventoryFileRepository repository;

    public void savePdf(String invoiceIdentifier, byte[] pdfBytes) {
        InventoryFile file = new InventoryFile();
        file.setInvoiceIdentifier(invoiceIdentifier);
        file.setFileData(pdfBytes);
        file.setContentType("application/pdf");
        file.setCreatedAt(LocalDateTime.now());
        repository.save(file);
    }

    public List<InventoryFile> getPdfByInvoiceIdentifier(String invoiceIdentifier) {
        return repository.findByInvoiceIdentifier(invoiceIdentifier);
    }
    public boolean existsByInvoiceIdentifier(String invoiceIdentifier) {
        return !repository.findByInvoiceIdentifier(invoiceIdentifier).isEmpty();
    }

}
