package com.mra.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mra.model.Invoice;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Integer>{

	String findNonUpdated = "select * from invoices where processStatus is NULL and invoiceResponse is NOT NULL";
	@Query(value= findNonUpdated,nativeQuery=true)
	List<Invoice> getNonProcessedInvoices();
	
	String findNonProcessed = "select * from invoices where processStatus=false";
	@Query(value= findNonProcessed,nativeQuery=true)
	List<Invoice> getFailedInvoices();
	
	String findNonProcessedRange = "select * from invoices where processStatus=false`and processingDateTime between (?1 and ?2)";
	@Query(value= findNonProcessedRange,nativeQuery=true)
	List<Invoice> getFailedInvoicesRange(String start, String end);
	
	String findProcessed = "select * from invoices where processStatus=true and processingDateTime between (?1 and ?2)";
	@Query(value= findProcessed,nativeQuery=true)
	List<Invoice> getProcessedInvoices(String start, String end);

	@Query(value = "select * from invoices where invoiceIndentifier=?1 and processStatus=1", nativeQuery = true)
	Invoice findInvoiceByIdentifier(String invoiceIndentifier);

	@Query(value = "select * from invoices where invoiceIndentifier=?1 and processStatus=0", nativeQuery = true)
	Invoice findFailedInvoiceByIdentifier(String invoiceIndentifier);


//

	@Query("SELECT i FROM Invoice i WHERE " +
			"STR(i.invoiceId) LIKE CONCAT('%', :value, '%') OR " +
			"LOWER(i.buyerName) LIKE LOWER(CONCAT('%', :value, '%')) OR " +
			"LOWER(i.invoiceIndentifier) LIKE LOWER(CONCAT('%', :value, '%')) OR " +
			"STR(i.buyerMsisdn) LIKE CONCAT('%', :value, '%') OR " +
			"LOWER(i.buyerTan) LIKE LOWER(CONCAT('%', :value, '%')) OR " +
			"LOWER(i.buyerBrn) LIKE LOWER(CONCAT('%', :value, '%'))")
	List<Invoice> searchInvoicesByAnyField(@Param("value") String value);


	@Query("""
    SELECT i 
    FROM Invoice i
    WHERE LOWER(i.invoiceIndentifier) = LOWER(:identifier)
       OR LOWER(i.invoiceIndentifier) LIKE CONCAT('%', LOWER(:identifier))
""")
	List<Invoice> findByInvoiceIdentifierExactOrEndsWith(@Param("identifier") String identifier);


	@Query("SELECT i FROM Invoice i " +
			"WHERE (:startDate IS NOT NULL AND :endDate IS NULL AND i.dateTimeInvoiceIssued LIKE CONCAT(:startDate, '%')) " +
			"OR (:startDate IS NOT NULL AND :endDate IS NOT NULL AND i.dateTimeInvoiceIssued BETWEEN :startDate AND :endDate)")
	List<Invoice> findByDateRange(
			@Param("startDate") String startDate,
			@Param("endDate") String endDate
	);

//

	// DAILY: Count invoices
	@Query(value = """
    SELECT STR_TO_DATE(SUBSTRING(dateTimeInvoiceIssued, 1, 8), '%Y%m%d') AS issuedDate,
           COUNT(invoiceId)
    FROM invoices
    WHERE dateTimeInvoiceIssued IS NOT NULL
    GROUP BY issuedDate
    ORDER BY issuedDate
""", nativeQuery = true)
	List<Object[]> getInvoiceCountByDay();

	// DAILY: Total paid
	@Query(value = """
    SELECT STR_TO_DATE(SUBSTRING(dateTimeInvoiceIssued, 1, 8), '%Y%m%d') AS issuedDate,
           SUM(CAST(totalAmtPaid AS DECIMAL(10,2)))
    FROM invoices
    WHERE dateTimeInvoiceIssued IS NOT NULL
    GROUP BY issuedDate
    ORDER BY issuedDate
""", nativeQuery = true)
	List<Object[]> totalPaidPerDay();

	// MONTHLY: Count invoices
	@Query(value = """
    SELECT DATE_FORMAT(STR_TO_DATE(SUBSTRING(dateTimeInvoiceIssued, 1, 8), '%Y%m%d'), '%Y') AS year,
           DATE_FORMAT(STR_TO_DATE(SUBSTRING(dateTimeInvoiceIssued, 1, 8), '%Y%m%d'), '%m') AS month,
           COUNT(invoiceId)
    FROM invoices
    WHERE dateTimeInvoiceIssued IS NOT NULL
    GROUP BY year, month
    ORDER BY year, month
""", nativeQuery = true)
	List<Object[]> countInvoicesPerMonth();

	// MONTHLY: Total paid
	@Query(value = """
    SELECT DATE_FORMAT(STR_TO_DATE(SUBSTRING(dateTimeInvoiceIssued, 1, 8), '%Y%m%d'), '%Y') AS year,
           DATE_FORMAT(STR_TO_DATE(SUBSTRING(dateTimeInvoiceIssued, 1, 8), '%Y%m%d'), '%m') AS month,
           SUM(CAST(totalAmtPaid AS DECIMAL(10,2)))
    FROM invoices
    WHERE dateTimeInvoiceIssued IS NOT NULL
    GROUP BY year, month
    ORDER BY year, month
""", nativeQuery = true)
	List<Object[]> totalPaidPerMonth();

	// YEARLY: Count invoices
	@Query(value = """
    SELECT DATE_FORMAT(STR_TO_DATE(SUBSTRING(dateTimeInvoiceIssued, 1, 8), '%Y%m%d'), '%Y') AS year,
           COUNT(invoiceId)
    FROM invoices
    WHERE dateTimeInvoiceIssued IS NOT NULL
    GROUP BY year
    ORDER BY year
""", nativeQuery = true)
	List<Object[]> countInvoicesPerYear();

	// YEARLY: Total paid
	@Query(value = """
    SELECT DATE_FORMAT(STR_TO_DATE(SUBSTRING(dateTimeInvoiceIssued, 1, 8), '%Y%m%d'), '%Y') AS year,
           SUM(CAST(totalAmtPaid AS DECIMAL(10,2)))
    FROM invoices
    WHERE dateTimeInvoiceIssued IS NOT NULL
    GROUP BY year
    ORDER BY year
""", nativeQuery = true)
	List<Object[]> totalPaidPerYear();

}
