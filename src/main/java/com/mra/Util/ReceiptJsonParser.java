package com.mra.Util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.IOException;
import java.util.*;

public class ReceiptPdfToJsonParser {

    public static void main(String[] args) {
        String pdfFilePath = "C:\\Users\\Krishna Purohit\\Downloads\\630235891_1753089849000-30235891-Bill Pay-Multi.pdf";

        try {
            List<String> pageContents = extractTextFromPdf(pdfFilePath);

            // Convert to raw structured JSON (as if from receipt OCR step)
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            ArrayNode receipts = mapper.createArrayNode();

            for (int i = 0; i < pageContents.size(); i++) {
                ObjectNode receipt = mapper.createObjectNode();
                receipt.put("pageNumber", i + 1);
                ObjectNode content = mapper.createObjectNode();

                String[] lines = pageContents.get(i).split("\\r?\\n|(?<=\\d) (?=[A-Z])");

                int lineNo = 1;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        content.put(String.valueOf(lineNo++), trimmed);
                    }
                }

                receipt.set("content", content);
                receipts.add(receipt);
            }

            // Now pass this JSON into the parser logic
            parseReceiptJson(receipts);

            System.out.println("✅ PDF parsed and items extracted as JSON.");
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static List<String> extractTextFromPdf(String pdfFilePath) throws IOException {
        List<String> pageContents = new ArrayList<>();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFilePath))) {
            int numPages = pdfDoc.getNumberOfPages();
            for (int i = 1; i <= numPages; i++) {
                PdfPage page = pdfDoc.getPage(i);
                String content = PdfTextExtractor.getTextFromPage(page);
                pageContents.add(content);
            }
        }
        return pageContents;
    }

    public static void parseReceiptJson(ArrayNode receipts) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        for (JsonNode receipt : receipts) {
            JsonNode content = receipt.get("content");

            boolean startCollecting = false;
            List<String> itemLines = new ArrayList<>();

            for (int i = 1; i <= content.size(); i++) {
                String value = content.path(String.valueOf(i)).asText().trim();

                if (value.equals("No. Item Type Description Amount VAT Total")) {
                    startCollecting = true;
                    continue;
                }

                if (value.startsWith("Pre Balance Total:")) {
                    break;
                }

                if (startCollecting) {
                    itemLines.add(value);
                }
            }

            List<ObjectNode> parsedItems = new ArrayList<>();

            for (int i = 0; i < itemLines.size(); i++) {
                String line = itemLines.get(i).trim();
                if (line.matches("^\\d+$") && (i + 1 < itemLines.size())) {
                    String itemDesc = itemLines.get(i + 1);
                    ObjectNode itemJson = parseItemJson(line, itemDesc, mapper);
                    parsedItems.add(itemJson);
                    i++; // Skip next line
                } else if (line.matches("^\\d+\\s+.*\\s+[\\d.]+\\s+[\\d.]+\\s+[\\d.]+$")) {
                    // Line starts with number and ends with three numbers — directly parse
                    String[] parts = line.split("\\s+");
                    String no = parts[0];
                    String amount = parts[parts.length - 3];
                    String vat = parts[parts.length - 2];
                    String total = parts[parts.length - 1];
                    String itemType = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length - 3));

                    ObjectNode itemJson = mapper.createObjectNode();
                    itemJson.put("No", no);
                    itemJson.put("Item", itemType);
                    itemJson.put("Type", "");
                    itemJson.put("Description", "");
                    itemJson.put("Amount", amount);
                    itemJson.put("VAT", vat);
                    itemJson.put("Total", total);
                    parsedItems.add(itemJson);
                }
            }

            // Print final output
            String output = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedItems);
            System.out.println("==== JSON Items ====");
            System.out.println(output);
        }
    }

    private static ObjectNode parseItemJson(String no, String line, ObjectMapper mapper) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 4) {
            return mapper.createObjectNode(); // Invalid item
        }

        String amount = parts[parts.length - 3];
        String vat = parts[parts.length - 2];
        String total = parts[parts.length - 1];
        String item = parts[0];
        String type = (parts.length > 4) ? parts[1] : "";
        String description = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length - 3));

        ObjectNode node = mapper.createObjectNode();
        node.put("No", no);
        node.put("Item", item);
        node.put("Type", type);
        node.put("Description", description);
        node.put("Amount", amount);
        node.put("VAT", vat);
        node.put("Total", total);
        return node;
    }
}
