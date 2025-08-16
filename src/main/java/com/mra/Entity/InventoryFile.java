package com.mra.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "inventory_files")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class InventoryFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String invoiceIdentifier;

    @Lob
    private byte[] fileData;

    private String contentType;

    private LocalDateTime createdAt;
}
