package com.mra.controller;

import java.util.Iterator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.mra.model.InvoiceBean;
import com.mra.service.MRAService;

@RestController
@RequestMapping("/mtml/mra/invoices/v1/")
public class MRAInvoiceController {

	@Autowired
	MRAService mraService;
	
//	@CrossOrigin(origins = "*", allowedHeaders = "*")
	@PostMapping(value="transmitInvoice",consumes=org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> transmitInvoice( @RequestBody List<InvoiceBean> invoices){

		String response =	mraService.submitInvoices(invoices);
		return ResponseEntity.ok(response);
	}
	
	//Single file upload and call method
	// 1. Upload to local directory
	// 2. Parse to json and bean
	// 3. Call method
	
	//Bulk file upload upto 10 files
	
	//Dashboard GET APIs
	

}
