package com.mra.controller;

import com.mra.model.InvoiceBean;
import com.mra.service.MRAService;
import com.mra.service.RechargeService;
import com.mra.service.SalesGoodService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
@RestController
@RequestMapping("/api/recharge")
@RequiredArgsConstructor
public class RechargeController {
    private final RechargeService rechargeService;
    private final MRAService mraService;
    @PostMapping("/upload-and-transmit-recharge")
    public ResponseEntity<String> uploadAndTransmit(@RequestParam("file") MultipartFile file) {
        try {
            // Add validation for empty file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }

            // Add content type validation if needed
            if (!file.getContentType().equals("application/pdf")) {
                return ResponseEntity.badRequest().body("Only PDF files are allowed");
            }

            List<InvoiceBean> invoices = rechargeService.extractAndConvertToInvoiceBean(file);
            System.out.println(invoices.toString()+"----- values before saving----");
            String response = mraService.submitInvoices(invoices);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
    @PostMapping("/upload-multiple-and-transmit-recharge")
    public ResponseEntity<String> uploadMultipleAndTransmit(@RequestParam("files") MultipartFile[] files) {
        StringBuilder result = new StringBuilder();

        for (MultipartFile file : files) {
            try {
                if (file.isEmpty()) {
                    result.append("File is empty: ").append(file.getOriginalFilename()).append("\n");
                    continue;
                }

                List<InvoiceBean> invoices = rechargeService.extractAndConvertToInvoiceBean(file);
                String response = mraService.submitInvoices(invoices);

                result.append("File: ").append(file.getOriginalFilename())
                        .append(" => Success. Response: ").append(response).append("\n");

            } catch (Exception e) {
                result.append("File: ").append(file.getOriginalFilename())
                        .append(" => Error: ").append(e.getMessage()).append("\n");
                e.printStackTrace();
            }
        }

        return ResponseEntity.ok(result.toString());
    }
}
