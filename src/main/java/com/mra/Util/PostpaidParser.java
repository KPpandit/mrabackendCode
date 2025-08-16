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

public class PostpaidParser {

    public static String parsePdfToInvoiceJson(String pdfFilePath) {
        try {
            StringBuilder rawText = new StringBuilder();
            PdfDocument pdfDoc = new PdfDocument(new PdfReader(new File(pdfFilePath)));
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

        // Extract core invoice details
        invoiceNode.put("invoiceIdentifier", extractValue(text, "Bill No\\.?\\s*:?\\s*(\\d{14,})"));
        invoiceNode.put("invoiceRefIdentifier", extractValue(text, "Bill No\\.?\\s*:?\\s*(\\d{14,})"));
        invoiceNode.put("previousNoteHash", "prevNote");
        invoiceNode.put("reasonStated", "Postpaid invoice Receipt ~ 0");
        invoiceNode.put("salesTransactions", "CASH");

        String vatAmount = extractLastDecimal(text, "VAT\\(@15.*?\\)\\s*Rs.?\\s*(\\d+\\.\\d+)");
        String amtBeforeVat = extractLastDecimal(text, "Total Amount Before VAT Rs.?\\s*(\\d+\\.\\d+)");
        String totalAfterVat = extractLastDecimal(text, "Total Amount After VAT Rs.?\\s*(\\d+\\.\\d+)");

        invoiceNode.put("totalVatAmount", vatAmount);
        invoiceNode.put("totalAmtWoVatCur", amtBeforeVat);
        invoiceNode.put("totalAmtWoVatMur", amtBeforeVat);
        invoiceNode.put("invoiceTotal", totalAfterVat);
        invoiceNode.put("discountTotalAmount", "0.00");
        invoiceNode.put("totalAmtPaid", totalAfterVat);

        // Add invoice due date
        String dueDate = extractValue(text, "Bill Due Date:?\\s*(\\d{2}/\\d{2}/\\d{4})");
        invoiceNode.put("invoiceDueDate", formatDate(dueDate));

        System.out.println(" --- Total VAT --- "+totalAfterVat);
        String totalAmtPaidStr = totalAfterVat;
        double surchargeAmountDouble = 0.0;
        try {
            surchargeAmountDouble = Double.parseDouble(totalAmtPaidStr.replace(",", ""));
            surchargeAmountDouble = Math.round(surchargeAmountDouble * 0.10 * 100.0) / 100.0;
        } catch (Exception e) {
            surchargeAmountDouble = 0.0;
        }
        invoiceNode.put("surchargeAmount", String.format("%.2f", surchargeAmountDouble));

        // Date issued
        String billDate = extractValue(text, "Bill Date:?\\s*(\\d{2}/\\d{2}/\\d{4})");
        invoiceNode.put("dateTimeInvoiceIssued", formatDate(billDate));

        // Seller (hardcoded)
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
        buyer.put("name", extractValue(text, "Customer Name:?\\s*([A-Z ]+\\d*)"));
        buyer.put("tan", "");
        buyer.put("brn", extractValue(text, "BRN:?\\s*([A-Z0-9]+)"));
        buyer.put("businessAddr", extractValue(text, "Customer Address:?\\s*([^\n]*)"));
        buyer.put("buyerType", "VATR");
        buyer.put("nic", "");
        buyer.put("msisdn", extractValue(text, "Customer MTML Number:?\\s*(\\d{8})"));
        invoiceNode.set("buyer", buyer);

        // Items
        ArrayNode itemList = mapper.createArrayNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("itemNo", "1");
        item.put("taxCode", "TC01");
        item.put("nature", "SERVICES");
        item.put("productCodeMra", "");
        item.put("productCodeOwn", "");
        item.put("itemDesc", "Postpaid Mobile Bill ~ " + extractValue(text, "Bill Month:?\\s*([A-Z]+\\s+\\d{4})"));
        item.put("quantity", "1");
        item.put("unitPrice", amtBeforeVat);
        item.put("discount", "0.00");
        item.put("discountedValue", "");
        item.put("amtWoVatCur", amtBeforeVat);
        item.put("amtWoVatMur", amtBeforeVat);
        item.put("vatAmt", vatAmount);
        item.put("totalPrice", totalAfterVat);
        item.put("previousBalance", extractPreviousBalance(text));
        itemList.add(item);

        invoiceNode.set("itemList", itemList);
        rootArray.add(invoiceNode);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootArray);
    }

    private static String extractValue(String text, String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String extractLastDecimal(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        String lastMatch = "";
        while (matcher.find()) {
            lastMatch = matcher.group(1);
        }
        return lastMatch.isEmpty() ? "0.00" : lastMatch.replace(",", "");
    }

    private static String extractPreviousBalance(String text) {
        Pattern pattern = Pattern.compile("Prevoious Balance\\s*Rs.?\\s*(-?\\d+[.,]?\\d*)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        String lastMatch = "";
        while (matcher.find()) {
            lastMatch = matcher.group(1);
        }
        return lastMatch.isEmpty() ? "0.00" : lastMatch.replace(",", "");
    }

    private static String formatDate(String inputDate) {
        if (inputDate == null || inputDate.isEmpty()) return "";
        try {
            String[] parts = inputDate.split("/");
            return parts[2] + parts[1] + parts[0] + " 10:40:30"; // yyyyMMdd HH:mm:ss
        } catch (Exception e) {
            return "20250701 10:40:30";
        }
    }

    // 🔍 Local test
    public static void main(String[] args) {
        String pdfPath = "C:\\home\\Processed_Files\\einv\\bills\\DONE_10118.pdf";
        String json = parsePdfToInvoiceJson(pdfPath);
        System.out.println(json);
    }
}
