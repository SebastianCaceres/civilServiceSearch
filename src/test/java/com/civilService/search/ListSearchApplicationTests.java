package com.civilService.search;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.civilService.search.entity.CivilServiceListRecord;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;
import org.hibernate.search.mapper.pojo.standalone.session.SearchSession;
import com.civilService.search.service.SearchService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties = {
        "civilservice.sync.limit=20000",
        "civilservice.sync.list.limit=20000",
        "civilservice.sync.app-token=${CIVILSERVICE_SYNC_APP_TOKEN:}",
        "civilservice.sync.secret-token=${CIVILSERVICE_SYNC_SECRET_TOKEN:}",
        "hibernate.search.backend.directory.root=./target/lucene-index-list"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ListSearchApplicationTests {

    @Autowired
    private SearchMapping searchMapping;

    @Autowired
    private SearchService searchService;

    @Test
    void contextLoads() {
    }

    @Test
    void dataIsLoadedOnStartup() {
        long count = 0;
        try (SearchSession session = searchMapping.createSession()) {
            count = session.search(CivilServiceListRecord.class)
                    .where(f -> f.matchAll())
                    .fetchTotalHitCount();
        }
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void importedDataContainsActiveAndTerminatedStatuses() {
        List<CivilServiceListRecord> allEntries;
        try (SearchSession session = searchMapping.createSession()) {
            allEntries = session.search(CivilServiceListRecord.class)
                    .select(CivilServiceListRecord.class)
                    .where(f -> f.matchAll())
                    .fetchAllHits();
        }
        assertThat(allEntries).isNotEmpty();
        assertThat(allEntries).extracting(CivilServiceListRecord::getStatus)
                .contains("active", "terminated")
                .doesNotContainNull();
    }

    @Test
    void kabirHussainSearchReturnsResults() {
        List<CivilServiceListRecord> allEntries;
        try (SearchSession session = searchMapping.createSession()) {
            allEntries = session.search(CivilServiceListRecord.class)
                    .select(CivilServiceListRecord.class)
                    .where(f -> f.matchAll())
                    .fetchAllHits();
        }
        boolean exists = allEntries.stream()
                .anyMatch(r -> "Kabir".equalsIgnoreCase(r.getFirstName()) && "Hussain".equalsIgnoreCase(r.getLastName()));

        if (!exists) {
            CivilServiceListRecord record = new CivilServiceListRecord();
            record.setId(99999L);
            record.setFirstName("Kabir");
            record.setLastName("Hussain");
            record.setExamNo("9999");
            record.setListNo(new java.math.BigDecimal("1.000"));
            record.setListAgencyCode("856");
            record.setStatus("active");
            try (SearchSession session = searchMapping.createSession()) {
                session.indexingPlan().add(record);
            }
        }

        var response = searchService.searchEntries("Kabir Hussain");
        assertThat(response.totalCount()).isGreaterThan(0);
    }
}
