package com.mra.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.io.Serializable;

import javax.persistence.*;

/**
 * The persistent class for the products_invoice database table.
 *
 */
@Entity
@Table(name="products")
@NamedQuery(name="Products.findAll", query="SELECT p FROM Products p")
public class Products implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private int productId;

	private String amtWoVatCur;

	private String amtWoVatMur;

	private String discount;

	private String discountedValue;

	private String itemDesc;

	private String nature;

	private String productCodeMra;

	private String productCodeOwn;

	private String quantity;

	private String taxCode;

	private String totalPrice;

	private String unitPrice;

	private String vatAmt;

	//  New Field
	private String previousBalance;

	//bi-directional many-to-one association to Invoice
	@ManyToOne
	@JoinColumn(name="invoiceId")
	@JsonIgnore
	private Invoice invoice;

	public Products() {
	}

	// getters & setters

	public int getProductId() {
		return this.productId;
	}

	public void setProductId(int productId) {
		this.productId = productId;
	}

	public String getAmtWoVatCur() {
		return this.amtWoVatCur;
	}

	public void setAmtWoVatCur(String amtWoVatCur) {
		this.amtWoVatCur = amtWoVatCur;
	}

	public String getAmtWoVatMur() {
		return this.amtWoVatMur;
	}

	public void setAmtWoVatMur(String amtWoVatMur) {
		this.amtWoVatMur = amtWoVatMur;
	}

	public String getDiscount() {
		return this.discount;
	}

	public void setDiscount(String discount) {
		this.discount = discount;
	}

	public String getDiscountedValue() {
		return this.discountedValue;
	}

	public void setDiscountedValue(String discountedValue) {
		this.discountedValue = discountedValue;
	}

	public String getItemDesc() {
		return this.itemDesc;
	}

	public void setItemDesc(String itemDesc) {
		this.itemDesc = itemDesc;
	}

	public String getNature() {
		return this.nature;
	}

	public void setNature(String nature) {
		this.nature = nature;
	}

	public String getProductCodeMra() {
		return this.productCodeMra;
	}

	public void setProductCodeMra(String productCodeMra) {
		this.productCodeMra = productCodeMra;
	}

	public String getProductCodeOwn() {
		return this.productCodeOwn;
	}

	public void setProductCodeOwn(String productCodeOwn) {
		this.productCodeOwn = productCodeOwn;
	}

	public String getQuantity() {
		return this.quantity;
	}

	public void setQuantity(String quantity) {
		this.quantity = quantity;
	}

	public String getTaxCode() {
		return this.taxCode;
	}

	public void setTaxCode(String taxCode) {
		this.taxCode = taxCode;
	}

	public String getTotalPrice() {
		return this.totalPrice;
	}

	public void setTotalPrice(String totalPrice) {
		this.totalPrice = totalPrice;
	}

	public String getUnitPrice() {
		return this.unitPrice;
	}

	public void setUnitPrice(String unitPrice) {
		this.unitPrice = unitPrice;
	}

	public String getVatAmt() {
		return this.vatAmt;
	}

	public void setVatAmt(String vatAmt) {
		this.vatAmt = vatAmt;
	}

	// ✅ Getter and Setter for previousBalance
	public String getPreviousBalance() {
		return previousBalance;
	}

	public void setPreviousBalance(String previousBalance) {
		this.previousBalance = previousBalance;
	}

	public Invoice getInvoice() {
		return this.invoice;
	}

	public void setInvoice(Invoice invoice) {
		this.invoice = invoice;
	}
}
