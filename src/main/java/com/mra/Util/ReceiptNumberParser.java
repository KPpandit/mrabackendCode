package com.mra.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.File;
import java.util.*;
import java.util.regex.*;

public class ReceiptNumberParser {

    public static void main(String[] args) {
        String pdfFilePath = "C:\\Users\\Krishna Purohit\\Downloads\\receipts_630237011_1753090668000 (1).pdf";
        try {
            String resultJson = parsePdfToInvoiceJson(pdfFilePath);
            System.out.println("✅ Output JSON:\n" + resultJson);
        } catch (Exception e) {
            System.err.println("❌ Error parsing PDF: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String parsePdfToInvoiceJson(String pdfFilePath) throws Exception {
        StringBuilder allText = new StringBuilder();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(new File(pdfFilePath)))) {
            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc.getPage(i);
                allText.append(PdfTextExtractor.getTextFromPage(page)).append("\n");
            }
        }

        String content = allText.toString();
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        Map<String, Object> invoice = new LinkedHashMap<>();

        invoice.put("invoiceCounter", "1");
        invoice.put("transactionType", "B2C");
        invoice.put("personType", "VATR");
        invoice.put("invoiceTypeDesc", "CRN");
        invoice.put("currency", "MUR");

        String invoiceId = extractRegex(content, "(\\d{8}/\\d{8})");
        invoice.put("invoiceIdentifier", invoiceId);
        invoice.put("invoiceRefIdentifier", invoiceId);
        invoice.put("previousNoteHash", "prevNote");
        invoice.put("reasonStated", "Recharge Reversal Credit Note");
        invoice.put("salesTransactions", "CASH");

        String date = extractRegex(content, "(\\d{4}/\\d{2}/\\d{2})");
        String time = extractRegex(content, "(\\d{2}:\\d{2}:\\d{2})");
        String finalDate = (date + " " + time).replace("/", "");
        invoice.put("dateTimeInvoiceIssued", finalDate);

        // ✅ SAFELY extract 3 price parts
        double amtWoVat = 0, vat = 0, total = 0;
        Matcher priceMatcher = Pattern.compile("(\\d+\\.\\d{3})\\s+(\\d+\\.\\d{3})\\s+(\\d+\\.\\d{3})").matcher(content);
        if (priceMatcher.find()) {
            double val1 = Double.parseDouble(priceMatcher.group(1));
            double val2 = Double.parseDouble(priceMatcher.group(2));
            double val3 = Double.parseDouble(priceMatcher.group(3));

            total = Math.max(Math.max(val1, val2), val3);
            List<Double> others = new ArrayList<>(List.of(val1, val2, val3));
            others.remove(total);

            // Assume: smaller is WoVat, bigger is VAT
            amtWoVat = Math.min(others.get(0), others.get(1));
            vat = Math.max(others.get(0), others.get(1));
        }

        invoice.put("totalAmtWoVatCur", format(amtWoVat));
        invoice.put("totalAmtWoVatMur", format(amtWoVat));
        invoice.put("totalVatAmount", format(vat));
        invoice.put("invoiceTotal", format(total));
//        invoice.put("totalAmountPayable", format(total));
        invoice.put("discountTotalAmount", "0.00000");
        invoice.put("totalAmtPaid", format(total));

        // Seller
        Map<String, String> seller = new LinkedHashMap<>();
        seller.put("name", "SysAdmin");
        seller.put("tradeName", "MTML");
        seller.put("tan", "20275899");
        seller.put("brn", "C07048459");
        seller.put("businessAddr", "MTML Square, 63 Cybercity");
        seller.put("businessPhoneNo", "12");
        seller.put("ebsCounterNo", "a1");
        invoice.put("seller", seller);

        // Buyer
        Map<String, String> buyer = new LinkedHashMap<>();
        buyer.put("name", extractRegex(content, "Customer Name.*\\n(.*?)\\s\\d{8}"));
        buyer.put("businessAddr", "Port Louis");
        buyer.put("buyerType", "VATR");
        buyer.put("msisdn", extractRegex(content, "(\\d{8})", 2));
        buyer.put("tan", "");
        buyer.put("brn", "");
        buyer.put("nic", "");
        invoice.put("buyer", buyer);

        // Item
        Map<String, String> item = new LinkedHashMap<>();
        item.put("itemNo", "1");
        item.put("nature", "GOODS");
        item.put("taxCode", "TC01");
        item.put("itemDesc", "Reversal");
        item.put("quantity", "1");
        item.put("unitPrice", format(total));
        item.put("discount", "0.00000");
        item.put("amtWoVatCur", format(amtWoVat));
        item.put("amtWoVatMur", format(amtWoVat));
        item.put("vatAmt", format(vat));
        item.put("totalPrice", format(total));
        item.put("previousBalance", "0");

        invoice.put("itemList", List.of(item));

        return mapper.writeValueAsString(List.of(invoice));
    }

    private static String extractRegex(String text, String patternStr) {
        return extractRegex(text, patternStr, 1);
    }

    private static String extractRegex(String text, String patternStr, int occurrence) {
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count == occurrence) return matcher.group(1).trim();
        }
        return "";
    }

    private static String format(double value) {
        return String.format("%.5f", Math.abs(value));
    }
}
