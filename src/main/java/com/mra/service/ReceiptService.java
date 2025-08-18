package com.mra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.mra.Util.ReciptVATParser;
import com.mra.Util.ReceiptNumberParser;
import com.mra.model.InvoiceBean;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
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
    private static final String BASE_URL = "http://172.28.5.2:22221";
//    private static final String BASE_URL = "http://41.222.103.118:22221";
    private static final String DOWNLOAD_DIR = "/home/downloads/receipt";
    private static final String PROCESSED_DIR = "/home/Processed_Files/einv/receipts";

    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void scheduledInvoiceProcessing() {
        try {
            List<Map<String, Object>> subfolders = listFiles("receipts");
            for (Map<String, Object> folder : subfolders) {
                if (!(Boolean) folder.get("is_dir")) continue;
                String dateFolder = (String) folder.get("name");
                downloadAndSubmitInvoices("receipts/" + dateFolder, dateFolder);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void downloadAndSubmitInvoices(String remoteFolder, String dateFolder) {
        try {
            List<Map<String, Object>> files = listFiles(remoteFolder);

            for (Map<String, Object> fileMap : files) {
                String fileName = (String) fileMap.get("name");
                if (fileName.startsWith("ReturnReceipt")) continue;

                File downloadedFile = downloadFile(remoteFolder, fileName, dateFolder);
                if (downloadedFile == null) continue;

                try {
                    String json;
                    if (fileName.startsWith("VATInvoice")) {
                        json = ReciptVATParser.parsePdfToInvoiceJson(downloadedFile.getAbsolutePath());
                    } else if (fileName.matches("^\\d+.*\\.pdf$")) {
                        json = ReceiptNumberParser.parsePdfAndGenerateInvoice(downloadedFile.getAbsolutePath());
                    } else {
                        continue;
                    }

                    ObjectMapper objectMapper = new ObjectMapper();
                    List<InvoiceBean> invoiceBeans = objectMapper.readValue(
                            json,
                            new com.fasterxml.jackson.core.type.TypeReference<>() {}
                    );

                    List<InvoiceBean> nonZeroVatInvoices = invoiceBeans.stream()
                            .filter(invoice -> !"0".equalsIgnoreCase(invoice.getTotalVatAmount()))
                            .toList();
                    if (nonZeroVatInvoices.isEmpty()) continue;

                    String invoiceIdentifier = sanitizeInvoiceIdentifier(invoiceBeans.get(0).getInvoiceIdentifier());
                    String processedDatedDir = PROCESSED_DIR + "/" + dateFolder;

                    if (isAlreadyProcessed(fileName, invoiceIdentifier, processedDatedDir)) {
                        System.out.println("⚠️ File already exist in processed path, skipping: " + fileName);
                        continue;
                    }

                    String result = mraService.submitInvoices(invoiceBeans);
                    System.out.println("Result from MRAService: " + result);

                    // ✅ Check if response is JSON or plain string
                    if (result.startsWith("{")) {
                        JSONObject root = new JSONObject(result);
                        String status = root.optString("status", "");

                        if ("SUCCESS".equalsIgnoreCase(status)) {
                            // ✅ NEW SUCCESS
                            String[] qrAndIrn = extractQrAndIrnFromResponse(result);
                            byte[] modifiedPdf = addQrAndIrnToPdf(downloadedFile, qrAndIrn[0], qrAndIrn[1]);
                            moveFileToProcessed(modifiedPdf, fileName, invoiceIdentifier, processedDatedDir, true);

                        } else if ("ALREADY_EXISTS".equalsIgnoreCase(status)) {
                            // ✅ ALREADY EXISTS → Extract QR & IRN from existingResponse
                            JSONArray existingResp = root.optJSONArray("existingResponse");
                            if (existingResp == null) existingResp = new JSONArray(); // fallback

                            if (existingResp.length() > 0) {
                                JSONObject invoiceObj = existingResp.getJSONObject(0);
                                String qrBase64 = invoiceObj.optString("qrCode", "");
                                String irn = invoiceObj.optString("irn", "");

                                // 🔹 Print extracted values for debugging
                                System.out.println("🔹 ALREADY_EXISTS CASE - Extracted QR Base64: " +
                                        (qrBase64.length() > 50 ? qrBase64.substring(0, 50) + "..." : qrBase64));
                                System.out.println("🔹 ALREADY_EXISTS CASE - Extracted IRN: " + irn);

                                byte[] modifiedPdf = addQrAndIrnToPdf(downloadedFile, qrBase64, irn);
                                moveFileToProcessed(modifiedPdf, fileName, invoiceIdentifier, processedDatedDir, true);
                            } else {
                                System.out.println("⚠️ No existingResponse data, moving original PDF as DONE");
                                byte[] originalPdf = Files.readAllBytes(downloadedFile.toPath());
                                moveFileToProcessed(originalPdf, fileName, invoiceIdentifier, processedDatedDir, true);
                            }

                        } else {
                            // ❌ Any other JSON status = FAILED
                            byte[] failedPdf = addFailedStampToPdf(downloadedFile);
                            moveFileToProcessed(failedPdf, fileName, invoiceIdentifier, processedDatedDir, false);
                        }

                    } else if (result.contains("Failed File Validation errors:")) {
//                         ✅ Plain text error response
                        System.out.println("❌ Validation failed, moving file as FAILED: " + fileName);
                        byte[] failedPdf = addFailedStampToPdf(downloadedFile);
                        moveFileToProcessed(failedPdf, fileName, invoiceIdentifier, processedDatedDir, false);

                    } else {
                        System.out.println("❌ Unknown response, moving file as FAILED: " + fileName);
                        byte[] failedPdf = addFailedStampToPdf(downloadedFile);
                        moveFileToProcessed(failedPdf, fileName, invoiceIdentifier, processedDatedDir, false);
                    }

                } catch (Exception e) {
                    System.err.println("❌ Error processing file: " + fileName);
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error in downloadAndSubmitInvoices");
            e.printStackTrace();
        }
    }


    private boolean isAlreadyProcessed(String originalFileName, String invoiceIdentifier, String processedDirPath) {
        File processedDir = new File(processedDirPath);
        if (!processedDir.exists()) return false;

        String baseName = originalFileName.replace(".pdf", "") + "_" + invoiceIdentifier + ".pdf";
        File doneFile = new File(processedDir, "DONE_" + baseName);
        File failedFile = new File(processedDir, "FAILED_" + baseName);
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

    private File downloadFile(String folder, String fileName, String dateFolder) {
        try {
            String decodedFileName = URLDecoder.decode(fileName, "UTF-8");
            String processedDatedDir = PROCESSED_DIR + "/" + dateFolder;

            File processedDir = new File(processedDatedDir);
            if (processedDir.exists()) {
                File[] existing = processedDir.listFiles((dir, name) -> name.contains(fileName.replace(".pdf", "")));
                if (existing != null && existing.length > 0) {
                    System.out.println("⚠️ File already exist in processed path, skipping: " + fileName);
                    return null;
                }
            }

            String saveDir = DOWNLOAD_DIR + "/" + dateFolder;
            File dir = new File(saveDir);
            if (!dir.exists()) dir.mkdirs();

            File outputFile = new File(dir, fileName);
            if (outputFile.exists()) return outputFile;

            String fileUrl = BASE_URL + "/download/" + folder + "/" + decodedFileName;
            HttpURLConnection conn = (HttpURLConnection) new URL(fileUrl).openConnection();
            conn.setRequestMethod("GET");

            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return outputFile;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void moveFileToProcessed(byte[] pdfBytes, String originalFileName, String invoiceIdentifier, String processedDirPath, boolean isSuccess) {
        try {
            File processedDir = new File(processedDirPath);
            if (!processedDir.exists()) processedDir.mkdirs();

            String prefix = isSuccess ? "DONE_" : "FAILED_";
            String newFileName = prefix + originalFileName.replace(".pdf", "") + "_" + invoiceIdentifier + ".pdf";

            File newFile = new File(processedDir, newFileName);
            if (newFile.exists()) {
                System.out.println("⚠️ Skipping save, file already exists in processed path: " + newFile.getAbsolutePath());
                return;
            }

            try (FileOutputStream fos = new FileOutputStream(newFile)) {
                fos.write(pdfBytes);
            }

            System.out.println("✅ File processed: " + newFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String[] extractQrAndIrnFromResponse(String resultJson) {
        JSONObject root = new JSONObject(resultJson);
        if (!root.has("fiscalisedInvoices")) return new String[]{"", ""};

        JSONArray fiscalisedInvoices = root.getJSONArray("fiscalisedInvoices");
        if (fiscalisedInvoices.length() > 0) {
            JSONObject invoice = fiscalisedInvoices.getJSONObject(0);
            return new String[]{invoice.optString("qrCode", ""), invoice.optString("irn", "")};
        }
        return new String[]{"", ""};
    }

    private String sanitizeInvoiceIdentifier(String invoiceIdentifier) {
        if (invoiceIdentifier == null) return "UNKNOWN";
        return invoiceIdentifier.replace("/", "-").replace("\\", "-");
    }

    public byte[] addQrAndIrnToPdf(File inputPdf, String qrBase64, String irnText) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(inputPdf), new PdfWriter(outputStream));
        Document document = new Document(pdfDoc);

        PdfPage page = pdfDoc.getFirstPage();

        // 1️⃣ Add QR code if exists
        if (qrBase64 != null && !qrBase64.isEmpty()) {
            if (qrBase64.contains(",")) qrBase64 = qrBase64.split(",")[1];
            qrBase64 = qrBase64.replaceAll("\\s+", "");
            byte[] imageBytes = Base64.getDecoder().decode(qrBase64);
            ImageData imageData = ImageDataFactory.create(imageBytes);
            Image image = new Image(imageData).setFixedPosition(36f, page.getPageSize().getHeight() - 50f).scaleToFit(50, 50);
            document.add(image);
        }

        // 2️⃣ Add IRN text
        PdfCanvas canvas = new PdfCanvas(page);
        canvas.beginText()
                .setFontAndSize(PdfFontFactory.createFont(), 10)
                .moveText(36f, page.getPageSize().getHeight() - 80f)
                .showText("IRN: " + (irnText != null ? irnText : "N/A"))
                .endText();

        document.close();
        return outputStream.toByteArray();
    }

    public byte[] addFailedStampToPdf(File inputPdf) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(inputPdf), new PdfWriter(outputStream));
        PdfPage page = pdfDoc.getFirstPage();
        PdfCanvas canvas = new PdfCanvas(page);

        canvas.beginText()
                .setFontAndSize(PdfFontFactory.createFont(), 30)
                .setColor(ColorConstants.RED, true)
                .moveText(36f, page.getPageSize().getHeight() - 50f)
                .showText("FAILED")
                .endText();

        pdfDoc.close();
        return outputStream.toByteArray();
    }


}
