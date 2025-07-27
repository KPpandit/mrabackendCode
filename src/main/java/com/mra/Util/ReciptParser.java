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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReciptParser {

    public static void main(String[] args) {
        String pdfPath = "C:/Users/Krishna Purohit/Downloads/receipts_194023_1739177951000.pdf"; // change path as needed
        try {
            String json = parsePdfToInvoiceJson(pdfPath);
            System.out.println("\n✅ MRA Invoice JSON:\n" + json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String parsePdfToInvoiceJson(String pdfFilePath) throws Exception {
        StringBuilder rawText = new StringBuilder();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(new File(pdfFilePath)))) {
            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc.getPage(i);
                rawText.append(PdfTextExtractor.getTextFromPage(page)).append("\n");
            }
        }

        String text = rawText.toString();
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ArrayNode rootArray = mapper.createArrayNode();
        ObjectNode invoiceNode = mapper.createObjectNode();

        // Header
        invoiceNode.put("invoiceCounter", "1");
        invoiceNode.put("transactionType", "B2C");
        invoiceNode.put("personType", "VATR");
        invoiceNode.put("invoiceTypeDesc", "CRN");
        invoiceNode.put("currency", "MUR");

        String invoiceId = getValue(text, "Receipt No\\.?\\s*(\\d{8}/\\d{8})");
        invoiceNode.put("invoiceIdentifier",  invoiceId);
        invoiceNode.put("invoiceRefIdentifier",  invoiceId);
        invoiceNode.put("previousNoteHash", "prevNote");
        invoiceNode.put("reasonStated", "Recharge Reversal Credit Note");
        invoiceNode.put("salesTransactions", "CASH");

        String[] amounts = extractRechargeAmounts(text); // [woVAT, VAT, total]
        invoiceNode.put("totalVatAmount", formatAmount(amounts[1]));
        invoiceNode.put("totalAmtWoVatCur", formatAmount(amounts[0]));
        invoiceNode.put("totalAmtWoVatMur", formatAmount(amounts[0]));
        invoiceNode.put("invoiceTotal", formatAmount(amounts[2]));
//        invoiceNode.put("totalAmountPayable", formatAmount(amounts[2]));
        invoiceNode.put("discountTotalAmount", "0.00000");
        invoiceNode.put("totalAmtPaid", formatAmount(amounts[2]));

        invoiceNode.put("dateTimeInvoiceIssued", getDateTime(text));

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
        buyer.put("name", extractBuyerName(text));
        buyer.put("tan", "");
        buyer.put("brn", "");
        buyer.put("businessAddr", "Port Louis");
        buyer.put("buyerType", "VATR");
        buyer.put("nic", "");
        buyer.put("msisdn", extractBuyerMobile(text));
        invoiceNode.set("buyer", buyer);

        // Item
        ArrayNode itemList = mapper.createArrayNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("itemNo", "1");
        item.put("taxCode", "TC01");
        item.put("nature", "GOODS");
        item.put("productCodeMra", "");
        item.put("productCodeOwn", "");
        item.put("itemDesc", "Reversal");
        item.put("quantity", "1");
        item.put("unitPrice", formatAmount(amounts[2]));
        item.put("discount", "0.00000");
        item.put("discountedValue", "");
        item.put("amtWoVatCur", formatAmount(amounts[0]));
        item.put("amtWoVatMur", formatAmount(amounts[0]));
        item.put("vatAmt", formatAmount(amounts[1]));
        item.put("totalPrice", formatAmount(amounts[2]));
        item.put("previousBalance", "0");
        itemList.add(item);
        invoiceNode.set("itemList", itemList);

        rootArray.add(invoiceNode);
        return mapper.writeValueAsString(rootArray);
    }

    private static String extractBuyerName(String text) {
        // Match "Customer Name  :" then collect the next non-empty line
        Pattern pattern = Pattern.compile("Customer Name\\s*:?[\\s\\n]+([A-Z][\\w\\s\\-\\(\\)]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "Unknown";
    }

    private static String extractBuyerMobile(String text) {
        // 8-digit numbers near 'Mobile' or inside parentheses
        Matcher matcher = Pattern.compile("(\\d{8})(?!.*\\d{8})").matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String getValue(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String[] extractRechargeAmounts(String text) {
        // Match three numbers: VAT, Total, and WithoutVAT in any order
        Matcher matcher = Pattern.compile("(\\d+\\.\\d+)\\s+(\\d+\\.\\d+)\\s+(\\d+\\.\\d+)").matcher(text);
        while (matcher.find()) {
            double[] values = new double[]{
                    Double.parseDouble(matcher.group(1)),
                    Double.parseDouble(matcher.group(2)),
                    Double.parseDouble(matcher.group(3))
            };
            double total = Math.max(Math.max(values[0], values[1]), values[2]);
            double vat = 0, woVat = 0;

            for (double v : values) {
                if (v != total) {
                    if (vat == 0) vat = v;
                    else woVat = v;
                }
            }
            if (woVat > vat) {
                double temp = vat;
                vat = woVat;
                woVat = temp;
            }
            return new String[]{
                    String.valueOf(woVat),
                    String.valueOf(vat),
                    String.valueOf(total)
            };
        }
        return new String[]{"0.00000", "0.00000", "0.00000"};
    }

    private static String getDateTime(String text) {
        String date = getValue(text, "(\\d{4}/\\d{2}/\\d{2})");
        String time = getValue(text, "(\\d{2}:\\d{2}:\\d{2})");
        if (!date.isEmpty() && !time.isEmpty()) {
            return date.replace("/", "") + " " + time;
        }
        return "20250101 00:00:00";
    }

    private static String formatAmount(String amountStr) {
        try {
            double value = Math.abs(Double.parseDouble(amountStr));
            return String.format("%.5f", value);
        } catch (NumberFormatException e) {
            return "0.00000";
        }
    }
}
