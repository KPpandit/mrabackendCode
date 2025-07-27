package com.mra.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SalesReturnParser {

    public static String parseToJson(String pdfFilePath) {
        try {
            PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFilePath));
            StringBuilder rawText = new StringBuilder();

            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc.getPage(i);
                rawText.append(PdfTextExtractor.getTextFromPage(page)).append("\n");
            }

            pdfDoc.close();
            return buildInvoiceJson(rawText.toString());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String buildInvoiceJson(String text) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode rootArray = mapper.createArrayNode();

        ObjectNode invoiceNode = mapper.createObjectNode();
        invoiceNode.put("invoiceCounter", "1");
        invoiceNode.put("transactionType", "B2C");
        invoiceNode.put("personType", "VATR");
        invoiceNode.put("invoiceTypeDesc", "STD");
        invoiceNode.put("currency", "MUR");

        invoiceNode.put("invoiceIdentifier", "R_" + getValue(text, "Invoice No\\.:\\s*([\\d/]+)"));
        invoiceNode.put("invoiceRefIdentifier", "R_" + getValue(text, "Invoice No\\.:\\s*([\\d/]+)"));

        invoiceNode.put("previousNoteHash", "prevNote");
        invoiceNode.put("reasonStated", "Sales return credit note");
        invoiceNode.put("salesTransactions", "CASH");
        invoiceNode.put("totalVatAmount", "0.00");
        invoiceNode.put("totalAmtWoVatCur", getLastRsAmount(text, "Total Amount Due before VAT"));
        invoiceNode.put("totalAmtWoVatMur", getLastRsAmount(text, "Total Amount Due before VAT"));
        invoiceNode.put("invoiceTotal", getLastRsAmount(text, "Total Amount Due with VAT"));
        invoiceNode.put("discountTotalAmount", "0.00");
        invoiceNode.put("totalAmtPaid", getLastRsAmount(text, "Total Amount Paid"));
        invoiceNode.put("dateTimeInvoiceIssued", formatDate(getValue(text, "Invoice Date:\\s*(\\d{2}/\\d{2}/\\d{4})")));

        // Seller
        ObjectNode seller = mapper.createObjectNode();
        seller.put("name", "SysAdmin");
        seller.put("tradeName", "MTML");
        seller.put("tan", "20275899");
        seller.put("brn", "C07048459");
        seller.put("businessAddr", "MTML Square, 63 Cybercity");
        seller.put("businessPhoneNo", "12");
        seller.put("ebsCounterNo", "a1");
        invoiceNode.set("seller", seller);

        // Buyer
        ObjectNode buyer = mapper.createObjectNode();
        buyer.put("name", extractFirstWords(getValue(text, "Customer Name:\\s*([^\n]*)"), 2));
        buyer.put("tan", "");
        buyer.put("brn", "");
        buyer.put("businessAddr", extractFirstWords(getValue(text, "Customer Address:\\s*([^\n]*)"), 3));
        buyer.put("buyerType", "VATR");
        buyer.put("nic", "");
        buyer.put("msisdn", "0");
        invoiceNode.set("buyer", buyer);

        // Item
        ArrayNode itemList = mapper.createArrayNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("itemNo", "1");
        item.put("taxCode", "TC01");
        item.put("nature", "GOODS");
        item.put("productCodeMra", "");
        item.put("productCodeOwn", "");
        item.put("itemDesc", "Returned Item");
        item.put("quantity", "1");
        item.put("unitPrice", getLastRsAmount(text, "Return Amount"));
        item.put("discount", "0.00");
        item.put("discountedValue", "");
        item.put("amtWoVatCur", getLastRsAmount(text, "Total Amount Due before VAT"));
        item.put("amtWoVatMur", getLastRsAmount(text, "Total Amount Due before VAT"));
        item.put("vatAmt", "0.00");
        item.put("totalPrice", getLastRsAmount(text, "Return Amount"));
        item.put("previousBalance", "0");
        itemList.add(item);

        invoiceNode.set("itemList", itemList);
        rootArray.add(invoiceNode);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootArray);
    }

    private static String getValue(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String getLastRsAmount(String text, String label) {
        Pattern pattern = Pattern.compile(label + ".*?Rs\\.\\s*([\\d,.]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        String lastValue = "";
        while (matcher.find()) {
            lastValue = matcher.group(1).replace(",", "");
        }
        return lastValue.isEmpty() ? "0.00" : lastValue;
    }

    private static String formatDate(String date) {
        if (date == null || date.isEmpty()) return "";
        String[] parts = date.split("/");
        return parts[2] + parts[1] + parts[0] + " 10:40:30";
    }

    private static String extractFirstWords(String input, int count) {
        if (input == null || input.isEmpty()) return "";
        String[] words = input.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(words.length, count); i++) {
            result.append(words[i]);
            if (i < count - 1 && i < words.length - 1) result.append(" ");
        }
        return result.toString().replaceAll("[^\\w, ]", "").trim();
    }

    // 🔧 Test locally
    public static void main(String[] args) {
        String path = "C:\\Users\\Krishna Purohit\\Downloads\\testingEmp\\downloads\\salesReturn\\43298867-805b-4a46-b8fd-187ff80909e0.pdf";
        String json = parseToJson(path);
        System.out.println(json);
    }
}
