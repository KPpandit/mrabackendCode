package com.mra.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mra.Util.SalesGoodPdfExtractor;
import com.mra.model.InvoiceBean;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesGoodService {
    private final MRAService mraService;

    public List<InvoiceBean> extractAndConvertToInvoiceBean(MultipartFile file) throws Exception {
        JsonNode jsonNode = SalesGoodPdfExtractor.convertPdfToJson( file);
        ObjectMapper mapper = new ObjectMapper();
        InvoiceBean invoice = mapper.treeToValue(jsonNode, InvoiceBean.class);
        return Arrays.asList(invoice);
    }

    public List<InvoiceBean> extractAndConvertToInvoiceBean(File file) throws Exception {

        JsonNode jsonNode = SalesGoodPdfExtractor.convertPdfToJson(file);
        ObjectMapper mapper = new ObjectMapper();
        InvoiceBean invoice = mapper.treeToValue(jsonNode, InvoiceBean.class);
        return Arrays.asList(invoice);
    }
    public void processInvoicesHourly() {
        File folder = new File("/home/inovice/Sales good/");
//        C:\Users\Krishna Purohit\Downloads\invoice\invoice\Mobile_sale
//        File folder = new File("C:\\Users\\Krishna Purohit\\Downloads\\invoice\\invoice\\Mobile_sale\\");

        System.out.println("Checking folder: " + folder.getAbsolutePath());
        System.out.println("Exists: " + folder.exists());
        System.out.println("Is directory: " + folder.isDirectory());
        System.out.println("Can read: " + folder.canRead());

        // Only process PDF files that do NOT start with "checked_"
        File[] files = folder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".pdf") && !name.toLowerCase().startsWith("checked_")
        );

        if (files == null || files.length == 0) {
            System.out.println("No files found to process. for Mobile sales PDF Scanning");
            return;
        }

        for (File file : files) {
            try {
                System.out.println("Processing file: " + file.getName());

                List<InvoiceBean> invoiceBeans = extractAndConvertToInvoiceBean(file);
                String response = mraService.submitInvoices(invoiceBeans);
                System.out.println("Invoice transmitted. Response: " + response);

                File renamed = new File(file.getParent(), "checked_" + file.getName());
                Files.move(file.toPath(), renamed.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Renamed file to: " + renamed.getName());

            } catch (Exception e) {
                System.err.println("Failed to process file: " + file.getName());
                e.printStackTrace();
            }
        }
    }

}
