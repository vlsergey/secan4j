package io.github.vlsergey.secan4j.core.springwebmvc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

public class BadControllerExample {

	@Autowired
	private DataSource dataSource;

	@GetMapping
	public boolean sqlInjection(@RequestParam String userLogin, @RequestParam String userPassword) throws SQLException {
		try (final Connection c = dataSource.getConnection();
				final PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE userLogin='" + userLogin
						+ "' AND userPassword='" + userPassword + "'");
				final ResultSet rs = ps.executeQuery()) {
			return rs.next();
		}
	}

}
