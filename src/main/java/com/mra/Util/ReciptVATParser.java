package com.mra.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.File;
import java.util.*;
import java.util.regex.*;

public class ReciptVATParser {

    public static void main(String[] args) {
        String pdfPath = "C:\\Users\\Krishna Purohit\\Downloads\\VATINVOICE\\VATInvoiceMTML1753091187000.pdf";
        try {
            String jsonOutput = parsePdfToInvoiceJson(pdfPath);
            System.out.println("✅ Final JSON Output:\n" + jsonOutput);
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String parsePdfToInvoiceJson(String pdfFilePath) throws Exception {
        StringBuilder textBuilder = new StringBuilder();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(new File(pdfFilePath)))) {
            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc.getPage(i);
                textBuilder.append(PdfTextExtractor.getTextFromPage(page)).append("\n");
            }
        }

        String text = textBuilder.toString().replaceAll("\\s{2,}", " ").trim();
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Map<String, Object> invoice = new LinkedHashMap<>();

        // Static fields
        invoice.put("invoiceCounter", "1");
        invoice.put("transactionType", "B2C");
        invoice.put("personType", "VATR");
        invoice.put("invoiceTypeDesc", "CRN");
        invoice.put("currency", "MUR");

        // Extract fields
        String invoiceId = extractRegex(text, "Invoice No\\.:\\s*(\\d{8}/\\d{8})");
        invoice.put("invoiceIdentifier", invoiceId);
        invoice.put("invoiceRefIdentifier", invoiceId);
        invoice.put("previousNoteHash", "prevNote");
        invoice.put("reasonStated", "Recharge Reversal Credit Note");
        invoice.put("salesTransactions", "CASH");

        // Format dateTimeInvoiceIssued as yyyyMMdd HH:mm:ss
        String invoiceDateRaw = extractRegex(text, "Invoice Date:\\s*(\\d{2}/\\d{2}/\\d{4})");
        String[] parts = invoiceDateRaw.split("/");
        String invoiceDate = parts.length == 3 ? parts[2] + parts[1] + parts[0] : "20250101"; // fallback date

        String invoiceTime = extractRegex(text, "(\\d{2}:\\d{2}:\\d{2})");
        if (invoiceTime.isEmpty()) {
            invoiceTime = "00:00:00"; // fallback time
        }
        invoice.put("dateTimeInvoiceIssued", invoiceDate + " " + invoiceTime);
        // Totals
        double amtWoVat = parseAmountAfterLabel(text, "Total Amount Due before VAT");
        double total = parseAmountAfterLabel(text, "Total Amount Due with VAT");
        double vat = total - amtWoVat;

        invoice.put("totalAmtWoVatCur", format(amtWoVat));
        invoice.put("totalAmtWoVatMur", format(amtWoVat));
        invoice.put("totalVatAmount", format(vat));
        invoice.put("invoiceTotal", format(total));
//        invoice.put("totalAmountPayable", format(total));
        invoice.put("discountTotalAmount", format(parseAmountAfterLabel(text, "Discount")));
        invoice.put("totalAmtPaid", format(parseAmountAfterLabel(text, "Total Amount Paid")));

        // Seller (static)
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
        String fullName = extractRegex(text, "Customer Name:\\s*([^\n]+)");
        String[] nameParts = fullName.split("\\s+");
        String buyerName = (nameParts.length >= 2) ? nameParts[0] + " " + nameParts[1] : fullName;
        buyer.put("name", buyerName.trim());

        String fullAddr = extractRegex(text, "Customer Address:\\s*([^\n]+)");
        String[] addrParts = fullAddr.split("\\s+");
        String buyerAddr = (addrParts.length >= 2) ? addrParts[0] + " " + addrParts[1] : fullAddr;
        buyer.put("businessAddr", buyerAddr.trim());

        buyer.put("buyerType", "VATR");
        buyer.put("msisdn", extractRegex(text, "Customer Account ID:\\s*(\\d{8})"));
        buyer.put("tan", "");

        // Validate BRN
        String brn = extractRegex(text, "Customer BRN:\\s*([A-Z0-9]+)");
        if (!brn.matches("^[A-Z]\\d{8}$")) {
            brn = "";
        }
        buyer.put("brn", brn);
        buyer.put("nic", "");
        invoice.put("buyer", buyer);

        // Item
        Map<String, String> item = new LinkedHashMap<>();
        double unitPrice = extractUnitPrice(text);

        item.put("itemNo", "1");
        item.put("nature", "GOODS");
        item.put("taxCode", "TC01");
        item.put("itemDesc", extractRegex(text, "\\d+\\s+(.+?)\\s+Rs\\.") // fallback
                .replaceAll("\\s+", " ").trim());
        item.put("quantity", "1");
        item.put("unitPrice", format(unitPrice));
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
        Matcher matcher = Pattern.compile(patternStr).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static double parseAmountAfterLabel(String text, String label) {
        Matcher matcher = Pattern.compile(label + ".*?Rs\\.?\\s?(\\d+\\.\\d{3})").matcher(text);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0.0;
    }

    private static double extractUnitPrice(String text) {
        // Case 1: "Sale Goods Rs.20269.565"
        Matcher saleGoods = Pattern.compile("Sale Goods\\s+Rs\\.(\\d+\\.\\d{3})").matcher(text);
        if (saleGoods.find()) {
            return Double.parseDouble(saleGoods.group(1));
        }

        // Case 2: sequence like Rs.xxx Rs.xxx Rs.xxx (use 2nd value)
        Matcher sequence = Pattern.compile("Rs\\.(\\d+\\.\\d{3})\\s+Rs\\.(\\d+\\.\\d{3})").matcher(text);
        if (sequence.find()) {
            return Double.parseDouble(sequence.group(2));
        }

        // Case 3: "New Individual Price Plan Rs.10.434"
        Matcher plan = Pattern.compile("New Individual Price Plan\\s+Rs\\.(\\d+\\.\\d{3})").matcher(text);
        if (plan.find()) {
            return Double.parseDouble(plan.group(1));
        }

        return 0.0;
    }

    private static String format(double value) {
        return String.format("%.5f", Math.abs(value));
    }
}
