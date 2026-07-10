package com.civilService.search;

import com.civilService.search.repository.CivilServiceListRecordRepository;
import com.civilService.search.service.CivilServiceListSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
class SearchApplicationTests {

	@Autowired
	private CivilServiceListSyncService listSyncService;

	@Autowired
	private CivilServiceListRecordRepository listRepository;

	@Autowired
	private com.civilService.search.service.LuceneIndexService luceneIndexService;

	@Test
	void contextLoads() {
		assertThat(listSyncService).isNotNull();
		assertThat(listRepository).isNotNull();
	}

	@Test
	void testSyncListRecords() {
		// Clear repository to ensure clean state
		listRepository.deleteAll();

		// Run sync
		listSyncService.syncAllLists();

		// Assert that records were successfully fetched and saved
		long count = listRepository.count();
		System.out.println("Test successfully synchronized " + count + " civil service list records.");
		assertThat(count).isGreaterThan(0);

		// Assert that both active and terminated statuses are present
		long activeCount = listRepository.findAll().stream()
				.filter(r -> "active".equals(r.getStatus()))
				.count();
		long terminatedCount = listRepository.findAll().stream()
				.filter(r -> "terminated".equals(r.getStatus()))
				.count();

		System.out.println("Active list records synced: " + activeCount);
		System.out.println("Terminated list records synced: " + terminatedCount);

		assertThat(activeCount).isGreaterThan(0);
		assertThat(terminatedCount).isGreaterThan(0);
	}

	@Test
	void testLuceneIndexAndSearch() {
		// Run lists sync if empty to guarantee records to index
		if (listRepository.count() == 0) {
			listSyncService.syncAllLists();
		}

		// Run manual reindexing
		luceneIndexService.reindexAll();

		// Search list records for "OMAR"
		java.util.List<com.civilService.search.entity.CivilServiceListRecord> omarResults = luceneIndexService.searchLists("OMAR", 10);
		System.out.println("Lucene search for 'OMAR' returned: " + omarResults.size() + " matches.");
		assertThat(omarResults).isNotEmpty();
		for (com.civilService.search.entity.CivilServiceListRecord match : omarResults) {
			System.out.println("Match: " + match.getFirstName() + " " + match.getLastName() + " - status: " + match.getStatus());
			assertThat(match.getFirstName() + " " + match.getLastName()).containsIgnoringCase("OMAR");
		}

		// Search list records for "terminated"
		java.util.List<com.civilService.search.entity.CivilServiceListRecord> terminatedResults = luceneIndexService.searchLists("terminated", 10);
		System.out.println("Lucene search for 'terminated' returned: " + terminatedResults.size() + " matches.");
		assertThat(terminatedResults).isNotEmpty();
		for (com.civilService.search.entity.CivilServiceListRecord match : terminatedResults) {
			assertThat(match.getStatus()).isEqualTo("terminated");
		}
	}
}
