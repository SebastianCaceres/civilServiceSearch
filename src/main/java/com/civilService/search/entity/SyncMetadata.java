package com.civilService.search.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "sync_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncMetadata {
    @Id
    private String datasetId;
    private Long lastSyncedRowsUpdatedAt;
}
