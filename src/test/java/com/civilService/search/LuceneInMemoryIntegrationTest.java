package com.civilService.search;

import com.civilService.search.entity.CivilServiceListRecord;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;
import org.hibernate.search.mapper.pojo.standalone.session.SearchSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LuceneInMemoryIntegrationTest {

    @Autowired
    private SearchMapping searchMapping;

    @Test
    void shouldIndexAndRetrieveDocumentFromRam() {
        // 1. Create a sample POJO document
        CivilServiceListRecord record = new CivilServiceListRecord();
        record.setId(12345L);
        record.setFirstName("John");
        record.setLastName("Doe");
        record.setExamNo("1234");
        record.setListNo(new BigDecimal("12.340"));
        record.setListAgencyCode("856");
        record.setListAgencyDesc("DEPARTMENT OF CITYWIDE ADMINISTRATIVE SERVICES");
        record.setListTitleDesc("COMPUTER ASSOCIATE (SOFTWARE)");
        record.setStatus("active");

        // 2. Save it using the searchMapping/SearchSession
        try (SearchSession session = searchMapping.createSession()) {
            session.indexingPlan().add(record);
        } // commits changes

        // 3. Search for it using a known term
        List<CivilServiceListRecord> results;
        try (SearchSession session = searchMapping.createSession()) {
            results = session.search(CivilServiceListRecord.class)
                    .select(CivilServiceListRecord.class)
                    .where(f -> f.match().field("firstName").matching("John"))
                    .fetchAllHits();
        }

        // 4. Assert that the document was successfully found and the data matches
        assertThat(results).hasSize(1);
        CivilServiceListRecord found = results.get(0);
        assertThat(found.getId()).isEqualTo(12345L);
        assertThat(found.getFirstName()).isEqualTo("John");
        assertThat(found.getLastName()).isEqualTo("Doe");
        assertThat(found.getListTitleDesc()).isEqualTo("COMPUTER ASSOCIATE (SOFTWARE)");
    }
}
