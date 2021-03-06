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
package org.ojai;

import org.ojai.annotation.API;
import org.ojai.exceptions.OjaiException;

import java.util.Iterator;

/**
 * A stream of documents.
 *
 * Implements Iterable&lt;Document&gt; but only one call is allows to iterator()
 * or readerIterator(). Only one of these iterators can be retrieved
 * from the stream.
 */
@API.Public
public interface DocumentStream extends AutoCloseable, Iterable<Document> {

  /**
   * Stream all the documents in this {@code DocumentStream} to the specified
   * listener.
   *
   * @param listener a {@code DocumentListener} which is notified of
   *        {@code Document}s as they arrive
   */
  public void streamTo(DocumentListener listener);

  /**
   * Returns an iterator over a set of {@code Document}.
   */
  Iterator<Document> iterator();

  /**
   * Returns an {@code Iterable} over a set of {@code DocumentReader}.
   */
  Iterable<DocumentReader> documentReaders();

  /**
   * Overridden to remove checked exception
   */
  void close() throws OjaiException;
}
