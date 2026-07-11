package com.civilService.search;

import com.civilService.search.entity.CivilServiceListRecord;
import com.civilService.search.service.CivilServiceListSyncService;
import com.civilService.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;
import org.hibernate.search.mapper.pojo.standalone.session.SearchSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties = {
		"civilservice.sync.limit=20000",
		"civilservice.sync.list.limit=20000",
		"civilservice.sync.app-token=${CIVILSERVICE_SYNC_APP_TOKEN:}",
		"civilservice.sync.secret-token=${CIVILSERVICE_SYNC_SECRET_TOKEN:}",
		"hibernate.search.backend.directory.root=./target/lucene-index-search"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SearchApplicationTests {

	@Autowired
	private CivilServiceListSyncService listSyncService;

	@Autowired
	private SearchMapping searchMapping;

	@Autowired
	private SearchService searchService;

	@Test
	void contextLoads() {
		assertThat(listSyncService).isNotNull();
		assertThat(searchMapping).isNotNull();
	}

	@Test
	void testSyncListRecords() {
		// Clear Lucene index to ensure clean state
		try (SearchSession session = searchMapping.createSession()) {
			session.workspace(CivilServiceListRecord.class).purge();
		}

		// Run sync
		listSyncService.syncAllLists();

		// Assert that records were successfully fetched and saved
		long count = 0;
		List<CivilServiceListRecord> allRecords;
		try (SearchSession session = searchMapping.createSession()) {
			count = session.search(CivilServiceListRecord.class)
					.where(f -> f.matchAll())
					.fetchTotalHitCount();

			allRecords = session.search(CivilServiceListRecord.class)
					.select(CivilServiceListRecord.class)
					.where(f -> f.matchAll())
					.fetchAllHits();
		}

		System.out.println("Test successfully synchronized " + count + " civil service list records.");
		assertThat(count).isGreaterThan(0);

		// Assert that both active and terminated statuses are present
		long activeCount = allRecords.stream()
				.filter(r -> "active".equals(r.getStatus()))
				.count();
		long terminatedCount = allRecords.stream()
				.filter(r -> "terminated".equals(r.getStatus()))
				.count();

		System.out.println("Active list records synced: " + activeCount);
		System.out.println("Terminated list records synced: " + terminatedCount);

		assertThat(activeCount).isGreaterThan(0);
		assertThat(terminatedCount).isGreaterThan(0);
	}

	@Test
	void testLuceneSearchService() {
		// Run lists sync if empty to guarantee records to index
		long count = 0;
		try (SearchSession session = searchMapping.createSession()) {
			count = session.search(CivilServiceListRecord.class)
					.where(f -> f.matchAll())
					.fetchTotalHitCount();
		}
		if (count == 0) {
			listSyncService.syncAllLists();
		}

		// Search list records using SearchService
		SearchService.SearchResponse omarResponse = searchService.searchEntries("OMAR");
		System.out.println("SearchService for 'OMAR' returned: " + omarResponse.totalCount() + " matches.");
		assertThat(omarResponse.results()).isNotEmpty();
		for (SearchService.SearchHit match : omarResponse.results()) {
			System.out.println("Match: " + match.fullNameHtml() + " - status: " + match.status());
			assertThat(match.fullNameHtml()).containsIgnoringCase("OMAR");
		}

		// Search list records for "terminated"
		SearchService.SearchResponse terminatedResponse = searchService.searchEntries("terminated");
		System.out.println("SearchService for 'terminated' returned: " + terminatedResponse.totalCount() + " matches.");
		assertThat(terminatedResponse.results()).isNotEmpty();
		for (SearchService.SearchHit match : terminatedResponse.results()) {
			assertThat(match.status()).isEqualTo("terminated");
		}
	}
}
