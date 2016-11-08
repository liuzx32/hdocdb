/**
 * Copyright (c) 2015 MapR, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ojai.store;

import org.ojai.Document;
import org.ojai.FieldPath;
import org.ojai.Value;
import org.ojai.Value.Type;
import org.ojai.annotation.API;
import org.ojai.types.ODate;
import org.ojai.types.OInterval;
import org.ojai.types.OTime;
import org.ojai.types.OTimestamp;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * The DocumentMutation interface defines the APIs to perform mutation of a
 * Document already stored in a DocumentStore.
 *
 * Please see the following notes regarding the behavior of the API types in
 * this Interface.
 *
 * <h3>{@code set()}</h3>
 * These APIs validate the type of existing value at the specified FieldPath
 * before applying the mutation. If the field does not exist in the corresponding
 * Document in the DocumentStore, it is created. If the field exists but is not
 * of the same type as the type of new value, then the entire mutation fails.
 *
 * <h3>{@code setOrReplace()}</h3>
 * These are performant APIs that do not require or perform a read-modify-write
 * operation on the server.
 *
 * If a segment in the specified FieldPath doesn't exist, it is created. For example:
 * <blockquote>{@code setOrReplace("a.b.c", (int) 10)}</blockquote>
 * In this example, if the Document stored in the DocumentStore has an empty MAP
 * field {@code "a"}, then a setOrReplace of {@code "a.b.c"} will create a field
 * {@code "b"} of type MAP under {@code "a"}. It will also create an field named
 * {@code "c"} of type INTEGER under {@code "b"} and set its value to 10.
 *
 * If any segment specified in the FieldPath is of a different type than the
 * existing field segment on the server, it will be deleted and replaced by
 * a new segment. For example:
 * <blockquote>{@code setOrReplace("a.b.c", (int) 10)}</blockquote>
 * If the Document stored in the DocumentStore has a field "a" of type array.
 * This operation will delete "a", create new field "a" of type map, add a MAP
 * field "b" under "a" and finally create an INTEGER field "c" with value 10 under
 * "b".
 *
 * <b>Warning:</b> These are potentially destructive operations since they do
 * not validate existence or type of any field segment in the specified FieldPath.
 *
 * <h3>{@code append()}</h3>
 * These operations perform read-modify-write on the server and will fail if
 * type of any of the intermediate fields segment in the specified FieldPath
 * does not match the type of the corresponding field in the document stored
 * on server. For example, an append operation on field {@code "a.b.c"} will
 * fail if, on the server, the field {@code "a"} itself is an ARRAY or INTEGER.
 *
 * <h3>{@code merge()}</h3>
 * If the specified field is of a type other than MAP, then the operation will fail.
 * If the field doesn't exist in the Document on the server, then this operation will
 * create a new field at the given path. This new field will be of the MAP type and
 * its value will be as specified in the parameter.
 *
 * This operation will fail if any type of intermediate field segment specified
 * in the FieldPath doesn't match the type of the corresponding field in the
 * record stored on the server. For example, a merge operation on field {@code "a.b.c"}
 * will fail if, on the server, the field {@code "a"} itself is an ARRAY or INTEGER.
 *
 * <h3>{@code increment()}</h3>
 * If the FieldPath specified for the incremental change doesn't exist in the
 * corresponding Document in the DocumentStore then this operation will create
 * a new field at the given path. This new field will be of same type as the
 * value specified in the parameter.
 *
 * This operation will fail if the type of any intermediate fields specified
 * in the FieldPath doesn't match the type of the corresponding field in the
 * record stored on the server. For example, an operation on field "a.b.c" will fail
 * if, on the server, record a itself is an array or integer.
 *
 * An increment operation can be applied on any of the numeric types such as byte,
 * short, int, long, float, double, or decimal. The operation will fail if the
 * increment is applied to a field that is of a non-numeric type.
 *
 * The increment operation won't change the type of the existing value stored in
 * the given field for the row. The resultant value of the field will be
 * truncated based on the original type of the field.
 *
 * For example, field 'score' is of type int and contains 60. The increment
 * '5.675', a double, is applied. The resultant value of the field will be 65
 * (65.675 will be truncated to 65).
 */
@API.Public
public interface DocumentMutation extends Iterable<MutationOp> {

  /**
   * Empties this Mutation object.
   */
  public DocumentMutation empty();

  /**
   * Sets the field at the given FieldPath to {@link Type#NULL NULL} Value.
   *
   * @param path path of the field that needs to be updated
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setNull(String path);

  /**
   * Sets the field at the given FieldPath to {@link Type#NULL NULL} Value.
   *
   * @param path path of the field that needs to be updated
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setNull(FieldPath path);

  /**
   * Sets the field at the given FieldPath to the specified value.
   *
   * @param path path of the field that needs to be updated
   * @param value The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, Value value);

  /**
   * Sets the field at the given FieldPath to the specified value.
   *
   * @param path path of the field that needs to be updated
   * @param value The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, Value value);

  /**
   * Sets the field at the given FieldPath to the specified {@code boolean} value.
   *
   * @param path path of the field that needs to be updated
   * @param b The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, boolean b);

  /**
   * Sets the field at the given FieldPath to the specified {@code boolean} value.
   *
   * @param path path of the field that needs to be updated
   * @param b The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, boolean b);

  /**
   * Sets the field at the given FieldPath to the specified {@code byte} value.
   *
   * @param path path of the field that needs to be updated
   * @param b The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, byte b);

  /**
   * Sets the field at the given FieldPath to the specified {@code byte} value.
   *
   * @param path path of the field that needs to be updated
   * @param b The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, byte b);

  /**
   * Sets the field at the given FieldPath to the specified {@code short} value.
   *
   * @param path path of the field that needs to be updated
   * @param s The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, short s);

  /**
   * Sets the field at the given FieldPath to the specified {@code short} value.
   *
   * @param path path of the field that needs to be updated
   * @param s The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, short s);

  /**
   * Sets the field at the given FieldPath to the specified {@code int} value.
   *
   * @param path path of the field that needs to be updated
   * @param i The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, int i);

  /**
   * Sets the field at the given FieldPath to the specified {@code int} value.
   *
   * @param path path of the field that needs to be updated
   * @param i The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, int i);

  /**
   * Sets the field at the given FieldPath to the specified {@code long} value.
   *
   * @param path path of the field that needs to be updated
   * @param l The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, long l);

  /**
   * Sets the field at the given FieldPath to the specified {@code long} value.
   *
   * @param path path of the field that needs to be updated
   * @param l The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, long l);

  /**
   * Sets the field at the given FieldPath to the specified {@code float} value.
   *
   * @param path path of the field that needs to be updated
   * @param f The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, float f);

  /**
   * Sets the field at the given FieldPath to the specified {@code float} value.
   *
   * @param path path of the field that needs to be updated
   * @param f The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, float f);

  /**
   * Sets the field at the given FieldPath to the specified {@code double} value.
   *
   * @param path path of the field that needs to be updated
   * @param d The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, double d);

  /**
   * Sets the field at the given FieldPath to the specified {@code double} value.
   *
   * @param path path of the field that needs to be updated
   * @param d The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, double d);

  /**
   * Sets the field at the given FieldPath to the specified {@code String} value.
   *
   * @param path path of the field that needs to be updated
   * @param value The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, String value);

  /**
   * Sets the field at the given FieldPath to the specified {@code String} value.
   *
   * @param path path of the field that needs to be updated
   * @param value The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, String value);

  /**
   * Sets the field at the given FieldPath to the specified {@code BigDecimal} value.
   *
   * @param path path of the field that needs to be updated
   * @param bd The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, BigDecimal bd);

  /**
   * Sets the field at the given FieldPath to the specified {@code BigDecimal} value.
   *
   * @param path path of the field that needs to be updated
   * @param bd The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, BigDecimal bd);

  /**
   * Sets the field at the given FieldPath to the specified {@code Date} value.
   *
   * @param path path of the field that needs to be updated
   * @param d The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, ODate d);

  /**
   * Sets the field at the given FieldPath to the specified {@code Date} value.
   *
   * @param path path of the field that needs to be updated
   * @param d The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, ODate d);

  /**
   * Sets the field at the given FieldPath to the specified {@code Time} value.
   *
   * @param path path of the field that needs to be updated
   * @param t The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, OTime t);

  /**
   * Sets the field at the given FieldPath to the specified {@code Time} value.
   *
   * @param path path of the field that needs to be updated
   * @param t The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, OTime t);

  /**
   * Sets the field at the given FieldPath to the specified {@code Timestamp} value.
   *
   * @param path path of the field that needs to be updated
   * @param ts The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, OTimestamp ts);

  /**
   * Sets the field at the given FieldPath to the specified {@code Timestamp} value.
   *
   * @param path path of the field that needs to be updated
   * @param ts The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, OTimestamp ts);

  /**
   * Sets the field at the given FieldPath to the specified {@code Interval} value.
   *
   * @param path path of the field that needs to be updated
   * @param intv The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, OInterval intv);

  /**
   * Sets the field at the given FieldPath to the specified {@code Interval} value.
   *
   * @param path path of the field that needs to be updated
   * @param intv The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, OInterval intv);

  /**
   * Sets the field at the given FieldPath to the specified {@code ByteBuffer}.
   *
   * @param path path of the field that needs to be updated
   * @param bb The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, ByteBuffer bb);

  /**
   * Sets the field at the given FieldPath to the specified {@code ByteBuffer}.
   *
   * @param path path of the field that needs to be updated
   * @param bb The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, ByteBuffer bb);

  /**
   * Sets the field at the given FieldPath to the specified {@code List}.
   *
   * @param path path of the field that needs to be updated
   * @param list The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, List<? extends Object> list);

  /**
   * Sets the field at the given FieldPath to the specified {@code List}.
   *
   * @param path path of the field that needs to be updated
   * @param list The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, List<? extends Object> list);

  /**
   * Sets the field at the given FieldPath to the specified {@code Map}.
   *
   * @param path path of the field that needs to be updated
   * @param map The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, Map<String, ? extends Object> map);

  /**
   * Sets the field at the given FieldPath to the specified {@code Map}.
   *
   * @param path path of the field that needs to be updated
   * @param map The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, Map<String, ? extends Object> map);

  /**
   * Sets the field at the given FieldPath to the specified {@code Document}.
   *
   * @param path path of the field that needs to be updated
   * @param doc The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(String path, Document doc);

  /**
   * Sets the field at the given FieldPath to the specified {@code Document}.
   *
   * @param path path of the field that needs to be updated
   * @param doc The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation set(FieldPath path, Document doc);

  /**
   * Sets or replaces the field at the given FieldPath to {@link Type#NULL NULL} Value.
   *
   * @param path FieldPath in the document that needs to be updated
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplaceNull(String path);

  /**
   * Sets or replaces the field at the given FieldPath to {@link Type#NULL NULL} Value.
   *
   * @param path FieldPath in the document that needs to be updated
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplaceNull(FieldPath path);

  /**
   * Sets or replaces the field at the given FieldPath to the new value.
   *
   * @param path FieldPath in the document that needs to be updated
   * @param value The new value to set or replace at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, Value value);

  /**
   * Sets or replaces the field at the given FieldPath to the new value.
   *
   * @param path FieldPath in the document that needs to be updated
   * @param value The new value to set or replace at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, Value value);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code boolean} value.
   *
   * @param path path of the field that needs to be updated
   * @param b The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, boolean b);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code boolean} value.
   *
   * @param path path of the field that needs to be updated
   * @param b The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, boolean b);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code byte} value.
   *
   * @param path path of the field that needs to be updated
   * @param b The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, byte b);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code byte} value.
   *
   * @param path path of the field that needs to be updated
   * @param b The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, byte b);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code short} value.
   *
   * @param path path of the field that needs to be updated
   * @param s The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, short s);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code short} value.
   *
   * @param path path of the field that needs to be updated
   * @param s The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, short s);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code int} value.
   *
   * @param path path of the field that needs to be updated
   * @param i The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, int i);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code int} value.
   *
   * @param path path of the field that needs to be updated
   * @param i The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, int i);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code long} value.
   *
   * @param path path of the field that needs to be updated
   * @param l The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, long l);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code long} value.
   *
   * @param path path of the field that needs to be updated
   * @param l The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, long l);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code float} value.
   *
   * @param path path of the field that needs to be updated
   * @param f The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, float f);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code float} value.
   *
   * @param path path of the field that needs to be updated
   * @param f The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, float f);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code double} value.
   *
   * @param path path of the field that needs to be updated
   * @param d The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, double d);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code double} value.
   *
   * @param path path of the field that needs to be updated
   * @param d The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, double d);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code String} value.
   *
   * @param path path of the field that needs to be updated
   * @param string The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, String string);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code String} value.
   *
   * @param path path of the field that needs to be updated
   * @param string The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, String string);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code BigDecimal} value.
   *
   * @param path path of the field that needs to be updated
   * @param bd The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, BigDecimal bd);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code BigDecimal} value.
   *
   * @param path path of the field that needs to be updated
   * @param bd The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, BigDecimal bd);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code Date} value.
   *
   * @param path path of the field that needs to be updated
   * @param d The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, ODate d);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code Date} value.
   *
   * @param path path of the field that needs to be updated
   * @param d The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, ODate d);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code Time} value.
   *
   * @param path path of the field that needs to be updated
   * @param t The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, OTime t);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code Time} value.
   *
   * @param path path of the field that needs to be updated
   * @param t The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, OTime t);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code Timestamp} value.
   *
   * @param path path of the field that needs to be updated
   * @param ts The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, OTimestamp ts);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code Timestamp} value.
   *
   * @param path path of the field that needs to be updated
   * @param ts The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, OTimestamp ts);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code Interval}.
   *
   * @param path path of the field that needs to be updated
   * @param intv The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, OInterval intv);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code Interval}.
   *
   * @param path path of the field that needs to be updated
   * @param intv The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, OInterval intv);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code ByteBuffer}.
   *
   * @param path path of the field that needs to be updated
   * @param bb The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, ByteBuffer bb);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code ByteBuffer}.
   *
   * @param path path of the field that needs to be updated
   * @param bb The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, ByteBuffer bb);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code List}.
   *
   * @param path path of the field that needs to be updated
   * @param list The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, List<? extends Object> list);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code List}.
   *
   * @param path path of the field that needs to be updated
   * @param list The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, List<? extends Object> list);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code Map}.
   *
   * @param path path of the field that needs to be updated
   * @param map The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, Map<String, ? extends Object> map);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code Map}.
   *
   * @param path path of the field that needs to be updated
   * @param map The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, Map<String, ? extends Object> map);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code Document}.
   *
   * @param path path of the field that needs to be updated
   * @param doc The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(String path, Document doc);

  /**
   * Sets or replaces the field at the given FieldPath to the specified
   * {@code Document}.
   *
   * @param path path of the field that needs to be updated
   * @param doc The new value to set at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation setOrReplace(FieldPath path, Document doc);

  /**
   * Appends elements of the given list to an existing ARRAY at the given FieldPath.
   *
   * If the field doesn't exist on server, it will be created and will be set to
   * the specified List. If the field already exists, but is not of ARRAY type,
   * then this operation will fail.
   *
   * @param path path of the field that needs to be appended
   * @param list The List to append at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation append(String path, List<? extends Object> list);

  /**
   * Appends elements of the given list to an existing ARRAY at the given FieldPath.
   *
   * If the field doesn't exist on server, it will be created and will be set to
   * the specified List. If the field already exists, but is not of ARRAY type,
   * then this operation will fail.
   *
   * @param path path of the field that needs to be appended
   * @param list The List to append at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation append(FieldPath path, List<? extends Object> list);

  /**
   * Appends the given string to an existing STRING at the given FieldPath.
   *
   * If the field doesn't exist on server, it will be created and will be set to
   * the specified String. If the field already exists, but is not of STRING type,
   * then this operation will fail.
   *
   * @param path path of the field that needs to be appended
   * @param string The String to append at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation append(String path, String string);

  /**
   * Appends the given string to an existing STRING at the given FieldPath.
   *
   * If the field doesn't exist on server, it will be created and will be set to
   * the specified String. If the field already exists, but is not of STRING type,
   * then this operation will fail.
   *
   * @param path path of the field that needs to be appended
   * @param string The String to append at the FieldPath
   * @return {@code this} for chained invocation
   */
  public DocumentMutation append(FieldPath path, String string);

  /**
   * Appends the given byte array to an existing BINARY value at the given FieldPath.
   *
   * If the field doesn't exist on server, it will be created and will be set to
   * the BINARY value specified by the given byte array. If the field already exists,
   * but is not of BINARY type, then this operation will fail.
   *
   * @param path the FieldPath to apply this append operation
   * @param value the byte array to append
   * @param offset offset in byte array
   * @param len length in byte array
   * @return {@code this} for chained invocation
   */
  public DocumentMutation append(String path, byte[] value, int offset, int len);

  /**
   * Appends the given byte array to an existing BINARY value at the given FieldPath.
   *
   * If the field doesn't exist on server, it will be created and will be set to
   * the BINARY value specified by the given byte array. If the field already exists,
   * but is not of BINARY type, then this operation will fail.
   *
   * @param path the FieldPath to apply this append operation
   * @param value the byte array to append
   * @param offset offset in byte array
   * @param len length in byte array
   * @return {@code this} for chained invocation
   */
  public DocumentMutation append(FieldPath path, byte[] value, int offset, int len);

  /**
   * Appends the given byte array to an existing BINARY value at the given FieldPath.
   *
   * If the field doesn't exist on server, it will be created and will be set to
   * the BINARY value specified by the given byte array. If the field already exists,
   * but is not of BINARY type, then this operation will fail.
   *
   * @param path the FieldPath to apply this append operation
   * @param value the byte array to append
   * @return {@code this} for chained invocation
   */
  public DocumentMutation append(String path, byte[] value);

  /**
   * Appends the given byte array to an existing BINARY value at the given FieldPath.
   *
   * If the field doesn't exist on server, it will be created and will be set to
   * the BINARY value specified by the given byte array. If the field already exists,
   * but is not of BINARY type, then this operation will fail.
   *
   * @param path the FieldPath to apply this append operation
   * @param value the byte array to append
   * @return {@code this} for chained invocation
   */
  public DocumentMutation append(FieldPath path, byte[] value);

  /**
   * Appends the given ByteBuffer to an existing BINARY value at the given FieldPath.
   *
   * If the field doesn't exist on server, it will be created and will be set to
   * the BINARY value specified by the given ByteBuffer. If the field already exists,
   * but is not of BINARY type, then this operation will fail.
   *
   * @param path the FieldPath to apply this append operation
   * @param value the ByteBuffer to append
   * @return {@code this} for chained invocation
   */
  public DocumentMutation append(String path, ByteBuffer value);

  /**
   * Appends the given ByteBuffer to an existing BINARY value at the given FieldPath.
   *
   * If the field doesn't exist on server, it will be created and will be set to
   * the BINARY value specified by the given ByteBuffer. If the field already exists,
   * but is not of BINARY type, then this operation will fail.
   *
   * @param path the FieldPath to apply this append operation
   * @param value the ByteBuffer to append
   * @return {@code this} for chained invocation
   */
  public DocumentMutation append(FieldPath path, ByteBuffer value);

  /**
   * Merges the existing MAP at the given FieldPath with the specified Document.
   *
   * @param path the FieldPath to apply this merge operation
   * @param doc the document to be merged
   * @return {@code this} for chained invocation
   */
  public DocumentMutation merge(String path, Document doc);

  /**
   * Merges the existing MAP at the given FieldPath with the specified Document.
   *
   * @param path the FieldPath to apply this merge operation
   * @param doc the document to be merged
   * @return {@code this} for chained invocation
   */
  public DocumentMutation merge(FieldPath path, Document doc);

  /**
   * Merges the existing MAP at the given FieldPath with the specified Map.
   *
   * @param path the FieldPath to apply this merge operation
   * @param map the Map to be merged
   * @return {@code this} for chained invocation
   */
  public DocumentMutation merge(String path, Map<String, Object> map);

  /**
   * Merges the existing MAP at the given FieldPath with the specified Map.
   *
   * @param path the FieldPath to apply this merge operation
   * @param map the Map to be merged
   * @return {@code this} for chained invocation
   */
  public DocumentMutation merge(FieldPath path, Map<String, Object> map);

  /**
   * Atomically increment the existing value at given the FieldPath by the given value.
   *
   * @param path the FieldPath to apply this increment operation
   * @param inc increment to apply to a field - can be positive or negative
   */
  public DocumentMutation increment(FieldPath path, byte inc);

  /**
   * Atomically increment the existing value at given the FieldPath by the given value.
   *
   * @param path the FieldPath to apply this increment operation
   * @param inc increment to apply to a field - can be positive or negative
   */
  public DocumentMutation increment(String path, byte inc);

  /**
   * Atomically increment the existing value at given the FieldPath by the given value.
   *
   * @param path the FieldPath to apply this increment operation
   * @param inc increment to apply to a field - can be positive or negative
   */
  public DocumentMutation increment(FieldPath path, short inc);

  /**
   * Atomically increment the existing value at given the FieldPath by the given value.
   *
   * @param path the FieldPath to apply this increment operation
   * @param inc increment to apply to a field - can be positive or negative
   */
  public DocumentMutation increment(String path, short inc);

  /**
   * Atomically increment the existing value at given the FieldPath by the given value.
   *
   * @param path the FieldPath to apply this increment operation
   * @param inc increment to apply to a field - can be positive or negative
   */
  public DocumentMutation increment(String path, int inc);

  /**
   * Atomically increment the existing value at given the FieldPath by the given value.
   *
   * @param path the FieldPath to apply this increment operation
   * @param inc increment to apply to a field - can be positive or negative
   */
  public DocumentMutation increment(FieldPath path, int inc);

  /**
   * Atomically increment the existing value at given the FieldPath by the given value.
   *
   * @param path the FieldPath to apply this increment operation
   * @param inc increment to apply to a field - can be positive or negative
   */
  public DocumentMutation increment(FieldPath path, long inc);

  /**
   * Atomically increment the existing value at given the FieldPath by the given value.
   *
   * @param path the FieldPath to apply this increment operation
   * @param inc increment to apply to a field - can be positive or negative
   */
  public DocumentMutation increment(String path, long inc);

  /**
   * Atomically increment the existing value at given the FieldPath by the given value.
   *
   * @param path the FieldPath to apply this increment operation
   * @param inc increment to apply to a field - can be positive or negative
   */
  public DocumentMutation increment(String path, float inc);

  /**
   * Atomically increment the field specified by the FieldPath by the given value.
   *
   * @param path the FieldPath to apply this increment operation
   * @param inc increment to apply to a field - can be positive or negative
   */
  public DocumentMutation increment(FieldPath path, float inc);

  public DocumentMutation increment(String path, double inc);

  /**
   * Atomically increment the existing value at given the FieldPath by the given value.
   *
   * @param path the FieldPath to apply this increment operation
   * @param inc increment to apply to a field - can be positive or negative
   */
  public DocumentMutation increment(FieldPath path, double inc);

  /**
   * Atomically increment the existing value at given the FieldPath by the given value.
   *
   * @param path the FieldPath to apply this increment operation
   * @param inc increment to apply to a field - can be positive or negative
   */
  public DocumentMutation increment(String path, BigDecimal inc);

  /**
   * Atomically increment the existing value at given the FieldPath by the given value.
   *
   * @param path the FieldPath to apply this increment operation
   * @param inc increment to apply to a field - can be positive or negative
   */
  public DocumentMutation increment(FieldPath path, BigDecimal inc);

  /**
   * Deletes the field at the given path.
   *
   * If the field does not exist, the mutation operation will silently succeed.
   * For example, if a delete operation is attempted on {@code "a.b.c"}, and the
   * field {@code "a.b"} is an array, then {@code "a.b.c"} will not be deleted.
   *
   * @param path the FieldPath to delete
   */
  public DocumentMutation delete(String path);

  /**
   * Deletes the field at the given path.
   *
   * If the field does not exist, the mutation operation will silently succeed.
   * For example, if a delete operation is attempted on {@code "a.b.c"}, and the
   * field {@code "a.b"} is an array, then {@code "a.b.c"} will not be deleted.
   *
   * @param path the FieldPath to delete
   */
  public DocumentMutation delete(FieldPath path);

}
