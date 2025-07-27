package com.mra.model;

import java.util.ArrayList;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class InvoiceBean{
    public String invoiceCounter;
    public String transactionType;
    public String personType;
    public String invoiceTypeDesc;
    public String currency;
    public String invoiceIdentifier;
    public String invoiceRefIdentifier;
    public String previousNoteHash;
    public String reasonStated;
    public String totalVatAmount;
    public String totalAmtWoVatCur;
    public String totalAmtWoVatMur;
    public String invoiceTotal;
    public String discountTotalAmount;
    public String totalAmtPaid;
    public String dateTimeInvoiceIssued;
    public Seller seller;
    public Buyer buyer;
    public ArrayList<ItemList> itemList;
    public String salesTransactions;
    public String surchargeAmount;
    public String invoicePath;
    public String invoiceDueDate;
}

