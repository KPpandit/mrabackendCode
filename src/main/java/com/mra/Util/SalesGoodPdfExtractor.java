package com.mra.Util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SalesGoodPdfExtractor {

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
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(inputStream))) {
            StringBuilder allText = new StringBuilder();
            List<String> pdfLines = new ArrayList<>();

            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc.getPage(i);
                String pageText = PdfTextExtractor.getTextFromPage(page);
                allText.append(pageText).append("\n");
                pdfLines.addAll(Arrays.asList(pageText.split("\\r?\\n")));
            }

            String content = allText.toString().replaceAll(",", "");

            ObjectMapper mapper = new ObjectMapper();
            JsonNodeFactory factory = JsonNodeFactory.instance;
            ObjectNode root = factory.objectNode();

            // Extract values
            String name = extract(content, "Customer Name: (.*?) E-?mail", 1);
            String email = extract(content, "[a-zA-Z0-9._%+-]+@mtmltd\\.net", 0);
            String accId = extract(content, "Account ID: (\\d+)", 1);
            String contact = extract(content, "Contact: (\\d+)", 1);
            String brn = extract(content, "BRN: (C\\d+)", 1);
            String orderNo = extract(content, "Order No: ([\\d/]+)", 1);
            String orderDate = extract(content, "Order Date: ([\\d:\\s\\-.]+)", 1);
            String sellerName = extract(content, "Shop Name: (\\w+)", 1);

            String[] amounts = extractAllThreeAmounts(content);
            String beforeVat = amounts[0];
            String vatAmount = amounts[1];
            String afterVat = amounts[2];

            String discount = extract(content, "Discount Rs\\. Rs\\.(\\d+\\.\\d{3})", 1);
            String totalPaid = extract(content, "Total After Discount Rs\\. (\\d+\\.\\d{3})", 1);

            String formattedDate = parseDateTime(orderDate);

            // ✅ Fallbacks
            if (orderNo.isEmpty()) orderNo = "N/A";
            if (contact.isEmpty()) contact = "N/A";

            root.put("invoiceCounter", "1");
            root.put("transactionType", "B2C");
            root.put("personType", "VATR");
            root.put("invoiceTypeDesc", "STD");
            root.put("currency", "MUR");

            // ✅ Set invoiceIdentifier from Order No
            root.put("invoiceIdentifier", orderNo);
            root.put("invoiceRefIdentifier", "");
            root.put("previousNoteHash", "prevNote");
            root.put("reasonStated", "rgeegr");
            root.put("salesTransactions", "CASH");
            root.put("totalVatAmount", vatAmount);
            root.put("totalAmtWoVatCur", beforeVat);
            root.put("totalAmtWoVatMur", beforeVat);
            root.put("invoiceTotal", afterVat);
            root.put("discountTotalAmount", discount);
            root.put("totalAmtPaid", totalPaid);
            root.put("dateTimeInvoiceIssued", formattedDate);

            ObjectNode seller = factory.objectNode();
            seller.put("name", sellerName);
            seller.put("tradeName", "MTML");
            seller.put("tan", "20275899");
            seller.put("brn", "C07048459");
            seller.put("businessAddr", "MTML Square, 63 Cybercity");
            seller.put("businessPhoneNo", "12");
            seller.put("ebsCounterNo", "a1");
            root.set("seller", seller);

            ObjectNode buyer = factory.objectNode();
            buyer.put("name", name);
            buyer.put("tan", "");
            buyer.put("brn", "");
            buyer.put("businessAddr", "");
            buyer.put("buyerType", "VATR");
            buyer.put("nic", "");
            buyer.put("msisdn", contact); // ✅ Correct MSISDN
            root.set("buyer", buyer);

            ObjectNode item = factory.objectNode();
            item.put("itemNo", "1");
            item.put("taxCode", "TC01");
            item.put("nature", "GOODS");
            item.put("productCodeMra", "");
            item.put("productCodeOwn", "");
            item.put("itemDesc", "Goods sale");
            item.put("quantity", "1");
            item.put("unitPrice", beforeVat);
            item.put("discount", discount);
            item.put("discountedValue", "");
            item.put("amtWoVatCur", beforeVat);
            item.put("amtWoVatMur", beforeVat);
            item.put("vatAmt", vatAmount);
            item.put("totalPrice", afterVat);
            item.put("previousBalance", "0");
            root.putArray("itemList").add(item);

            return root;
        }
    }

    private static String extract(String text, String regex, int group) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(group).trim() : "";
    }

    private static String[] extractAllThreeAmounts(String text) {
        Pattern pattern = Pattern.compile("Rs\\.(\\d+\\.\\d{3})[^R\\n]*Rs\\.(\\d+\\.\\d{3})[^R\\n]*Rs\\.(\\d+\\.\\d{3})");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return new String[]{matcher.group(1), matcher.group(2), matcher.group(3)};
        }
        return new String[]{"", "", ""};
    }

    private static String parseDateTime(String rawDate) {
        try {
            LocalDateTime dt = LocalDateTime.parse(rawDate.trim());
            return dt.format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));
        } catch (Exception e) {
            return "20250101 10:40:30";
        }
    }

    public static void main(String[] args) {
        try {
            File file = new File("C:\\Users\\Krishna Purohit\\Downloads\\invoice\\invoice\\Sales good\\SIM_ACRD_SALE.pdf");
            JsonNode jsonOutput = convertPdfToJson(file);
            ObjectMapper mapper = new ObjectMapper();
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonOutput));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
