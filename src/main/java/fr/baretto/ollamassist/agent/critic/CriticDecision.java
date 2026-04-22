package fr.baretto.ollamassist.agent.critic;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.baretto.ollamassist.agent.plan.Phase;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class CriticDecision {

    public enum Status { OK, ADAPT, ABORT }

    private final Status status;
    private final String reasoning;
    private final List<Phase> revisedPhases;

    @JsonCreator
    public CriticDecision(
            @JsonProperty("status") Status status,
            @JsonProperty("reasoning") String reasoning,
            @JsonProperty("revisedPhases") List<Phase> revisedPhases) {
        this.status = status != null ? status : Status.ABORT;
        this.reasoning = reasoning != null ? reasoning : "";
        this.revisedPhases = revisedPhases != null
                ? Collections.unmodifiableList(revisedPhases)
                : Collections.emptyList();
    }

    public Status getStatus() {
        return status;
    }

    public String getReasoning() {
        return reasoning;
    }

    /**
     * Non-empty only when status is ADAPT.
     * Contains the replacement phases to continue execution with.
     */
    public List<Phase> getRevisedPhases() {
        return revisedPhases;
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    public boolean shouldAbort() {
        return status == Status.ABORT;
    }

    public boolean shouldAdapt() {
        return status == Status.ADAPT;
    }

    @Override
    public String toString() {
        return "CriticDecision{status=" + status + ", reasoning='" + reasoning + "'}";
    }
}
