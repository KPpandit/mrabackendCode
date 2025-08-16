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

    private static final String BASE_URL = "http://41.222.103.118:22221";
//    private static final String BASE_URL = "http://172.28.5.2:22221";
    private static final String DOWNLOAD_DIR = "/home/downloads/receipt";
    private static final String PROCESSED_DIR = "/home/Processed_Files/einv/receipts";

//    @Scheduled(fixedRate = 60 * 60 * 1000)
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

                String datedDownloadDir = DOWNLOAD_DIR + "/" + dateFolder;
                File downloadedFile = downloadFile(remoteFolder, fileName, datedDownloadDir);
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

                    String invoiceIdentifier = invoiceBeans.get(0).getInvoiceIdentifier();
                    String processedDatedDir = PROCESSED_DIR + "/" + dateFolder;

                    String result = mraService.submitInvoices(invoiceBeans);

                    if (result != null && result.contains("SUCCESS")) {
                        // ✅ SUCCESS
                        String[] qrAndIrn = extractQrAndIrnFromResponse(result);
                        String qrBase64 = qrAndIrn[0];
                        String irn = qrAndIrn[1];

                        byte[] modifiedPdf = addQrAndIrnToPdf(downloadedFile, qrBase64, irn);

                        moveFileToProcessed(modifiedPdf, fileName, sanitizeInvoiceIdentifier(invoiceIdentifier), processedDatedDir, true);

                    } else {
                        // ❌ FAILED
                        byte[] failedPdf = addFailedStampToPdf(downloadedFile);
                        moveFileToProcessed(failedPdf, fileName, sanitizeInvoiceIdentifier(invoiceIdentifier), processedDatedDir, false);

                    }

                } catch (Exception e) {
                    e.printStackTrace();
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
            String dateFolder = saveDir.substring(saveDir.lastIndexOf("/") + 1);

            // ✅ Already processed check
            File processedDoneFile = new File(PROCESSED_DIR + "/" + dateFolder, "DONE_" + fileName);
            File processedFailedFile = new File(PROCESSED_DIR + "/" + dateFolder, "FAILED_" + fileName);
            if (processedDoneFile.exists() || processedFailedFile.exists()) {
                System.out.println("⚠️ File already processed earlier, skipping: " + fileName);
                return null;
            }

            File dir = new File(saveDir);
            if (!dir.exists()) dir.mkdirs();

            File outputFile = new File(dir, fileName);
            if (outputFile.exists()) {
                return outputFile; // Use already downloaded
            }

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
        JSONArray fiscalisedInvoices = root.getJSONArray("fiscalisedInvoices");
        if (fiscalisedInvoices.length() > 0) {
            JSONObject invoice = fiscalisedInvoices.getJSONObject(0);
            return new String[]{invoice.getString("qrCode"), invoice.getString("irn")};
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
        PdfCanvas canvas = new PdfCanvas(page);

        float pageHeight = page.getPageSize().getHeight();
        float qrX = 36f;
        float qrY = pageHeight - 100f;

        float irnX = 36f;
        float irnY = qrY - 15f;

        if (qrBase64.contains(",")) {
            qrBase64 = qrBase64.split(",")[1];
        }
        byte[] imageBytes = Base64.getDecoder().decode(qrBase64);
        ImageData imageData = ImageDataFactory.create(imageBytes);
        Image image = new Image(imageData).setFixedPosition(qrX, qrY).scaleToFit(100, 100);
        document.add(image);

        canvas.beginText()
                .setFontAndSize(PdfFontFactory.createFont(), 10)
                .moveText(irnX, irnY)
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
                .setColor(ColorConstants.RED, true)
                .moveText(36f, page.getPageSize().getHeight() - 50f)
                .showText("FAILED")
                .endText();

        pdfDoc.close();
        return outputStream.toByteArray();
    }
}
