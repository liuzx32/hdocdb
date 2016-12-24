/**
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package org.apache.hadoop.hbase.client.mock;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.Service;
import com.google.protobuf.ServiceException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.client.coprocessor.Batch;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.ipc.CoprocessorRpcChannel;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * MockHTable.
 *
 * original MockHTable (by agaoglu) : https://gist.github.com/agaoglu/613217#file_mock_h_table.java
 *
 * Modifications
 *
 * <ul>
 *     <li>fix filter (by k-mack) : https://gist.github.com/k-mack/4600133</li>
 *     <li>fix batch() : implement all mutate operation and fix result[] count.</li>
 *     <li>fix exists()</li>
 *     <li>fix increment() : wrong return value</li>
 *     <li>check columnFamily</li>
 *     <li>implement mutateRow()</li>
 *     <li>implement getTableName()</li>
 *     <li>implement getTableDescriptor()</li>
 *     <li>throws RuntimeException when unimplemented method was called.</li>
 *     <li>remove some methods for loading data, checking values ...</li>
 * </ul>
 */
@SuppressWarnings("deprecation")
public class MockHTable implements HTableInterface {
    private static final Logger LOG = LoggerFactory.getLogger(MockHTable.class);

    private final TableName tableName;
    private final List<String> columnFamilies = new ArrayList<>();
    private final List<Class<? extends Service>> coprocessorClasses = new ArrayList<>();
    private Configuration config;

    private final NavigableMap<byte[], NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>>> data
            = new ConcurrentSkipListMap<>(Bytes.BYTES_COMPARATOR);

    private static List<KeyValue> toKeyValue(byte[] row, NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> rowdata, int maxVersions) {
        return toKeyValue(row, rowdata, 0, Long.MAX_VALUE, maxVersions);
    }

    public MockHTable(TableName tableName) {
        this.tableName = tableName;
    }

    public MockHTable(TableName tableName, String... columnFamilies) {
        this.tableName = tableName;
        this.columnFamilies.addAll(Arrays.asList(columnFamilies));
    }

    public MockHTable(TableName tableName, List<String> columnFamilies) {
        this.tableName = tableName;
        this.columnFamilies.addAll(columnFamilies);
    }

    public MockHTable(TableName tableName, List<String> columnFamilies,
                      List<Class<? extends Service>> coprocessorClasses) {
        this.tableName = tableName;
        this.columnFamilies.addAll(columnFamilies);
        this.coprocessorClasses.addAll(coprocessorClasses);
    }

    public void addColumnFamily(String columnFamily) {
        this.columnFamilies.add(columnFamily);
    }

    public void clear() {
        data.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getTableName() {
        return tableName.getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TableName getName() {
        return tableName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Configuration getConfiguration() {
        return config;
    }

    public MockHTable setConfiguration(Configuration config) {
        this.config = config;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HTableDescriptor getTableDescriptor() throws IOException {
        HTableDescriptor table = new HTableDescriptor(tableName);
        for (String columnFamily : columnFamilies) {
            table.addFamily(new HColumnDescriptor(columnFamily));
        }
        return table;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mutateRow(RowMutations rm) throws IOException {
        // currently only support Put and Delete
        long maxTs = System.currentTimeMillis();
        for (Mutation mutation : rm.getMutations()) {
            if (mutation instanceof Put) {
                put((Put) mutation);
            } else if (mutation instanceof Delete) {
                delete((Delete) mutation);
            }
            long ts = mutation.getTimeStamp();
            if (ts != HConstants.LATEST_TIMESTAMP && ts > maxTs) maxTs = ts;
        }
        // we have intentionally set the ts in the future, so wait
        long now = System.currentTimeMillis();
        while (now <= maxTs) {
            now = System.currentTimeMillis();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result append(Append append) throws IOException {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    private static List<KeyValue> toKeyValue(byte[] row, NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> rowdata, long timestampStart, long timestampEnd, int maxVersions) {
        List<KeyValue> ret = new ArrayList<>();
        for (byte[] family : rowdata.keySet())
            for (byte[] qualifier : rowdata.get(family).keySet()) {
                int versionsAdded = 0;
                for (Map.Entry<Long, byte[]> tsToVal : rowdata.get(family).get(qualifier).descendingMap().entrySet()) {
                    if (versionsAdded++ == maxVersions)
                        break;
                    Long timestamp = tsToVal.getKey();
                    if (timestamp < timestampStart)
                        continue;
                    if (timestamp > timestampEnd)
                        continue;
                    byte[] value = tsToVal.getValue();
                    ret.add(new KeyValue(row, family, qualifier, timestamp, value));
                }
            }
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(Get get) throws IOException {
        Result result = get(get);
        return result != null && !result.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean[] exists(List<Get> gets) throws IOException {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    @Override
    public boolean[] existsAll(List<Get> var1) throws IOException {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void batch(List<? extends Row> actions, Object[] results) throws IOException, InterruptedException {
        Object[] rows = batch(actions);
        System.arraycopy(rows, 0, results, 0, rows.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object[] batch(List<? extends Row> actions) throws IOException, InterruptedException {
        Object[] results = new Object[actions.size()]; // same size.
        for (int i = 0; i < actions.size(); i++) {
            Row r = actions.get(i);
            if (r instanceof Delete) {
                delete((Delete) r);
                results[i] = new Result();
            }
            if (r instanceof Put) {
                put((Put) r);
                results[i] = new Result();
            }
            if (r instanceof Get) {
                Result result = get((Get) r);
                results[i] = result;
            }
            if (r instanceof Increment) {
                Result result = increment((Increment) r);
                results[i] = result;
            }
            if (r instanceof Append) {
                Result result = append((Append) r);
                results[i] = result;
            }
        }
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> void batchCallback(
            final List<? extends Row> actions, final Object[] results, final Batch.Callback<R> callback)
            throws IOException, InterruptedException {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public <R> Object[] batchCallback(
            List<? extends Row> actions, Batch.Callback<R> callback) throws IOException,
            InterruptedException {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result get(Get get) throws IOException {
        if (!data.containsKey(get.getRow()))
            return new Result();
        byte[] row = get.getRow();
        List<KeyValue> kvs = new ArrayList<>();
        Filter filter = get.getFilter();
        int maxResults = get.getMaxResultsPerColumnFamily();

        if (!get.hasFamilies()) {
            kvs = toKeyValue(row, data.get(row), get.getMaxVersions());
            if (filter != null) {
                kvs = filter(filter, kvs);
            }
            if (maxResults >= 0 && kvs.size() > maxResults) {
                kvs = kvs.subList(0, maxResults);
            }
        } else {
            for (byte[] family : get.getFamilyMap().keySet()) {
                if (data.get(row).get(family) == null)
                    continue;
                NavigableSet<byte[]> qualifiers = get.getFamilyMap().get(family);
                if (qualifiers == null || qualifiers.isEmpty())
                    qualifiers = data.get(row).get(family).navigableKeySet();
                List<KeyValue> familyKvs = new ArrayList<>();
                for (byte[] qualifier : qualifiers) {
                    if (qualifier == null)
                        qualifier = "".getBytes();
                    if (!data.get(row).containsKey(family) ||
                            !data.get(row).get(family).containsKey(qualifier) ||
                            data.get(row).get(family).get(qualifier).isEmpty())
                        continue;
                    Map.Entry<Long, byte[]> timestampAndValue = data.get(row).get(family).get(qualifier).lastEntry();
                    familyKvs.add(new KeyValue(row, family, qualifier, timestampAndValue.getKey(), timestampAndValue.getValue()));
                }
                if (filter != null) {
                    familyKvs = filter(filter, familyKvs);
                }
                if (maxResults >= 0 && familyKvs.size() > maxResults) {
                    familyKvs = familyKvs.subList(0, maxResults);
                }
                kvs.addAll(familyKvs);
            }
        }
        return new Result(kvs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result[] get(List<Get> gets) throws IOException {
        List<Result> results = new ArrayList<>();
        for (Get g : gets) {
            results.add(get(g));
        }
        return results.toArray(new Result[results.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result getRowOrBefore(byte[] row, byte[] family) throws IOException {
        // FIXME: implement
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultScanner getScanner(Scan scan) throws IOException {
        final List<Result> ret = new ArrayList<>();
        byte[] st = scan.getStartRow();
        byte[] sp = scan.getStopRow();
        Filter filter = scan.getFilter();
        int maxResults = scan.getMaxResultsPerColumnFamily();

        Set<byte[]> dataKeySet = scan.isReversed() ? data.descendingKeySet() : data.keySet();
        for (byte[] row : dataKeySet) {
            // if row is equal to startRow emit it. When startRow (inclusive) and
            // stopRow (exclusive) is the same, it should not be excluded which would
            // happen w/o this control.
            if (st != null && st.length > 0 &&
                    Bytes.BYTES_COMPARATOR.compare(st, row) != 0) {
                if (scan.isReversed()) {
                    // if row is before startRow do not emit, pass to next row
                    if (st != null && st.length > 0 &&
                            Bytes.BYTES_COMPARATOR.compare(st, row) <= 0)
                        continue;
                    // if row is equal to stopRow or after it do not emit, stop iteration
                    if (sp != null && sp.length > 0 &&
                            Bytes.BYTES_COMPARATOR.compare(sp, row) > 0)
                        break;
                } else {
                    // if row is before startRow do not emit, pass to next row
                    if (st != null && st.length > 0 &&
                            Bytes.BYTES_COMPARATOR.compare(st, row) > 0)
                        continue;
                    // if row is equal to stopRow or after it do not emit, stop iteration
                    if (sp != null && sp.length > 0 &&
                            Bytes.BYTES_COMPARATOR.compare(sp, row) <= 0)
                        break;
                }
            }

            List<KeyValue> kvs;
            if (!scan.hasFamilies()) {
                kvs = toKeyValue(row, data.get(row), scan.getTimeRange().getMin(), scan.getTimeRange().getMax(), scan.getMaxVersions());
                if (filter != null) {
                    kvs = filter(filter, kvs);
                }
                if (maxResults >= 0 && kvs.size() > maxResults) {
                    kvs = kvs.subList(0, maxResults);
                }
            } else {
                kvs = new ArrayList<>();
                for (byte[] family : scan.getFamilyMap().keySet()) {
                    if (data.get(row).get(family) == null)
                        continue;
                    NavigableSet<byte[]> qualifiers = scan.getFamilyMap().get(family);
                    if (qualifiers == null || qualifiers.isEmpty())
                        qualifiers = data.get(row).get(family).navigableKeySet();
                    List<KeyValue> familyKvs = new ArrayList<>();
                    for (byte[] qualifier : qualifiers) {
                        if (data.get(row).get(family).get(qualifier) == null)
                            continue;
                        List<KeyValue> tsKvs = new ArrayList<>();
                        for (Long timestamp : data.get(row).get(family).get(qualifier).descendingKeySet()) {
                            if (timestamp < scan.getTimeRange().getMin())
                                continue;
                            if (timestamp > scan.getTimeRange().getMax())
                                continue;
                            byte[] value = data.get(row).get(family).get(qualifier).get(timestamp);
                            tsKvs.add(new KeyValue(row, family, qualifier, timestamp, value));
                            if (tsKvs.size() == scan.getMaxVersions()) {
                                break;
                            }
                        }
                        familyKvs.addAll(tsKvs);
                    }
                    if (filter != null) {
                        familyKvs = filter(filter, familyKvs);
                    }
                    if (maxResults >= 0 && familyKvs.size() > maxResults) {
                        familyKvs = familyKvs.subList(0, maxResults);
                    }
                    kvs.addAll(familyKvs);
                }
            }
            if (!kvs.isEmpty()) {
                ret.add(new Result(kvs));
            }
            // Check for early out optimization
            if (filter != null && filter.filterAllRemaining()) {
                break;
            }
        }

        return new ResultScanner() {
            private final Iterator<Result> iterator = ret.iterator();

            public Iterator<Result> iterator() {
                return iterator;
            }

            public Result[] next(int nbRows) throws IOException {
                ArrayList<Result> resultSets = new ArrayList<>(nbRows);
                for (int i = 0; i < nbRows; i++) {
                    Result next = next();
                    if (next != null) {
                        resultSets.add(next);
                    } else {
                        break;
                    }
                }
                return resultSets.toArray(new Result[resultSets.size()]);
            }

            public Result next() throws IOException {
                try {
                    return iterator().next();
                } catch (NoSuchElementException e) {
                    return null;
                }
            }

            public void close() {
            }
        };
    }

    /**
     * Follows the logical flow through the filter methods for a single row.
     *
     * @param filter HBase filter.
     * @param kvs    List of a row's KeyValues
     * @return List of KeyValues that were not filtered.
     */
    private List<KeyValue> filter(Filter filter, List<KeyValue> kvs) throws IOException {
        filter.reset();

        List<KeyValue> tmp = new ArrayList<>(kvs.size());
        tmp.addAll(kvs);

      /*
       * Note. Filter flow for a single row. Adapted from
       * "HBase: The Definitive Guide" (p. 163) by Lars George, 2011.
       * See Figure 4-2 on p. 163.
       */
        boolean filteredOnRowKey = false;
        List<KeyValue> nkvs = new ArrayList<>(tmp.size());
        for (KeyValue kv : tmp) {
            if (filter.filterRowKey(kv.getBuffer(), kv.getRowOffset(), kv.getRowLength())) {
                filteredOnRowKey = true;
                break;
            }
            Filter.ReturnCode filterResult = filter.filterKeyValue(kv);
            if (filterResult == Filter.ReturnCode.INCLUDE || filterResult == Filter.ReturnCode.INCLUDE_AND_NEXT_COL) {
                nkvs.add(KeyValueUtil.ensureKeyValue(filter.transformCell(kv)));
            } else if (filterResult == Filter.ReturnCode.NEXT_ROW) {
                break;
            } else if (filterResult == Filter.ReturnCode.NEXT_COL || filterResult == Filter.ReturnCode.SKIP) {
                //noinspection UnnecessaryContinue
                continue;
            }
          /*
           * Ignoring next key hint which is a optimization to reduce file
           * system IO
           */
        }
        if (filter.hasFilterRow() && !filteredOnRowKey) {
            //filter.filterRow(nkvs);  // deprecated API
            List<Cell> cells = new ArrayList<>(nkvs.size());
            cells.addAll(nkvs);
            filter.filterRowCells(cells);
            nkvs.clear();
            for (Cell cell : cells) {
                nkvs.add((KeyValue) cell);
            }
        }
        if (filter.filterRow() || filteredOnRowKey) {
            nkvs.clear();
        }
        tmp = nkvs;
        return tmp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultScanner getScanner(byte[] family) throws IOException {
        Scan scan = new Scan();
        scan.addFamily(family);
        return getScanner(scan);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultScanner getScanner(byte[] family, byte[] qualifier) throws IOException {
        Scan scan = new Scan();
        scan.addColumn(family, qualifier);
        return getScanner(scan);
    }

    private <K, V> V forceFind(NavigableMap<K, V> map, K key, V newObject) {
        V data = map.putIfAbsent(key, newObject);
        if (data == null) {
            data = newObject;
        }
        return data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(Put put) throws IOException {
        byte[] row = put.getRow();
        NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> rowData = forceFind(data, row, new ConcurrentSkipListMap<>(Bytes.BYTES_COMPARATOR));
        for (byte[] family : put.getFamilyMap().keySet()) {
            if (!columnFamilies.contains(new String(family))) {
                throw new RuntimeException("Not Exists columnFamily : " + new String(family));
            }
            NavigableMap<byte[], NavigableMap<Long, byte[]>> familyData = forceFind(rowData, family, new ConcurrentSkipListMap<>(Bytes.BYTES_COMPARATOR));
            for (KeyValue kv : put.getFamilyMap().get(family)) {
                long ts = put.getTimeStamp();
                if (ts == HConstants.LATEST_TIMESTAMP) ts = System.currentTimeMillis();
                kv.updateLatestStamp(Bytes.toBytes(ts));
                byte[] qualifier = kv.getQualifier();
                NavigableMap<Long, byte[]> qualifierData = forceFind(familyData, qualifier, new ConcurrentSkipListMap<>());
                qualifierData.put(kv.getTimestamp(), kv.getValue());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(List<Put> puts) throws IOException {
        for (Put put : puts) {
            put(put);
        }

    }

    private boolean check(byte[] row, byte[] family, byte[] qualifier, CompareFilter.CompareOp compareOp, byte[] value) {
        if (value == null)
            return !data.containsKey(row) ||
                    !data.get(row).containsKey(family) ||
                    !data.get(row).get(family).containsKey(qualifier);
        else if (data.containsKey(row) &&
                data.get(row).containsKey(family) &&
                data.get(row).get(family).containsKey(qualifier) &&
                !data.get(row).get(family).get(qualifier).isEmpty()) {

            byte[] oldValue = data.get(row).get(family).get(qualifier).lastEntry().getValue();
            int compareResult = Bytes.compareTo(value, oldValue);
            switch (compareOp) {
                case LESS:
                    return compareResult < 0;
                case LESS_OR_EQUAL:
                    return compareResult <= 0;
                case EQUAL:
                    return compareResult == 0;
                case NOT_EQUAL:
                    return compareResult != 0;
                case GREATER_OR_EQUAL:
                    return compareResult >= 0;
                case GREATER:
                    return compareResult > 0;
                default:
                    throw new RuntimeException("Unknown Compare op " + compareOp.name());
            }
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkAndPut(byte[] row, byte[] family, byte[] qualifier, byte[] value, Put put) throws IOException {
        return checkAndPut(row, family, qualifier, CompareFilter.CompareOp.EQUAL, value, put);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkAndPut(byte[] row, byte[] family, byte[] qualifier, CompareFilter.CompareOp compareOp, byte[] value, Put put) throws IOException {
        if (check(row, family, qualifier, compareOp, value)) {
            put(put);
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Delete delete) throws IOException {
        byte[] row = delete.getRow();
        if (data.get(row) == null)
            return;
        if (delete.getFamilyMap().size() == 0) {
            data.remove(row);
            return;
        }
        for (byte[] family : delete.getFamilyMap().keySet()) {
            if (data.get(row).get(family) == null)
                continue;
            if (delete.getFamilyMap().get(family).isEmpty()) {
                data.get(row).remove(family);
                continue;
            }
            for (KeyValue kv : delete.getFamilyMap().get(family)) {
                long ts = kv.getTimestamp();
                if (kv.getTypeByte() == KeyValue.Type.DeleteColumn.getCode()) {
                    if (ts == HConstants.LATEST_TIMESTAMP) {
                        data.get(row).get(kv.getFamily()).remove(kv.getQualifier());
                    } else {
                        data.get(row).get(kv.getFamily()).get(kv.getQualifier()).subMap(0L, true, ts, true).clear();
                    }
                } else {
                    if (ts == HConstants.LATEST_TIMESTAMP) {
                        data.get(row).get(kv.getFamily()).get(kv.getQualifier()).pollLastEntry();
                    } else {
                        data.get(row).get(kv.getFamily()).get(kv.getQualifier()).remove(ts);
                    }
                }
            }
            if (data.get(row).get(family).isEmpty()) {
                data.get(row).remove(family);
            }
        }
        if (data.get(row).isEmpty()) {
            data.remove(row);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(List<Delete> deletes) throws IOException {
        for (Delete delete : deletes) {
            delete(delete);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkAndDelete(byte[] row, byte[] family, byte[] qualifier, byte[] value, Delete delete) throws IOException {
        return checkAndDelete(row, family, qualifier, CompareFilter.CompareOp.EQUAL, value, delete);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkAndDelete(byte[] row, byte[] family, byte[] qualifier, CompareFilter.CompareOp compareOp, byte[] value, Delete delete) throws IOException {
        if (check(row, family, qualifier, compareOp, value)) {
            delete(delete);
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkAndMutate(byte[] row, byte[] family, byte[] qualifier, CompareFilter.CompareOp compareOp, byte[] value, RowMutations rm) throws IOException {
        if (check(row, family, qualifier, compareOp, value)) {
            mutateRow(rm);
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result increment(Increment increment) throws IOException {
        List<KeyValue> kvs = new ArrayList<>();
        Map<byte[], NavigableMap<byte[], Long>> famToVal = increment.getFamilyMapOfLongs();
        for (Map.Entry<byte[], NavigableMap<byte[], Long>> ef : famToVal.entrySet()) {
            byte[] family = ef.getKey();
            NavigableMap<byte[], Long> qToVal = ef.getValue();
            for (Map.Entry<byte[], Long> eq : qToVal.entrySet()) {
                long newValue = incrementColumnValue(increment.getRow(), family, eq.getKey(), eq.getValue());
                Map.Entry<Long, byte[]> timestampAndValue = data.get(increment.getRow()).get(family).get(eq.getKey()).lastEntry();
                kvs.add(new KeyValue(increment.getRow(), family, eq.getKey(), timestampAndValue.getKey(), timestampAndValue.getValue()));
            }
        }
        return new Result(kvs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long incrementColumnValue(byte[] row, byte[] family, byte[] qualifier, long amount) throws IOException {
        return incrementColumnValue(row, family, qualifier, amount, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long incrementColumnValue(byte[] row, byte[] family, byte[] qualifier,
                                     long amount, Durability durability) throws IOException {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long incrementColumnValue(byte[] row, byte[] family, byte[] qualifier, long amount, boolean writeToWAL) throws IOException {
        if (check(row, family, qualifier, CompareFilter.CompareOp.EQUAL, null)) {
            Put put = new Put(row);
            put.add(family, qualifier, Bytes.toBytes(amount));
            put(put);
            return amount;
        }
        long newValue = Bytes.toLong(data.get(row).get(family).get(qualifier).lastEntry().getValue()) + amount;
        data.get(row).get(family).get(qualifier).put(System.currentTimeMillis(),
                Bytes.toBytes(newValue));
        return newValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAutoFlush() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flushCommits() throws IOException {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CoprocessorRpcChannel coprocessorService(byte[] row) {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends Service, R> Map<byte[], R> coprocessorService(final Class<T> service,
                                                                    byte[] startKey, byte[] endKey, final Batch.Call<T, R> callable)
            throws ServiceException {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    private Class<? extends Service> getCoprocessorClass(final Class<? extends Service> service) {
        for (Class<? extends Service> coprocessorClass : coprocessorClasses) {
            if (service.isAssignableFrom(coprocessorClass)) {
                return coprocessorClass;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends Service, R> void coprocessorService(final Class<T> service,
                                                          byte[] startKey, byte[] endKey, final Batch.Call<T, R> callable,
                                                          final Batch.Callback<R> callback) throws ServiceException {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAutoFlush(boolean autoFlush) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAutoFlush(boolean autoFlush, boolean clearBufferOnFail) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAutoFlushTo(boolean autoFlush) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getWriteBufferSize() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWriteBufferSize(long writeBufferSize) throws IOException {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R extends Message> Map<byte[], R> batchCoprocessorService(
            Descriptors.MethodDescriptor methodDescriptor, Message request,
            byte[] startKey, byte[] endKey, R responsePrototype) throws ServiceException {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R extends Message> void batchCoprocessorService(Descriptors.MethodDescriptor methodDescriptor,
                                                            Message request, byte[] startKey, byte[] endKey, R responsePrototype,
                                                            Batch.Callback<R> callback) throws ServiceException {
        throw new RuntimeException(this.getClass() + " does NOT implement this method.");
    }
}
