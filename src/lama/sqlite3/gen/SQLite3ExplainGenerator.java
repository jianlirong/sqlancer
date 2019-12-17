package lama.sqlite3.gen;

import java.sql.SQLException;

import lama.Query;
import lama.QueryAdapter;
import lama.Randomly;
import lama.sqlite3.SQLite3Provider;
import lama.sqlite3.SQLite3Provider.Action;
import lama.sqlite3.SQLite3Provider.SQLite3GlobalState;

public class SQLite3ExplainGenerator {

	public static Query explain(SQLite3GlobalState globalState) throws SQLException {
		StringBuilder sb = new StringBuilder();
		sb.append("EXPLAIN ");
		if (Randomly.getBoolean()) {
			sb.append("QUERY PLAN ");
		}
		Action action;
		do {
			action = Randomly.fromOptions(SQLite3Provider.Action.values());
			if (action == Action.EXPLAIN) {
				continue;
			}
			break;
		} while (true);
		Query query = action.getQuery(globalState);
		sb.append(query);
		return new QueryAdapter(sb.toString(), query.getExpectedErrors());
	}

}
