package com.mra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.mra.Util.PostpaidParser;
import com.mra.model.InvoiceBean;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostpaidService {

    private final MRAService mraService;
//    private static final String BASE_URL = "http://41.222.103.118:22221";
     private static final String BASE_URL = "http://172.28.5.2:22221";
    private static final String DOWNLOAD_DIR = "/home/downloads/bills";
    private static final String PROCESSED_DIR = "/home/Processed_Files/einv/bills";

//    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void scheduledInvoiceProcessing() {
        System.out.println("⏳ Scheduled job started...");
        downloadAndSubmitInvoices("bills");
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
                    String json = PostpaidParser.parsePdfToInvoiceJson(downloaded.getAbsolutePath());

                    ObjectMapper objectMapper = new ObjectMapper();
                    List<InvoiceBean> invoiceBeans = objectMapper.readValue(
                            json,
                            new com.fasterxml.jackson.core.type.TypeReference<List<InvoiceBean>>() {}
                    );
                    List<InvoiceBean> nonZeroVatInvoices = invoiceBeans.stream()
                            .filter(invoice -> !"0".equalsIgnoreCase(invoice.getTotalVatAmount()))
                            .toList();

                    if (nonZeroVatInvoices.isEmpty()) {
                        System.out.println("⚠️ All invoices have VAT = 0. Skipping file: " + fileName);
                        continue;
                    }

                    String invoiceIdentifier = sanitizeInvoiceIdentifier(invoiceBeans.get(0).getInvoiceIdentifier());

                    // ✅ Skip immediately if already processed
                    if (isAlreadyProcessed(fileName, invoiceIdentifier)) {
                        System.out.println("⚠️ Skipping file (already processed): " + fileName);
                        downloaded.delete();
                        continue;
                    }

                    String result = null;
                    boolean isSuccess = false;

                    try {
                        result = mraService.submitInvoices(invoiceBeans);
                        isSuccess = result != null && result.contains("SUCCESS");
                    } catch (Exception ex) {
                        System.err.println("❌ Exception while submitting invoices: " + ex.getMessage());
                    }

                    if (isSuccess) {
                        System.out.println("✅ Submitted Successfully. Embedding QR & IRN...");

                        String[] qrAndIrn = extractQrAndIrnFromResponse(result);
                        String qrBase64 = qrAndIrn[0];
                        String irn = qrAndIrn[1];

                        byte[] modifiedPdf = addQrAndIrnToPdf(downloaded, qrBase64, irn);

                        try (FileOutputStream fos = new FileOutputStream(downloaded)) {
                            fos.write(modifiedPdf);
                        }

                        moveFileToProcessed(downloaded, fileName, invoiceIdentifier, true);
                    } else {
                        System.err.println("❌ Submission failed for: " + fileName);
                        moveFileToProcessed(downloaded, fileName, invoiceIdentifier, false);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ✅ Checks if DONE_ or FAILED_ file already exists in processed folder
     */
    private boolean isAlreadyProcessed(String originalFileName, String invoiceIdentifier) {
        String baseName = originalFileName.replaceFirst("(?i)\\.pdf$", "");
        File processedDir = new File(PROCESSED_DIR);

        File doneFile = new File(processedDir, "DONE_" + baseName + "_" + invoiceIdentifier + ".pdf");
        File failedFile = new File(processedDir, "FAILED_" + baseName + "_" + invoiceIdentifier + ".pdf");

        return doneFile.exists() || failedFile.exists();
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
            String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            String fileUrl = BASE_URL + "/download/" + folder + "/" + encodedFileName;

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

    private void moveFileToProcessed(File original, String originalFileName, String invoiceIdentifier, boolean success) {
        try {
            File processedDir = new File(PROCESSED_DIR);
            if (!processedDir.exists()) processedDir.mkdirs();

            String prefix = success ? "DONE_" : "FAILED_";

            // strip .pdf from original name
            String baseName = originalFileName.replaceFirst("(?i)\\.pdf$", "");
            String newFileName = prefix + baseName + "_" + invoiceIdentifier + ".pdf";

            // ✅ Skip if file already exists in DONE or FAILED
            File doneFile = new File(processedDir, "DONE_" + baseName + "_" + invoiceIdentifier + ".pdf");
            File failedFile = new File(processedDir, "FAILED_" + baseName + "_" + invoiceIdentifier + ".pdf");
            if (doneFile.exists() || failedFile.exists()) {
                System.out.println("⚠️ Skipping file, already exists in processed: " + newFileName);
                original.delete();
                return;
            }

            File newFile = new File(processedDir, newFileName);
            Files.move(original.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("📁 Moved to: " + newFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("⚠️ Failed to move file to processed directory.");
            e.printStackTrace();
        }
    }

    private String[] extractQrAndIrnFromResponse(String resultJson) {
        JSONObject root = new JSONObject(resultJson);
        JSONArray fiscalisedInvoices = root.getJSONArray("fiscalisedInvoices");
        if (fiscalisedInvoices.length() > 0) {
            JSONObject invoice = fiscalisedInvoices.getJSONObject(0);
            return new String[]{
                    invoice.getString("qrCode"),
                    invoice.getString("irn")
            };
        }
        return new String[]{"", ""};
    }

    public byte[] addQrAndIrnToPdf(File inputPdf, String qrBase64, String irnText) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(inputPdf), new PdfWriter(outputStream));
        Document document = new Document(pdfDoc);
        PdfPage page = pdfDoc.getFirstPage();
        PdfCanvas canvas = new PdfCanvas(page);

        float qrX = 35;
        float qrY = 750;
        float irnX = 50;
        float irnY = 730;

        // Decode QR
        if (qrBase64.contains(",")) {
            qrBase64 = qrBase64.split(",")[1];
        }
        byte[] imageBytes = Base64.getDecoder().decode(qrBase64);
        ImageData imageData = ImageDataFactory.create(imageBytes);
        Image image = new Image(imageData)
                .setFixedPosition(qrX, qrY)
                .scaleToFit(100, 100);
        document.add(image);

        // IRN Text
        canvas.beginText()
                .setFontAndSize(PdfFontFactory.createFont(), 10)
                .moveText(irnX, irnY)
                .showText(irnText)
                .endText();

        document.close();
        return outputStream.toByteArray();
    }

    private String sanitizeInvoiceIdentifier(String identifier) {
        if (identifier == null) return "UNKNOWN";
        return identifier.replace("/", "-").replace("\\", "-");
    }
}
