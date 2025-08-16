package com.mra.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InterConnectParser {

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

        // Extract values
        String invoiceNumber = getValue(text, "Invoice No\\.:\\s*(\\d+)");
        String vatAmount = getValue(text, "Total VAT\\(@15%\\) amount Rs\\.\\s*([\\d,.]+)");
        String amountWoVat = getValue(text, "Total Amount Due before VAT Rs\\.\\s*([\\d,.]+)");
        String amountWithVat = getValue(text, "Total Amount Due with VAT Rs\\.\\s*([\\d,.]+)");
        String issuedDate = getValue(text, "Invoice Date:\\s*(\\d{2}/\\d{2}/\\d{4})");
        String dueDate = getValue(text, "Invoice Due Date:\\s*(\\d{2}/\\d{2}/\\d{4})"); // ✅ Extract due date

        invoiceNode.put("invoiceIdentifier", invoiceNumber);
        invoiceNode.put("invoiceRefIdentifier", invoiceNumber);
        invoiceNode.put("previousNoteHash", "prevNote");
        invoiceNode.put("reasonStated", "Interconnect Receipt ~ 0");
        invoiceNode.put("salesTransactions", "CASH");
        invoiceNode.put("totalVatAmount", vatAmount);
        invoiceNode.put("totalAmtWoVatCur", amountWoVat);
        invoiceNode.put("totalAmtWoVatMur", amountWoVat);
        invoiceNode.put("invoiceTotal", amountWithVat);
        invoiceNode.put("discountTotalAmount", "0.00");
        invoiceNode.put("totalAmtPaid", amountWithVat);
        invoiceNode.put("dateTimeInvoiceIssued", formatDate(issuedDate));
        invoiceNode.put("invoiceDueDate", formatDate(dueDate)); // ✅ Add due date

        // Calculate surcharge as 10% of totalAmtPaid
        double amtPaid = parseAmount(amountWithVat);
        double surcharge = Math.round(amtPaid * 0.10 * 100.0) / 100.0;
        invoiceNode.put("surchargeAmount", String.format("%.2f", surcharge));

        // Seller details (hardcoded)
        ObjectNode seller = mapper.createObjectNode();
        seller.put("name", "SysAdmin");
        seller.put("tradeName", "MTML");
        seller.put("tan", "20275899");
        seller.put("brn", "C07048459");
        seller.put("businessAddr", "MTML Square, 63 Cybercity");
        seller.put("businessPhoneNo", "12");
        seller.put("ebsCounterNo", "a1");
        invoiceNode.set("seller", seller);

        // Buyer details (dynamic)
        ObjectNode buyer = mapper.createObjectNode();
        String fullName = getValue(text, "Partner name[: ]*([^\n]*)");
        String[] nameParts = fullName.trim().split("\\s+");
        String shortName = nameParts.length >= 2 ? nameParts[0] + " " + nameParts[1] : fullName;
        buyer.put("name", shortName);
        String tanWithPrefix = getValue(text, "Partner VAT Reg\\. No\\.: (VAT\\d{8})");
        String tanDigitsOnly = tanWithPrefix.replaceAll("[^\\d]", ""); // remove non-digits
        buyer.put("tan", tanDigitsOnly);

        buyer.put("brn", getValue(text, "Partner BRN: (C\\d{8})"));
        buyer.put("businessAddr", getValue(text, "Partner Address[: ]*([^\n]*)"));
        buyer.put("buyerType", "VATR");
        buyer.put("nic", "");
        buyer.put("msisdn", "");
        invoiceNode.set("buyer", buyer);

        // Items list
        ArrayNode itemList = mapper.createArrayNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("itemNo", "1");
        item.put("taxCode", "TC01");
        item.put("nature", "GOODS");
        item.put("productCodeMra", "");
        item.put("productCodeOwn", "");
        item.put("itemDesc", "Monthly Fee");
        item.put("quantity", "1");
        item.put("unitPrice", amountWithVat);
        item.put("discount", "0.00");
        item.put("discountedValue", "");
        item.put("amtWoVatCur", amountWoVat);
        item.put("amtWoVatMur", amountWoVat);
        item.put("vatAmt", vatAmount);
        item.put("totalPrice", amountWithVat);
        item.put("previousBalance", "0.00000 ~ 0.00000");
        itemList.add(item);

        invoiceNode.set("itemList", itemList);
        rootArray.add(invoiceNode);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootArray);
    }

    private static String getValue(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).replace(",", "") : "";
    }

    private static String formatDate(String date) {
        if (date == null || date.isEmpty()) return "";
        try {
            String[] parts = date.split("/");
            return parts[2] + parts[1] + parts[0] + " 10:40:30"; // yyyyMMdd HH:mm:ss
        } catch (Exception e) {
            return "20250701 10:40:30";
        }
    }

    private static double parseAmount(String value) {
        try {
            return Double.parseDouble(value.replace(",", ""));
        } catch (Exception e) {
            return 0.0;
        }
    }

    // 🔧 For testing locally
    public static void main(String[] args) {
        String pdfPath = "C:/home/processed/interconnect/DONE_202506012667.pdf";
        String json = parseToJson(pdfPath);
        System.out.println(json);
    }
}
