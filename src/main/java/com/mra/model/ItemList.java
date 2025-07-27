package com.mra.model;

import lombok.Data;

@Data
public class ItemList{
    public String itemNo;
    public String taxCode;
    public String nature;
    public String productCodeMra;
    public String productCodeOwn;
    public String itemDesc;
    public String quantity;
    public String unitPrice;
    public String discount;
    public String discountedValue;
    public String amtWoVatCur;
    public String amtWoVatMur;
    public String vatAmt;
    public String totalPrice;
    // New Field
    private String previousBalance;
}

