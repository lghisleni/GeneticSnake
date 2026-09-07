package geneticsAlg;

import org.uncommons.maths.random.Probability;
import org.uncommons.watchmaker.framework.SelectionStrategy;
import org.uncommons.watchmaker.framework.selection.RankSelection;
import org.uncommons.watchmaker.framework.selection.RouletteWheelSelection;
import org.uncommons.watchmaker.framework.selection.SigmaScaling;
import org.uncommons.watchmaker.framework.selection.StochasticUniversalSampling;
import org.uncommons.watchmaker.framework.selection.TournamentSelection;
import org.uncommons.watchmaker.framework.selection.TruncationSelection;

public enum SelectionMethod {

    TOURNAMENT("Tournament", "Buon equilibrio tra pressione selettiva e diversita"),
    ROULETTE("Roulette Wheel", "Probabilita proporzionale alla fitness"),
    RANK("Rank", "Selezione basata sulla posizione in classifica"),
    SUS("Stochastic Universal Sampling", "Versione piu regolare della roulette"),
    TRUNCATION("Truncation", "Riproduce solo la parte migliore della popolazione"),
    SIGMA("Sigma Scaling", "Ridimensiona la fitness per limitare convergenza prematura");

    private final String displayName;
    private final String description;

    SelectionMethod(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public SelectionStrategy<Object> createStrategy() {
        switch (this) {
            case TOURNAMENT:
                return new TournamentSelection(new Probability(0.75));
            case ROULETTE:
                return new RouletteWheelSelection();
            case RANK:
                return new RankSelection();
            case SUS:
                return new StochasticUniversalSampling();
            case TRUNCATION:
                return new TruncationSelection(0.50);
            case SIGMA:
                return new SigmaScaling();
            default:
                throw new IllegalStateException("Strategia non gestita: " + this);
        }
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static SelectionMethod fromString(String value) {
        if (value == null) {
            return TOURNAMENT;
        }

        String normalized = value.trim().toLowerCase();
        switch (normalized) {
            case "tournament":
            case "torneo":
                return TOURNAMENT;
            case "roulette":
            case "roulette-wheel":
                return ROULETTE;
            case "rank":
            case "ranking":
                return RANK;
            case "sus":
            case "stochastic":
                return SUS;
            case "truncation":
            case "troncamento":
                return TRUNCATION;
            case "sigma":
            case "sigma-scaling":
                return SIGMA;
            default:
                throw new IllegalArgumentException(
                        "Strategia sconosciuta: " + value
                                + ". Usa tournament, roulette, rank, sus, truncation o sigma."
                );
        }
    }
}

