package com.mra.Util.mra.Util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.regex.*;

public class ReciptVatItem {

    public static List<ObjectNode> parseVatItemsFromContent(JsonNode content) {
        ObjectMapper mapper = new ObjectMapper();
        List<String> lines = new ArrayList<>();

        for (int i = 1; i <= content.size(); i++) {
            lines.add(content.path(String.valueOf(i)).asText().trim());
        }

        List<ObjectNode> items = new ArrayList<>();
        int i = 0, itemNo = 1;

        while (i < lines.size()) {
            String line = lines.get(i);

            if (line.matches("^\\d+\\s+.*\\s+[\\d.]+\\s+[\\d.]+\\s+[\\d.]+$")) {
                String[] parts = line.split("\\s+");
                String no = parts[0];
                String amount = parts[parts.length - 3];
                String vat = parts[parts.length - 2];
                String total = parts[parts.length - 1];
                String itemType = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length - 3));

                ObjectNode item = mapper.createObjectNode();
                item.put("No", no);
                item.put("Item", itemType);
                item.put("Type", "");
                item.put("Description", "");
                item.put("Amount", amount);
                item.put("VAT", vat);
                item.put("Total", total);
                items.add(item);
                i++;
            } else if (line.matches("^\\d+$") && (i + 3 < lines.size())) {
                String no = line;
                StringBuilder description = new StringBuilder();
                double amount = 0.0, vat = 0.0, total = 0.0;
                int consumed = 0;

                for (int j = i + 1; j <= i + 5 && j < lines.size(); j++) {
                    String next = lines.get(j);
                    Matcher m = Pattern.compile("Rs\\.?\\s?(\\d+\\.\\d{1,3})").matcher(next);

                    if (m.find()) {
                        List<Double> values = new ArrayList<>();
                        m.reset();
                        while (m.find()) {
                            values.add(Double.parseDouble(m.group(1)));
                        }

                        if (values.size() == 3) {
                            amount = values.get(0);
                            vat = values.get(1);
                            total = values.get(2);
                            consumed = j - i;
                            break;
                        } else if (values.size() == 1) {
                            total = values.get(0);
                            consumed = j - i;
                        }
                    } else {
                        description.append(next).append(" ");
                    }
                }

                ObjectNode item = mapper.createObjectNode();
                item.put("No", no);
                item.put("Item", "Bundle/Plan");
                item.put("Type", "");
                item.put("Description", description.toString().trim());
                item.put("Amount", String.format("%.3f", amount));
                item.put("VAT", String.format("%.3f", vat));
                item.put("Total", String.format("%.3f", total));
                items.add(item);
                i += consumed + 1;
            } else {
                i++;
            }
        }

        return items;
    }
}
