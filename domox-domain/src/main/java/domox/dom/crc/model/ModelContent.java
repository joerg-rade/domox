package domox.dom.crc.model;

import java.util.List;

/**
 * The <b>assembled model</b>: the one answer to "what is part of the model?".
 *
 * <p>Before this existed, three places answered that question differently — the export rendered every
 * candidate the analysis produced, the D3 diagram seeded from the approved set, and the review counted
 * its own approvals — which is how the model came to claim 168 operation lines while only 85 were
 * approved ({@code MODEL_UML_READINESS_REVIEW.md} §2.1).  Everything that needs the answer now reads
 * it here: the PlantUML export ({@code generate.ClassDiagram}) and the empty-class sweep
 * ({@code DomainModels#rejectEmptyClasses}).</p>
 *
 * <p><b>Why it is not a method on {@link ClassCdd}.</b>  Whether a class is <em>connected</em> is a
 * property of the model: ingestion attaches each {@link CollaboratorCdd} to its source only
 * ({@code CollaboratorCandidates}), so a class that is merely the <em>target</em> of a relation has an
 * empty {@code collaboratorList} of its own yet is unmistakably part of the diagram — it is one end of
 * a drawn edge.  Reading the model's collaboration list is the only way to see both ends.</p>
 *
 * <p>Pure and static: no state, no injected services, so it can be reasoned about (and tested) on
 * hand-built entities.</p>
 */
public final class ModelContent {

    private ModelContent() {
    }

    /**
     * The model's collaborations that are <b>material</b>: approved, and drawn between two approved
     * classes ({@link CollaboratorCdd#connectsApprovedClasses()}).
     *
     * <p>Iterating the model's list (rather than each class's own) is also what makes an association
     * appear exactly once, whichever end owns it.</p>
     */
    public static List<CollaboratorCdd> materialCollaborations(final DomainModel model) {
        if (model == null || model.collaboratorList == null) {
            return List.of();
        }
        return model.collaboratorList.stream()
                .filter(collaboration -> collaboration != null && collaboration.connectsApprovedClasses())
                .toList();
    }

    /**
     * The classes the assembled model contains: <b>approved</b>, and either carrying approved content
     * of their own ({@link ClassCdd#hasOwnModelledContent()}) or participating in a material
     * collaboration on either end.
     *
     * <p>A {@code null} model or class list counts as empty, so a hand-built or half-loaded model
     * renders an empty diagram instead of throwing.</p>
     */
    public static List<ClassCdd> includedClasses(final DomainModel model) {
        if (model == null || model.classList == null) {
            return List.of();
        }
        final List<CollaboratorCdd> material = materialCollaborations(model);
        return model.classList.stream()
                .filter(clazz -> clazz != null && clazz.isApproved())
                .filter(clazz -> isIncluded(clazz, material))
                .toList();
    }

    /**
     * The approved classes the assembled model does <em>not</em> contain — the classes the pipeline
     * rejects as "empty boxes" ({@code REVIEW_PLAYBOOK.md} §4.6).
     *
     * <p>The complement of {@link #includedClasses(DomainModel)} within the approved classes, and
     * computed from the same predicate, so the export and the sweep can never disagree about which
     * class is empty.</p>
     */
    public static List<ClassCdd> emptyClasses(final DomainModel model) {
        if (model == null || model.classList == null) {
            return List.of();
        }
        final List<CollaboratorCdd> material = materialCollaborations(model);
        return model.classList.stream()
                .filter(clazz -> clazz != null && clazz.isApproved())
                .filter(clazz -> !isIncluded(clazz, material))
                .toList();
    }

    /** Approved content of its own, or an end of a material collaboration. */
    private static boolean isIncluded(final ClassCdd clazz, final List<CollaboratorCdd> material) {
        return clazz.hasOwnModelledContent() || participatesIn(clazz, material);
    }

    /** Whether {@code clazz} is the source or the target of one of {@code material}. */
    private static boolean participatesIn(final ClassCdd clazz, final List<CollaboratorCdd> material) {
        for (final CollaboratorCdd collaboration : material) {
            if (collaboration.getSource() == clazz || collaboration.getTarget() == clazz) {
                return true;
            }
        }
        return false;
    }
}
