package com.mra.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@RestController
@RequestMapping("/receipts")
public class ReceiptController {

    private static final String PROCESSED_DIR = "/home/Processed_Files/einv/receipts";

    // ✅ API 1: Check if file exists by invoiceIdentifier (as request param)
    @GetMapping("/check")
    public ResponseEntity<Void> checkFile(@RequestParam String invoiceIdentifier) {
        File file = findFileByInvoiceIdentifier(invoiceIdentifier);
        if (file != null && file.exists()) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // ✅ API 2: Download file by invoiceIdentifier (as request param)
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFile(@RequestParam String invoiceIdentifier) throws IOException {
        File file = findFileByInvoiceIdentifier(invoiceIdentifier);
        if (file == null || !file.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        byte[] fileContent = Files.readAllBytes(file.toPath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .body(fileContent);
    }

    // 🔍 Helper: Find file by invoiceIdentifier inside processed folder
    private File findFileByInvoiceIdentifier(String invoiceIdentifier) {
        File rootDir = new File(PROCESSED_DIR);
        if (!rootDir.exists()) return null;

        File[] dateDirs = rootDir.listFiles(File::isDirectory);
        if (dateDirs == null) return null;

        for (File dateDir : dateDirs) {
            File[] files = dateDir.listFiles((dir, name) -> name.contains(invoiceIdentifier));
            if (files != null && files.length > 0) {
                return files[0]; // first match
            }
        }
        return null;
    }
}
