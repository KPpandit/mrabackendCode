package com.mra.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;


import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mra.model.Invoice;
import com.mra.model.InvoiceBean;
import com.mra.model.ItemList;
import com.mra.model.Products;
import com.mra.repository.InvoiceRepository;
import com.mra.repository.ProductsInvoiceRepository;

@Service
public class MRAService {

	private static final Logger logger = (Logger) LoggerFactory.getLogger(MRAService.class);

	@Autowired
	InvoiceRepository invoiceRepository;
	
	@Autowired
	ProductsInvoiceRepository productsInvoiceRepository;

	SecretKey secretKey = null;
	private HashMap<String, Object> keystore = new HashMap<String, Object>();

	private static final String PUBLIC_KEY_FILE = "C:\\Users\\Krishna Purohit\\Downloads\\PublicKey.crt";
	private static final String ALGORITHM = "AES";
//	private static final String PUBLIC_KEY_FILE ="/home/certificate/PublicKey.crt";
	private byte[] readFileBytes() throws IOException
	{
		Path path = Paths.get(PUBLIC_KEY_FILE);
		return Files.readAllBytes(path);        
	}


	public String submitInvoices(List<InvoiceBean> invoiceBean) {
		if (invoiceBean == null || invoiceBean.isEmpty()) {
			return "Invalid Invoice";
		}

		// Get invoice identifier from first invoice
		String invoiceIdentifier = invoiceBean.get(0).getInvoiceIdentifier();
//		System.out.println(invoiceIdentifier + "-----");

		// Check if this invoice already exists (successful or failed)
		Invoice existingInvoice = invoiceRepository.findInvoiceByIdentifier(invoiceIdentifier);
		if (existingInvoice != null) {
			if (existingInvoice.getProcessStatus()) {
				System.out.println("Successful Invoice already exists in DB. Skipping submission.");
				return "Invoice already submitted. Skipping submission.";
			} else {
				System.out.println("Failed Invoice already exists in DB. Skipping submission.");
				return "Failed File Already Exist";
			}
		}

		String responseStr = null;
		String errorResponse = null;
		boolean isSuccessful = false;

		try {
			// Prepare encrypted payload
			Gson gson = new Gson();
			String json = gson.toJson(invoiceBean);
			String token = generateAuthToken();
			String encodedString = encryptAES(json);

			String payload = "{  \"requestId\": \"" + System.currentTimeMillis() + "\", \r\n"
					+ "\"requestDateTime\": \"" + getRequestDateTime() + "\", \r\n"
					+ "\"signedHash\": \"\", \r\n"
					+ "\"encryptedInvoice\": \"" + encodedString + "\"}";

			// SSL/TLS setup
			TrustManager[] trustAllCerts = new TrustManager[]{
					new X509TrustManager() {
						public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
						public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
						public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
					}
			};

			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

			HttpClient client = HttpClient.newBuilder()
//					.proxy(ProxySelector.of(new InetSocketAddress("172.28.5.2",8888)))
					.connectTimeout(Duration.ofMillis(3 * 1000))
					.sslContext(sc)
					.build();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("https://vfisc.mra.mu/realtime/invoice/transmit"))
					.header("Content-Type", "application/json")
					.header("username", "TU20275899")
					.header("ebsMraId", "17543875615480ULGPJVT26A")
					.header("areaCode", "914")
					.header("token", token)
					.method("POST", BodyPublishers.ofString(payload))
					.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			System.out.println(response.statusCode() + " " + response.body());

			responseStr = response.body();

			if (response.statusCode() == 200) {
				if (responseStr != null) {
					JsonObject jsonObject = JsonParser.parseString(responseStr).getAsJsonObject();

					if (responseStr.contains("SUCCESS")) {
						JsonArray invoiceResponse = jsonObject.getAsJsonArray("fiscalisedInvoices");
						if (invoiceResponse != null && invoiceResponse.size() > 0) {
							isSuccessful = true;
							persistInvoice(invoiceBean, true, invoiceResponse.toString());
						}
					} else {
						// Save failed invoice only if not exists
						saveFailedInvoiceIfNotExists(invoiceIdentifier, responseStr);

						// Extract error messages
						if (jsonObject.has("fiscalisedInvoices")) {
							JsonArray fiscalisedInvoices = jsonObject.getAsJsonArray("fiscalisedInvoices");
							if (fiscalisedInvoices.size() > 0) {
								JsonObject firstInvoice = fiscalisedInvoices.get(0).getAsJsonObject();
								if (firstInvoice.has("errorMessages")) {
									errorResponse = " Failed File Validation errors: " +
											firstInvoice.getAsJsonArray("errorMessages").toString();
								}
							}
						}
					}
				}
			} else {
				errorResponse = "HTTP Error: " + response.statusCode() + " - " + response.body();
				saveFailedInvoiceIfNotExists(invoiceIdentifier, errorResponse);
			}
		} catch (Exception e) {
			errorResponse = "Exception occurred: " + e.getMessage();
			saveFailedInvoiceIfNotExists(invoiceIdentifier, errorResponse);
			e.printStackTrace();
		}

		return isSuccessful ? responseStr : errorResponse;
	}

	private String saveFailedInvoiceIfNotExists(String invoiceIdentifier, String response) {
		Invoice existingInvoice = invoiceRepository.findFailedInvoiceByIdentifier(invoiceIdentifier);
		if (existingInvoice == null) {
			Invoice minimalInvoice = new Invoice();
			minimalInvoice.setInvoiceIndentifier(invoiceIdentifier);
			minimalInvoice.setProcessStatus(false);
			minimalInvoice.setInvoiceResponse(response);
			minimalInvoice.setProcessingDateTime(new Date());
			invoiceRepository.save(minimalInvoice);
			return "Failed file saved successfully";
		} else {
			return "Failed file already existed";
		}
	}



	private void persistInvoice(List<InvoiceBean> invoices, boolean isSucessful, String ebsResponse) {
		//check if invoiceIdentifier exists in db
		for (Iterator<InvoiceBean> iterator = invoices.iterator(); iterator.hasNext();) {
			InvoiceBean invoiceBean = (InvoiceBean) iterator.next();
			Invoice invoice = new Invoice();
			invoice.setBuyerBrn(invoiceBean.getBuyer().getBrn());
			invoice.setBuyerbusinessAddr(invoiceBean.getBuyer().getBusinessAddr());
			invoice.setBuyerName(invoiceBean.getBuyer().getName());
			invoice.setBuyerNic(invoiceBean.getBuyer().getNic());
			invoice.setBuyerTan(invoiceBean.getBuyer().getTan());
			invoice.setBuyerType(invoiceBean.getBuyer().getBuyerType());
			invoice.setBuyerMsisdn(invoiceBean.getBuyer().getMsisdn());
			invoice.setCurrency(invoiceBean.getCurrency());
			invoice.setDateTimeInvoiceIssued(invoiceBean.getDateTimeInvoiceIssued());
			invoice.setDiscountTotalAmount(invoiceBean.getDiscountTotalAmount());
			invoice.setInvoiceIndentifier(invoiceBean.getInvoiceIdentifier());
			invoice.setInvoicePath(invoiceBean.getInvoicePath());
			invoice.setInvoiceTotal(invoiceBean.getInvoiceTotal());
			invoice.setInvoiceTypeDesc(invoiceBean.getInvoiceTypeDesc());
			invoice.setPersonType(invoiceBean.getPersonType());
			invoice.setPrevNoteHash(invoiceBean.getPreviousNoteHash());
			invoice.setProcessingDateTime(new Date());
			invoice.setReasonStated(invoiceBean.getReasonStated());
			invoice.setSellerBrn(invoiceBean.getSeller().getBrn());
			invoice.setSellerbusinessAddr(invoiceBean.getSeller().getBusinessAddr());
			invoice.setSellerbusinessPhoneNo(invoiceBean.getSeller().getBusinessPhoneNo());
			invoice.setSellerebsCounterNo(invoiceBean.getSeller().getEbsCounterNo());
			invoice.setSellerName(invoiceBean.getSeller().getName());
			invoice.setSellerTan(invoiceBean.getSeller().getTan());
			invoice.setSellerTradeName(invoiceBean.getSeller().getTradeName());
			invoice.setTotalAmtPaid(invoiceBean.getTotalAmtPaid());
			invoice.setTotalAmtWoVatCur(invoiceBean.getTotalAmtWoVatCur());
			invoice.setTotalVatAmount(invoiceBean.getTotalVatAmount());
			invoice.setTotalAmtWoVatMur(invoiceBean.getTotalAmtWoVatMur());
			invoice.setTxnType(invoiceBean.getTransactionType());
			invoice.setSurchargeAmount(invoiceBean.getSurchargeAmount());
			invoice.setInvoiceDueDate(invoiceBean.getInvoiceDueDate());
			invoice.setInvoiceRefIndentifier(invoiceBean.getInvoiceRefIdentifier());
			if(isSucessful)
				invoice.setProcessStatus(true);
			else
				invoice.setProcessStatus(false);
			invoice.setInvoiceResponse(ebsResponse);
			invoice = invoiceRepository.save(invoice);
			List<Products> productsInvoices = new ArrayList<Products>();
			for (Iterator<ItemList> iterator2 = invoiceBean.getItemList().listIterator(); iterator2.hasNext();) {
				System.out.println("**********Item List*********************");
				ItemList items = (ItemList) iterator2.next();
				Products productsInvoice = new Products();
				productsInvoice.setAmtWoVatCur(items.getAmtWoVatCur());
				productsInvoice.setAmtWoVatMur(items.getAmtWoVatMur());
				productsInvoice.setDiscount(items.getDiscount());
				productsInvoice.setDiscountedValue(items.getDiscountedValue());
				productsInvoice.setItemDesc(items.getItemDesc());
				productsInvoice.setNature(items.getNature());
				productsInvoice.setProductCodeMra(items.getProductCodeMra());
				productsInvoice.setProductCodeOwn(items.getProductCodeOwn());
				productsInvoice.setQuantity(items.getQuantity());
				productsInvoice.setTaxCode(items.getTaxCode());
				productsInvoice.setTotalPrice(items.getTotalPrice());
				productsInvoice.setUnitPrice(items.getUnitPrice());
				productsInvoice.setVatAmt(items.getVatAmt());
				productsInvoice.setPreviousBalance(items.getPreviousBalance());
				productsInvoice.setInvoice(invoice);
				productsInvoices.add(productsInvoice);
				productsInvoiceRepository.save(productsInvoice);
			}
			invoice.setProducts(productsInvoices);
			invoiceRepository.save(invoice);

		}

	}


	private String getRequestDateTime() {

		LocalDateTime now = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
		String formattedDateTime = now.format(formatter);
		return formattedDateTime;
	}

	private String generateAuthToken() {
		String responseStr = null;
		String encodedAuth = generateEncodedAuthString();
		String payload = "{  \"requestId\": \""+System.currentTimeMillis()+"\", \r\n"
				+ "    \"payload\": \""+encodedAuth+"\"}";
		TrustManager[] trustAllCerts = new TrustManager[]{
				new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}
					public void checkClientTrusted(
							java.security.cert.X509Certificate[] certs, String authType) {
					}
					public void checkServerTrusted(
							java.security.cert.X509Certificate[] certs, String authType) {
					}
				}
		};

		// Install the all-trusting trust manager
		HttpResponse<String> response = null;
		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

			// Creat HttpClient with new SSLContext.
			HttpClient client = HttpClient.newBuilder()
//					.proxy(ProxySelector.of(new InetSocketAddress("172.28.5.2",8888)))
					.connectTimeout(Duration.ofMillis(3 * 1000))
					.sslContext(sc) // SSL context 'sc' initialised as earlier
					.build();

			//HttpClient client = HttpClient.newHttpClient();
			//System.out.println(payload);
			HttpRequest request = HttpRequest.newBuilder()
					//Staging
					.uri(URI.create("https://vfisc.mra.mu/einvoice-token-service/token-api/generate-token"))
					.header("Content-Type", "application/json")
					.header("username", "TU20275899")
					.header("ebsMraId", "17543875615480ULGPJVT26A")
					.header("areaCode", "914")
					.method("POST", BodyPublishers.ofString(payload))
					.build();

			response = client
					.send(request, HttpResponse.BodyHandlers.ofString());
			//System.out.println(response.statusCode()+ " "+response.body()+ " "+response.toString());
			if(response.statusCode() == 200) {
				responseStr = response.body();
				//System.out.println(responseStr);
				if(responseStr!=null && responseStr.contains("token")) {
					JsonObject jsonObject = JsonParser.parseString(responseStr)
							.getAsJsonObject();
					String encodedResponse = null;
					if(jsonObject.has("token") && !jsonObject.get("token").isJsonNull()) {
						responseStr = jsonObject.get("token").getAsString();
						keystore.put("key", jsonObject.get("key").getAsString());
						//System.out.println("Token : "+responseStr);
					}
				}
				else if(responseStr!=null && responseStr.contains("errors")) {
					JsonObject jsonObject = JsonParser.parseString(responseStr)
							.getAsJsonObject();
					responseStr = jsonObject.get("errors").getAsString();
				}

			}
		} catch (KeyManagementException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return responseStr;

	}

	private String generateEncodedAuthString() {

		String json = null ;
		String encodedString = null;
		SecureRandom rand = new SecureRandom(); 
		KeyGenerator generator;
		try {
			generator = KeyGenerator.getInstance("AES");
			generator.init(256, rand); 
			secretKey = generator.generateKey();
			keystore.put("secretKey", secretKey);
			byte[] rawData = secretKey.getEncoded(); 
			keystore.put("secret", new String(rawData));
			String encodedKey = Base64.getEncoder().encodeToString(rawData); 
			//System.out.println("encodedKey "+ encodedKey);
			json = "{     \"username\": \"TU20275899\", \r\n"
					+ "    \"password\": \"Mtml@123\", \r\n"
					+ "    \"encryptKey\": \""+encodedKey+"\", \r\n"
					+ "    \"refreshToken\": \"false\" \r\n"
					+ "} ";
			//System.out.println(json);

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} 

		PublicKey pkey = null; 
		try(FileInputStream fis = new FileInputStream(PUBLIC_KEY_FILE)) { 
			CertificateFactory cf = CertificateFactory.getInstance("X.509"); 
			X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);  
			pkey = cert.getPublicKey(); 

		} catch (Exception e) { 
		} 

		try {
			Cipher cipher = Cipher.getInstance("RSA"); 
			cipher.init(Cipher.ENCRYPT_MODE, pkey); 
			encodedString = Base64.getEncoder().encodeToString((cipher.doFinal(json.getBytes("UTF-8"))));

			//System.out.println(encodedString);
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		}
		return encodedString; 
	}

	private String decrypt(String data) throws Exception {
		if(data!=null) {
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, (SecretKey)keystore.get("secretKey"));
			byte[] decoded = Base64.getDecoder().decode(data.getBytes());
			byte[] encryptedBytes = cipher.doFinal(decoded);
			//System.out.println(new String(encryptedBytes));
			return new String(encryptedBytes);
		}
		return null;

	}

	private String encryptAES(String data) throws Exception {
		//Staging
		//String key = "wkdleuwfvyfgbfaehlmfgoes";
		//String key = "dfbnvfchjthbnvfcdegnvjkm";
		String encodedKey = (String) keystore.get("key");
		String decryptedKey = decrypt(encodedKey);
		byte[] decodedKey = Base64.getDecoder().decode(decryptedKey);
		Cipher cipher = Cipher.getInstance(ALGORITHM);
		SecretKeySpec secretKeySpec = new SecretKeySpec(decodedKey, ALGORITHM);
		cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
		byte[] encryptedBytes = cipher.doFinal(data.getBytes("UTF-8"));
		return Base64.getEncoder().encodeToString(encryptedBytes);

	}

	private String encrypt(String text) {
		//System.out.println("Key: "+keystore.get("key") );
		String encodedKey = (String) keystore.get("key");
		byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
		// rebuild key using SecretKeySpec
		SecretKey originalKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

		Cipher cipher = null;
		try {
			cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		try {
			cipher.init(Cipher.ENCRYPT_MODE, originalKey);
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		String encryptedString = null;
		try {
			encryptedString = Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes("UTF-8")));
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 


		return encryptedString;
	}


	public static void main(String a[]) {
		String json = " \r\n["
				+ " { \r\n"
				+ "  \"invoiceCounter\": \"1\", \r\n"
				+ "  \"transactionType\": \"B2C\", \r\n"
				+ "  \"personType\": \"VATR\", \r\n"
				+ "  \"invoiceTypeDesc\": \"STD\", \r\n"
				+ "  \"currency\": \"MUR\", \r\n"
				+ "  \"invoiceIdentifier\": \"abscs\", \r\n"
				+ "  \"invoiceRefIdentifier\": \"\", \r\n"
				+ "  \"previousNoteHash\": \"prevNote\", \r\n"
				+ "  \"reasonStated\": \"rgeegr\", \r\n"
				+ "  \"totalVatAmount\": \"60.0\", \r\n"
				+ "  \"totalAmtWoVatCur\": \"310.0\", \r\n"
				+ "  \"totalAmtWoVatMur\": \"310.0\", \r\n"
				+ "            \"invoiceTotal\": \"370.0\", \r\n"
				+ "            \"discountTotalAmount\": \"50.0\", \r\n"
				+ "  \"totalAmtPaid\": \"320.0\", \r\n"
				+ "  \"dateTimeInvoiceIssued\": \"20230531 10:40:30\", \r\n"
				+ "  \"seller\": { \r\n"
				+ "   \"name\": \"MTML GSM SERVICE\", \r\n"
				+ "   \"tradeName\": \"TEST\", \r\n"
				+ "   \"tan\": \"20275899\", \r\n"
				+ "   \"brn\": \"C07048459\", \r\n"
				+ "   \"businessAddr\": \"Test address\", \r\n"
				+ "   \"businessPhoneNo\": \"\", \r\n"
				+ "   \"ebsCounterNo\": \"a1\" \r\n"
				+ "  }, \r\n"
				+ "  \"buyer\": { \r\n"
				+ "   \"name\": \"Test user 2\", \r\n"
				+ "   \"tan\": \"\", \r\n"
				+ "   \"brn\": \"\", \r\n"
				+ "   \"businessAddr\": \"Test address 1\", \r\n"
				+ "   \"buyerType\": \"VATR\", \r\n"
				+ "   \"nic\": \"\" \r\n"
				+ "  }, \r\n"
				+ "  \"itemList\": [ \r\n"
				+ "   { \r\n"
				+ "    \"itemNo\": \"1\", \r\n"
				+ "    \"taxCode\": \"TC01\", \r\n"
				+ "    \"nature\": \"GOODS\", \r\n"
				+ "    \"productCodeMra\": \"pdtCode\", \r\n"
				+ "    \"productCodeOwn\": \"pdtOwn\", \r\n"
				+ "    \"itemDesc\": \"dILAIT CONDENc 23\", \r\n"
				+ "    \"quantity\": \"23214\", \r\n"
				+ "    \"unitPrice\": \"20\", \r\n"
				+ "    \"discount\": \"1.23\", \r\n"
				+ "    \"discountedValue\": \"10.1\", \r\n"
				+ "    \"amtWoVatCur\": \"60\", \r\n"
				+ "    \"amtWoVatMur\": \"50\", \r\n"
				+ "    \"vatAmt\": \"10\", \r\n"
				+ "    \"totalPrice\": \"60\" \r\n"
				+ "   }, \r\n"
				+ "   { \r\n"
				+ "    \"itemNo\": \"2\", \r\n"
				+ "    \"taxCode\": \"TC01\", \r\n"
				+ "    \"nature\": \"GOODS\", \r\n"
				+ "    \"productCodeMra\": \"pdtCode\", \r\n"
				+ "    \"productCodeOwn\": \"pdtOwn\", \r\n"
				+ "    \"itemDesc\": \"2\", \r\n"
				+ "    \"quantity\": \"3\", \r\n"
				+ "    \"unitPrice\": \"20\", \r\n"
				+ "    \"discount\": \"0\", \r\n"
				+ "    \"discountedValue\": \"12.0\", \r\n"
				+ "    \"amtWoVatCur\": \"50\", \r\n"
				+ "    \"amtWoVatMur\": \"50\", \r\n"
				+ "    \"vatAmt\": \"10\", \r\n"
				+"\"totalPrice\": \"60\" \r\n"
				+ "   }, \r\n"
				+ "   { \r\n"
				+ "    \"itemNo\": \"3\", \r\n"
				+ "    \"taxCode\": \"TC01\", \r\n"
				+ "    \"nature\": \"GOODS\", \r\n"
				+ "    \"productCodeMra\": \"pdtCode\", \r\n"
				+ "    \"productCodeOwn\": \"pdtOwn\", \r\n"
				+ "    \"itemDesc\": \"2\", \r\n"
				+ "    \"quantity\": \"3\", \r\n"
				+ "    \"unitPrice\": \"20\", \r\n"
				+ "    \"discount\": \"0\", \r\n"
				+ "    \"discountedValue\": \"12\", \r\n"
				+ "    \"amtWoVatCur\": \"50\", \r\n"
				+ "    \"amtWoVatMur\": \"50\", \r\n"
				+ "    \"vatAmt\": \"10\", \r\n"
				+ "    \"totalPrice\": \"60\" \r\n"
				+ "   }, \r\n"
				+ "   { \r\n"
				+ "    \"itemNo\": \"4\", \r\n"
				+ "    \"taxCode\": \"TC01\", \r\n"
				+ "    \"nature\": \"GOODS\", \r\n"
				+ "    \"productCodeMra\": \"pdtCode\", \r\n"
				+ "    \"productCodeOwn\": \"pdtOwn\", \r\n"
				+ "    \"itemDesc\": \"2\", \r\n"
				+ "    \"quantity\": \"3\", \r\n"
				+ "    \"unitPrice\": \"20\", \r\n"
				+ "    \"discount\": \"0\", \r\n"
				+ "    \"discountedValue\": \"12.0\", \r\n"
				+ "    \"amtWoVatCur\": \"50\", \r\n"
				+ "    \"amtWoVatMur\": \"50\", \r\n"
				+ "    \"vatAmt\": \"0\", \r\n"
				+ "    \"totalPrice\": \"60\" \r\n"
				+ "   }, \r\n"
				+ "   { \r\n"
				+ "    \"itemNo\": \"5\", \r\n"
				+ "    \"taxCode\": \"TC01\", \r\n"
				+ "    \"nature\": \"GOODS\", \r\n"
				+ "    \"productCodeMra\": \"pdtCode\", \r\n"
				+ "    \"productCodeOwn\": \"pdtOwn\", \r\n"
				+ "    \"itemDesc\": \"2\", \r\n"
				+ "    \"quantity\": \"3\", \r\n"
				+ "    \"unitPrice\": \"20\", \r\n"
				+ "    \"discount\": \"0\", \r\n"
				+ "    \"discountedValue\": \"12.6\", \r\n"
				+ "    \"amtWoVatCur\": \"50\", \r\n"
				+ "    \"amtWoVatMur\": \"50\", \r\n"
				+ "    \"vatAmt\": \"0\", \r\n"
				+ "    \"totalPrice\": \"60\" \r\n"
				+ "   }    \r\n"
				+ "  ], \r\n"
				+ "  \"salesTransactions\": \"CASH\" \r\n"
				+ " } \r\n"
				+ "]";

		Gson gson = new Gson();
		Type typeMyType = new TypeToken<ArrayList<InvoiceBean>>(){}.getType();
		List<InvoiceBean> ib = gson.fromJson(json, typeMyType);

		System.out.println(json);
		new MRAService().submitInvoices(ib);
	}
}
