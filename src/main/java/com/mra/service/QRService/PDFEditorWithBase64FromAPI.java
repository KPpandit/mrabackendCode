package com.mra.service.QRService;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.kernel.font.PdfFontFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;



public class PDFEditorWithBase64FromAPI {

    public static void main(String[] args) throws Exception {
        String apiUrl = "http://41.222.103.118:8889/api/invoices/search?value=2025070725273131"; // Your actual API endpoint
        String inputPdfPath = "/Users/sangameshhiremath/Desktop/630235891_1753089849000-30235891-Bill Pay-Multi.pdf";
        String outputPdfPath = "/Users/sangameshhiremath/Desktop/630235891_1753089849000-30235891-Bill Pay-Multi-p.pdf";

        // Step 1: Call API and get Base64 image string
        //String base64Image = fetchBase64FromAPI(apiUrl);
        String[] values = extractBase64QrCodeFromAPI(apiUrl);

        // Step 2: Decode and insert into PDF

        String base64Image = values[0];
        System.out.println(base64Image);

        // Optional: Remove data URI prefix if present
        if (base64Image.contains(",")) {
            base64Image = base64Image.split(",")[1];
        }

        // Step 2: Decode Base64 to bytes
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);

        // Step 3: Edit PDF
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(inputPdfPath), new PdfWriter(outputPdfPath));
        Document document = new Document(pdfDoc);
        PdfPage page = pdfDoc.getFirstPage();
        PdfCanvas canvas = new PdfCanvas(page);

        // Insert text
        canvas.beginText()
                .setFontAndSize(PdfFontFactory.createFont(), 10)
                .moveText(10, 150)
                .showText("MRA Processed Invoice\nIRN No:"+values[1])
                .endText();

        // Insert image
        ImageData imageData = ImageDataFactory.create(imageBytes);
        Image image = new Image(imageData).setFixedPosition(10, 50).scaleToFit(100, 100);
        document.add(image);

        document.close();
        System.out.println("PDF updated with image from API.");
    }

    private static String[] extractBase64QrCodeFromAPI(String apiUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");

        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("HTTP error: " + conn.getResponseCode());
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
        }
        conn.disconnect();

        // Step 1: Outer JSON is an array
        JSONArray outerArray = new JSONArray(response.toString());
        JSONObject invoiceObject = outerArray.getJSONObject(0);

        // Step 2: invoiceResponse is a stringified JSON array
        String invoiceResponseStr = invoiceObject.getString("invoiceResponse");
        JSONArray invoiceResponseArray = new JSONArray(invoiceResponseStr);
        JSONObject innerObj = invoiceResponseArray.getJSONObject(0);

        // Step 3: Extract QR Code (base64)
        return new String[] {innerObj.getString("qrCode"), innerObj.getString("irn")};
    }


}
