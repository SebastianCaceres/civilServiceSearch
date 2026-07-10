package com.civilService.search.service;

import com.civilService.search.entity.CivilServiceListRecord;
import com.civilService.search.entity.CivilServiceRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for full-text searching database entities using Hibernate Search.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LuceneIndexService {

    private final EntityManager entityManager;

    /**
     * Executes a full mass-reindexing of all database entities.
     */
    @Transactional
    public void reindexAll() {
        log.info("Starting Hibernate Search mass indexing...");
        try {
            SearchSession searchSession = Search.session(entityManager);
            searchSession.massIndexer().startAndWait();
            log.info("Hibernate Search mass indexing completed successfully!");
        } catch (InterruptedException e) {
            log.error("Mass indexing was interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error running mass indexing", e);
        }
    }

    /**
     * Search certifications using simple query string parsing over mapped fields.
     */
    @Transactional(readOnly = true)
    public List<CivilServiceRecord> searchCertifications(String textQuery, int maxResults) {
        if (textQuery == null || textQuery.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            SearchSession searchSession = Search.session(entityManager);
            return searchSession.search(CivilServiceRecord.class)
                    .where(f -> f.simpleQueryString()
                            .fields("examNo", "firstName", "lastName", "listTitleDesc", "listAgencyDesc")
                            .matching(textQuery)
                    )
                    .fetchHits(maxResults);
        } catch (Exception e) {
            log.error("Error performing Hibernate Search on certifications", e);
            return new ArrayList<>();
        }
    }

    /**
     * Search active/terminated candidate lists using simple query string parsing over mapped fields.
     */
    @Transactional(readOnly = true)
    public List<CivilServiceListRecord> searchLists(String textQuery, int maxResults) {
        if (textQuery == null || textQuery.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            SearchSession searchSession = Search.session(entityManager);
            return searchSession.search(CivilServiceListRecord.class)
                    .where(f -> f.simpleQueryString()
                            .fields("examNo", "firstName", "lastName", "listTitleDesc", "listAgencyDesc", "status")
                            .matching(textQuery)
                    )
                    .fetchHits(maxResults);
        } catch (Exception e) {
            log.error("Error performing Hibernate Search on candidate lists", e);
            return new ArrayList<>();
        }
    }


}
