package com.mra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.mra.Util.SalesReturnParser;
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
public class SalesReturnService {

    private final MRAService mraService;

//  private static final String BASE_URL = "http://41.222.103.118:22221";
    private static final String BASE_URL = "http://172.28.5.2:22221";
    private static final String DOWNLOAD_DIR = "/home/downloads/salesReturn";
    private static final String PROCESSED_DIR = "/home/Processed_Files/einv/sales_return";

    @Scheduled(fixedRate = 60 * 60 * 1000) // every 1 hour
    public void scheduledInvoiceProcessing() {
        downloadAndSubmitInvoices("sales_return");
    }

    public void downloadAndSubmitInvoices(String folder) {
        try {
            List<Map<String, Object>> files = listFiles(folder);
            for (Map<String, Object> file : files) {
                String fileName = (String) file.get("name");
                File downloaded = downloadFile(folder, fileName, DOWNLOAD_DIR);

                if (downloaded == null) continue;

                try {
                    String json = SalesReturnParser.parseToJson(downloaded.getAbsolutePath());
                    ObjectMapper objectMapper = new ObjectMapper();
                    List<InvoiceBean> invoiceBeans = objectMapper.readValue(
                            json,
                            new com.fasterxml.jackson.core.type.TypeReference<List<InvoiceBean>>() {}
                    );

                    if (invoiceBeans.isEmpty()) continue;

                    // Take first invoice identifier
                    String invoiceIdentifier = invoiceBeans.get(0).getInvoiceIdentifier();
                    invoiceIdentifier = invoiceIdentifier.replace("/", "-").replace("\\", "-");

                    // Check if already processed
                    if (isAlreadyProcessed(fileName, invoiceIdentifier)) {
                        System.out.println("⏩ Skipping already processed file: " + fileName);
                        continue;
                    }

                    // Filter VAT != 0
                    List<InvoiceBean> nonZeroVatInvoices = invoiceBeans.stream()
                            .filter(invoice -> !"0".equalsIgnoreCase(invoice.getTotalVatAmount()))
                            .toList();

                    if (nonZeroVatInvoices.isEmpty()) continue;

                    String result;
                    try {
                        result = mraService.submitInvoices(invoiceBeans);
                    } catch (Exception e) {
                        System.err.println("❌ Exception while calling MRA service for " + fileName);
                        moveFileToProcessed(downloaded, fileName, invoiceIdentifier, false);
                        continue;
                    }

                    if (result != null && result.contains("SUCCESS")) {
                        String[] qrAndIrn = extractQrAndIrnFromResponse(result);
                        String qrBase64 = qrAndIrn[0];
                        String irn = qrAndIrn[1];

                        byte[] modifiedPdf = addQrAndIrnToPdf(downloaded, qrBase64, irn);

                        // overwrite original before moving
                        try (FileOutputStream fos = new FileOutputStream(downloaded)) {
                            fos.write(modifiedPdf);
                        }

                        moveFileToProcessed(downloaded, fileName, invoiceIdentifier, true);
                    } else {
                        moveFileToProcessed(downloaded, fileName, invoiceIdentifier, false);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    moveFileToProcessed(downloaded, fileName, "UNKNOWN", false);
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
            e.printStackTrace();
            return null;
        }
    }

    private boolean isAlreadyProcessed(String originalFileName, String invoiceIdentifier) {
        String baseName = originalFileName.endsWith(".pdf")
                ? originalFileName.substring(0, originalFileName.length() - 4)
                : originalFileName;

        String doneName = "DONE_" + baseName + "_" + invoiceIdentifier + ".pdf";
        String failedName = "FAILED_" + baseName + "_" + invoiceIdentifier + ".pdf";

        File doneFile = new File(PROCESSED_DIR, doneName);
        File failedFile = new File(PROCESSED_DIR, failedName);

        return doneFile.exists() || failedFile.exists();
    }

    private void moveFileToProcessed(File original, String originalFileName, String invoiceIdentifier, boolean success) {
        try {
            File processedDir = new File(PROCESSED_DIR);
            if (!processedDir.exists()) processedDir.mkdirs();

            // Remove extension
            String baseName = originalFileName.endsWith(".pdf")
                    ? originalFileName.substring(0, originalFileName.length() - 4)
                    : originalFileName;

            String safeInvoiceIdentifier = invoiceIdentifier.replace("/", "-").replace("\\", "-");
            String prefix = success ? "DONE_" : "FAILED_";
            String finalFileName = prefix + baseName + "_" + safeInvoiceIdentifier + ".pdf";

            File newFile = new File(processedDir, finalFileName);
            Files.move(original.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            System.out.println("📂 File moved to: " + newFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String[] extractQrAndIrnFromResponse(String resultJson) {
        JSONObject root = new JSONObject(resultJson);
        JSONArray fiscalisedInvoices = root.getJSONArray("fiscalisedInvoices");
        if (fiscalisedInvoices.length() > 0) {
            JSONObject invoice = fiscalisedInvoices.getJSONObject(0);
            return new String[]{invoice.optString("qrCode", ""), invoice.optString("irn", "")};
        }
        return new String[]{"", ""};
    }

    public byte[] addQrAndIrnToPdf(File inputPdf, String qrBase64, String irnText) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(inputPdf), new PdfWriter(outputStream));
        Document document = new Document(pdfDoc);
        PdfPage page = pdfDoc.getFirstPage();
        PdfCanvas canvas = new PdfCanvas(page);

        float pageHeight = page.getPageSize().getHeight();

        float qrX = 30f;
        float qrYFromTop = 100f;
        float qrY = pageHeight - qrYFromTop;

        if (qrBase64.contains(",")) {
            qrBase64 = qrBase64.split(",")[1];
        }
        byte[] imageBytes = Base64.getDecoder().decode(qrBase64);
        ImageData imageData = ImageDataFactory.create(imageBytes);
        Image image = new Image(imageData)
                .setFixedPosition(qrX, qrY)
                .scaleToFit(100, 100);
        document.add(image);

        canvas.beginText()
                .setFontAndSize(PdfFontFactory.createFont(), 10)
                .moveText(qrX, qrY - 15f)
                .showText("IRN: " + irnText)
                .endText();

        document.close();
        return outputStream.toByteArray();
    }
}
