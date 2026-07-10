package com.civilService.search;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.civilService.search.entity.CivilServiceListRecord;
import com.civilService.search.repository.CivilServiceListRecordRepository;
import com.civilService.search.service.SearchService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "civilservice.sync.limit=2000",
        "civilservice.sync.list.limit=2000",
        "civilservice.sync.app-token=***REMOVED***",
        "civilservice.sync.secret-token=***REMOVED***"
})
class ListSearchApplicationTests {

    @Autowired
    private CivilServiceListRecordRepository repository;

    @Autowired
    private SearchService searchService;

    @Test
    void contextLoads() {
    }

    @Test
    void dataIsLoadedOnStartup() {
        assertThat(repository.count()).isGreaterThan(0);
    }

    @Test
    void importedDataContainsActiveAndTerminatedStatuses() {
        var allEntries = repository.findAll();
        assertThat(allEntries).isNotEmpty();
        assertThat(allEntries).extracting(CivilServiceListRecord::getStatus)
                .contains("active", "terminated")
                .doesNotContainNull();
    }

    @Test
    void kabirHussainSearchReturnsResults() {
        // Guarantee "Kabir Hussain" entry exists in the repository
        boolean exists = repository.findAll().stream()
                .anyMatch(r -> "Kabir".equalsIgnoreCase(r.getFirstName()) && "Hussain".equalsIgnoreCase(r.getLastName()));

        if (!exists) {
            CivilServiceListRecord record = new CivilServiceListRecord();
            record.setFirstName("Kabir");
            record.setLastName("Hussain");
            record.setExamNo("9999");
            record.setListNo(new java.math.BigDecimal("1.000"));
            record.setListAgencyCode("856");
            record.setStatus("active");
            repository.save(record);
        }

        var response = searchService.searchEntries("Kabir Hussain");
        assertThat(response.totalCount()).isGreaterThan(0);
    }
}
