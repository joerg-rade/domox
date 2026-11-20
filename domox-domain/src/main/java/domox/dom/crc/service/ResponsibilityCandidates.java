package domox.dom.crc.service;
import domox.DomainModule;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.model.ResponsibilityParameter;
import domox.dom.crc.repository.ResponsibilityCddRepository;
import domox.dom.rules.NlpProperties;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@DomainService
@Named(DomainModule.NAMESPACE + ".ResponsibilityCandidates")
@Priority(PriorityPrecedence.EARLY)
public class ResponsibilityCandidates {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final ResponsibilityCddRepository responsibilityCddRepository;
    private final NlpProperties nlpProperties;

    @Inject
    public ResponsibilityCandidates(RepositoryService repositoryService, FactoryService factoryService, ResponsibilityCddRepository responsibilityCddRepository, NlpProperties nlpProperties) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.responsibilityCddRepository = responsibilityCddRepository;
        this.nlpProperties = nlpProperties;
    }

    // Internal helper (exposed in the UI via the "Candidates" menu service).
    @Programmatic
    public List<ResponsibilityCdd> listAll() {
        return repositoryService.allInstances(ResponsibilityCdd.class).stream()
                .sorted(Comparator.comparingInt(Candidate::getRuleMatchCount).reversed())
                .toList();
    }

    @Programmatic
    public ResponsibilityCdd findByCandidateName(String candidateName) {
        return responsibilityCddRepository.findByCandidateName(candidateName);
    }

    @Programmatic
    public ResponsibilityCdd create(String candidateName) {
        return create(candidateName, null);
    }

    @Programmatic
    public ResponsibilityCdd create(String candidateName, DomainModel domainModel) {
        return create(candidateName, null, domainModel);
    }

    @Programmatic
    public ResponsibilityCdd create(String candidateName, ClassCdd classCdd, DomainModel domainModel) {
        final ResponsibilityCdd obj = factoryService.detachedEntity(ResponsibilityCdd.class);
        obj.setCandidateName(candidateName);
        obj.setCandidateType("ActionCdd");
        obj.setOutputType(outputTypeFor(candidateName, classCdd));
        obj.classCdd = classCdd;
        obj.domainModel = domainModel;
        if (classCdd != null) {
            classCdd.responsibilityList.add(obj);
        }
        if (domainModel != null) {
            domainModel.responsibilityList.add(obj);
        }
        repositoryService.persist(obj);
        return obj;
    }

    /**
     * The UML return type of the operation named {@code verb} — the verb→return-type mapping of
     * FIX-3b (`UML_GAP_FIX_PLAN.md` §13.2), which resolves the one open decision of the §13 table.
     * <p>
     * {@code outputType} is the operation's <em>return type</em>, rendered after the parameter list
     * ({@code name(params): outputType}) — not the CRC element type, which lives in
     * {@link Candidate#getCandidateType()}. Three cases, from the configured verb catalogues:
     * <ul>
     *   <li><b>a receive verb</b> ({@code receive}, {@code accept}, {@code obtain}, …) → the
     *       <em>received class</em>, i.e. the class the operation is attached to; the sentence's
     *       direct object is what the operation receives, and the factory makes it the owner.</li>
     *   <li><b>an output/notify verb</b> ({@code display}, {@code show}, {@code print}, …) →
     *       {@code String} — the operation produces something to read.</li>
     *   <li><b>otherwise</b> → no return type ({@code null}, which
     *       {@link ResponsibilityCdd#toPlantUmlString()} omits).</li>
     * </ul>
     * Deliberately no {@code void}: the renderer omits blank/{@code void} anyway, and a real
     * {@code null} says "the mapping had no answer" rather than "the mapping answered void".
     *
     * @param verb  the operation name, as the rule recorded it (the catalogues are lower-case, and
     *              TDR32/TDR33 capitalise the name, so the lookup lower-cases it)
     * @param owner the class the operation is attached to, or {@code null} for an orphan operation
     */
    @Programmatic
    String outputTypeFor(final String verb, final ClassCdd owner) {
        if (verb == null || nlpProperties == null) {
            return null;
        }
        final String lower = verb.toLowerCase(Locale.ROOT);
        if (contains(nlpProperties.getReceiveVerbs(), lower)) {
            return owner != null ? owner.getCandidateName() : null;
        }
        if (contains(nlpProperties.getSystemOutputVerbs(), lower)) {
            return "String";
        }
        return null;
    }

    /** Null-safe, case-insensitive membership — the catalogues are unbound in some unit tests. */
    private static boolean contains(final List<String> catalogue, final String verb) {
        if (catalogue == null) {
            return false;
        }
        for (final String entry : catalogue) {
            if (entry != null && entry.trim().toLowerCase(Locale.ROOT).equals(verb)) {
                return true;
            }
        }
        return false;
    }

    @Programmatic
    public ResponsibilityCdd findByCandidateName(String candidateName, DomainModel domainModel) {
        if (domainModel == null) {
            return findByCandidateName(candidateName);
        }
        return responsibilityCddRepository.findByCandidateNameAndDomainModel(candidateName, domainModel);
    }

    @Programmatic
    public ResponsibilityCdd findOrCreate(final String candidateName) {
        return findOrCreate(candidateName, null, null);
    }

    @Programmatic
    public ResponsibilityCdd findOrCreate(final String candidateName, final DomainModel domainModel) {
        return findOrCreate(candidateName, null, domainModel);
    }

    @Programmatic
    public ResponsibilityCdd findOrCreate(final String candidateName, final ClassCdd classCdd, final DomainModel domainModel) {
        ResponsibilityCdd candidate = findByCandidateName(candidateName, domainModel);
        if (candidate == null) {
            candidate = create(candidateName, classCdd, domainModel);
        }
        return candidate;
    }

    /**
     * Adds one parameter to an operation, if that operation does not already carry a parameter of that
     * name.
     * <p>
     * Idempotent on purpose: Phase 2 may be re-run over the same matches, and {@code findOrCreate}
     * returns the <em>existing</em> operation on a re-run — appending blindly would duplicate every
     * parameter on every pass.
     *
     * @param responsibility the operation the parameter belongs to (ignored when {@code null})
     * @param name           the parameter name, as the sentence states it
     * @param type           the parameter type (a class name, or an inferred built-in type)
     * @return the parameter, or {@code null} when there is no operation or no name
     */
    @Programmatic
    public ResponsibilityParameter addParameter(
            final ResponsibilityCdd responsibility, final String name, final String type) {
        if (responsibility == null || name == null) {
            return null;
        }
        final ResponsibilityParameter existing = responsibility.inputTypeList.stream()
                .filter(parameter -> name.equals(parameter.getCandidateName()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        final ResponsibilityParameter parameter =
                factoryService.detachedEntity(ResponsibilityParameter.class);
        parameter.setCandidateName(name);
        parameter.setCandidateType("ParameterCdd");
        parameter.setType(type);
        parameter.responsibilityCdd = responsibility;
        responsibility.inputTypeList.add(parameter);
        repositoryService.persist(parameter);
        return parameter;
    }

}