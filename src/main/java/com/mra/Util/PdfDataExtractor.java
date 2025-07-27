package com.mra.Util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.Locale;

public class PdfDataExtractor {

    public static void main(String[] args) throws Exception {

        // ✅ Option 1: Provide PDF path as a program argument
        String pdfPath;
        if (args.length > 0) {
            pdfPath = args[0];
        } else {
            // ✅ Option 2: Hardcode your path here
            pdfPath = "C:\\Users\\Krishna Purohit\\Downloads\\invoice\\invoice\\Postpaid-bills\\20762975.pdf";
        }

        File pdfFile = new File(pdfPath);
        if (!pdfFile.exists()) {
            System.err.println("File not found: " + pdfFile.getAbsolutePath());
            return;
        }

//        System.out.println("✅ Reading PDF: " + pdfFile.getAbsolutePath());

        // Convert PDF to JSON
        JsonNode result = convertPdfToJson(pdfFile);

        System.out.println("\n✅ JSON Result:");
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    public static JsonNode convertPdfToJson(MultipartFile multipartFile) throws Exception {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            return parseAndConvert(inputStream);
        }
    }

    public static JsonNode convertPdfToJson(File file) throws Exception {
        try (InputStream inputStream = new FileInputStream(file)) {
            return parseAndConvert(inputStream);
        }
    }

    private static JsonNode parseAndConvert(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            if (!document.isEncrypted()) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(1);

                String text = stripper.getText(document);
                String[] lines = text.split("\\r?\\n");
//                System.out.println("\n--- Full Line Dump ---");
//                for (int i = 0; i < lines.length; i++) {
//                    System.out.println(i + " ➤ " + lines[i]);
//                }
                Map<String, String> data = extractValues(lines);

                ObjectMapper mapper = new ObjectMapper();
                JsonNodeFactory factory = JsonNodeFactory.instance;
                ObjectNode root = factory.objectNode();

                root.put("invoiceCounter", "1");
                root.put("transactionType", "B2C");
                root.put("personType", "VATR");
                root.put("invoiceTypeDesc", "STD");
                root.put("currency", "MUR");
                root.put("invoiceIdentifier", data.getOrDefault("billNumber", "1"));
                root.put("invoiceRefIdentifier", "");
                root.put("previousNoteHash", "prevNote");
                root.put("reasonStated", "rgeegr");
                root.put("salesTransactions", "CASH");

                root.put("totalVatAmount", data.get("vatAmount"));
                root.put("totalAmtWoVatCur", data.get("totalAmountBeforeVat"));
//                root.put("totalAmtWoVatCur", data.get("totalAmountBeforeVat"));
                root.put("totalAmtWoVatMur", data.get("totalAmountBeforeVat"));
                root.put("invoiceTotal", data.get("totalAmountDue"));
                root.put("discountTotalAmount", "0");
                root.put("totalAmtPaid", data.get("totalAmountDue"));

                String billDateRaw = data.get("billDate").trim();
                LocalDate parsedDate;
                DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

                try {
                    DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                    parsedDate = LocalDate.parse(billDateRaw, inputFormatter);
                } catch (Exception e) {
                    try {
                        billDateRaw = billDateRaw.replaceAll("\\s+", " ");
                        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
                        YearMonth ym = YearMonth.parse(billDateRaw, inputFormatter);
                        parsedDate = ym.atDay(1);
                    } catch (Exception e2) {
                        parsedDate = LocalDate.now();
                        System.err.println("Failed to parse date: " + billDateRaw + ", using current date");
                    }
                }
                root.put("dateTimeInvoiceIssued", parsedDate.format(outputFormatter) + " 10:40:30");

                ObjectNode seller = factory.objectNode();
                seller.put("name", data.get("sellerName"));
                seller.put("tradeName", "MTML");
                seller.put("tan", "20275899");
                seller.put("brn", "C07048459");
                seller.put("businessAddr", "MTML Square, 63 Cybercity");
                seller.put("businessPhoneNo", "+23052943333");
                seller.put("ebsCounterNo", "a1");
                root.set("seller", seller);

                ObjectNode buyer = factory.objectNode();
                buyer.put("name", data.get("customerName"));
                buyer.put("tan", "");
                buyer.put("brn", "");
                buyer.put("businessAddr", "");
                buyer.put("buyerType", "VATR");
                buyer.put("nic", "");
                buyer.put("msisdn", data.getOrDefault("buyerMsisdn", ""));

                root.set("buyer", buyer);

                ObjectNode item = factory.objectNode();
                item.put("itemNo", "1");
                item.put("taxCode", "TC01");
                item.put("nature", "GOODS");
                item.put("productCodeMra", "");
                item.put("productCodeOwn", "");
                item.put("itemDesc", "Postpaid Invoice");
                item.put("quantity", "1");
                item.put("unitPrice", data.get("totalAmountBeforeVat"));
                item.put("discount", "");
                item.put("discountedValue", "50.0");
                item.put("amtWoVatCur", data.get("totalAmountBeforeVat"));
//                item.put("amtWoVatCur", data.get("totalAmountAfterVat"));
                item.put("amtWoVatMur", data.get("totalAmountBeforeVat"));
                item.put("vatAmt", data.get("vatAmount"));
                item.put("totalPrice", data.get("totalAmountDue"));
                item.put("previousBalance", data.getOrDefault("previousBalance", "0.00"));
                root.putArray("itemList").add(item);

                return root;
            }
            throw new Exception("Document is encrypted");
        }
    }

    // ---------------------- Extraction Helpers ----------------------------

    private static Map<String, String> extractValues(String[] lines) {
        Map<String, String> map = new HashMap<>();

        map.put("customerName", lines[11].trim());
        map.put("vatRegNo", lines[9].trim());
        map.put("brn", lines[10].trim());
        map.put("email", extractEmail(lines[16]));
        map.put("billNumber", extractBillNumberFromLine(lines[96]));
        map.put("billDate", cleanLine(lines[3]));
        map.put("billMonth", extractBillMonthFromLine(lines[96]));
        map.put("billPeriod", extractBillPeriod(lines[19]));
        map.put("dueDate", extractDateAtEnd(lines[77]));
        map.put("sellerName", "MTML");

        if (lines.length > 12) {
            map.put("sellerAddress", lines[12]);
        } else {
            map.put("sellerAddress", "");
        }
        // ✅ New - extract msisdn from index 7
        if (lines.length > 7) {
            map.put("buyerMsisdn", lines[7].trim());
        } else {
            map.put("buyerMsisdn", "");
        }
        if (lines.length > 31) {
            String totalDue = extractTotalAmountFromLine31(lines[31]);
            map.put("totalAmountDue", totalDue);
        }
        if (lines.length > 94) {
            String previousBalance = extractPreviousBalance(lines[94]);
            map.put("previousBalance", previousBalance);
        } else {
            map.put("previousBalance", "0.00");
        }
        map.put("totalAmountBeforeVat", lines.length > 32 ? extractFirstMonetaryValue(lines[32]) : "");
        map.put("vatAmount", lines.length > 43 ? extractFirstMonetaryValue(lines[43]) : "");
        map.put("totalAmountAfterVat", lines.length > 84 ? extractFirstMonetaryValue(lines[84]) : "");

        return map;
    }

    private static String extractEmail(String line) {
        int atIndex = line.indexOf("@");
        if (atIndex != -1) {
            int start = line.lastIndexOf(" ", atIndex);
            return line.substring(start + 1).trim();
        }
        return "";
    }

    private static String extractBillNumberFromLine(String line) {
        String digits = line.replaceAll("[^0-9]", " ").trim();
        String[] parts = digits.split("\\s+");
        return parts.length > 0 ? parts[0] : "";
    }

    private static String extractBillMonthFromLine(String line) {
        line = line.replaceAll("\\s+", " ").trim();
        Matcher matcher = Pattern.compile("([A-Z]{3,})\\s+\\d{4}").matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String extractBillPeriod(String line) {
        if (line.contains("Bill Period")) {
            return line.split("Bill Period")[0].trim();
        }
        return "";
    }

    private static String extractDateAtEnd(String line) {
        String[] tokens = line.trim().split(" ");
        for (int i = tokens.length - 1; i >= 0; i--) {
            if (tokens[i].matches("\\d{2}/\\d{2}/\\d{4}")) {
                return tokens[i];
            }
        }
        return "";
    }

    private static String cleanLine(String line) {
        return line.replaceAll(".*?:", "").trim();
    }

    private static String extractFirstMonetaryValue(String line) {
        String cleaned = line.replaceAll(",", "");
        Matcher matcher = Pattern.compile("-?\\d+\\.\\d{2}").matcher(cleaned);
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    private static String extractTotalAmountFromLine31(String line) {
        String cleaned = line.replaceAll(",", "").trim();
        Matcher matcher = Pattern.compile("\\d+\\.\\d{2}").matcher(cleaned);

        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group());
        }

        if (values.size() >= 2) {
            return values.get(1);
        }
        return "";
    }
    private static String extractPreviousBalance(String line) {
        // Remove commas
        String cleaned = line.replaceAll(",", "");
        Matcher matcher = Pattern.compile("Arrears:\\s*Rs\\.\\s*(-?\\d+\\.\\d{2})").matcher(cleaned);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "0.00";
    }

}
