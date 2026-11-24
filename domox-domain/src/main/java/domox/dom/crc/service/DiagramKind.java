package domox.dom.crc.service;

import domox.dom.crc.model.DomainModel;
import generate.ClassDiagram;

import java.util.Arrays;
import java.util.List;

/**
 * <b>Which class diagram a request is for — and everything that differs between the three.</b>
 *
 * <p>The kind is a value the three diagram actions and the PlantUML download all carry (it is the
 * {@code kind} of a {@link UmlClassDiagram} memento), and the fact behind it used to be re-<em>switched</em>
 * in five methods across three classes: the two {@link ClassDiagram} assembler calls, the two wordings of
 * the diagram's name, and the download's file name.  A fourth view of the model therefore meant finding all
 * five, in three files.  They are one place now — a constant carries its own assembler calls and its own
 * words, and {@link #of(String)} is the only way in.</p>
 *
 * <p><b>Two forms, one content.</b>  {@link #plantUml} is the text
 * ({@link DomainModels#downloadPlantUml}) and {@link #graph} is the picture the D3 panel draws; both read
 * the same class list and the same collaboration list of the <em>one</em> {@link ClassDiagram} the kinds
 * share, so the download and the picture cannot disagree about what the model contains.  That instance is
 * stateless, so the kinds hold it rather than each call site new-ing its own (see {@link #ASSEMBLER}).</p>
 *
 * <p>Package-private: it is the internal collaboration of {@link DomainModels}, {@link UmlClassDiagram} and
 * {@link UmlDiagramSet}, not a member of the model's own metamodel.</p>
 */
enum DiagramKind {

    /**
     * The whole model, flat — every included class and every material edge between two of them.
     *
     * <p>Also what an <em>unset</em> or unknown kind reads as (see {@link #of(String)}): it is the diagram
     * with no other parameter to get wrong.</p>
     */
    MODEL("model") {

        @Override
        ClassDiagram.Graph graph(final DomainModel domainModel, final String subDomainName) {
            return ASSEMBLER.graph(domainModel);
        }

        @Override
        String plantUml(final DomainModel domainModel, final String subDomainName) {
            return ASSEMBLER.build(domainModel);
        }

        @Override
        String label(final String subDomainName) {
            return "Whole model";
        }

        @Override
        String describe(final String subDomainName) {
            return "whole model";
        }

        @Override
        String fileName(final String subDomainName) {
            return "domain-model.puml";
        }
    },

    /**
     * One sub-domain: its included classes and the material edges <em>wholly inside</em> it — the
     * "one diagram per sub-domain" of the assembly pass.
     */
    SUB_DOMAIN("sub-domain") {

        @Override
        ClassDiagram.Graph graph(final DomainModel domainModel, final String subDomainName) {
            return ASSEMBLER.graphForSubDomain(domainModel, subDomainName);
        }

        @Override
        String plantUml(final DomainModel domainModel, final String subDomainName) {
            return ASSEMBLER.buildForSubDomain(domainModel, subDomainName);
        }

        @Override
        String label(final String subDomainName) {
            return "Sub-domain: " + subDomainOrUnnamed(subDomainName);
        }

        @Override
        String describe(final String subDomainName) {
            return "sub-domain " + subDomainOrUnnamed(subDomainName);
        }

        @Override
        String fileName(final String subDomainName) {
            return "domain-model-" + (subDomainName == null ? "sub-domain" : subDomainName) + ".puml";
        }
    },

    /** The sub-domains as nodes and their cross-sub-domain relations as edges — the map of the model. */
    SUB_DOMAIN_OVERVIEW("sub-domain-overview") {

        @Override
        ClassDiagram.Graph graph(final DomainModel domainModel, final String subDomainName) {
            return ASSEMBLER.graphOverview(domainModel);
        }

        @Override
        String plantUml(final DomainModel domainModel, final String subDomainName) {
            return ASSEMBLER.buildSubDomainOverview(domainModel);
        }

        @Override
        String label(final String subDomainName) {
            return "Sub-domain overview";
        }

        @Override
        String describe(final String subDomainName) {
            return "sub-domain overview";
        }

        @Override
        String fileName(final String subDomainName) {
            return "domain-model-sub-domains.puml";
        }
    };

    /**
     * The one {@link ClassDiagram} the kinds share — it is stateless, so a new one at every call site bought
     * nothing but an inconsistency with the collaborators the callers inject.
     *
     * <p>Declared after the constants on purpose: an enum constant may not read a static field while the
     * constants are being constructed, but these method bodies only ever run later.</p>
     */
    private static final ClassDiagram ASSEMBLER = new ClassDiagram();

    private final String key;

    DiagramKind(final String key) {
        this.key = key;
    }

    /**
     * The memento value of this kind — the string a caller passes and a bookmark carries, named by the
     * {@code KIND_*} constants of {@link UmlClassDiagram}.
     */
    String key() {
        return key;
    }

    /**
     * The kind a stored or requested {@code kind} names.
     *
     * <p>{@code null}, a blank string and anything unrecognised all read as {@link #MODEL} — the diagram
     * that needs no other parameter, so a caller cannot get the fallback wrong.</p>
     */
    static DiagramKind of(final String kind) {
        for (final DiagramKind candidate : values()) {
            if (candidate.key.equals(kind)) {
                return candidate;
            }
        }
        return MODEL;
    }

    /** Every kind's memento value, in declaration order — the choices a caller may ask for. */
    static List<String> keys() {
        return Arrays.stream(values()).map(DiagramKind::key).toList();
    }

    /**
     * The sub-domains of a model that would produce a non-empty diagram — a question about the
     * <em>model</em> rather than about a kind, asked here because the kinds already share the one
     * {@link ClassDiagram} that answers it.
     */
    static List<String> subDomainNames(final DomainModel domainModel) {
        return ASSEMBLER.subDomainNames(domainModel);
    }

    /** The diagram as the picture the D3 panel draws — the boxes and the connectors between them. */
    abstract ClassDiagram.Graph graph(DomainModel domainModel, String subDomainName);

    /** The same diagram as PlantUML text — what {@link DomainModels#downloadPlantUml} serves as a file. */
    abstract String plantUml(DomainModel domainModel, String subDomainName);

    /** The diagram's own name — what a reader sees above it, and the label that carries the sub-domain. */
    abstract String label(String subDomainName);

    /** The same name as a sentence fragment, for {@link UmlClassDiagram#title()}. */
    abstract String describe(String subDomainName);

    /** The download name of this diagram's PlantUML — named for the diagram, so two exports differ. */
    abstract String fileName(String subDomainName);

    /** The sub-domain as the two wordings above say it — shared, so they cannot name it differently. */
    private static String subDomainOrUnnamed(final String subDomainName) {
        return subDomainName == null ? "(unnamed)" : subDomainName;
    }
}
