/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.odbc.odbc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.apache.ignite.IgniteException;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.internal.processors.cache.QueryCursorImpl;
import org.apache.ignite.internal.processors.odbc.SqlListenerDataTypes;
import org.apache.ignite.internal.processors.odbc.SqlListenerUtils;
import org.apache.ignite.internal.processors.query.GridQueryFieldMetadata;
import org.apache.ignite.internal.processors.query.QueryUtils;
import org.apache.ignite.internal.util.typedef.F;

/**
 * Various ODBC utility methods.
 */
public class OdbcUtils {
    /**
     * Add quotation marks at the beginning and end of the string.
     *
     * @param str Input string.
     * @return String surrounded with quotation marks.
     */
    public static String addQuotationMarksIfNeeded(String str) {
        if (str != null && !str.isEmpty() && !(str.startsWith("\"") && str.endsWith("\"")))
            return "\"" + str + "\"";

        return str;
    }

    /**
     * Remove quotation marks at the beginning and end of the string if present.
     *
     * @param str Input string.
     * @return String without leading and trailing quotation marks.
     */
    public static String removeQuotationMarksIfNeeded(String str) {
        if (str != null && str.startsWith("\"") && str.endsWith("\""))
            return str.substring(1, str.length() - 1);

        return str;
    }

    /**
     * Pre-process table or column pattern.
     *
     * @param ptrn Pattern to pre-process.
     * @return Processed pattern.
     */
    public static String preprocessPattern(String ptrn) {
        if (F.isEmpty(ptrn))
            return ptrn;

        String ptrn0 = removeQuotationMarksIfNeeded(ptrn.toUpperCase());

        return SqlListenerUtils.translateSqlWildcardsToRegex(ptrn0);
    }

    /**
     * Prepare client's schema for processing.
     * @param schema Schema.
     * @return Prepared schema.
     */
    public static String prepareSchema(String schema) {
        String schema0 = removeQuotationMarksIfNeeded(schema);

        return F.isEmpty(schema0) ? QueryUtils.DFLT_SCHEMA : schema0;
    }

    /**
     * Private constructor.
     */
    private OdbcUtils() {
        // No-op.
    }

    /**
     * Lookup Ignite data type corresponding to specific ODBC data type
     *
     * @param odbcDataType ODBC data type identifier
     * @return Ignite data type name
     */
    public static String getIgniteTypeFromOdbcType(String odbcDataType) {
        assert odbcDataType != null;
        switch (odbcDataType.toUpperCase()) {
            case OdbcTypes.SQL_BIGINT:
                return SqlListenerDataTypes.BIGINT;

            case OdbcTypes.SQL_BINARY:
            case OdbcTypes.SQL_LONGVARBINARY:
            case OdbcTypes.SQL_VARBINARY:
                return SqlListenerDataTypes.BINARY;

            case OdbcTypes.SQL_BIT:
                return SqlListenerDataTypes.BIT;

            case OdbcTypes.SQL_CHAR:
                return SqlListenerDataTypes.CHAR;

            case OdbcTypes.SQL_DECIMAL:
            case OdbcTypes.SQL_NUMERIC:
                return SqlListenerDataTypes.DECIMAL;

            case OdbcTypes.SQL_LONGVARCHAR:
            case OdbcTypes.SQL_VARCHAR:
            case OdbcTypes.SQL_WCHAR:
            case OdbcTypes.SQL_WLONGVARCHAR:
            case OdbcTypes.SQL_WVARCHAR:
                return SqlListenerDataTypes.VARCHAR;

            case OdbcTypes.SQL_DOUBLE:
            case OdbcTypes.SQL_FLOAT:
                return SqlListenerDataTypes.DOUBLE;

            case OdbcTypes.SQL_REAL:
                return SqlListenerDataTypes.REAL;

            case OdbcTypes.SQL_GUID:
                return SqlListenerDataTypes.UUID;

            case OdbcTypes.SQL_SMALLINT:
                return SqlListenerDataTypes.SMALLINT;

            case OdbcTypes.SQL_INTEGER:
                return SqlListenerDataTypes.INTEGER;

            case OdbcTypes.SQL_DATE:
                return SqlListenerDataTypes.DATE;

            case OdbcTypes.SQL_TIME:
                return SqlListenerDataTypes.TIME;

            case OdbcTypes.SQL_TIMESTAMP:
                return SqlListenerDataTypes.TIMESTAMP;

            case OdbcTypes.SQL_TINYINT:
                return SqlListenerDataTypes.TINYINT;

            //No support for interval types
            case OdbcTypes.SQL_INTERVAL_SECOND:
            case OdbcTypes.SQL_INTERVAL_MINUTE:
            case OdbcTypes.SQL_INTERVAL_HOUR:
            case OdbcTypes.SQL_INTERVAL_DAY:
            case OdbcTypes.SQL_INTERVAL_MONTH:
            case OdbcTypes.SQL_INTERVAL_YEAR:
            case OdbcTypes.SQL_INTERVAL_YEAR_TO_MONTH:
            case OdbcTypes.SQL_INTERVAL_HOUR_TO_MINUTE:
            case OdbcTypes.SQL_INTERVAL_HOUR_TO_SECOND:
            case OdbcTypes.SQL_INTERVAL_MINUTE_TO_SECOND:
            case OdbcTypes.SQL_INTERVAL_DAY_TO_HOUR:
            case OdbcTypes.SQL_INTERVAL_DAY_TO_MINUTE:
            case OdbcTypes.SQL_INTERVAL_DAY_TO_SECOND:
                throw new IgniteException("Unsupported ODBC data type '" + odbcDataType + "'");

            default:
                throw new IgniteException("Invalid ODBC data type '" + odbcDataType + "'");
        }
    }

    /**
     * Tries to retrieve H2 engine error message from exception. If the exception is not of type
     * "org.h2.jdbc.JdbcSQLException" returns original error message.
     *
     * @param err Exception.
     * @return Error message.
     */
    public static String tryRetrieveH2ErrorMessage(Throwable err) {
        String msg = err.getMessage();

        Throwable e = err.getCause();

        while (e != null) {
            if (e.getClass().getCanonicalName().equals("org.h2.jdbc.JdbcSQLException")) {
                msg = e.getMessage();

                break;
            }

            e = e.getCause();
        }

        return msg;
    }

    /**
     * Get affected rows for statement.
     * @param qryCur Cursor.
     * @return Number of table rows affected, if the query is DML, and -1 otherwise.
     */
    public static long rowsAffected(QueryCursor<List<?>> qryCur) {
        QueryCursorImpl<List<?>> qryCur0 = (QueryCursorImpl<List<?>>)qryCur;

        if (qryCur0.isQuery())
            return -1;

        Iterator<List<?>> iter = qryCur0.iterator();

        if (iter.hasNext()) {
            List<?> res = iter.next();

            if (!res.isEmpty()) {
                Long affected = (Long)res.get(0);

                if (affected != null)
                    return affected;
            }
        }

        return 0;
    }

    /**
     * Convert metadata in collection from {@link GridQueryFieldMetadata} to
     * {@link OdbcColumnMeta}.
     *
     * @param meta Internal query field metadata.
     * @return Odbc query field metadata.
     */
    public static Collection<OdbcColumnMeta> convertMetadata(Collection<GridQueryFieldMetadata> meta) {
        List<OdbcColumnMeta> res = new ArrayList<>();

        if (meta != null) {
            for (GridQueryFieldMetadata info : meta)
                res.add(new OdbcColumnMeta(info));
        }

        return res;
    }
}
