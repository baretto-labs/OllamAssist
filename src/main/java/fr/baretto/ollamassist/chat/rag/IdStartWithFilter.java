package fr.baretto.ollamassist.chat.rag;

import dev.langchain4j.store.embedding.filter.Filter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;

/**
 * A filter to match documents with a specific ID.
 */
@RequiredArgsConstructor
@Getter
public class IdStartWithFilter implements Filter {

    private final String id;

    /**
     * Converts this filter into a Lucene query.
     *
     * @return A Lucene {@link PrefixQuery} on the "id" field: a segment id is the file path
     * followed by a UUID, so an exact match on the path never matches anything.
     */
    public Query toLuceneQuery() {
        return new PrefixQuery(new Term("id", id));
    }

    @Override
    public boolean test(Object object) {
        if (object instanceof String value) {
            return value.startsWith(id);
        }
        return false;
    }

    private static final String TO_STRING_FORMAT = "IdEqualsFilter{id='%s'}";

    @Override
    public String toString() {
        return String.format(TO_STRING_FORMAT, id);
    }
}
