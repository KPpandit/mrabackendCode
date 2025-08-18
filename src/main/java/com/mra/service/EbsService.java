package com.mra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.mra.Util.EBSPdfParser;
import com.mra.model.InvoiceBean;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EbsService {

    private final MRAService mraService;
//    private static final String BASE_URL = "http://41.222.103.118:22221";
     private static final String BASE_URL = "http://172.28.5.2:22221";
    private static final String DOWNLOAD_DIR = "/home/downloads/ebs";
    private static final String PROCESSED_DIR = "/home/Processed_Files/einv/ebs_bills";

    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void scheduledInvoiceProcessing() {
        System.out.println("⏳ Scheduled job started... EBS SERVICE");
        downloadAndSubmitInvoices("ebs_bills");
    }

    private void downloadAndSubmitInvoices(String folder) {
        try {
            List<Map<String, Object>> files = listFiles(folder);
            System.out.println("📄 Files Found: " + files.size());

            for (Map<String, Object> file : files) {
                String fileName = (String) file.get("name");
                System.out.println("\n📥 Downloading: " + fileName);
                File downloaded = downloadFile(folder, fileName, DOWNLOAD_DIR);

                if (downloaded != null) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    InvoiceBean invoiceBean = objectMapper.readValue(
                            EBSPdfParser.parseToJson(downloaded.getAbsolutePath()),
                            InvoiceBean.class
                    );

                    // ✅ Sanitize invoiceIdentifier
                    String invoiceIdentifier = invoiceBean.getInvoiceIdentifier()
                            .replace("/", "-")
                            .replace("\\", "-");

                    // ✅ Skip if processed file already exists
                    if (isAlreadyProcessed(fileName, invoiceIdentifier)) {
                        System.out.println("⚠️ Already processed, skipping: " + fileName);
                        continue;
                    }

                    String result = mraService.submitInvoices(List.of(invoiceBean));
                    JSONObject root = new JSONObject(result);
                    String status = root.optString("status");

                    if ("SUCCESS".equalsIgnoreCase(status)) {
                        String[] qrAndIrn = extractQrAndIrnFromResponse(root);
                        byte[] modifiedPdf = addQrAndIrnToPdf(downloaded, qrAndIrn[0], qrAndIrn[1]);
                        moveFileToProcessed(modifiedPdf, "DONE_" + fileName, invoiceIdentifier);
                        System.out.println("✅ Submitted and saved: " + fileName);

                    } else if ("ALREADY_EXISTS".equalsIgnoreCase(status)) {
                        try {
                            String[] qrAndIrn = extractQrAndIrnFromResponse(root);
                            if (!qrAndIrn[0].isEmpty() && !qrAndIrn[1].isEmpty()) {
                                byte[] modifiedPdf = addQrAndIrnToPdf(downloaded, qrAndIrn[0], qrAndIrn[1]);
                                moveFileToProcessed(modifiedPdf, "DONE_" + fileName, invoiceIdentifier);
                                System.out.println("✅ Already existed, saved with QR+IRN: " + fileName);
                            } else {
                                System.err.println("⚠️ Already submitted but no QR/IRN found: " + fileName);
                                byte[] failedPdf = addFailedStampToPdf(downloaded);
                                moveFileToProcessed(failedPdf, "FAILED_" + fileName, invoiceIdentifier);
                            }
                        } catch (Exception e) {
                            System.err.println("⚠️ Error handling existingResponse for: " + fileName);
                            e.printStackTrace();
                        }

                    } else if ("FAILED".equalsIgnoreCase(status)
                            || result.contains("Failed File Validation errors:")) {
                        byte[] failedPdf = addFailedStampToPdf(downloaded);
                        moveFileToProcessed(failedPdf, "FAILED_" + fileName, invoiceIdentifier);
                        System.out.println("❌ Failed validation, marked FAILED: " + fileName);

                    } else {
                        System.err.println("❌ Unknown submission response for: " + fileName);
                        byte[] failedPdf = addFailedStampToPdf(downloaded);
                        moveFileToProcessed(failedPdf, "FAILED_" + fileName, invoiceIdentifier);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String[] extractQrAndIrnFromResponse(JSONObject root) {
        JSONArray invoicesArr = null;

        if (root.has("fiscalisedInvoices")) {
            invoicesArr = root.optJSONArray("fiscalisedInvoices");
        } else if (root.has("existingResponse")) {
            invoicesArr = root.optJSONArray("existingResponse");
        }

        if (invoicesArr != null && invoicesArr.length() > 0) {
            JSONObject invoice = invoicesArr.getJSONObject(0);
            String qr = invoice.optString("qrCode", "");
            String irn = invoice.optString("irn", "");
            return new String[]{qr, irn};
        }

        return new String[]{"", ""};
    }


    private List<Map<String, Object>> listFiles(String folder) throws IOException {
        String urlString = BASE_URL + "/list/" + folder;
        System.out.println(urlString + "  ----  ");
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
            System.out.println(fileUrl + " downloaded");
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

    private boolean isAlreadyProcessed(String fileName, String invoiceIdentifier) {
        File processedDir = new File(PROCESSED_DIR);
        if (!processedDir.exists()) return false;

        String doneFile = "DONE_" + fileName.replace(".pdf", "") + "_" + invoiceIdentifier + ".pdf";
        String failedFile = "FAILED_" + fileName.replace(".pdf", "") + "_" + invoiceIdentifier + ".pdf";

        File done = new File(processedDir, doneFile);
        File failed = new File(processedDir, failedFile);

        return done.exists() || failed.exists();
    }

    private void moveFileToProcessed(byte[] pdfBytes, String prefixFileName, String invoiceIdentifier) {
        try {
            File processedDir = new File(PROCESSED_DIR);
            if (!processedDir.exists()) processedDir.mkdirs();

            String newFileName = prefixFileName.replace(".pdf", "") + "_" + invoiceIdentifier + ".pdf";
            File newFile = new File(processedDir, newFileName);

            try (FileOutputStream fos = new FileOutputStream(newFile)) {
                fos.write(pdfBytes);
            }

            System.out.println("📁 Saved processed file: " + newFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("⚠️ Failed to save processed file: " + prefixFileName);
            e.printStackTrace();
        }
    }

    private String[] extractQrAndIrnFromResponse(String resultJson) {
        JSONObject root = new JSONObject(resultJson);
        JSONArray fiscalisedInvoices = root.getJSONArray("fiscalisedInvoices");
        if (fiscalisedInvoices.length() > 0) {
            JSONObject invoice = fiscalisedInvoices.getJSONObject(0);
            return new String[]{invoice.getString("qrCode"), invoice.getString("irn")};
        }
        return new String[]{"", ""};
    }

    public byte[] addQrAndIrnToPdf(File inputPdf, String qrBase64, String irnText) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(inputPdf), new PdfWriter(outputStream));
        Document document = new Document(pdfDoc);

        PdfPage page = pdfDoc.getFirstPage();
        PdfCanvas canvas = new PdfCanvas(page);

        // Decode QR
        if (qrBase64.contains(",")) {
            qrBase64 = qrBase64.split(",")[1];
        }
        byte[] imageBytes = Base64.getDecoder().decode(qrBase64);
        ImageData imageData = ImageDataFactory.create(imageBytes);

        // QR position
        float qrX = 42f;
        float qrY = 180f;
        float qrWidth = 70f;
        float qrHeight = 80f;

        Image image = new Image(imageData)
                .scaleToFit(qrWidth, qrHeight)
                .setFixedPosition(qrX, qrY);
        document.add(image);

        // IRN below QR
        canvas.beginText()
                .setFontAndSize(PdfFontFactory.createFont(), 9)
                .moveText(qrX, qrY - 12)
                .showText("IRN: " + irnText)
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
                .moveText(36f, page.getPageSize().getHeight() - 50f)
                .showText("FAILED")
                .endText();

        pdfDoc.close();
        return outputStream.toByteArray();
    }
}
