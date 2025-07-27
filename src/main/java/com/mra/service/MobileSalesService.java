package com.mra.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mra.Util.MobileSalePdfDataExtractor;
import com.mra.Util.PdfDataExtractor;
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
public class MobileSalesService {
    private final MRAService mraService;
    private static final String SFTP_HOST = "10.60.1.158";
    private static final int SFTP_PORT = 22;
    private static final String SFTP_USER = "einv";
    private static final String SFTP_PASSWORD = "einv#123$";
    private static final List<String> REMOTE_FOLDERS = Arrays.asList(
            "/home/einv/receipts"
    );
    public List<InvoiceBean> extractAndConvertToInvoiceBean(MultipartFile file) throws Exception {
        JsonNode jsonNode = MobileSalePdfDataExtractor.convertPdfToJson(file);
        ObjectMapper mapper = new ObjectMapper();
        InvoiceBean invoice = mapper.treeToValue(jsonNode, InvoiceBean.class);
        return Arrays.asList(invoice);
    }

    public List<InvoiceBean> extractAndConvertToInvoiceBean(File file) throws Exception {

        JsonNode jsonNode = MobileSalePdfDataExtractor.convertPdfToJson(file);
        ObjectMapper mapper = new ObjectMapper();
        InvoiceBean invoice = mapper.treeToValue(jsonNode, InvoiceBean.class);
        return Arrays.asList(invoice);
    }
    @Scheduled(fixedRate = 3600000)
    public void processInvoicesHourly() {
        File folder = new File("/home/inovice/Mobile_sale/");
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
