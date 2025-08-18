package com.mra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.mra.Util.InterConnectParser;
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
public class InterConnectBills {

    private final MRAService mraService;
//    private final InventoryFileService inventoryFileService;

//    private static final String BASE_URL = "http://41.222.103.118:22221";
       private static final String BASE_URL = "http://172.28.5.2:22221";
    private static final String DOWNLOAD_DIR = "/home/downloads/interconnect";
    private static final String PROCESSED_DIR = "/home/Processed_Files/einv/interconnect_bills";

//    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void scheduledInvoiceProcessing() {
        System.out.println("⏳ Scheduled job started...");
        downloadAndSubmitInvoices("interconnect_bills");
    }

    public void downloadAndSubmitInvoices(String folder) {
        try {
            List<Map<String, Object>> files = listFiles(folder);
            System.out.println("📄 Files Found: " + files.size());

            for (Map<String, Object> file : files) {
                String fileName = (String) file.get("name");
                System.out.println("\n📅 Checking: " + fileName);

                File downloaded = downloadFile(folder, fileName, DOWNLOAD_DIR);
                if (downloaded == null) {
                    System.err.println("❌ Could not download: " + fileName);
                    continue;
                }

                // 🔹 Parse invoice JSON
                String json = InterConnectParser.parseToJson(downloaded.getAbsolutePath());
                ObjectMapper objectMapper = new ObjectMapper();
                List<InvoiceBean> invoiceBeans = objectMapper.readValue(
                        json,
                        new com.fasterxml.jackson.core.type.TypeReference<>() {}
                );

                // 🔹 Skip invoices with VAT = 0
                List<InvoiceBean> nonZeroVatInvoices = invoiceBeans.stream()
                        .filter(invoice -> !"0".equalsIgnoreCase(invoice.getTotalVatAmount()))
                        .toList();
                if (nonZeroVatInvoices.isEmpty()) {
                    System.out.println("⚠️ All invoices have VAT = 0. Skipping file: " + fileName);
                    continue;
                }

                // 🔹 Generate invoiceIdentifier (safe for filenames)
                String invoiceIdentifier = invoiceBeans.get(0).getInvoiceIdentifier()
                        .replace("/", "-")
                        .replace("\\", "-");

                // 🔹 Prepare processed file names (DONE & FAILED)
                String doneFileName = "DONE_" + fileName.replace(".pdf", "") + "_" + invoiceIdentifier + ".pdf";
                String failedFileName = "FAILED_" + fileName.replace(".pdf", "") + "_" + invoiceIdentifier + ".pdf";

                File processedDone = new File(PROCESSED_DIR, doneFileName);
                File processedFailed = new File(PROCESSED_DIR, failedFileName);

                // ✅ Skip if already processed (either DONE or FAILED exists)
                if (processedDone.exists() || processedFailed.exists()) {
                    System.out.println("⏭️ Already processed earlier (DONE/FAILED). Skipping: " + fileName);
                    // delete temp downloaded file
                    if (downloaded.exists()) {
                        downloaded.delete();
                    }
                    continue;
                }

                // 🔹 Submit to MRA
                String result;
                boolean isSuccess;
                try {
                    result = mraService.submitInvoices(invoiceBeans);
                    isSuccess = result != null && result.contains("SUCCESS");
                } catch (Exception e) {
                    System.err.println("❌ Exception while submitting to MRA: " + e.getMessage());
                    isSuccess = false;
                    result = null;
                }

                if (isSuccess) {
                    String[] qrAndIrn = extractQrAndIrnFromResponse(result);
                    String qrBase64 = qrAndIrn[0];
                    String irn = qrAndIrn[1];

                    byte[] modifiedPdf = addQrAndIrnToPdf(downloaded, qrBase64, irn);
//                    inventoryFileService.savePdf(invoiceIdentifier, modifiedPdf);

                    // ✅ Save modified file instead of moving original
                    saveProcessedFile("DONE", fileName, invoiceIdentifier, modifiedPdf);

                    System.out.println("✅ Submitted Successfully. Saved modified file.");

                    // delete temp file
                    if (downloaded.exists()) {
                        downloaded.delete();
                    }
                } else {
                    System.err.println("❌ Submission failed. Moving file.");
                    moveFileToProcessed(downloaded, "FAILED", fileName, invoiceIdentifier);
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

    // ✅ Save processed PDF with QR + IRN
    private void saveProcessedFile(String prefix, String fileName, String invoiceIdentifier, byte[] pdfBytes) {
        try {
            File processedDir = new File(PROCESSED_DIR);
            if (!processedDir.exists()) processedDir.mkdirs();

            String newFileName = prefix + "_" + fileName.replace(".pdf", "") + "_" + invoiceIdentifier + ".pdf";
            File newFile = new File(processedDir, newFileName);

            try (FileOutputStream fos = new FileOutputStream(newFile)) {
                fos.write(pdfBytes);
            }

            System.out.println("📂 Saved processed PDF with QR + IRN: " + newFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("⚠️ Failed to save processed PDF.");
            e.printStackTrace();
        }
    }

    // ❌ On failure still move original file
    private void moveFileToProcessed(File original, String prefix, String fileName, String invoiceIdentifier) {
        try {
            File processedDir = new File(PROCESSED_DIR);
            if (!processedDir.exists()) processedDir.mkdirs();

            String newFileName = prefix + "_" + fileName.replace(".pdf", "") + "_" + invoiceIdentifier + ".pdf";
            File newFile = new File(processedDir, newFileName);

            if (newFile.exists()) {
                System.out.println("⏭️ File already exists in processed folder, skipping: " + newFile.getAbsolutePath());
                if (original.exists()) {
                    original.delete();
                }
                return;
            }

            Files.move(original.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("📂 Moved to: " + newFile.getAbsolutePath());
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
            return new String[]{invoice.getString("qrCode"), invoice.getString("irn")};
        }
        return new String[]{"", ""};
    }

    public byte[] addQrAndIrnToPdf(File inputPdf, String qrBase64, String irnText) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(inputPdf), new PdfWriter(outputStream));
        Document document = new Document(pdfDoc);
        PdfPage page = pdfDoc.getLastPage();
        PdfCanvas canvas = new PdfCanvas(page);

        // Decode QR
        if (qrBase64.contains(",")) {
            qrBase64 = qrBase64.split(",")[1];
        }
        byte[] imageBytes = Base64.getDecoder().decode(qrBase64);
        ImageData imageData = ImageDataFactory.create(imageBytes);

        float marginRight = 100f;
        float marginBottom = 10f;
        float qrWidth = 70f;
        float qrHeight = 70f;

        float pageWidth = page.getPageSize().getWidth();
        float qrX = pageWidth - marginRight - qrWidth - 20;
        float qrY = marginBottom + 20f;

        Image qrImage = new Image(imageData)
                .scaleToFit(qrWidth, qrHeight)
                .setFixedPosition(qrX, qrY);
        document.add(qrImage);

        canvas.beginText()
                .setFontAndSize(PdfFontFactory.createFont(), 9)
                .moveText(qrX, qrY - 10f)
                .showText(irnText)
                .endText();

        document.close();
        return outputStream.toByteArray();
    }
}
