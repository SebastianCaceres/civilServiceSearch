package com.civilService.search.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.DocumentId;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.SearchEntity;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ProjectionConstructor;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IdProjection;
import org.hibernate.search.engine.backend.types.Projectable;

@SearchEntity
@Indexed
@Data
@NoArgsConstructor
public class SyncMetadata {
    @DocumentId
    private String datasetId;
    @GenericField(projectable = Projectable.YES)
    private Long lastSyncedRowsUpdatedAt;

    @ProjectionConstructor
    public SyncMetadata(@IdProjection String datasetId, Long lastSyncedRowsUpdatedAt) {
        this.datasetId = datasetId;
        this.lastSyncedRowsUpdatedAt = lastSyncedRowsUpdatedAt;
    }
}
