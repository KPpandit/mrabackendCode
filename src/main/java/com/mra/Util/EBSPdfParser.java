package com.mra.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EBSPdfParser {

    public static void main(String[] args) throws IOException {
        String pdfPath = "C:\\Users\\Krishna Purohit\\Downloads\\testingEmp\\downloads\\11000047.pdf";
        String content = extractText(pdfPath);
        ObjectNode root = buildJsonFromContent(content);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(root);
        System.out.println("--- Final Structured JSON ---\n" + json);
    }

    public static String parseToJson(String pdfPath) throws IOException {
        String content = extractText(pdfPath);
        ObjectNode root = buildJsonFromContent(content);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        return mapper.writeValueAsString(root);
    }

    private static ObjectNode buildJsonFromContent(String content) throws IOException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode root = mapper.createObjectNode();

        String amtWoVatStr = extract(content, "Total Amount Before VAT\\s*Rs\\.\\s*(\\d+[.,]?\\d*)", "0.0").replace(",", "");
        String invoiceTotalStr = extract(content, "Total Amount After VAT\\s*Rs\\.\\s*(\\d+[.,]?\\d*)", "0.0").replace(",", "");
        double amtWoVat = Double.parseDouble(amtWoVatStr);
        double invoiceTotal = Double.parseDouble(invoiceTotalStr);
        double vatAmount = invoiceTotal - amtWoVat;

        root.put("invoiceCounter", "1");
        root.put("transactionType", "B2C");
        root.put("personType", "VATR");
        root.put("invoiceTypeDesc", "STD");
        root.put("currency", "MUR");

        String billNo = extract(content, "Bill No\\.:\\s*(\\d+)");
        root.put("invoiceIdentifier", billNo);
        root.put("invoiceRefIdentifier", billNo);
        root.put("previousNoteHash", "prevNote");
        root.put("reasonStated", "rgeegr");
        root.put("salesTransactions", "CASH");
        root.put("totalVatAmount", String.format("%.3f", vatAmount));
        root.put("totalAmtWoVatCur", String.format("%.3f", amtWoVat));
        root.put("totalAmtWoVatMur", String.format("%.3f", amtWoVat));
        root.put("invoiceTotal", String.format("%.3f", invoiceTotal));
        root.put("discountTotalAmount", "0.00");
        root.put("totalAmtPaid", String.format("%.3f", invoiceTotal));

        // Date Format: yyyyMMdd HH:mm:ss
        String billDate = extract(content, "Bill Date:\\s*(\\d{2}/\\d{2}/\\d{4})");
        if (!billDate.isEmpty()) {
            String[] parts = billDate.split("/"); // [dd, MM, yyyy]
            String formattedDate = parts[2] + parts[1] + parts[0]; // yyyyMMdd
            root.put("dateTimeInvoiceIssued", formattedDate + " 10:40:30");
        } else {
            root.put("dateTimeInvoiceIssued", "20250707 10:40:30");
        }

        // Seller
        ObjectNode seller = mapper.createObjectNode();
        seller.put("name", "SysAdmin");
        seller.put("tradeName", "MTML");
        seller.put("tan", extract(content, "VAT Reg\\.No:\\s*(VAT\\d+)").replace("VAT", ""));
        seller.put("brn", extract(content, "BRN:\\s*(\\w+)"));
        seller.put("businessAddr", "MTML Square, 63 Cybercity");
        seller.put("businessPhoneNo", "12");
        seller.put("ebsCounterNo", "a1");
        root.set("seller", seller);

        // Buyer
        ObjectNode buyer = mapper.createObjectNode();
        String buyerNameFull = extract(content, "Customer Name:\\s*(.*?)\\n");
        String[] buyerNameWords = buyerNameFull.split("\\s+");
        StringBuilder trimmedName = new StringBuilder();
        for (int i = 0; i < Math.min(4, buyerNameWords.length); i++) {
            trimmedName.append(buyerNameWords[i]).append(" ");
        }

        buyer.put("name", trimmedName.toString().trim());
        buyer.put("tan", "");
        String extractedBrn = extract(content, "Customer BRN:\\s*(\\w+)", "");
        if (!extractedBrn.startsWith("C")) {
            extractedBrn = "";
        }
        buyer.put("brn", extractedBrn);
        buyer.put("businessAddr", extract(content, "Customer Address:\\s*(.*?)\\n", "BRN: " + seller.get("brn").asText()));
        buyer.put("buyerType", "VATR");
        buyer.put("nic", "");
        buyer.put("msisdn", extract(content, "msisdn\\s*:\\s*(\\d+)", ""));
        root.set("buyer", buyer);

        // Items
        ArrayNode itemList = mapper.createArrayNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("itemNo", "1");
        item.put("taxCode", "TC01");
        item.put("nature", "GOODS");
        item.put("productCodeMra", "");
        item.put("productCodeOwn", "");
        item.put("itemDesc", "30 Mbps Internet - Monthly Fee");
        item.put("quantity", "1");
        item.put("unitPrice", String.format("%.3f", invoiceTotal));
        item.put("discount", "0.00");
        item.put("discountedValue", "");
        item.put("amtWoVatCur", String.format("%.3f", amtWoVat));
        item.put("amtWoVatMur", String.format("%.3f", amtWoVat));
        item.put("vatAmt", String.format("%.3f", vatAmount));
        item.put("totalPrice", String.format("%.3f", invoiceTotal));
        item.put("previousBalance", "0");
        itemList.add(item);
        root.set("itemList", itemList);

        return root;
    }

    private static String extractText(String path) throws IOException {
        StringBuilder text = new StringBuilder();
        try (PdfDocument pdf = new PdfDocument(new PdfReader(new File(path)))) {
            for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                PdfPage page = pdf.getPage(i);
                text.append(PdfTextExtractor.getTextFromPage(page)).append("\n");
            }
        }
        return text.toString();
    }

    private static String extract(String content, String pattern) {
        return extract(content, pattern, "");
    }

    private static String extract(String content, String pattern, String defaultValue) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(content);
        if (m.find()) return m.group(1).trim();
        return defaultValue;
    }
}
