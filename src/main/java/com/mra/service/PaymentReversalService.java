package com.mra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mra.Util.EBSPdfParser;
import com.mra.Util.PaymentReversalParser;
import com.mra.Util.SalesReturnParser;
import com.mra.model.InvoiceBean;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentReversalService {

 private final MRAService mraService;
// private static final String BASE_URL = "http://41.222.103.118:22221";
 private static final String BASE_URL = "http://172.28.5.2:22221";
 private static final String DOWNLOAD_DIR = "/home/downloads/payment_reversal";
 private static final String PROCESSED_DIR = "/home/Processed_Files/einv/payment_reversal";

 // 🔁 Scheduled every hour
 @Scheduled(fixedRate = 60 * 60 * 1000)
 public void scheduledInvoiceProcessing() {
  System.out.println("⏳ Scheduled job started...");
  downloadAndSubmitInvoices("payment_reversal"); // ✅ correct folder name
 }

 public void downloadAndSubmitInvoices(String folder) {
  try {
   List<Map<String, Object>> files = listFiles(folder);
   System.out.println("📄 Files Found: " + files.size());
   System.out.println(" ***************** Files started for Sales return *****************");
   for (Map<String, Object> file : files) {
    String fileName = (String) file.get("name");
    System.out.println("\n📥 Downloading: " + fileName);
    File downloaded = downloadFile(folder, fileName, DOWNLOAD_DIR);

    if (downloaded != null) {
     String json = PaymentReversalParser.parseToJson(downloaded.getAbsolutePath());
     System.out.println("📤 Submitting invoice for: " + fileName);
     System.out.println(json + "  ---- json Invoice ----");

     ObjectMapper objectMapper = new ObjectMapper();
     List<InvoiceBean> invoiceBeans = objectMapper.readValue(
             json,
             new com.fasterxml.jackson.core.type.TypeReference<List<InvoiceBean>>() {}
     );

     String result = mraService.submitInvoices(invoiceBeans);

     boolean isSuccess = result != null && result.contains("SUCCESS");

     if (isSuccess) {
      System.out.println("✅ Submitted Successfully Payment Reversal . Moving file.");
      moveFileToProcessed(downloaded, fileName);
     } else {
      System.err.println("❌ Submission failed for: " + fileName);
     }
    }
   }
  } catch (Exception e) {
   e.printStackTrace();
  }
 }

 private List<Map<String, Object>> listFiles(String folder) throws IOException {
  String urlString = BASE_URL + "/list/" + folder;
  HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
  conn.setRequestMethod("GET");

  try (InputStream inputStream = conn.getInputStream()) {
   ObjectMapper mapper = new ObjectMapper();
   return mapper.readValue(inputStream, new com.fasterxml.jackson.core.type.TypeReference<>() {});
  }
 }

 private File downloadFile(String folder, String fileName, String saveDir) {
  try {
   String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
   String fileUrl = BASE_URL + "/download/" + folder + "/" + encodedFileName;

   File dir = new File(saveDir);
   if (!dir.exists()) dir.mkdirs();

   // 🛡️ Sanitize filename
   String safeFileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");

   File outputFile = new File(dir, safeFileName);
   System.out.println("Saving to: " + outputFile.getAbsolutePath());

   HttpURLConnection conn = (HttpURLConnection) new URL(fileUrl).openConnection();
   conn.setRequestMethod("GET");

   try (InputStream in = conn.getInputStream()) {
    Files.copy(in, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    return outputFile;
   }
  } catch (Exception e) {
   System.err.println("❌ Failed to download: " + fileName);
   e.printStackTrace();
   return null;
  }
 }


 private void moveFileToProcessed(File original, String originalFileName) {
  try {
   File processedDir = new File(PROCESSED_DIR);
   if (!processedDir.exists()) processedDir.mkdirs();

   String newFileName = "DONE_" + originalFileName;
   File newFile = new File(processedDir, newFileName);
   Files.move(original.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
   System.out.println("📁 Moved to: " + newFile.getAbsolutePath());
  } catch (IOException e) {
   System.err.println("⚠️ Failed to move file to processed directory.");
   e.printStackTrace();
  }
 }

 // 🧪 Test manually
 public static void main(String[] args) {
  SalesReturnService service = new SalesReturnService(null); // ✅ corrected class
  try {
   service.downloadAndSubmitInvoices("payment_reversal"); // ✅ correct endpoint path
  } catch (Exception e) {
   e.printStackTrace();
  }
 }
}
