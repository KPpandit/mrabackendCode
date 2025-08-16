package com.mra.Util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.util.*;

public class ReceiptJsonParser {

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Load the JSON array from file
        List<JsonNode> receipts = mapper.readValue(new File("receipts.json"), new TypeReference<>() {});

        for (JsonNode receipt : receipts) {
            JsonNode content = receipt.get("content");

            boolean startCollecting = false;
            List<String> itemLines = new ArrayList<>();

            for (int i = 1; i <= content.size(); i++) {
                String value = content.path(String.valueOf(i)).asText().trim();

                if (value.equals("No. Item Type Description Amount VAT Total")) {
                    startCollecting = true;
                    continue;
                }

                if (value.startsWith("Pre Balance Total:")) {
                    break;
                }

                if (startCollecting) {
                    itemLines.add(value);
                }
            }

            List<ObjectNode> parsedItems = new ArrayList<>();

            for (int i = 0; i < itemLines.size(); i++) {
                String line = itemLines.get(i).trim();
                if (line.matches("^\\d+$") && (i + 1 < itemLines.size())) {
                    String itemDesc = itemLines.get(i + 1);
                    ObjectNode itemJson = parseItemJson(line, itemDesc, mapper);
                    parsedItems.add(itemJson);
                    i++; // Skip next line
                } else if (line.matches("^\\d+\\s+.*\\s+[\\d.]+\\s+[\\d.]+\\s+[\\d.]+$")) {
                    // Line starts with number and ends with three numbers — directly parse
                    String[] parts = line.split("\\s+");
                    String no = parts[0];
                    String amount = parts[parts.length - 3];
                    String vat = parts[parts.length - 2];
                    String total = parts[parts.length - 1];
                    String itemType = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length - 3));

                    ObjectNode itemJson = mapper.createObjectNode();
                    itemJson.put("No", no);
                    itemJson.put("Item", itemType);
                    itemJson.put("Type", "");
                    itemJson.put("Description", "");
                    itemJson.put("Amount", amount);
                    itemJson.put("VAT", vat);
                    itemJson.put("Total", total);
                    parsedItems.add(itemJson);
                }
            }

            // Print output JSON
            String output = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedItems);
            System.out.println("==== JSON Items ====");
            System.out.println(output);
        }
    }

    private static ObjectNode parseItemJson(String no, String line, ObjectMapper mapper) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 4) {
            return mapper.createObjectNode(); // Invalid item
        }

        String amount = parts[parts.length - 3];
        String vat = parts[parts.length - 2];
        String total = parts[parts.length - 1];
        String item = parts[0];
        String type = (parts.length > 4) ? parts[1] : "";
        String description = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length - 3));

        ObjectNode node = mapper.createObjectNode();
        node.put("No", no);
        node.put("Item", item);
        node.put("Type", type);
        node.put("Description", description);
        node.put("Amount", amount);
        node.put("VAT", vat);
        node.put("Total", total);
        return node;
    }
}