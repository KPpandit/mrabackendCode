package com.mra.controller;

import com.mra.service.InventoryFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
//@CrossOrigin("*")
public class InventoryFileController {

    private final InventoryFileService inventoryFileService;

    @GetMapping("/invoice/download")
    public ResponseEntity<byte[]> downloadPdf(@RequestParam String invoiceIdentifier) {
        return inventoryFileService.getPdfByInvoiceIdentifier(invoiceIdentifier)
                .stream().map(file -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + invoiceIdentifier + ".pdf")
                        .body(file.getFileData()))
                .findAny().orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/invoice/exists")
    public ResponseEntity<Void> checkPdfExists(@RequestParam String invoiceIdentifier) {
        boolean exists = inventoryFileService.existsByInvoiceIdentifier(invoiceIdentifier);
        return exists ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

}
