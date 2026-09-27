package com.tutorial.ai_challenge;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SampleStore {

	public record Summary(long total, Map<String, Long> byColor, double avgNumber, int minNumber, int maxNumber,
			Timestamp firstTs, Timestamp lastTs) {
	}

	private final JdbcTemplate jdbc;

	public SampleStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void insert(String color, int number) {
		jdbc.update("INSERT INTO mock_samples (color, sample_number) VALUES (?, ?)", color, number);
	}

	public void deleteOlderThan24h() {
		jdbc.update("DELETE FROM mock_samples WHERE ts < now() - INTERVAL '24 hours'");
	}

	public Summary aggregate() {
		Map<String, Long> byColor = new LinkedHashMap<>();
		jdbc.query("SELECT color, count(*) AS cnt FROM mock_samples GROUP BY color ORDER BY cnt DESC",
				rs -> {
					byColor.put(rs.getString("color"), rs.getLong("cnt"));
				});
		return jdbc.queryForObject(
				"SELECT count(*), coalesce(avg(sample_number), 0), coalesce(min(sample_number), 0), coalesce(max(sample_number), 0), min(ts), max(ts) FROM mock_samples",
				(rs, i) -> new Summary(rs.getLong(1), byColor, rs.getDouble(2), rs.getInt(3), rs.getInt(4),
						rs.getTimestamp(5), rs.getTimestamp(6)));
	}

}
