package domox.dom.requirements;

import domox.dom.crc.model.DomainModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorpusRepository extends JpaRepository<Corpus, Long> {

    List<Corpus> findByTitleContaining(final String title);

    Corpus findByTitle(final String title);

    /**
     * The corpus (or corpora) that own {@code domainModel}.
     *
     * <p><b>Why a {@code List} and not a single {@code Corpus}.</b>  The relationship is a
     * {@code @OneToOne} on the <em>owning</em> side ({@link Corpus#getDomainModel()}) whose join
     * column carries no unique constraint, so the database does not actually forbid two corpora
     * pointing at one model.  A single-result finder would then throw
     * {@code IncorrectResultSizeDataAccessException} at render time — i.e. a corrupt row would break
     * the UI instead of merely being visible in it.</p>
     */
    List<Corpus> findByDomainModel(final DomainModel domainModel);

}
