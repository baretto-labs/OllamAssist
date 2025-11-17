package fr.baretto.ollamassist.chat.rag;

import dev.langchain4j.store.embedding.filter.Filter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

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
     * @return A Lucene {@link TermQuery} targeting the "id" field.
     */
    public Query toLuceneQuery() {
        return new TermQuery(new Term("id", id));
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
