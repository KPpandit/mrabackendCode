package com.mra.model;

import lombok.Data;

import java.util.List;
@Data
public class CurrentInvoiceBean {
    private String invoiceCounter;
    private String transactionType;
    private String personType;
    private String invoiceTypeDesc;
    private String currency;
    private String invoiceIdentifier;
    private String invoiceRefIdentifier;
    private String previousNoteHash;
    private String reasonStated;
    private String salesTransactions;
    private String totalVatAmount;
    private String totalAmtWoVatCur;
    private String totalAmtWoVatMur;
    private String invoiceTotal;
    private String discountTotalAmount;
    private String totalAmtPaid;
    private String dateTimeInvoiceIssued;

    private Seller seller;
    private Buyer buyer;
    private List<Item> itemList;

    @Data
    public static class Seller {
        private String name;
        private String tradeName;
        private String tan;
        private String brn;
        private String businessAddr;
        private String businessPhoneNo;
        private String ebsCounterNo;

    }
    @Data
    public static class Buyer {
        private String name;
        private String tan;
        private String brn;
        private String businessAddr;
        private String buyerType;
        private String nic;
        // getters and setters
    }
    @Data
    public static class Item {
        private String itemNo;
        private String taxCode;
        private String nature;
        private String productCodeMra;
        private String productCodeOwn;
        private String itemDesc;
        private String quantity;
        private String unitPrice;
        private String discount;
        private String discountedValue;
        private String amtWoVatCur;
        private String amtWoVatMur;
        private String vatAmt;
        private String totalPrice;

    }
}
