package com.mra.controller;

import com.mra.model.Invoice;
import com.mra.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    @Autowired
    private InvoiceRepository invoiceRepository;


    @GetMapping("/all")
    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    @GetMapping("/search")
    public List<Invoice> searchInvoices(@RequestParam("value") String value) {
        return invoiceRepository.searchInvoicesByAnyField(value);
    }
    @GetMapping("/search/identifier")
    public List<Invoice> searchInvoicesByIdentifier(@RequestParam("value") String value) {
        return invoiceRepository.findByInvoiceIdentifierExactOrEndsWith(value);
    }

    @GetMapping("/filter-by-date")
    public List<Invoice> getInvoicesByDate(
            @RequestParam("start") String startDate,
            @RequestParam(value = "end", required = false) String endDate) {
        return invoiceRepository.findByDateRange(startDate, endDate);
    }


    @GetMapping("/{id}")
    public Invoice getInvoiceById(@PathVariable int id) {
        return invoiceRepository.findById(id).orElse(null);
    }


    @GetMapping("/stats/invoice-count-daily")
    public List<Map<String, Object>> getInvoiceCountDaily() {
        List<Object[]> result = invoiceRepository.getInvoiceCountByDay();
        List<Map<String, Object>> response = new ArrayList<>();
        for (Object[] entry : result) {
            Map<String, Object> map = new HashMap<>();
            map.put("date", entry[0] != null ? entry[0].toString() : "Unknown");
            map.put("count", entry[1]);
            response.add(map);
        }
        return response;
    }

    @GetMapping("/stats/total-paid-daily")
    public List<Map<String, Object>> getTotalPaidDaily() {
        List<Object[]> raw = invoiceRepository.totalPaidPerDay();
        return raw.stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("date", row[0] != null ? row[0].toString() : "Unknown");
            map.put("totalPaid", row[1]);
            return map;
        }).collect(Collectors.toList());
    }

    @GetMapping("/stats/invoice-count-monthly")
    public List<Map<String, Object>> getInvoiceCountMonthly() {
        List<Object[]> raw = invoiceRepository.countInvoicesPerMonth();
        List<Map<String, Object>> response = new ArrayList<>();

        for (Object[] row : raw) {
            if (row[0] != null && row[1] != null && row[2] != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("year", row[0].toString());
                map.put("month", row[1].toString());
                map.put("count", row[2]);
                response.add(map);
            }
        }

        return response;
    }


    @GetMapping("/stats/total-paid-monthly")
    public List<Map<String, Object>> getTotalPaidMonthly() {
        List<Object[]> raw = invoiceRepository.totalPaidPerMonth();
        return raw.stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("year", row[0]);
            map.put("month", row[1]);
            map.put("totalPaid", row[2]);
            return map;
        }).collect(Collectors.toList());
    }

    @GetMapping("/stats/invoice-count-yearly")
    public List<Map<String, Object>> getInvoiceCountYearly() {
        List<Object[]> raw = invoiceRepository.countInvoicesPerYear();
        return raw.stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("year", row[0]);
            map.put("count", row[1]);
            return map;
        }).collect(Collectors.toList());
    }

    @GetMapping("/stats/total-paid-yearly")
    public List<Map<String, Object>> getTotalPaidYearly() {
        List<Object[]> raw = invoiceRepository.totalPaidPerYear();
        return raw.stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("year", row[0]);
            map.put("totalPaid", row[1]);
            return map;
        }).collect(Collectors.toList());
    }

}
