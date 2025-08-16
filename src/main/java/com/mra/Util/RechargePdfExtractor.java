package com.mra.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RechargePdfExtractor {

    public static ObjectNode convertPdfToJson(File file) throws IOException {
        StringBuilder allText = new StringBuilder();
        List<String> pdfLines = new ArrayList<>();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(file))) {
            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc.getPage(i);
                String pageText = PdfTextExtractor.getTextFromPage(page);
                allText.append(pageText).append("\n");

                // Collect lines
                pdfLines.addAll(Arrays.asList(pageText.split("\\r?\\n")));
            }
        }

        String pdfText = allText.toString();
        String text = pdfText.replaceAll(",", "").replaceAll("\\s+", " ");

        // ✅ Extract receipt number from line 12
        String receiptNo = "N/A";
        if (pdfLines.size() > 11) {
            receiptNo = pdfLines.get(11).trim();
        }
        String msisdn = "0";
        for (int i = 0; i < pdfLines.size(); i++) {
            String line = pdfLines.get(i).toLowerCase();
            if (line.contains("customer mobile") && line.contains("brn")) {
                if (i + 1 < pdfLines.size()) {
                    msisdn = pdfLines.get(i + 1).trim();
                }
                break;
            }
        }


        String buyerName = extractBuyerName(pdfText);
        String vatRegNo = extractVatRegNo(pdfText);
        String brn = extractBrn(pdfText);

        String sellerName = extract(text, "Customer Name ?:?\\s*(.*?)\\s*Address", 1);
        if (sellerName.isEmpty()) sellerName = "N/A";

        String businessAddr = extract(text, "(\\d{1,3},?\\s*Rosiers Avenue)", 1);
        if (businessAddr.isEmpty()) businessAddr = "25, Rosiers Avenue";

        String paymentRemark = extract(text, "Payment\\s*Remark\\s*:?\\s*Rs\\s*(\\d+\\.?\\d*)", 1);
        if (paymentRemark.isEmpty()) paymentRemark = "0.00";

        String paymentMethod = extract(text, "Payment\\s*Method\\s*:?\\s*(\\w+)", 1);
        if (paymentMethod.isEmpty()) paymentMethod = "PAYMENT";

        String dateRaw = extract(text, "(\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2})", 1);
        String formattedDate = parseDateTime(dateRaw);

        ObjectMapper mapper = new ObjectMapper();
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode root = factory.objectNode();

        root.put("invoiceCounter", "1");
        root.put("transactionType", "B2C");
        root.put("personType", "VATR");
        root.put("invoiceTypeDesc", "STD");
        root.put("currency", "MUR");

        // ✅ Set invoiceIdentifier from receiptNo
        root.put("invoiceIdentifier", receiptNo);
        root.put("invoiceRefIdentifier", "");
        root.put("previousNoteHash", "prevNote");
        root.put("reasonStated", "rgeegr");
        root.put("salesTransactions", paymentMethod.toUpperCase());
        root.put("totalVatAmount", "0.00");
        root.put("totalAmtWoVatCur", paymentRemark);
        root.put("totalAmtWoVatMur", paymentRemark);
        root.put("invoiceTotal", paymentRemark);
        root.put("discountTotalAmount", "0.00");
        root.put("totalAmtPaid", paymentRemark);
        root.put("dateTimeInvoiceIssued", formattedDate);

        ObjectNode seller = factory.objectNode();
        seller.put("name", "MTML");
        seller.put("tradeName", "MTML");
        seller.put("tan", "20275899");
        seller.put("brn", "C07048459");
        seller.put("businessAddr", businessAddr);
        seller.put("businessPhoneNo", "12");
        seller.put("ebsCounterNo", "a1");
        root.set("seller", seller);

        ObjectNode buyer = factory.objectNode();
        buyer.put("name", buyerName.trim());
        buyer.put("tan", "");
        buyer.put("brn", "");
        buyer.put("businessAddr", "");
        buyer.put("buyerType", "VATR");
        buyer.put("nic", "");
        // ✅ Set msisdn into buyer
        buyer.put("msisdn", msisdn);
        root.set("buyer", buyer);

        ObjectNode item = factory.objectNode();
        item.put("itemNo", "1");
        item.put("taxCode", "TC01");
        item.put("nature", "GOODS");
        item.put("productCodeMra", "");
        item.put("productCodeOwn", "");
        item.put("itemDesc", "Recharge sale");
        item.put("quantity", "1");
        item.put("unitPrice", paymentRemark);
        item.put("discount", "");
        item.put("discountedValue", "");
        item.put("amtWoVatCur", paymentRemark);
        item.put("amtWoVatMur", paymentRemark);
        item.put("vatAmt", "0.00");
        item.put("totalPrice", paymentRemark);
        item.put("previousBalance", "0.00000 ~ 0.00000");
        root.putArray("itemList").add(item);

        return root;
    }

    private static String extractVatRegNo(String pdfText) {
        String vatNo = "0";
        String[] lines = pdfText.split("\\r?\\n");

        for (String line : lines) {
            if (line.toLowerCase().contains("vat reg. no.")) {
                String[] parts = line.split(":");
                if (parts.length > 1) {
                    String candidate = parts[1].trim();
                    if (!candidate.isEmpty()) {
                        vatNo = candidate;
                    }
                }
                break;
            }
        }
        return vatNo;
    }

    private static String extractBrn(String pdfText) {
        String brn = "";
        String[] lines = pdfText.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toLowerCase().contains("customer mobile") && lines[i].toLowerCase().contains("brn")) {
                if (i + 1 < lines.length) {
                    brn = lines[i + 1].trim();
                }
                break;
            }
        }
        return brn.isEmpty() ? "0" : brn;
    }

    private static String extractBuyerName(String pdfText) {
        String[] lines = pdfText.split("\\r?\\n");
        StringBuilder buyerName = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].toLowerCase();
            if (line.contains("customer name")) {
                String before = (i >= 1) ? lines[i - 1].trim() : "";
                String after = (i + 1 < lines.length) ? lines[i + 1].trim() : "";

                buyerName.append(before).append(" ").append(after);
                break;
            }
        }

        String name = buyerName.toString().trim().replaceAll("\\s{2,}", " ");
        if (name.contains("Receipt No.")) {
            name = name.substring(0, name.indexOf("Receipt No.")).trim();
        }

        return name.isEmpty() ? "N/A" : name;
    }

    public static ObjectNode convertPdfToJson(MultipartFile multipartFile) throws IOException {
        File convFile = File.createTempFile("upload", ".pdf");
        multipartFile.transferTo(convFile);
        ObjectNode result = convertPdfToJson(convFile);
        convFile.delete();
        return result;
    }

    public static void main(String[] args) {
        File file = new File("C:\\Users\\Krishna Purohit\\Downloads\\invoice\\invoice\\Recharge\\checked_recharge.pdf");
        try {
            ObjectNode jsonOutput = convertPdfToJson(file);
            ObjectMapper mapper = new ObjectMapper();
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonOutput));
        } catch (Exception e) {
            System.err.println("Error processing PDF:");
            e.printStackTrace();
        }
    }

    private static String extract(String text, String regex, int group) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(group).trim() : "";
    }

    private static String parseDateTime(String input) {
        try {
            DateTimeFormatter inputFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
            LocalDateTime dt = LocalDateTime.parse(input.trim(), inputFormat);
            return dt.format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));
        } catch (Exception e) {
            return "20250101 10:40:30";
        }
    }
}
