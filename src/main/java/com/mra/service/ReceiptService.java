package com.mra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mra.Util.ReciptVATParser;
import com.mra.Util.ReceiptNumberParser;
import com.mra.model.InvoiceBean;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final MRAService mraService;

//    private static final String BASE_URL = "http://41.222.103.118:22221";
    private static final String BASE_URL = "http://172.28.5.2:22221";
    private static final String DOWNLOAD_DIR = "/home/downloads/receipt";
    private static final String PROCESSED_DIR = "/home/Processed_Files/einv/receipts";

    @Scheduled(fixedRate = 60 * 60 * 1000) // Every hour
    public void scheduledInvoiceProcessing() {
        System.out.println("⏳ Scheduled job started...");
        downloadAndSubmitInvoices("receipts");
    }

    public void downloadAndSubmitInvoices(String folder) {
        try {
            List<Map<String, Object>> files = listFiles(folder);
            System.out.println("📄 Total files to process: " + files.size());

            for (Map<String, Object> fileMap : files) {
                String fileName = (String) fileMap.get("name");

                // ⛔ Skip ReturnReceipt files
                if (fileName.startsWith("ReturnReceipt")) {
                    System.out.println("⏭️ Skipping ReturnReceipt file: " + fileName);
                    continue;
                }

                // ✅ Download the file
                System.out.println("📥 Downloading: " + fileName);
                File downloadedFile = downloadFile(folder, fileName, DOWNLOAD_DIR);
                if (downloadedFile == null) continue;

                try {
                    String json;

                    // ✅ Decide parser based on filename prefix
                    if (fileName.startsWith("VATInvoice")) {
                        json = ReciptVATParser.parsePdfToInvoiceJson(downloadedFile.getAbsolutePath());
                    } else if (fileName.matches("^\\d+.*\\.pdf$")) {
                        json = ReceiptNumberParser.parsePdfToInvoiceJson(downloadedFile.getAbsolutePath());
                    } else {
                        System.out.println("⏭️ Unknown format. Skipping file: " + fileName);
                        continue;
                    }

                    ObjectMapper objectMapper = new ObjectMapper();
                    List<InvoiceBean> invoiceBeans = objectMapper.readValue(
                            json,
                            new com.fasterxml.jackson.core.type.TypeReference<List<InvoiceBean>>() {}
                    );

                    String result = mraService.submitInvoices(invoiceBeans);
                    if (result != null && result.contains("SUCCESS")) {
                        System.out.println("✅ Successfully submitted:  Recipt Service " + fileName);
                        moveFileToProcessed(downloadedFile, "DONE_" + fileName);
                    } else {
                        System.err.println("❌ Submission failed for: " + fileName);
                    }

                } catch (Exception e) {
                    System.err.println("⚠️ Error parsing/submitting file: " + fileName);
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Critical error in invoice processing.");
            e.printStackTrace();
        }
    }

    private List<Map<String, Object>> listFiles(String folder) throws IOException {
        String urlString = BASE_URL + "/list/" + folder;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");

        try (InputStream inputStream = conn.getInputStream()) {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(inputStream, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        }
    }

    private File downloadFile(String folder, String fileName, String saveDir) {
        try {
            String decodedFileName = URLDecoder.decode(fileName, "UTF-8");
            String fileUrl = BASE_URL + "/download/" + folder + "/" + decodedFileName;

            File dir = new File(saveDir);
            if (!dir.exists()) dir.mkdirs();

            File outputFile = new File(dir, fileName);
            HttpURLConnection conn = (HttpURLConnection) new URL(fileUrl).openConnection();
            conn.setRequestMethod("GET");

            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return outputFile;
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to download file: " + fileName);
            e.printStackTrace();
            return null;
        }
    }

    private void moveFileToProcessed(File original, String newFileName) {
        try {
            File processedDir = new File(PROCESSED_DIR);
            if (!processedDir.exists()) processedDir.mkdirs();

            File newFile = new File(processedDir, newFileName);
            Files.move(original.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("📁 Moved to processed: " + newFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("⚠️ Failed to move file: " + original.getName());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ReceiptService service = new ReceiptService(null); // Inject real or mock service
        service.downloadAndSubmitInvoices("receipts");
    }
}
