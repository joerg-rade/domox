package domox.dom.crc.service;
import domox.DomainModule;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.SubDomain;
import domox.dom.crc.model.SubDomainClassification;
import domox.dom.crc.repository.SubDomainRepository;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.Optionality;
import org.apache.causeway.applib.annotation.Parameter;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The sub-domain reference data: the named business / functional areas that {@link ClassCdd class
 * candidates} are assigned to during review.
 *
 * <p><b>Two halves.</b>  The Causeway actions ({@link #listAll()}, {@link #create(String,
 * SubDomainClassification)}) are what a human reviewer uses in the UI; the {@link Programmatic}
 * methods are what the automated (MCP) sub-domain pass uses.  The programmatic half is deliberately
 * <em>idempotent</em> — {@link #findOrCreate(String, SubDomainClassification)} and
 * {@link #assignAll(List, SubDomain)} can be re-run over a corpus without producing a second
 * sub-domain or a duplicated assignment, which is what makes the pass safe to repeat after the
 * clustering has been adjusted.</p>
 */
@DomainService
@Named(DomainModule.NAMESPACE + ".SubDomains")
@Priority(PriorityPrecedence.EARLY)
public class SubDomains {

    /**
     * The classification a sub-domain gets when the caller does not supply one — deliberately
     * {@code UNCLASSIFIED} rather than a guess, so an undecided area is visibly undecided.
     */
    public static final SubDomainClassification DEFAULT_CLASSIFICATION = SubDomainClassification.UNCLASSIFIED;

    private final RepositoryService repositoryService;
    private final SubDomainRepository subDomainRepository;

    @Inject
    public SubDomains(RepositoryService repositoryService, SubDomainRepository subDomainRepository) {
        this.repositoryService = repositoryService;
        this.subDomainRepository = subDomainRepository;
    }

    @ActionLayout(sequence = "1")
    public List<SubDomain> listAll() {
        return repositoryService.allInstances(SubDomain.class);
    }

    @ActionLayout(sequence = "2")
    public SubDomain create(
            @Parameter(optionality = Optionality.MANDATORY)
            final String name,
            @Parameter(optionality = Optionality.OPTIONAL)
            final SubDomainClassification classification) {
        return createClassified(name, classification != null ? classification : DEFAULT_CLASSIFICATION);
    }

    @Programmatic
    public SubDomain findByName(final String name) {
        return name == null ? null : subDomainRepository.findByName(name);
    }

    /** The sub-domain with this name (case-insensitively), or {@code null} when there is none. */
    @Programmatic
    public SubDomain findByNameIgnoreCase(final String name) {
        if (name == null) {
            return null;
        }
        final List<SubDomain> matches = subDomainRepository.findAllByNameIgnoreCaseOrderByIdAsc(name.trim());
        return matches.isEmpty() ? null : matches.getFirst();
    }

    /**
     * The existing sub-domain with this name, or a newly created {@code UNCLASSIFIED} one —
     * idempotent, so a repeated assignment pass does not duplicate the reference row.
     */
    @Programmatic
    public SubDomain findOrCreate(final String name) {
        return findOrCreate(name, DEFAULT_CLASSIFICATION);
    }

    /**
     * The existing sub-domain with this name, or a newly created one with this classification.
     * <p>
     * The name is matched case-insensitively, but an <em>existing</em> sub-domain keeps the
     * classification it already has: the caller supplied a classification for the area it is
     * creating, not a re-classification of one that is already there.
     */
    @Programmatic
    public SubDomain findOrCreate(final String name, final SubDomainClassification classification) {
        final SubDomain existing = findByNameIgnoreCase(name);
        return existing != null ? existing : createClassified(name, classification);
    }

    /** Creates a sub-domain with an explicit classification. */
    @Programmatic
    public SubDomain createClassified(final String name, final SubDomainClassification classification) {
        final SubDomain obj = new SubDomain();
        obj.setName(requireName(name));
        obj.setClassification(classification != null ? classification : DEFAULT_CLASSIFICATION);
        repositoryService.persist(obj);
        return obj;
    }

    /** Re-classifies an existing sub-domain. */
    @Programmatic
    public SubDomain classify(final SubDomain subDomain, final SubDomainClassification classification) {
        subDomain.setClassification(classification != null ? classification : DEFAULT_CLASSIFICATION);
        repositoryService.persist(subDomain);
        return subDomain;
    }

    /** Assigns one class candidate to a sub-domain. */
    @Programmatic
    public ClassCdd assign(final ClassCdd candidate, final SubDomain subDomain) {
        candidate.setSubDomain(subDomain);
        repositoryService.persist(candidate);
        return candidate;
    }

    /**
     * Assigns every given class candidate to one sub-domain, in the order given.
     *
     * @return the candidates that were assigned, in the same order
     */
    @Programmatic
    public List<ClassCdd> assignAll(final List<ClassCdd> candidates, final SubDomain subDomain) {
        final List<ClassCdd> assigned = new ArrayList<>();
        if (candidates == null) {
            return assigned;
        }
        for (final ClassCdd candidate : candidates) {
            if (candidate != null) {
                assigned.add(assign(candidate, subDomain));
            }
        }
        return assigned;
    }

    /** Removes the sub-domain assignment from one class candidate. */
    @Programmatic
    public ClassCdd clearSubDomain(final ClassCdd candidate) {
        candidate.setSubDomain(null);
        repositoryService.persist(candidate);
        return candidate;
    }

    /** Every class candidate, ordered by id. */
    @Programmatic
    public List<ClassCdd> listAllClasses() {
        return repositoryService.allInstances(ClassCdd.class).stream()
                .sorted(Comparator.comparingLong(ClassCdd::getId))
                .toList();
    }

    /** Every class candidate that has no sub-domain yet — the input to a sub-domain pass. */
    @Programmatic
    public List<ClassCdd> listUnassigned() {
        return listAllClasses().stream()
                .filter(candidate -> candidate.getSubDomain() == null)
                .toList();
    }

    /** Every class candidate assigned to the given sub-domain, ordered by id. */
    @Programmatic
    public List<ClassCdd> listAssignedTo(final SubDomain subDomain) {
        if (subDomain == null) {
            return List.of();
        }
        return listAllClasses().stream()
                .filter(candidate -> candidate.getSubDomain() != null
                        && subDomain.getId() != null
                        && subDomain.getId().equals(candidate.getSubDomain().getId()))
                .toList();
    }

    private static String requireName(final String name) {
        final String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("A sub-domain name must not be blank");
        }
        return trimmed;
    }
}
