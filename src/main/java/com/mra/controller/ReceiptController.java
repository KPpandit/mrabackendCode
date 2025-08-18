package com.mra.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import java.util.stream.Stream;

@RestController
@RequestMapping("/receipts")
public class ReceiptController {

    private static final String BASE_DIR = "/home/Processed_Files/einv"; // 🔥 root instead of receipts

    // ✅ API 1: Check if file exists by invoiceIdentifier
    @GetMapping("/check")
    public ResponseEntity<Void> checkFile(@RequestParam String invoiceIdentifier) {
        File file = findFileByInvoiceIdentifier(invoiceIdentifier);
        if (file != null && file.exists()) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // ✅ API 2: Download file by invoiceIdentifier
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

    // 🔍 Helper: Search all folders recursively
    private File findFileByInvoiceIdentifier(String invoiceIdentifier) {
        try (Stream<Path> paths = Files.walk(Paths.get(BASE_DIR))) {
            Optional<Path> match = paths
                    .filter(Files::isRegularFile) // only files
                    .filter(path -> path.getFileName().toString().contains(invoiceIdentifier)) // match invoiceIdentifier
                    .findFirst(); // get first match

            return match.map(Path::toFile).orElse(null);

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
