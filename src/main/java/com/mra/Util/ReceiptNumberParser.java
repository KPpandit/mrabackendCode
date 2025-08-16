package com.mra.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptNumberParser {

    public static void main(String[] args) {
        String pdfFilePath = "C:\\home\\Processed_Files\\einv\\receipts\\630235891_1753089849000-30235891-Bill Pay-Multi (2).pdf";
        try {
            String finalJson = parsePdfAndGenerateInvoice(pdfFilePath);
            System.out.println("✅ Final JSON Output:\n" + finalJson);
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String parsePdfAndGenerateInvoice(String pdfFilePath) throws Exception {
        StringBuilder allText = new StringBuilder();
        List<String> lines = new ArrayList<>();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(new File(pdfFilePath)))) {
            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc.getPage(i);
                String content = PdfTextExtractor.getTextFromPage(page);
                allText.append(content).append("\n");

                for (String line : content.split("\\r?\\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) lines.add(trimmed);
                }
            }
        }

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Map<String, Object> invoice = new LinkedHashMap<>();

        invoice.put("invoiceCounter", "1");
        invoice.put("transactionType", "B2C");
        invoice.put("personType", "VATR");
        invoice.put("invoiceTypeDesc", "STD");
        invoice.put("currency", "MUR");

        String content = allText.toString();
        String invoiceId = extractRegex(content, "(\\d{8}/\\d{8})");
        invoice.put("invoiceIdentifier", invoiceId);
        invoice.put("invoiceRefIdentifier", invoiceId);
        invoice.put("previousNoteHash", "prevNote");

        // Extract customer account number for reason stated
        String customerAccount = extractCustomerAccount(lines);
        invoice.put("reasonStated", "Receipt Note ~ " + customerAccount);
        invoice.put("salesTransactions", "CASH");

        // Date & Time
        String date = extractRegex(content, "(\\d{4}/\\d{2}/\\d{2})");
        String time = extractRegex(content, "(\\d{2}:\\d{2}:\\d{2})");
        String finalDate = (date + " " + time).replace("/", "");
        invoice.put("dateTimeInvoiceIssued", finalDate);

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
        buyer.put("name", extractCustomerName(lines));
        buyer.put("businessAddr", extractCustomerAddress(lines));
        buyer.put("buyerType", "VATR");

        String mobile = extractMobileNumber(lines);
        buyer.put("msisdn", mobile);

        buyer.put("tan", "");
        buyer.put("brn", "");
        buyer.put("nic", "");
        invoice.put("buyer", buyer);

        // Items
        List<Map<String, String>> itemList = new ArrayList<>();
        double totalAmt = 0, totalVat = 0, totalFinal = 0;
        int itemCounter = 1;

        // Extract balances first
        String preBalance = extractBalance(lines, "pre balance total");
        String postBalance = extractBalance(lines, "post balance");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (line.matches("^.*\\d+\\.\\d+\\s+\\d+\\.\\d+\\s+\\d+\\.\\d+$")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 4) {
                    String total = parts[parts.length - 1];
                    String vat = parts[parts.length - 2];
                    String amount = parts[parts.length - 3];
                    String desc = String.join(" ", Arrays.copyOfRange(parts, 0, parts.length - 3));

                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("itemNo", String.valueOf(itemCounter++));
                    item.put("nature", "GOODS");
                    item.put("taxCode", "TC01");
                    item.put("itemDesc", desc);
                    item.put("quantity", "0");
//                    item.put("unitPrice", total);
                    item.put("unitPrice", amount);
                    item.put("discount", "0.00000");
                    item.put("amtWoVatCur", amount);
                    item.put("amtWoVatMur", amount);
                    item.put("vatAmt", vat);
                    item.put("totalPrice", total);
                    item.put("previousBalance", formatOrDefault(preBalance) + " ~ " + formatOrDefault(postBalance));
                    itemList.add(item);

                    totalAmt += Double.parseDouble(amount);
                    totalVat += Double.parseDouble(vat);
                    totalFinal += Double.parseDouble(total);
                }
            }
        }

        invoice.put("itemList", itemList);
        invoice.put("totalAmtWoVatCur", format(totalAmt));
        invoice.put("totalAmtWoVatMur", format(totalAmt));
        invoice.put("totalVatAmount", format(totalVat));
        invoice.put("invoiceTotal", format(totalFinal));
        invoice.put("discountTotalAmount", "0.00000");
        invoice.put("totalAmtPaid", format(totalFinal));

        return mapper.writeValueAsString(List.of(invoice));
    }

    private static String extractCustomerAccount(List<String> lines) {
        // Case 1: If line 2 contains "customer account", then line 3 is the ID
        if (lines.size() > 2 && lines.get(1).toLowerCase().contains("customer account")) {
            String possibleId = lines.get(2).trim();
            if (possibleId.matches("\\d+")) {
                return possibleId;
            }
        }

        // Case 2: If line 2 itself is a numeric value
        if (lines.size() > 1) {
            String line2 = lines.get(1).trim();
            if (line2.matches("\\d+")) {
                return line2;
            }
        }

        // Case 3: fallback - scan lines for first valid number
        for (String line : lines) {
            Matcher matcher = Pattern.compile("\\b(\\d{4,})\\b").matcher(line);
            if (matcher.find()) return matcher.group(1);
        }

        return "0";
    }


    private static String extractBalance(List<String> lines, String balanceType) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).toLowerCase();
            if (line.contains(balanceType.toLowerCase())) {
                // Check if balance is on the same line
                String balanceLine = lines.get(i);
                Pattern pattern = Pattern.compile("(?i)(?:rs|mru|mur)?\\s*([\\d.,]+)\\s*(?:\\(.*\\))?");
                Matcher matcher = pattern.matcher(balanceLine);

                if (matcher.find()) {
                    return matcher.group(1).replace(",", "");
                }

                // If not found on same line, check next line
                if (i + 1 < lines.size()) {
                    matcher = pattern.matcher(lines.get(i + 1));
                    if (matcher.find()) {
                        return matcher.group(1).replace(",", "");
                    }
                }
            }
        }
        return "0";
    }

    private static String extractMobileNumber(List<String> lines) {
        for (String line : lines) {
            Matcher m = Pattern.compile("\\b(5\\d{7})\\b").matcher(line);
            if (m.find()) return m.group(1);
        }
        return "0";
    }

    private static String extractCustomerName(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).toLowerCase();
            if (line.contains("receipt no")) {
                // The name is on the previous line
                if (i > 0) {
                    String nameLine = lines.get(i - 1).trim();
                    // Clean up the name - remove anything in parentheses and trim
                    return nameLine.replaceAll("\\(.*\\)", "").trim();
                }
            }
        }
        return "UNKNOWN";
    }

    private static String extractCustomerAddress(List<String> lines) {
        // Scenario 1: Multiple items - address appears with date after "Customer Address Receipt Date:"
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (line.toLowerCase().contains("customer address receipt date:")) {
                if (i + 1 < lines.size()) {
                    String addressLine = lines.get(i + 1).trim();
                    // Remove any date pattern (dd/MM/yyyy or similar)
                    addressLine = addressLine.replaceAll("\\d{2}/\\d{2}/\\d{4}", "").trim();
                    // Remove any receipt number pattern if present
                    addressLine = addressLine.replaceAll("\\d{8}/\\d{8}", "").trim();
                    return addressLine.isEmpty() ? "" : addressLine;
                }
                return "";
            }
        }

        // Scenario 2: Single item - address appears between Receipt No and Customer Address
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).toLowerCase().contains("receipt no")) {
                // Look ahead for Customer Address line
                for (int j = i + 1; j < lines.size(); j++) {
                    if (lines.get(j).toLowerCase().contains("customer address")) {
                        // Collect all non-empty lines between receipt number and customer address
                        StringBuilder addressBuilder = new StringBuilder();
                        for (int k = i + 1; k < j; k++) {
                            String potentialLine = lines.get(k).trim();
                            // Skip receipt numbers and empty lines
                            if (!potentialLine.isEmpty() &&
                                    !potentialLine.matches(".*\\d{8}/\\d{8}.*")) {
                                if (addressBuilder.length() > 0) {
                                    addressBuilder.append(" ");
                                }
                                addressBuilder.append(potentialLine);
                            }
                        }
                        return addressBuilder.toString();
                    }
                }
            }
        }

        // Scenario 3: No address found
        return "";
    }

    private static String getFirstThreeWords(String text) {
        if (text == null || text.isEmpty()) return "";
        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(3, words.length); i++) {
            if (i > 0) result.append(" ");
            result.append(words[i]);
        }
        return result.toString();
    }

    private static String extractRegex(String text, String patternStr) {
        Matcher matcher = Pattern.compile(patternStr).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String format(double value) {
        return String.format("%.5f", Math.abs(value));
    }

    private static String formatOrDefault(String value) {
        try {
            return String.format("%.5f", Double.parseDouble(value));
        } catch (Exception e) {
            return "0.00000";
        }
    }
}