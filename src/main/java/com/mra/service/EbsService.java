package com.mra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mra.Util.EBSPdfParser;
import com.mra.model.InvoiceBean;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EbsService {

    private final MRAService mraService;
//    private static final String BASE_URL = "http://41.222.103.118:22221";
    private static final String BASE_URL = "http://172.28.5.2:22221";
    private static final String DOWNLOAD_DIR = "/home/downloads/ebs";
    private static final String PROCESSED_DIR = "/home/Processed_Files/einv/ebs_bills";

    // 🔁 This will run every 5 minutes
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void scheduledInvoiceProcessing() {
        System.out.println("⏳ Scheduled job started...");
        downloadAndSubmitInvoices("ebs_bills");
    }

    public void downloadAndSubmitInvoices(String folder) {
        try {
            List<Map<String, Object>> files = listFiles(folder);
            System.out.println("📄 Files Found: " + files.size());

            for (Map<String, Object> file : files) {
                String fileName = (String) file.get("name");
                System.out.println("\n📥 Downloading: " + fileName);
                File downloaded = downloadFile(folder, fileName, DOWNLOAD_DIR);

                if (downloaded != null) {
                    String json = EBSPdfParser.parseToJson(downloaded.getAbsolutePath());
                    System.out.println("📤 Submitting invoice for: " + fileName);
                    System.out.println(json + "  ---- json Invoice ----");

                    ObjectMapper objectMapper = new ObjectMapper();
                    InvoiceBean invoiceBean = objectMapper.readValue(json, InvoiceBean.class);

                    String result = mraService.submitInvoices(List.of(invoiceBean));
                    boolean isSuccess = result != null && result.contains("SUCCESS");

                    if (isSuccess) {
                        System.out.println("✅ Submitted Successfully EBS File. Moving file. ");
                        moveFileToProcessed(downloaded, fileName);
                    } else {
                        System.err.println("❌ Submission failed for: " + fileName);
                    }
                }
            }
        } catch (Exception e) {
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
            System.err.println("❌ Failed to download: " + fileName);
            e.printStackTrace();
            return null;
        }
    }

    private void moveFileToProcessed(File original, String originalFileName) {
        try {
            File processedDir = new File(PROCESSED_DIR);
            if (!processedDir.exists()) processedDir.mkdirs();

            String newFileName = "DONE_" + originalFileName;
            File newFile = new File(processedDir, newFileName);
            Files.move(original.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("📁 Moved to: " + newFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("⚠️ Failed to move file to processed directory.");
            e.printStackTrace();
        }
    }
}
