package com.mra.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentReversalParser {

    public static String parseToJson(String pdfFilePath) {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(pdfFilePath))) {
            StringBuilder rawText = new StringBuilder();
            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc.getPage(i);
                rawText.append(PdfTextExtractor.getTextFromPage(page)).append("\n");
            }
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
        invoiceNode.put("invoiceTypeDesc", "CRN");
        invoiceNode.put("currency", "MUR");

        String invoiceId = getValue(text, "Receipt No\\.?\\s*[:\\s]*([\\d/]+)");
        invoiceNode.put("invoiceIdentifier", "R_" + invoiceId);
        invoiceNode.put("invoiceRefIdentifier",  invoiceId);
        invoiceNode.put("previousNoteHash", "prevNote");
        invoiceNode.put("reasonStated", "Payment Reversal Receipt ~ 0");
        invoiceNode.put("salesTransactions", "CASH");

        String vat = getAmount(text, 1);
        String amtWoVat = getAmount(text, 0);
        String total = getAmount(text, 2);
        String paid = formatAmount(getValue(text, "Paid Total:?\\s*Rs\\s*(-?\\d+\\.\\d{1,10})"));

        invoiceNode.put("totalVatAmount", vat);
        invoiceNode.put("totalAmtWoVatCur", amtWoVat);
        invoiceNode.put("totalAmtWoVatMur", amtWoVat);
        invoiceNode.put("invoiceTotal", total);
        invoiceNode.put("discountTotalAmount", formatAmount("0.0"));
        invoiceNode.put("totalAmtPaid", paid);

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
        String buyerName = getBuyerName(text);
        buyer.put("name", buyerName);
        buyer.put("tan", "");
        buyer.put("brn", "");
        buyer.put("businessAddr", "Port Louis");
        buyer.put("buyerType", "VATR");
        buyer.put("nic", "");
        buyer.put("msisdn", getValue(text, "Mobile Number:?\\s*(\\d{8})"));
        invoiceNode.set("buyer", buyer);

        // Item List
        ArrayNode itemList = mapper.createArrayNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("itemNo", "1");
        item.put("taxCode", "TC01");
        item.put("nature", "GOODS");
        item.put("productCodeMra", "");
        item.put("productCodeOwn", "");
        item.put("itemDesc", "Reversal");
        item.put("quantity", "1");
        item.put("unitPrice", total);
        item.put("discount", formatAmount("0.0"));
        item.put("discountedValue", "");
        item.put("amtWoVatCur", amtWoVat);
        item.put("amtWoVatMur", amtWoVat);
        item.put("vatAmt", vat);
        item.put("totalPrice", total);

        // ✅ Extract post balance from: Date: Rs 261.140 (Credit)
        String postBalance = getValue(text, "Date:?\\s*Rs\\s*(-?\\d+\\.\\d{1,10})\\s*\\(Credit\\)");
        item.put("previousBalance", formatAmount(postBalance));

        itemList.add(item);
        invoiceNode.set("itemList", itemList);
        rootArray.add(invoiceNode);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootArray);
    }

    private static String getValue(String text, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String getAmount(String text, int position) {
        Pattern pattern = Pattern.compile("-?\\d+\\.\\d{1,10}");
        Matcher matcher = pattern.matcher(text);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group());
        }
        return (values.size() > position) ? formatAmount(values.get(position)) : formatAmount("0.0");
    }

    private static String formatAmount(String raw) {
        try {
            double value = Math.abs(Double.parseDouble(raw));
            return String.format("%.5f", value);
        } catch (NumberFormatException e) {
            return "0.00000";
        }
    }

    private static String getDateTime(String text) {
        String date = getValue(text, "Date:?\\s*(\\d{4}/\\d{2}/\\d{2})");
        String time = getValue(text, "(\\d{2}:\\d{2}:\\d{2})");
        if (!date.isEmpty() && !time.isEmpty()) {
            return date.replace("/", "") + " " + time;
        }
        return "20250101 00:00:00";
    }

    private static String getBuyerName(String text) {
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toLowerCase().contains("customer name")) {
                int j = i + 1;
                // Skip emails
                while (j < lines.length && lines[j].contains("@")) {
                    j++;
                }
                if (j < lines.length) {
                    String name = lines[j].trim();
                    String[] words = name.split("\\s+");
                    return words.length >= 2 ? words[0] + " " + words[1] : words[0];
                }
            }
        }
        return "Unknown";
    }


    public static void main(String[] args) {
        String path = "C:\\home\\Processed_Files\\einv\\payment_reversal\\DONE_09b46caa-efb7-441a-b259-b2d448de2ce4.pdf";
        String json = parseToJson(path);
        System.out.println(json);
    }
}
