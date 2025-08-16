package com.mra.model;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import javax.persistence.*;


/**
 * The persistent class for the invoices database table.
 * 
 */
@Entity
@Table(name="invoices")
@NamedQuery(name="Invoice.findAll", query="SELECT i FROM Invoice i")
public class Invoice implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private int invoiceId;

	private String buyerBrn;

	private String buyerbusinessAddr;

	private String buyerName;

	private String buyerNic;

	private String buyerTan;

	private String buyerType;

	private String buyerMsisdn;

	private String currency;

	private String dateTimeInvoiceIssued;

	private String discountTotalAmount;
//	@Column(name = "invoiceIndentifier", unique = true, nullable = false)
	private String invoiceIndentifier;
	private String invoiceRefIdentifier;
	private String invoicePath;

	private String invoiceResponse;

	private String invoiceTotal;

	private String invoiceTypeDesc;

	private String personType;

	private String prevNoteHash;

	@Temporal(TemporalType.TIMESTAMP)
	private Date processingDateTime;

	private String invoiceDueDate;

	public String getInvoiceRefIndentifier() {
		return invoiceRefIdentifier;
	}

	public void setInvoiceRefIndentifier(String invoiceRefIndentifier) {
		this.invoiceRefIdentifier = invoiceRefIndentifier;
	}

	public String getInvoiceDueDate() {
		return invoiceDueDate;
	}

	public void setInvoiceDueDate(String invoiceDueDate) {
		this.invoiceDueDate = invoiceDueDate;
	}

	public String getSurchargeAmount() {
		return surchargeAmount;
	}

	public void setSurchargeAmount(String surchargeAmount) {
		this.surchargeAmount = surchargeAmount;
	}

	public boolean isProcessStatus() {
		return processStatus;
	}

//	public List<Products> getProductsInvoices() {
//		return productsInvoices;
//	}

//	public void setProductsInvoices(List<Products> productsInvoices) {
//		this.productsInvoices = productsInvoices;
//	}

	public String surchargeAmount;

	private boolean processStatus;

	private String reasonStated;

	private String sellerBrn;

	private String sellerbusinessAddr;

	private String sellerbusinessPhoneNo;

	private String sellerebsCounterNo;

	private String sellerName;

	private String sellerTan;

	private String sellerTradeName;

	private String totalAmtPaid;

	private String totalAmtWoVatCur;

	private String totalAmtWoVatMur;

	private String totalVatAmount;

	private String txnType;

	//bi-directional many-to-one association to Products
	@OneToMany(mappedBy="invoice")
	private List<Products> productsInvoices;

	public Invoice() {
	}
	public String getBuyerMsisdn() {
		return buyerMsisdn;
	}

	public void setBuyerMsisdn(String buyerMsisdn) {
		this.buyerMsisdn = buyerMsisdn;
	}

	public int getInvoiceId() {
		return this.invoiceId;
	}

	public void setInvoiceId(int invoiceId) {
		this.invoiceId = invoiceId;
	}

	public String getBuyerBrn() {
		return this.buyerBrn;
	}

	public void setBuyerBrn(String buyerBrn) {
		this.buyerBrn = buyerBrn;
	}

	public String getBuyerbusinessAddr() {
		return this.buyerbusinessAddr;
	}

	public void setBuyerbusinessAddr(String buyerbusinessAddr) {
		this.buyerbusinessAddr = buyerbusinessAddr;
	}

	public String getBuyerName() {
		return this.buyerName;
	}

	public void setBuyerName(String buyerName) {
		this.buyerName = buyerName;
	}

	public String getBuyerNic() {
		return this.buyerNic;
	}

	public void setBuyerNic(String buyerNic) {
		this.buyerNic = buyerNic;
	}

	public String getBuyerTan() {
		return this.buyerTan;
	}

	public void setBuyerTan(String buyerTan) {
		this.buyerTan = buyerTan;
	}

	public String getBuyerType() {
		return this.buyerType;
	}

	public void setBuyerType(String buyerType) {
		this.buyerType = buyerType;
	}

	public String getCurrency() {
		return this.currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public String getDateTimeInvoiceIssued() {
		return this.dateTimeInvoiceIssued;
	}

	public void setDateTimeInvoiceIssued(String dateTimeInvoiceIssued) {
		this.dateTimeInvoiceIssued = dateTimeInvoiceIssued;
	}

	public String getDiscountTotalAmount() {
		return this.discountTotalAmount;
	}

	public void setDiscountTotalAmount(String discountTotalAmount) {
		this.discountTotalAmount = discountTotalAmount;
	}

	public String getInvoiceIndentifier() {
		return this.invoiceIndentifier;
	}

	public void setInvoiceIndentifier(String invoiceIndentifier) {
		this.invoiceIndentifier = invoiceIndentifier;
	}

	public String getInvoicePath() {
		return this.invoicePath;
	}

	public void setInvoicePath(String invoicePath) {
		this.invoicePath = invoicePath;
	}

	public String getInvoiceResponse() {
		return this.invoiceResponse;
	}

	public void setInvoiceResponse(String invoiceResponse) {
		this.invoiceResponse = invoiceResponse;
	}

	public String getInvoiceTotal() {
		return this.invoiceTotal;
	}

	public void setInvoiceTotal(String invoiceTotal) {
		this.invoiceTotal = invoiceTotal;
	}

	public String getInvoiceTypeDesc() {
		return this.invoiceTypeDesc;
	}

	public void setInvoiceTypeDesc(String invoiceTypeDesc) {
		this.invoiceTypeDesc = invoiceTypeDesc;
	}

	public String getPersonType() {
		return this.personType;
	}

	public void setPersonType(String personType) {
		this.personType = personType;
	}

	public String getPrevNoteHash() {
		return this.prevNoteHash;
	}

	public void setPrevNoteHash(String prevNoteHash) {
		this.prevNoteHash = prevNoteHash;
	}

	public Date getProcessingDateTime() {
		return this.processingDateTime;
	}

	public void setProcessingDateTime(Date processingDateTime) {
		this.processingDateTime = processingDateTime;
	}

	public boolean getProcessStatus() {
		return this.processStatus;
	}

	public void setProcessStatus(boolean processStatus) {
		this.processStatus = processStatus;
	}

	public String getReasonStated() {
		return this.reasonStated;
	}

	public void setReasonStated(String reasonStated) {
		this.reasonStated = reasonStated;
	}

	public String getSellerBrn() {
		return this.sellerBrn;
	}

	public void setSellerBrn(String sellerBrn) {
		this.sellerBrn = sellerBrn;
	}

	public String getSellerbusinessAddr() {
		return this.sellerbusinessAddr;
	}

	public void setSellerbusinessAddr(String sellerbusinessAddr) {
		this.sellerbusinessAddr = sellerbusinessAddr;
	}

	public String getSellerbusinessPhoneNo() {
		return this.sellerbusinessPhoneNo;
	}

	public void setSellerbusinessPhoneNo(String sellerbusinessPhoneNo) {
		this.sellerbusinessPhoneNo = sellerbusinessPhoneNo;
	}

	public String getSellerebsCounterNo() {
		return this.sellerebsCounterNo;
	}

	public void setSellerebsCounterNo(String sellerebsCounterNo) {
		this.sellerebsCounterNo = sellerebsCounterNo;
	}

	public String getSellerName() {
		return this.sellerName;
	}

	public void setSellerName(String sellerName) {
		this.sellerName = sellerName;
	}

	public String getSellerTan() {
		return this.sellerTan;
	}

	public void setSellerTan(String sellerTan) {
		this.sellerTan = sellerTan;
	}

	public String getSellerTradeName() {
		return this.sellerTradeName;
	}

	public void setSellerTradeName(String sellerTradeName) {
		this.sellerTradeName = sellerTradeName;
	}

	public String getTotalAmtPaid() {
		return this.totalAmtPaid;
	}

	public void setTotalAmtPaid(String totalAmtPaid) {
		this.totalAmtPaid = totalAmtPaid;
	}

	public String getTotalAmtWoVatCur() {
		return this.totalAmtWoVatCur;
	}

	public void setTotalAmtWoVatCur(String totalAmtWoVatCur) {
		this.totalAmtWoVatCur = totalAmtWoVatCur;
	}

	public String getTotalAmtWoVatMur() {
		return this.totalAmtWoVatMur;
	}

	public void setTotalAmtWoVatMur(String totalAmtWoVatMur) {
		this.totalAmtWoVatMur = totalAmtWoVatMur;
	}

	public String getTotalVatAmount() {
		return this.totalVatAmount;
	}

	public void setTotalVatAmount(String totalVatAmount) {
		this.totalVatAmount = totalVatAmount;
	}

	public String getTxnType() {
		return this.txnType;
	}

	public void setTxnType(String txnType) {
		this.txnType = txnType;
	}

	public List<Products> getProducts() {
		return this.productsInvoices;
	}

	public void setProducts(List<Products> productsInvoices) {
		this.productsInvoices = productsInvoices;
	}

	public Products addProducts(Products productsInvoice) {
		getProducts().add(productsInvoice);
		productsInvoice.setInvoice(this);

		return productsInvoice;
	}

	public Products removeProducts(Products productsInvoice) {
		getProducts().remove(productsInvoice);
		productsInvoice.setInvoice(null);

		return productsInvoice;
	}

}