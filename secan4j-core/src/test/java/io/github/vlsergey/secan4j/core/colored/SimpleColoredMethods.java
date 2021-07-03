package io.github.vlsergey.secan4j.core.colored;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import io.github.vlsergey.secan4j.annotations.Command;
import io.github.vlsergey.secan4j.annotations.UserProvided;

public class SimpleColoredMethods {

	public void append(@UserProvided String src, StringBuilder dst) {
		dst.append(src);
	}

	public void arraycopy(@UserProvided byte[] src, byte[] dst) {
		System.arraycopy(src, 0, dst, 0, src.length);
	}

	@Command
	public String concatenation(@UserProvided String a, @UserProvided String b) {
		final String result = a + b;
		return result;
	}

	public PreparedStatement prepareStatement(Connection connection, @UserProvided String sql) throws SQLException {
		return connection.prepareStatement(sql);
	}

}
