package com.themistra.auth.account;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Binds/extracts a {@code String}-mapped {@code citext} column ({@link Account#email}) without
 * going through Hibernate's {@code ObjectJdbcType} (the usual choice for {@code SqlTypes.OTHER}).
 * Confirmed by reading {@code ObjectJdbcType}'s own source: its {@code getBinder()} special-cases
 * any {@code java.io.Serializable} Java type — which {@code String} is — and delegates to {@code
 * VarbinaryJdbcType}, correct for a genuine serialized-blob {@code OTHER} column but wrong for
 * text-like {@code citext}. This is the exact, confirmed root cause of
 * {@code "Could not convert 'java.lang.String' to '[B'"} on any parameterized query against
 * {@code email} — not specific to any one query shape, since {@code ObjectJdbcType} would produce
 * the same broken binder for every derived query touching this column.
 *
 * <p>Binds via {@code setObject(index, value, Types.OTHER)} — deliberately not {@code
 * Types.VARCHAR}: an explicit VARCHAR type code was tried and confirmed, by test, to defeat
 * Postgres's citext comparison-operator inference, silently turning email lookups
 * case-sensitive. {@code Types.OTHER} leaves the parameter's type unspecified from Postgres's
 * perspective, which is what lets the server infer {@code citext}'s own operator from the
 * column being compared against.</p>
 */
public class CitextJdbcType implements JdbcType {

    public static final CitextJdbcType INSTANCE = new CitextJdbcType();

    @Override
    public int getJdbcTypeCode() {
        return Types.OTHER;
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        return new BasicBinder<>(javaType, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                st.setObject(index, javaType.unwrap(value, String.class, options), Types.OTHER);
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options) throws SQLException {
                st.setObject(name, javaType.unwrap(value, String.class, options), Types.OTHER);
            }
        };
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        return new BasicExtractor<>(javaType, this) {
            @Override
            protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
                return javaType.wrap(rs.getString(paramIndex), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                return javaType.wrap(statement.getString(index), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
                return javaType.wrap(statement.getString(name), options);
            }
        };
    }
}
